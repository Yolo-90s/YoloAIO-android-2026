package com.example.yoloaio.features.wifi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SignalWifi4Bar
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.yoloaio.ui.components.FeatureScaffold
import com.example.yoloaio.ui.components.GlassCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random

/**
 * Wi-Fi Lab — educational concept screen.
 *
 * This screen visualises *the shape of* a WPA-PSK dictionary attack:
 *   1. Scan visible networks (real WifiManager.scanResults — read-only).
 *   2. "Target" a network and animate the conceptual stages:
 *      deauth → handshake capture → wordlist iteration → result.
 *   3. The animation is entirely synthetic — no packets are sent, no
 *      handshake is captured, no real keys are tried, and no password
 *      is ever produced. A real WPA attack needs monitor-mode capable
 *      Wi-Fi hardware + root + tools like aircrack-ng — none of which
 *      an Android app can do from the SDK.
 *
 * Purpose: show what each stage *looks like* so the underlying ideas
 * (4-way handshake, PSK derivation, dictionary attack) become tangible.
 */
@Composable
fun WifiLabScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val wifiManager = remember {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    }

    var hasLocationPerm by remember {
        mutableStateOf(hasLocationPermission(context))
    }
    var hasNearbyPerm by remember {
        mutableStateOf(hasNearbyDevicesPermission(context))
    }

    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasLocationPerm = granted }

    val nearbyPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasNearbyPerm = granted }

    val networks = remember { mutableStateListOf<WifiTarget>() }
    var scanning by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<WifiTarget?>(null) }
    val scope = rememberCoroutineScope()

    fun runScan() {
        val wm = wifiManager ?: return
        if (!hasLocationPerm) return
        if (!wm.isWifiEnabled) return
        scanning = true
        @Suppress("DEPRECATION")
        wm.startScan()
        // Read the latest cached results — startScan is throttled on
        // modern Android, so the freshest list usually arrives within
        // a second of opening the screen.
        scope.launch {
            delay(900)
            val results = runCatching { wm.scanResults }.getOrDefault(emptyList())
            networks.clear()
            networks.addAll(
                results
                    .filter { it.SSID.isNotBlank() }
                    .distinctBy { it.BSSID }
                    .sortedByDescending { it.level }
                    .map { it.toWifiTarget() }
            )
            scanning = false
        }
    }

    LaunchedEffect(hasLocationPerm) {
        if (hasLocationPerm) runScan()
    }

    FeatureScaffold(
        title = "Wi-Fi Lab",
        onBack = onBack,
        actions = {
            IconButton(onClick = { runScan() }, enabled = hasLocationPerm && !scanning) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Rescan")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DisclaimerBanner()

            if (!hasLocationPerm) {
                PermissionPrompt(
                    title = "Location permission",
                    body = "Android requires location permission to list nearby Wi-Fi " +
                        "networks. This screen only reads visible SSID + signal data — " +
                        "it does not track your location.",
                    actionLabel = "Grant location"
                ) {
                    locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
                return@Column
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNearbyPerm) {
                PermissionPrompt(
                    title = "Nearby devices (optional)",
                    body = "Grants access to richer scan info on Android 13+. " +
                        "Scanning works without it.",
                    actionLabel = "Grant nearby"
                ) {
                    nearbyPermLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
                }
            }

            val active = selected
            if (active != null) {
                AttackPanel(
                    target = active,
                    onClose = { selected = null }
                )
            } else {
                NetworkListPanel(
                    networks = networks,
                    scanning = scanning,
                    wifiOff = wifiManager?.isWifiEnabled == false,
                    onPick = { selected = it }
                )
            }

            HandshakeAnimationCard()
            PbkdfCostCard()
            CurrentConnectionCard(wifiManager)
            WpaComparisonCard()

            EducationCard()
        }
    }
}

// ───────────────────────── disclaimer ─────────────────────────

@Composable
private fun DisclaimerBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF2A1010),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Warning,
                contentDescription = null,
                tint = Color(0xFFFF8A80)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "Educational concept demo",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFCDD2)
                )
                Text(
                    "No real attack is performed. Stages are simulated to " +
                        "illustrate how a WPA dictionary attack works in theory.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFFCDD2).copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun PermissionPrompt(
    title: String,
    body: String,
    actionLabel: String,
    onRequest: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRequest) { Text(actionLabel) }
        }
    }
}

