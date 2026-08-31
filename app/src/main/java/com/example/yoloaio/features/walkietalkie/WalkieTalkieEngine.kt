package com.example.yoloaio.features.walkietalkie

import android.content.Context
import android.media.AudioManager
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.firestore.DocumentChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.AudioTrack
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Owns the raw WebRTC plumbing for one WalkieTalkie screen visit: the
 * [PeerConnectionFactory], the local mic track when transmitting, and one
 * [PeerConnection] per remote peer (the transmitter fans out one connection
 * per listener; the receiver has exactly one, to the transmitter).
 *
 * Plain class (this codebase has no ViewModel/DI usage anywhere — state
 * lives in Compose `mutableStateOf` inside plain classes, same as
 * `MicAnalyzer`). Call [stop] from `DisposableEffect(Unit) { onDispose {} }`
 * so navigating away always tears everything down.
 */
class WalkieTalkieEngine(private val appContext: Context) {

    private val tag = "WalkieTalkieEngine"
    private val repository = WalkieTalkieRepository()
    private val myUid: String? get() = com.example.yoloaio.data.FirebaseModule.auth.currentUser?.uid

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var factory: PeerConnectionFactory? = null
    private var localAudioTrack: AudioTrack? = null

    private val transmitConnections = mutableMapOf<String, PeerConnection>()
    // receiverUid -> was this session opened by an admin browsing live
    // channels (WalkieSessionDoc.isAdminMonitor)? Excluded from the
    // listener count shown to the transmitter — see nonAdminListenerCount().
    private val adminMonitorFlags = mutableMapOf<String, Boolean>()
    private var receiveConnection: PeerConnection? = null

    private fun nonAdminListenerCount(): Int =
        transmitConnections.keys.count { adminMonitorFlags[it] != true }

    private var sessionsJob: Job? = null
    private var heartbeatJob: Job? = null
    private var receiveJob: Job? = null
    private var perReceiverJobs = mutableListOf<Job>()

    var status by mutableStateOf<WalkieStatus>(WalkieStatus.Idle)
        private set

    private var activeCode: String? = null
    private var activeRole: WalkieRole? = null

    private var previousAudioMode: Int? = null
    private var previousSpeakerphoneOn: Boolean? = null

    /**
     * WebRTC's JavaAudioDeviceModule plays received audio on the voice-call
     * stream, which Android routes to the earpiece (near-silent unless held
     * to your ear) unless the app explicitly asks for speakerphone +
     * communication mode. Without this, transfer/receive negotiate and
     * "connect" successfully but nothing audible comes out.
     */
    private fun applyCallAudioRouting() {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (previousAudioMode == null) {
            previousAudioMode = am.mode
            previousSpeakerphoneOn = am.isSpeakerphoneOn
        }
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        am.isSpeakerphoneOn = true
    }

    private fun restoreAudioRouting() {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        previousAudioMode?.let { am.mode = it }
        previousSpeakerphoneOn?.let { am.isSpeakerphoneOn = it }
        previousAudioMode = null
        previousSpeakerphoneOn = null
    }

