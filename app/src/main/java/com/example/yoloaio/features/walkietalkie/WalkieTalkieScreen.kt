package com.example.yoloaio.features.walkietalkie

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CallReceived
import androidx.compose.material.icons.rounded.CallMade
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.yoloaio.data.LocalAppConfig
import com.example.yoloaio.ui.components.GlassCard
import kotlinx.coroutines.launch
import org.webrtc.PeerConnection

/**
 * Live push-to-talk between two devices over raw peer-to-peer WebRTC,
 * signaled through Firestore ([WalkieTalkieRepository]). Each signed-in
 * user has a stable 6-character channel code ([WalkieChannelId]) shown at
 * the top, refreshable on demand. Tapping Transfer broadcasts this
 * device's mic on that code; tapping Receive (after entering someone
 * else's code) listens to theirs. Only one mode is active at a time, and
 * only while this screen stays open — see [WalkieTalkieEngine].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalkieTalkieScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val config = LocalAppConfig.current
    val repository = remember { WalkieTalkieRepository() }
    val engine = remember { WalkieTalkieEngine(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()

    var myCode by remember { mutableStateOf<String?>(null) }
    var loadingCode by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var peerCodeInput by remember { mutableStateOf("") }
    var role by remember { mutableStateOf<WalkieRole?>(null) }
    var pendingTransfer by remember { mutableStateOf(false) }
    var isAdmin by remember { mutableStateOf(false) }
    var liveChannels by remember { mutableStateOf<List<LiveChannel>>(emptyList()) }

    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }

    val iceServers = remember(config.turnUrl, config.turnUsername, config.turnCredential) {
        buildList {
            add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
            if (config.turnUrl.isNotBlank()) {
                add(
                    PeerConnection.IceServer.builder(config.turnUrl)
                        .setUsername(config.turnUsername)
                        .setPassword(config.turnCredential)
                        .createIceServer()
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        myCode = repository.ensureChannelCode().getOrNull()
        loadingCode = false
    }

    LaunchedEffect(Unit) {
        isAdmin = repository.isCurrentUserAdmin()
    }

    // Only subscribed while isAdmin — non-admins would just have this
    // listener fail against firestore.rules' `allow list: if isAdmin()`
    // anyway, but there's no reason to even try.
    LaunchedEffect(isAdmin) {
        if (isAdmin) {
            repository.observeLiveChannels().collect { liveChannels = it }
        } else {
            liveChannels = emptyList()
        }
    }

    // Fires once the user grants mic permission after tapping Transfer.
    LaunchedEffect(permissionGranted, pendingTransfer) {
        if (pendingTransfer && permissionGranted) {
            pendingTransfer = false
            myCode?.let { code ->
                role = WalkieRole.TRANSMIT
                engine.startTransfer(code, iceServers)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { engine.release() }
    }

    fun stopActive() {
        engine.stop()
        role = null
    }

    fun onTransferTap() {
        if (role == WalkieRole.TRANSMIT) {
            stopActive()
            return
        }
        stopActive()
        if (!permissionGranted) {
            pendingTransfer = true
            permLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            myCode?.let { code ->
                role = WalkieRole.TRANSMIT
                engine.startTransfer(code, iceServers)
            }
        }
    }

    fun onReceiveTap() {
        if (role == WalkieRole.RECEIVE) {
            stopActive()
            return
        }
        val code = WalkieChannelId.normalize(peerCodeInput)
        if (code.length != 6) return
        stopActive()
        role = WalkieRole.RECEIVE
        engine.startReceive(code, iceServers)
    }

    fun onRefreshTap() {
        if (role == WalkieRole.TRANSMIT) stopActive()
        refreshing = true
        coroutineScope.launch {
            myCode = repository.refreshChannelCode().getOrNull() ?: myCode
            refreshing = false
        }
    }

    // Admin-only: tune into a channel discovered via the live-channels
    // list instead of a manually entered code. Marked isAdminMonitor so
    // the transmitter's own listener count doesn't reflect this session.
    fun onAdminListenTap(channel: LiveChannel) {
        stopActive()
        peerCodeInput = channel.code
        role = WalkieRole.RECEIVE
        engine.startReceive(channel.code, iceServers, isAdminMonitor = true)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Walkie Talkie") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            OwnCodeCard(
                code = myCode,
                loading = loadingCode || refreshing,
                onRefresh = ::onRefreshTap
            )

            OutlinedTextField(
                value = peerCodeInput,
                onValueChange = { peerCodeInput = WalkieChannelId.normalize(it) },
                label = { Text("Peer's code (to Receive)") },
                singleLine = true,
                enabled = role != WalkieRole.RECEIVE,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (role == WalkieRole.TRANSMIT) {
                    Button(
                        onClick = ::onTransferTap,
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Rounded.CallMade, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Stop Transfer")
                    }
                } else {
                    Button(
                        onClick = ::onTransferTap,
                        enabled = myCode != null,
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) {
                        Icon(Icons.Rounded.CallMade, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Transfer")
                    }
                }

                if (role == WalkieRole.RECEIVE) {
                    Button(
                        onClick = ::onReceiveTap,
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Rounded.CallReceived, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Stop Receive")
                    }
                } else {
                    OutlinedButton(
                        onClick = ::onReceiveTap,
                        enabled = WalkieChannelId.normalize(peerCodeInput).length == 6,
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) {
                        Icon(Icons.Rounded.CallReceived, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Receive")
                    }
                }
            }

            StatusLine(status = engine.status)

            if (isAdmin) {
                AdminLiveChannelsSection(
                    channels = liveChannels,
                    onListen = ::onAdminListenTap
                )
            }
        }
    }
}

@Composable
private fun AdminLiveChannelsSection(channels: List<LiveChannel>, onListen: (LiveChannel) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.AdminPanelSettings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text("Live channels (admin)", style = MaterialTheme.typography.titleMedium)
        }
        if (channels.isEmpty()) {
            Text(
                "No one else is currently transmitting.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                channels.forEach { channel ->
                    GlassCard(
                        onClick = { onListen(channel) },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(channel.ownerDisplayName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    formatCode(channel.code),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Rounded.Headset, contentDescription = "Listen")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OwnCodeCard(code: String?, loading: Boolean, onRefresh: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Your code",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    code?.let { formatCode(it) } ?: "…",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
            }
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Get a new code")
                }
            }
        }
    }
}

@Composable
private fun StatusLine(status: WalkieStatus) {
    val text = when (status) {
        is WalkieStatus.Idle -> "Not active"
        is WalkieStatus.Connecting -> "Connecting…"
        is WalkieStatus.Live -> if (status.listenerCount > 0)
            "Live — ${status.listenerCount} listening" else "Live — waiting for listeners"
        is WalkieStatus.Receiving -> "Receiving live audio"
        is WalkieStatus.Error -> status.message
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (status is WalkieStatus.Error) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun formatCode(code: String) = code.chunked(3).joinToString(" ")