// ─────────────────────── network list ───────────────────────

@Composable
private fun NetworkListPanel(
    networks: SnapshotStateList<WifiTarget>,
    scanning: Boolean,
    wifiOff: Boolean,
    onPick: (WifiTarget) -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Router,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Nearby networks",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (scanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        "${networks.size} found",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when {
                wifiOff -> EmptyState(
                    icon = Icons.Rounded.WifiOff,
                    title = "Wi-Fi is off",
                    body = "Turn Wi-Fi on to scan for networks."
                )
                networks.isEmpty() && !scanning -> EmptyState(
                    icon = Icons.Rounded.Wifi,
                    title = "No networks visible yet",
                    body = "Tap the refresh icon to rescan."
                )
                else -> LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp)
                ) {
                    items(networks, key = { it.bssid }) { net ->
                        NetworkRow(net = net, onClick = { onPick(net) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(36.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NetworkRow(net: WifiTarget, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.SignalWifi4Bar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                net.ssid,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    net.bssid,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "ch ${net.channel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            SecurityChip(net.security)
            Spacer(Modifier.height(4.dp))
            Text(
                "${net.rssi} dBm",
                style = MaterialTheme.typography.labelSmall,
                color = signalColor(net.rssi)
            )
        }
    }
}

@Composable
private fun SecurityChip(security: WifiSecurity) {
    val (bg, fg, icon, label) = when (security) {
        WifiSecurity.OPEN -> Quad(
            Color(0xFFFFCDD2), Color(0xFFB71C1C),
            Icons.Rounded.LockOpen, "OPEN"
        )
        WifiSecurity.WEP -> Quad(
            Color(0xFFFFE0B2), Color(0xFFE65100),
            Icons.Rounded.Lock, "WEP"
        )
        WifiSecurity.WPA -> Quad(
            Color(0xFFFFF9C4), Color(0xFFF57F17),
            Icons.Rounded.Lock, "WPA"
        )
        WifiSecurity.WPA2 -> Quad(
            Color(0xFFC8E6C9), Color(0xFF1B5E20),
            Icons.Rounded.Lock, "WPA2"
        )
        WifiSecurity.WPA3 -> Quad(
            Color(0xFFB3E5FC), Color(0xFF01579B),
            Icons.Rounded.Lock, "WPA3"
        )
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

private fun signalColor(rssi: Int): Color = when {
    rssi >= -55 -> Color(0xFF66BB6A)
    rssi >= -70 -> Color(0xFFFFB300)
    else -> Color(0xFFEF5350)
}

// ─────────────────────── attack panel ───────────────────────

private enum class AttackStage(val label: String) {
    Idle("Idle"),
    Recon("Reconnaissance"),
    Deauth("Sending deauth frames"),
    Handshake("Capturing 4-way handshake"),
    Wordlist("Dictionary attack"),
    Done("Demo complete")
}

@Composable
private fun AttackPanel(target: WifiTarget, onClose: () -> Unit) {
    var stage by remember { mutableStateOf(AttackStage.Recon) }
    var running by remember { mutableStateOf(true) }
    var keysTried by remember { mutableStateOf(0L) }
    var packets by remember { mutableStateOf(0L) }
    var handshake by remember { mutableStateOf(false) }
    val logs = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()

    fun log(line: String) {
        logs.add(0, line)
        while (logs.size > 60) logs.removeAt(logs.size - 1)
    }

    LaunchedEffect(target.bssid) {
        // Phase script — entirely simulated. Numbers are random-walk to
        // *look* alive; no real frames are transmitted, no real keys
        // are tested, no real password is ever derived.
        log("[recon] target=${target.ssid} bssid=${target.bssid} ch=${target.channel}")
        log("[recon] encryption=${target.security.name}")
        delay(1200)
        if (!isActive || !running) return@LaunchedEffect

        stage = AttackStage.Deauth
        log("[deauth] crafting 802.11 deauth frames (simulated)")
        repeat(8) {
            if (!isActive || !running) return@LaunchedEffect
            delay(220)
            packets += Random.nextLong(4, 12)
            log("[deauth] tx packets=$packets")
        }

        stage = AttackStage.Handshake
        log("[handshake] listening for client reassociation (simulated)")
        delay(1400)
        if (!isActive || !running) return@LaunchedEffect
        handshake = true
        log("[handshake] EAPOL frames 1/4 → 4/4 acquired (simulated)")
        delay(700)
        if (!isActive || !running) return@LaunchedEffect

        stage = AttackStage.Wordlist
        log("[dict] loading wordlist (simulated, 0 real entries)")
        // Just a counter going up — no actual candidate keys to display.
        val target_ = 14_500L + Random.nextLong(500, 5000)
        while (isActive && running && keysTried < target_) {
            delay(40)
            keysTried += Random.nextLong(40, 140)
        }

        stage = AttackStage.Done
        log("[done] simulation ended — no real attack was performed")
        log("[done] WPA2 PSK cannot be recovered without monitor-mode HW + root")
        running = false
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp)
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Bolt,
                    contentDescription = null,
                    tint = Color(0xFF00E5A8)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        target.ssid,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        "${target.bssid} · ${target.security.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Button(
                    onClick = {
                        running = false
                        onClose()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2A1010),
                        contentColor = Color(0xFFFF8A80)
                    )
                ) {
                    Icon(Icons.Rounded.Stop, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Stop")
                }
            }

            // Stages timeline
            StageTimeline(stage)

            Spacer(Modifier.height(8.dp))

            // Live metrics
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Metric("Packets", packets.toString(), modifier = Modifier.weight(1f))
                Metric(
                    "Handshake",
                    if (handshake) "✓" else "—",
                    modifier = Modifier.weight(1f),
                    accent = if (handshake) Color(0xFF66BB6A) else null
                )
                Metric("Keys tried", keysTried.toString(), modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(12.dp))

            // Pretty progress bar — only meaningful during Wordlist stage.
            val barProgress = when (stage) {
                AttackStage.Idle, AttackStage.Recon -> 0.05f
                AttackStage.Deauth -> 0.18f
                AttackStage.Handshake -> 0.38f
                AttackStage.Wordlist -> 0.6f + (keysTried.coerceAtMost(15_000) / 15_000f) * 0.35f
                AttackStage.Done -> 1f
            }
            LinearProgressIndicator(
                progress = { barProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = Color(0xFF00E5A8),
                trackColor = Color(0xFF1B2A2A)
            )

            Spacer(Modifier.height(12.dp))

            // Terminal-style log
            LogTerminal(logs)

            Spacer(Modifier.height(12.dp))

            // Outcome banner once done
            AnimatedVisibility(visible = stage == AttackStage.Done) {
                OutcomeBanner()
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun StageTimeline(stage: AttackStage) {
    val stages = listOf(
        AttackStage.Recon,
        AttackStage.Deauth,
        AttackStage.Handshake,
        AttackStage.Wordlist,
        AttackStage.Done
    )
    val activeIndex = stages.indexOf(stage).coerceAtLeast(0)
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        stages.forEachIndexed { idx, s ->
            val isActive = idx == activeIndex && stage != AttackStage.Done
            val isDone = idx < activeIndex || stage == AttackStage.Done && idx <= activeIndex
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StageDot(isActive = isActive, isDone = isDone)
                Spacer(Modifier.width(10.dp))
                Text(
                    s.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        isDone && !isActive -> Color(0xFF66BB6A)
                        isActive -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun StageDot(isActive: Boolean, isDone: Boolean) {
    val transition = rememberInfiniteTransition(label = "stagePulse")
    val pulse by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val color = when {
        isActive -> Color(0xFF00E5A8).copy(alpha = pulse)
        isDone -> Color(0xFF66BB6A)
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    }
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        if (isDone && !isActive) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(10.dp)
            )
        }
    }
}

@Composable
private fun Metric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color? = null
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = accent ?: MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun LogTerminal(logs: SnapshotStateList<String>) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF050B0A),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF143C2D))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 150.dp, max = 180.dp)
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            if (logs.isEmpty()) {
                Text(
                    "$ waiting…",
                    color = Color(0xFF00E5A8).copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
            logs.forEach { line ->
                Text(
                    line,
                    color = Color(0xFF7AF0C2),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun OutcomeBanner() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF102015),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1B5E20))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color(0xFF66BB6A)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Concept demo complete",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFFB9F6CA),
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "No real handshake was captured and no key was tested. " +
                    "Performing a real WPA-PSK attack requires monitor-mode " +
                    "capable Wi-Fi hardware, root, and tools like aircrack-ng " +
                    "or hashcat — none of which an Android app can access " +
                    "from the public SDK.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFB9F6CA).copy(alpha = 0.9f)
            )
        }
    }
}