    private fun ensureFactory(): PeerConnectionFactory {
        factory?.let { return it }
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .createInitializationOptions()
        )
        val adm = JavaAudioDeviceModule.builder(appContext).createAudioDeviceModule()
        val created = PeerConnectionFactory.builder()
            .setAudioDeviceModule(adm)
            .createPeerConnectionFactory()
        factory = created
        return created
    }

    // ── Transmit ─────────────────────────────────────────────────────────

    fun startTransfer(code: String, iceServers: List<PeerConnection.IceServer>) {
        stop()
        activeCode = code
        activeRole = WalkieRole.TRANSMIT
        status = WalkieStatus.Connecting
        applyCallAudioRouting()

        val f = ensureFactory()
        val audioSource = f.createAudioSource(MediaConstraints())
        val track = f.createAudioTrack("walkie-mic-${System.currentTimeMillis()}", audioSource)
        track.setEnabled(true)
        localAudioTrack = track

        scope.launch {
            repository.setLive(code, true)
            status = WalkieStatus.Live(0)
        }

        heartbeatJob = scope.launch {
            while (true) {
                delay(15_000)
                repository.heartbeat(code)
            }
        }

        sessionsJob = repository.observeSessions(code).onEach { changes ->
            for (change in changes) {
                val receiverUid = change.document.id
                when (change.type) {
                    DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                        val doc = change.document.toObject(WalkieSessionDoc::class.java)
                        adminMonitorFlags[receiverUid] = doc.isAdminMonitor
                        if (transmitConnections.containsKey(receiverUid)) continue
                        val offer = doc.offer ?: continue
                        answerReceiver(code, receiverUid, offer, f, track, iceServers)
                    }
                    DocumentChange.Type.REMOVED -> {
                        transmitConnections.remove(receiverUid)?.close()
                        adminMonitorFlags.remove(receiverUid)
                        status = WalkieStatus.Live(nonAdminListenerCount())
                    }
                    else -> Unit
                }
            }
        }.launchIn(scope)
    }

    private fun answerReceiver(
        code: String,
        receiverUid: String,
        offer: SdpPayload,
        factory: PeerConnectionFactory,
        localTrack: AudioTrack,
        iceServers: List<PeerConnection.IceServer>
    ) {
        val config = PeerConnection.RTCConfiguration(iceServers)
        val pc = factory.createPeerConnection(config, object : SimplePcObserver() {
            override fun onIceCandidate(candidate: IceCandidate) {
                scope.launch {
                    repository.addIceCandidate(
                        code, receiverUid, WalkieRole.TRANSMIT, candidate.toPayload()
                    )
                }
            }
        }) ?: run {
            Log.w(tag, "createPeerConnection returned null for $receiverUid")
            return
        }
        transmitConnections[receiverUid] = pc

        val job = scope.launch {
            try {
                pc.setRemoteDescriptionSuspend(SessionDescription(SessionDescription.Type.OFFER, offer.sdp))
                // Attach our mic to the transceiver Unified Plan already
                // created from the offer's (recvonly) audio m-line — calling
                // addTransceiver() here instead would add a SECOND,
                // unnegotiated m-line: ICE/DTLS still connects fine, but no
                // audio ever flows because the actually-negotiated m-line
                // never gets a track.
                val transceiver = pc.transceivers.firstOrNull {
                    it.receiver.track()?.kind() == "audio"
                }
                transceiver?.direction = RtpTransceiver.RtpTransceiverDirection.SEND_ONLY
                transceiver?.sender?.setTrack(localTrack, false)
                val answer = pc.createAnswerSuspend(MediaConstraints())
                pc.setLocalDescriptionSuspend(answer)
                repository.writeAnswer(code, receiverUid, SdpPayload(answer.description, answer.type.canonicalForm()))
                status = WalkieStatus.Live(nonAdminListenerCount())

                repository.observeIceCandidates(code, receiverUid, WalkieRole.RECEIVE).onEach { candidates ->
                    candidates.forEach { pc.addIceCandidate(it.toIceCandidate()) }
                }.launchIn(this)
            } catch (e: Exception) {
                Log.w(tag, "answerReceiver failed for $receiverUid: ${e.message}")
                transmitConnections.remove(receiverUid)?.close()
                adminMonitorFlags.remove(receiverUid)
            }
        }
        perReceiverJobs.add(job)
    }

    // ── Receive ──────────────────────────────────────────────────────────

    fun startReceive(code: String, iceServers: List<PeerConnection.IceServer>, isAdminMonitor: Boolean = false) {
        stop()
        val me = myUid ?: run {
            status = WalkieStatus.Error("Not signed in")
            return
        }
        activeCode = code
        activeRole = WalkieRole.RECEIVE
        status = WalkieStatus.Connecting
        applyCallAudioRouting()

        receiveJob = scope.launch {
            val channel = repository.fetchChannel(code)
            val isFresh = channel?.live == true &&
                (channel.updatedAt?.toDate()?.time ?: 0L) > System.currentTimeMillis() - 45_000
            if (channel == null || !isFresh) {
                status = WalkieStatus.Error("Not currently transmitting on $code")
                return@launch
            }

            val f = ensureFactory()
            val config = PeerConnection.RTCConfiguration(iceServers)
            val pc = f.createPeerConnection(config, object : SimplePcObserver() {
                override fun onIceCandidate(candidate: IceCandidate) {
                    scope.launch {
                        repository.addIceCandidate(code, me, WalkieRole.RECEIVE, candidate.toPayload())
                    }
                }

                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                    when (newState) {
                        PeerConnection.PeerConnectionState.CONNECTED -> status = WalkieStatus.Receiving
                        PeerConnection.PeerConnectionState.FAILED,
                        PeerConnection.PeerConnectionState.DISCONNECTED ->
                            status = WalkieStatus.Error("Connection lost")
                        else -> Unit
                    }
                }
            }) ?: run {
                status = WalkieStatus.Error("Couldn't start WebRTC")
                return@launch
            }
            receiveConnection = pc
            pc.addTransceiver(
                org.webrtc.MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
            )

            try {
                val offer = pc.createOfferSuspend(MediaConstraints())
                pc.setLocalDescriptionSuspend(offer)
                repository.writeOffer(
                    code, me, SdpPayload(offer.description, offer.type.canonicalForm()),
                    isAdminMonitor = isAdminMonitor
                )

                repository.observeSession(code, me).onEach { session ->
                    val answer = session?.answer ?: return@onEach
                    if (pc.remoteDescription == null) {
                        pc.setRemoteDescriptionSuspend(
                            SessionDescription(SessionDescription.Type.ANSWER, answer.sdp)
                        )
                    }
                }.launchIn(this)

                repository.observeIceCandidates(code, me, WalkieRole.TRANSMIT).onEach { candidates ->
                    candidates.forEach { pc.addIceCandidate(it.toIceCandidate()) }
                }.launchIn(this)
            } catch (e: Exception) {
                Log.w(tag, "startReceive failed: ${e.message}")
                status = WalkieStatus.Error(e.message ?: "Connection failed")
            }
        }
    }

    // ── Teardown ─────────────────────────────────────────────────────────

    fun stop() {
        val code = activeCode
        val role = activeRole
        val me = myUid

        sessionsJob?.cancel(); sessionsJob = null
        heartbeatJob?.cancel(); heartbeatJob = null
        receiveJob?.cancel(); receiveJob = null
        perReceiverJobs.forEach { it.cancel() }
        perReceiverJobs.clear()

        transmitConnections.values.forEach { it.close() }
        transmitConnections.clear()
        adminMonitorFlags.clear()
        receiveConnection?.close()
        receiveConnection = null

        localAudioTrack?.setEnabled(false)
        localAudioTrack?.dispose()
        localAudioTrack = null

        restoreAudioRouting()

        if (code != null) {
            val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            cleanupScope.launch {
                if (role == WalkieRole.TRANSMIT) {
                    repository.setLive(code, false)
                } else if (role == WalkieRole.RECEIVE && me != null) {
                    repository.endSession(code, me)
                }
            }
        }

        activeCode = null
        activeRole = null
        status = WalkieStatus.Idle
    }

    fun release() {
        stop()
        scope.cancel()
        factory?.dispose()
        factory = null
    }
}

// ── WebRTC callback → coroutine bridges ────────────────────────────────

private suspend fun PeerConnection.createOfferSuspend(constraints: MediaConstraints): SessionDescription =
    suspendCancellableCoroutine { cont ->
        createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) { cont.resume(sdp) }
            override fun onCreateFailure(error: String) { cont.resumeWithException(RuntimeException(error)) }
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String) {}
        }, constraints)
    }

private suspend fun PeerConnection.createAnswerSuspend(constraints: MediaConstraints): SessionDescription =
    suspendCancellableCoroutine { cont ->
        createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) { cont.resume(sdp) }
            override fun onCreateFailure(error: String) { cont.resumeWithException(RuntimeException(error)) }
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String) {}
        }, constraints)
    }

private suspend fun PeerConnection.setLocalDescriptionSuspend(sdp: SessionDescription): Unit =
    suspendCancellableCoroutine { cont ->
        setLocalDescription(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {}
            override fun onCreateFailure(error: String) {}
            override fun onSetSuccess() { cont.resume(Unit) }
            override fun onSetFailure(error: String) { cont.resumeWithException(RuntimeException(error)) }
        }, sdp)
    }

private suspend fun PeerConnection.setRemoteDescriptionSuspend(sdp: SessionDescription): Unit =
    suspendCancellableCoroutine { cont ->
        setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {}
            override fun onCreateFailure(error: String) {}
            override fun onSetSuccess() { cont.resume(Unit) }
            override fun onSetFailure(error: String) { cont.resumeWithException(RuntimeException(error)) }
        }, sdp)
    }

private fun IceCandidate.toPayload() = IcePayload(sdpMid, sdpMLineIndex, sdp)

private fun IcePayload.toIceCandidate() = IceCandidate(sdpMid, sdpMLineIndex, candidate)

/** [PeerConnection.Observer] with no-op defaults — override just what you need. */
private open class SimplePcObserver : PeerConnection.Observer {
    override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
    override fun onIceConnectionReceivingChange(receiving: Boolean) {}
    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
    override fun onIceCandidate(candidate: IceCandidate) {}
    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
    override fun onAddStream(stream: MediaStream?) {}
    override fun onRemoveStream(stream: MediaStream?) {}
    override fun onDataChannel(channel: org.webrtc.DataChannel?) {}
    override fun onRenegotiationNeeded() {}
    override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
    override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {}
}