// ─────────────────────── education ───────────────────────

@Composable
private fun EducationCard() {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.School,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "How WPA-PSK attacks work (in theory)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(10.dp))
            EduItem(
                "1. Capture the handshake",
                "When a client joins a WPA2 network, both sides exchange a " +
                    "4-way EAPOL handshake. An attacker in range can passively " +
                    "record these frames — or speed it up by sending a deauth " +
                    "that forces the client to reconnect."
            )
            EduItem(
                "2. Derive PMK candidates",
                "WPA2-PSK turns the password + SSID into a 256-bit Pairwise " +
                    "Master Key via PBKDF2 (4096 iterations of HMAC-SHA1). " +
                    "Slow on purpose — to make brute force expensive."
            )
            EduItem(
                "3. Dictionary / brute force",
                "Each candidate password is run through PBKDF2 and checked " +
                    "against the captured MIC in the handshake. Weak or common " +
                    "passwords fall in seconds; long random ones are infeasible."
            )
            EduItem(
                "4. WPA3 closes the door",
                "WPA3 replaces PSK with SAE (Dragonfly), which is resistant to " +
                    "offline dictionary attacks — even if you capture the handshake."
            )
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp)) {
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Defend yourself: use a long random WPA2/WPA3 passphrase " +
                            "(16+ chars), disable WPS, and keep router firmware up to date.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun EduItem(title: String, body: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─────────────────────── data + helpers ───────────────────────

private data class WifiTarget(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val channel: Int,
    val security: WifiSecurity
)

private enum class WifiSecurity { OPEN, WEP, WPA, WPA2, WPA3 }

private fun ScanResult.toWifiTarget(): WifiTarget {
    @Suppress("DEPRECATION")
    val cap = capabilities ?: ""
    val sec = when {
        cap.contains("WPA3") || cap.contains("SAE") -> WifiSecurity.WPA3
        cap.contains("WPA2") || cap.contains("RSN") -> WifiSecurity.WPA2
        cap.contains("WPA") -> WifiSecurity.WPA
        cap.contains("WEP") -> WifiSecurity.WEP
        else -> WifiSecurity.OPEN
    }
    val freq = frequency
    val ch = when {
        freq in 2412..2484 -> ((freq - 2412) / 5) + 1
        freq in 5170..5825 -> ((freq - 5000) / 5)
        else -> 0
    }
    @Suppress("DEPRECATION")
    return WifiTarget(
        ssid = SSID.ifBlank { "<hidden>" },
        bssid = BSSID.orEmpty(),
        rssi = level,
        channel = ch,
        security = sec
    )
}

private fun hasLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

private fun hasNearbyDevicesPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context, Manifest.permission.NEARBY_WIFI_DEVICES
    ) == PackageManager.PERMISSION_GRANTED
}

// ════════════════════ 4-way handshake animation ════════════════════

private data class HandshakeFrameInfo(
    val label: String,
    val payload: String,
    val explanation: String,
    val leftToRight: Boolean
)

private val handshakeFrames = listOf(
    HandshakeFrameInfo(
        "Frame 1/4", "ANonce",
        "AP sends a random nonce (ANonce). At this point the attacker " +
            "learns nothing useful yet — but the capture has started.",
        leftToRight = true
    ),
    HandshakeFrameInfo(
        "Frame 2/4", "SNonce + MIC",
        "Client computes the PTK from PMK + both nonces + MAC addresses, " +
            "then sends SNonce + a MIC proving it knows the PMK. " +
            "This frame is what an offline dictionary attack tests against.",
        leftToRight = false
    ),
    HandshakeFrameInfo(
        "Frame 3/4", "GTK + MIC",
        "AP confirms the PTK and ships the encrypted group key (GTK) " +
            "for broadcast traffic.",
        leftToRight = true
    ),
    HandshakeFrameInfo(
        "Frame 4/4", "ACK",
        "Client acknowledges. Both ends now share a session key (PTK) " +
            "and start encrypting all data frames.",
        leftToRight = false
    )
)

@Composable
private fun HandshakeAnimationCard() {
    var step by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            step = (step + 1) % handshakeFrames.size
        }
    }
    val frame = handshakeFrames[step]

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.SwapHoriz,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "4-way EAPOL handshake",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "What WPA2 actually exchanges when a client joins. An attacker " +
                    "in range can passively record all four frames — the dictionary " +
                    "attack then runs entirely offline.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EndpointBadge("AP", Icons.Rounded.Router)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    HandshakeArrow(step = step, frame = frame)
                }
                EndpointBadge("Client", Icons.Rounded.Smartphone)
            }

            Spacer(Modifier.height(14.dp))

            // Frame description block — changes per step
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            frame.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            frame.payload,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        frame.explanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Step dots
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                handshakeFrames.forEachIndexed { idx, _ ->
                    val active = idx == step
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (active) 10.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                                    .copy(alpha = 0.3f)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun EndpointBadge(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun HandshakeArrow(step: Int, frame: HandshakeFrameInfo) {
    // Re-key animation off step so the dot restarts every frame.
    val infinite = rememberInfiniteTransition(label = "handshakeFlow$step")
    val t by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "t$step"
    )
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)

    Box(modifier = Modifier.fillMaxWidth()) {
        // Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .align(Alignment.Center)
                .background(track)
        )
        // Moving dot: fraction along track, direction depends on frame
        val fraction = if (frame.leftToRight) t else (1f - t)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
            ) {
                Box(
                    modifier = Modifier
                        .align(if (frame.leftToRight) Alignment.CenterEnd else Alignment.CenterStart)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
            }
        }
        // Frame label above the track
        Text(
            "${frame.label} → ${frame.payload}",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = accent,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 2.dp)
        )
    }
}

// ════════════════════ PBKDF2 cost visualizer ════════════════════

private data class PbkdfResult(
    val pmkHex: String,
    val elapsedMs: Double
)

@Composable
private fun PbkdfCostCard() {
    var ssid by remember { mutableStateOf("HomeNetwork") }
    var password by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<PbkdfResult?>(null) }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Calculate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "PBKDF2 cost visualizer",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "WPA2 turns your password + SSID into the 256-bit PMK using " +
                    "PBKDF2-HMAC-SHA1 with 4096 iterations. The cost is the " +
                    "defence: every brute-force guess pays this tax.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = ssid,
                onValueChange = { ssid = it.take(32) },
                label = { Text("SSID (salt)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it.take(63) },
                label = { Text("Candidate passphrase") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    if (password.length < 8 || ssid.isBlank() || running) return@Button
                    running = true
                    result = null
                    scope.launch {
                        val r = withContext(Dispatchers.Default) {
                            derivePmk(password, ssid)
                        }
                        result = r
                        running = false
                    }
                },
                enabled = !running && password.length >= 8 && ssid.isNotBlank()
            ) {
                if (running) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Deriving…")
                } else {
                    Text("Derive PMK")
                }
            }
            if (password.isNotEmpty() && password.length < 8) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "WPA2 requires 8–63 chars.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            result?.let { r ->
                Spacer(Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Derived PMK (256-bit)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            r.pmkHex,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                .copy(alpha = 0.2f)
                        )
                        Spacer(Modifier.height(8.dp))
                        val devicePerSec = (1000.0 / r.elapsedMs).coerceAtLeast(1.0)
                        Text(
                            "One derivation on this device: %.1f ms".format(r.elapsedMs),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "≈ %.0f PMKs / second (this phone)".format(devicePerSec),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "Brute-force time estimates",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Realistic offline attacker rates:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                // Realistic public numbers for WPA-PMK hashcat mode (-m 2500):
                //  RTX 4090:   ~2,000,000 H/s
                //  RTX 3090:   ~1,000,000 H/s
                //  8-rig farm: ~16,000,000 H/s
                EstimateGrid(
                    devicePerSec = (1000.0 / r.elapsedMs).coerceAtLeast(1.0)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Note: estimates are average-case (half the keyspace). Real " +
                        "attackers also use targeted wordlists + rules, so password " +
                        "structure matters as much as length.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EstimateGrid(devicePerSec: Double) {
    val rates = listOf(
        "This phone" to devicePerSec,
        "High-end GPU (RTX 4090)" to 2_000_000.0,
        "8-GPU rig" to 16_000_000.0
    )
    val scenarios = listOf(
        ScenarioRow("8 digits (PIN)", 10.0.pow(8)),
        ScenarioRow("8 lowercase chars", 26.0.pow(8)),
        ScenarioRow("8 mixed alphanumeric", 62.0.pow(8)),
        ScenarioRow("12 mixed alphanumeric", 62.0.pow(12)),
        ScenarioRow("20 mixed alphanumeric", 62.0.pow(20))
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header row
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(
                "Charset · length",
                modifier = Modifier.weight(1.4f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            rates.forEach { (name, _) ->
                Text(
                    name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
        )
        scenarios.forEach { sc ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    sc.label,
                    modifier = Modifier.weight(1.4f),
                    style = MaterialTheme.typography.bodySmall
                )
                rates.forEach { (_, rate) ->
                    val seconds = (sc.keyspace / 2.0) / rate
                    Text(
                        formatDuration(seconds),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = durationColor(seconds)
                    )
                }
            }
        }
    }
}

private data class ScenarioRow(val label: String, val keyspace: Double)

private fun derivePmk(password: String, ssid: String): PbkdfResult {
    val spec = PBEKeySpec(password.toCharArray(), ssid.toByteArray(), 4096, 256)
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
    val t0 = System.nanoTime()
    val pmk = factory.generateSecret(spec).encoded
    val nanos = System.nanoTime() - t0
    val hex = pmk.joinToString(" ") { "%02x".format(it) }
        .chunked(24)
        .joinToString("\n")
    return PbkdfResult(
        pmkHex = hex,
        elapsedMs = nanos / 1_000_000.0
    )
}

private fun formatDuration(seconds: Double): String {
    if (!seconds.isFinite() || seconds < 0) return "—"
    val minute = 60.0
    val hour = 3600.0
    val day = 86_400.0
    val year = 365.25 * day
    return when {
        seconds < 1 -> "< 1 s"
        seconds < minute -> "%.0f s".format(seconds)
        seconds < hour -> "%.0f m".format(seconds / minute)
        seconds < day -> "%.1f h".format(seconds / hour)
        seconds < year -> "%.1f d".format(seconds / day)
        seconds < year * 1_000 -> "%.1f y".format(seconds / year)
        seconds < year * 1_000_000 -> "%.0fk y".format(seconds / year / 1_000)
        else -> {
            val years = seconds / year
            val exp = (ln(years) / ln(10.0)).toInt()
            "10^%d y".format(exp)
        }
    }
}

@Composable
private fun durationColor(seconds: Double): Color {
    val year = 365.25 * 86_400.0
    return when {
        seconds < 86_400 -> Color(0xFFEF5350)        // < 1 day = trivially crackable
        seconds < year -> Color(0xFFFFB300)          // < 1 year = weak
        seconds < year * 100 -> Color(0xFF66BB6A)    // < 100y = solid
        else -> MaterialTheme.colorScheme.primary    // infeasible
    }
}

// ════════════════════ current connection ════════════════════

@Composable
private fun CurrentConnectionCard(wifiManager: WifiManager?) {
    @Suppress("DEPRECATION")
    val info = remember(wifiManager) {
        runCatching { wifiManager?.connectionInfo }.getOrNull()
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Wifi,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Current connection",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))

            if (info == null || info.networkId == -1) {
                Text(
                    "Not connected to Wi-Fi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                @Suppress("DEPRECATION")
                val ssidRaw = info.ssid?.trim('"').orEmpty()
                @Suppress("DEPRECATION")
                val bssid = info.bssid.orEmpty()
                @Suppress("DEPRECATION")
                val linkSpeed = info.linkSpeed
                @Suppress("DEPRECATION")
                val rssi = info.rssi
                @Suppress("DEPRECATION")
                val freq = info.frequency
                @Suppress("DEPRECATION")
                val ipInt = info.ipAddress
                val hidden = info.hiddenSSID

                FieldRow("SSID", if (ssidRaw.isBlank() || ssidRaw == "<unknown ssid>") "<hidden>" else ssidRaw)
                FieldRow("BSSID", bssid.ifBlank { "—" })
                FieldRow("Link speed", "$linkSpeed ${WifiInfo.LINK_SPEED_UNITS}")
                FieldRow("Frequency", "$freq MHz")
                FieldRow("Signal", "$rssi dBm")
                FieldRow("IP address", ipv4ToString(ipInt))
                FieldRow("Hidden SSID", if (hidden) "yes" else "no")
            }

            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp)) {
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Android does not expose stored Wi-Fi passwords to apps — " +
                            "by OS design. Even the system Settings app only reveals " +
                            "them via the QR-share UI, which the user must trigger " +
                            "manually. There is no public SDK to retrieve a saved PSK.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun FieldRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(0.4f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            modifier = Modifier.weight(0.6f),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun ipv4ToString(raw: Int): String {
    if (raw == 0) return "—"
    return "${raw and 0xff}.${(raw shr 8) and 0xff}." +
        "${(raw shr 16) and 0xff}.${(raw shr 24) and 0xff}"
}

// ════════════════════ WPA2 vs WPA3 comparison ════════════════════

private data class WpaComparison(
    val property: String,
    val wpa2: String,
    val wpa3: String,
    val wpa3Wins: Boolean
)

private val wpaComparisons = listOf(
    WpaComparison(
        "Authentication",
        "PSK + 4-way handshake",
        "SAE (Dragonfly) — zero-knowledge",
        wpa3Wins = true
    ),
    WpaComparison(
        "Offline dictionary attack",
        "Feasible (capture → hashcat)",
        "Infeasible (per-attempt round-trip)",
        wpa3Wins = true
    ),
    WpaComparison(
        "Forward secrecy",
        "No — one PMK forever",
        "Yes — fresh PMK per session",
        wpa3Wins = true
    ),
    WpaComparison(
        "Mgmt frame protection (PMF)",
        "Optional (often off)",
        "Mandatory",
        wpa3Wins = true
    ),
    WpaComparison(
        "Key derivation",
        "PBKDF2-HMAC-SHA1, 4096 iters",
        "Hash-to-curve + per-handshake",
        wpa3Wins = true
    ),
    WpaComparison(
        "Open-network privacy",
        "Plaintext",
        "OWE (encrypted, no auth)",
        wpa3Wins = true
    )
)

@Composable
private fun WpaComparisonCard() {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.CompareArrows,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "WPA2 vs WPA3",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Why upgrading matters — and why WPA3 makes the dictionary " +
                    "attack you saw simulated above structurally impossible.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            // Header
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFFB300).copy(alpha = 0.25f),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        "WPA2",
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFB300)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF66BB6A).copy(alpha = 0.25f),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        "WPA3",
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF66BB6A)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            wpaComparisons.forEachIndexed { idx, cmp ->
                ComparisonRow(cmp)
                if (idx != wpaComparisons.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                            .copy(alpha = 0.15f)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF66BB6A).copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp)) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color(0xFF66BB6A)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Bottom line: if your router supports WPA3, turn it on. " +
                            "It makes offline brute-force a non-issue regardless " +
                            "of how weak your passphrase is.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun ComparisonRow(cmp: WpaComparison) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            cmp.property,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = null,
                    tint = Color(0xFFFFB300),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    cmp.wpa2,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color(0xFF66BB6A),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    cmp.wpa3,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
