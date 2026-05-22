package com.example.yoloaio.features.beat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Beat Analyser — listens to the device microphone and renders three
 * synced visualisations:
 *
 *   1. Circular noise meter (dBFS, -90 ≈ silent, 0 ≈ peak)
 *   2. Log-spaced spectrum bars (FFT magnitudes in the audible band)
 *   3. Pulsing 4×6 tile grid that flashes on each detected kick-drum
 *      onset, weighted by frequency band so bass hits light low tiles
 *      and treble hits light high tiles
 *
 * Capture is via [MicAnalyzer], which uses AudioRecord + an in-house
 * Cooley-Tukey FFT. Permission flow is similar to the music screen's
 * BeatAnalyzer — we request RECORD_AUDIO on first entry; if denied or
 * the OS blocks AudioRecord (some Vivo / OxygenOS builds), the visuals
 * stay in their idle synthetic state and the UI explains why.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeatAnalyserScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val analyzer = remember { MicAnalyzer() }
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var startFailed by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            permLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(permissionGranted) {
        startFailed = false
        if (permissionGranted) {
            val ok = analyzer.start()
            if (!ok) startFailed = true
        }
        onDispose { analyzer.stop() }
    }

    // Tab state lives above the Scaffold so the Disco tab can opt out
    // of the chrome entirely (no top bar, no tab strip) for a true
    // full-screen light show.
    var selectedTab by remember { mutableIntStateOf(0) }

    // System back / gesture: while in Disco, return to Spectrum
    // before exiting the screen.
    BackHandler(enabled = permissionGranted && !startFailed && selectedTab == 1) {
        selectedTab = 0
    }

    // ── Full-screen Disco branch ──────────────────────────────────
    if (permissionGranted && !startFailed && selectedTab == 1) {
        Box(modifier = Modifier.fillMaxSize()) {
            DiscoTab(
                analyzer = analyzer,
                modifier = Modifier.fillMaxSize()
            )
            // Floating close button — semi-transparent so it doesn't
            // distract from the light show but stays tappable.
            IconButton(
                onClick = { selectedTab = 0 },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 32.dp, end = 16.dp)
                    .background(
                        Color.Black.copy(alpha = 0.4f),
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Exit disco",
                    tint = Color.White
                )
            }
        }
        return
    }

    // ── Normal chromed branch (Spectrum + tab switcher) ───────────
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("Beat Analyser", fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBackIos,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                !permissionGranted -> Box(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                ) {
                    PermissionPrompt(
                        onGrant = { permLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                    )
                }
                startFailed -> Box(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                ) {
                    StartFailedPrompt()
                }
                else -> {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Spectrum") }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Disco") }
                        )
                    }
                    // selectedTab == 0 — Spectrum body.
                    // selectedTab == 1 is impossible here because the
                    // earlier branch returned for that case.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        AnalyserBody(analyzer = analyzer)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyserBody(analyzer: MicAnalyzer) {
    val dbDisplay by animateFloatAsState(
        targetValue = analyzer.rmsDb,
        label = "rmsDb"
    )
    val pulseDisplay by animateFloatAsState(
        targetValue = analyzer.pulse,
        label = "pulse"
    )
    val bands = analyzer.bandMagnitudes
    val energies = analyzer.bandEnergies

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        // ── Noise meter ─────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.6f),
            contentAlignment = Alignment.Center
        ) {
            NoiseMeter(
                db = dbDisplay,
                pulse = pulseDisplay,
                modifier = Modifier.fillMaxSize()
            )
        }

        // ── Spectrum bars ───────────────────────────────────────────
        SectionLabel("Spectrum")
        SpectrumBars(
            bands = bands,
            pulse = pulseDisplay,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        )

        // ── Pulse tiles ─────────────────────────────────────────────
        SectionLabel("Beat tiles")
        BeatTileGrid(
            energies = energies,
            pulse = pulseDisplay,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        )

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * Big circular gauge.
 *
 * Display uses a **0–100 loudness scale**, not dBFS. dBFS is technically
 * accurate but counter-intuitive (a "loud" sound shows -19; silence
 * shows -90). We map a useful real-world range:
 *
 *   dBFS  -60 → 0 %   (essentially silent — quiet room, distant noise)
 *   dBFS  -30 → 50 %  (normal conversation)
 *   dBFS  -10 → 83 %  (loud music)
 *   dBFS    0 → 100 % (digital clipping, max possible)
 *
 * The dBFS value is still shown as a smaller subtitle so the precise
 * reading is available to anyone who wants it.
 */
@Composable
private fun NoiseMeter(
    db: Float,
    pulse: Float,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    // dBFS → 0..1 percentage. Floor at -50 dB (was -60) so even
    // typing-volume sounds register on the meter. -50 dBFS is roughly
    // "quiet room with a fan" — anything below that we can fairly
    // treat as silent for this gauge's purpose.
    val dbFloor = -50f
    val normalized = ((db - dbFloor) / -dbFloor).coerceIn(0f, 1f)
    val percent = (normalized * 100f).toInt()

    val barColor = when {
        normalized < 0.4f -> Color(0xFF00E5A8)
        normalized < 0.75f -> Color(0xFFFFC36B)
        else -> Color(0xFFFF6E40)
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h * 0.62f  // sit the centre slightly low for label clearance
            val radius = min(w, h) * 0.42f * (1f + pulse * 0.05f)
            val stroke = radius * 0.16f

            val startAngle = 135f
            val sweep = 270f
            // Track
            drawArc(
                color = onSurfaceVariant.copy(alpha = 0.16f),
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(cx - radius, cy - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            // Filled portion
            drawArc(
                color = barColor,
                startAngle = startAngle,
                sweepAngle = sweep * normalized,
                useCenter = false,
                topLeft = Offset(cx - radius, cy - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            // Pulse halo: brief outer ring that grows on each kick.
            if (pulse > 0.02f) {
                drawArc(
                    color = primary.copy(alpha = 0.35f * pulse),
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(cx - radius - stroke, cy - radius - stroke),
                    size = Size((radius + stroke) * 2f, (radius + stroke) * 2f),
                    style = Stroke(width = stroke * 0.55f, cap = StrokeCap.Round)
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$percent",
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    color = onSurface
                )
                Text(
                    "%",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp, start = 2.dp)
                )
            }
            Text(
                if (db <= -89f) "Silent" else "${db.toInt()} dBFS",
                style = MaterialTheme.typography.labelMedium,
                color = onSurfaceVariant
            )
            Text(
                loudnessLabel(db),
                style = MaterialTheme.typography.labelLarge,
                color = barColor,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

private fun loudnessLabel(db: Float): String = when {
    db <= -75f -> "Silent"
    db <= -55f -> "Quiet"
    db <= -35f -> "Conversation"
    db <= -20f -> "Loud"
    db <= -10f -> "Very loud"
    else -> "Peak"
}

/**
 * Log-spaced spectrum bars. Each bar samples a single FFT bin chosen on
 * a logarithmic scale across the audible drum+midrange range. Bars get
 * a uniform lift on each detected beat. Idle (no FFT data yet) shows a
 * subtle sine-wave fallback so the screen never looks dead.
 */
@Composable
private fun SpectrumBars(
    bands: FloatArray?,
    pulse: Float,
    modifier: Modifier = Modifier,
    barCount: Int = 48
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    val time = remember { System.nanoTime() }

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.04f))
    ) {
        val w = size.width
        val h = size.height
        val gap = w * 0.005f
        val barW = (w - gap * (barCount - 1)) / barCount
        val cornerR = barW / 2f

        // Logarithmic bin mapping — keeps bass bars from clustering at
        // one end. The bands payload has fftSize/2 = 1024 bins; we
        // span the rhythm + low-mid range (bins 1..400 ≈ 22 Hz – 8.6 kHz).
        val minBin = 1
        val maxBin = min(400, bands?.size ?: 400)
        val logMin = log2(minBin.toDouble())
        val logMax = log2(maxBin.toDouble())

        for (i in 0 until barCount) {
            val t = i / (barCount - 1f)
            val idx = 2.0.pow(logMin + t * (logMax - logMin)).toInt()
                .coerceIn(0, (bands?.size ?: 1) - 1)
            val raw = bands?.getOrNull(idx)?.coerceAtMost(1f) ?: run {
                // Synthetic idle animation.
                val phase = (System.nanoTime() - time) / 1_000_000_000.0
                ((sin(phase + i * 0.27) + 1) / 2).toFloat() * 0.25f + 0.08f
            }
            // Perceptual scaling: sqrt curve + 12× gain. Mic input is
            // quiet by nature, so we need an aggressive boost for the
            // bars to react to soft sounds. A raw bin of 0.01 (room
            // hum) now displays at ~0.35; raw 0.05 (normal speech)
            // saturates near the top. Trades dynamic range at the
            // loud end for visible response at the quiet end — the
            // right call for a visualiser.
            val boosted = sqrt(raw * 12f).coerceAtMost(1f)
            val value = (boosted + pulse * 0.22f).coerceIn(0.04f, 1f)
            val barH = h * value
            val x = i * (barW + gap)
            val y = h - barH

            // Vertical gradient — primary at top, secondary at bottom —
            // so taller (louder) bars feel more energetic.
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(primary, secondary),
                    startY = y,
                    endY = h
                ),
                topLeft = Offset(x, y),
                size = Size(barW, barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerR, cornerR)
            )
        }
    }
}

/**
 * 4-row × 6-col tile grid. Each row maps to a frequency band
 * (sub/kick/bass/mids), and tile brightness within that row follows
 * the band's current energy plus the global pulse. Reads like a
 * sequencer light strip.
 */
@Composable
private fun BeatTileGrid(
    energies: MicBandEnergies,
    pulse: Float,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary

    val rows = listOf(
        "SUB"   to energies.sub,
        "KICK"  to energies.kick,
        "BASS"  to energies.bass,
        "MIDS"  to energies.mids
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        rows.forEach { (label, value) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label,
                    modifier = Modifier.width(44.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 6 tiles per row. Light up from the left in proportion
                // to the band energy; the last tile gets a flash on each
                // detected beat so the pulse is visible even when energy
                // is low.
                //
                // Perceptual boost (sqrt + 10× gain) — bumped up so
                // quieter sounds light up meaningful numbers of tiles.
                // Mic-level band energies are 0.01–0.10 for typical
                // ambient / speech content; with this gain that maps
                // to 3-6 lit tiles instead of 1-2.
                val tileCount = 6
                val boosted = sqrt(value * 10f).coerceIn(0f, 1f)
                val litFraction = (boosted + pulse * 0.35f).coerceIn(0f, 1f)
                val litCount = (litFraction * tileCount).toInt()
                repeat(tileCount) { idx ->
                    val isLit = idx < litCount
                    val isFlash = idx == tileCount - 1 && pulse > 0.05f
                    val alpha = when {
                        isLit -> 0.85f
                        isFlash -> 0.5f + pulse * 0.5f
                        else -> 0.18f
                    }
                    val color = if (idx < tileCount / 2) primary else tertiary
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(color.copy(alpha = alpha))
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionPrompt(onGrant: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Rounded.MicOff,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Microphone access needed",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Beat Analyser listens to ambient sound through the device " +
                "microphone to draw the noise level and react to beats. " +
                "Audio is never recorded or sent anywhere.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onGrant) {
            Icon(Icons.Rounded.Mic, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Allow microphone")
        }
    }
}

@Composable
private fun StartFailedPrompt() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Rounded.MicOff,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Couldn't access the microphone",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Another app may be holding the mic, or your device's privacy " +
                "settings block third-party audio capture. Close any active " +
                "calls or recordings and try again.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Disco lights tab — full-bleed reactive light show driven by the
 * MicAnalyzer. Designed to behave like the Spectrum tab's bars: every
 * detected onset *instantly* snaps the palette to new colours, and
 * the whole screen continuously paints in proportion to the live
 * band energies. No tweens between colour changes — snappy / strobey
 * by design.
 *
 * Three-band colour layering:
 *   - Bass band  drives a saturated wash filling the bottom 60% of the screen
 *   - Mids band  drives a horizontal sweep through the middle
 *   - Highs band drives a top wash + scattered sparkle dots
 *
 * Each band has its own palette index so three different colours can
 * coexist on screen at once. Indices advance on every detected pulse.
 *
 * On heavy beats, an additional radial flash bursts from centre and a
 * white strobe overlay briefly whites out the screen — same effect
 * pattern as a club's chase lights.
 */
@Composable
private fun DiscoTab(analyzer: MicAnalyzer, modifier: Modifier = Modifier) {
    val pulse = analyzer.pulse
    val energies = analyzer.bandEnergies

    // 16-colour palette of pure / near-pure RGB primaries and jewel
    // tones. Avoiding desaturated pastels — disco lights look right
    // when colours are saturated to clipping.
    val palette = remember {
        listOf(
            Color(0xFFFF0066),  // hot pink
            Color(0xFF00FFFF),  // pure cyan
            Color(0xFFFFFF00),  // pure yellow
            Color(0xFFFF00FF),  // pure magenta
            Color(0xFF00FF66),  // electric green
            Color(0xFFFF6600),  // pure orange
            Color(0xFF0066FF),  // pure blue
            Color(0xFFFF3366),  // raspberry
            Color(0xFF66FFCC),  // mint
            Color(0xFFFFAA00),  // amber
            Color(0xFFAA00FF),  // violet
            Color(0xFF00CCFF),  // sky
            Color(0xFFFF00AA),  // hot magenta
            Color(0xFFCCFF00),  // lime
            Color(0xFFFF99CC),  // bubble pink
            Color(0xFF6600FF)   // indigo
        )
    }

    // Three independent palette pointers — one per band layer. They
    // step at different rates on each beat so the three colours on
    // screen don't lock into a fixed relationship.
    var idxBass by remember { mutableIntStateOf(0) }
    var idxMids by remember { mutableIntStateOf(5) }
    var idxHighs by remember { mutableIntStateOf(10) }
    var lastBeatMs by remember { mutableLongStateOf(0L) }

    // Beat detection: almost no gating. Rising edge past 0.05 with a
    // 50 ms minimum gap (just enough to avoid flicker-strobing on a
    // single onset that overshoots and rings down). Every burst of
    // mic activity → palette advances → screen colours change.
    LaunchedEffect(Unit) {
        var prev = 0f
        snapshotFlow { analyzer.pulse }.collect { p ->
            val now = System.currentTimeMillis()
            if (prev < 0.05f && p >= 0.05f && now - lastBeatMs > 50L) {
                idxBass = (idxBass + 1) % palette.size
                idxMids = (idxMids + 2) % palette.size
                idxHighs = (idxHighs + 3) % palette.size
                lastBeatMs = now
            }
            prev = p
        }
    }

    // Idle drift: cycle palette every ~1.2 s when nothing's been
    // detected for a couple of seconds, so the screen never freezes.
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1200L)
            if (System.currentTimeMillis() - lastBeatMs > 1800L) {
                idxBass = (idxBass + 1) % palette.size
                idxMids = (idxMids + 1) % palette.size
                idxHighs = (idxHighs + 1) % palette.size
            }
        }
    }

    // No tween — palette swaps are instantaneous, like a strobe wheel
    // clicking to the next gel. This is what makes it feel like the
    // spectrum bars instead of a slow fade.
    val colorBass = palette[idxBass]
    val colorMids = palette[idxMids]
    val colorHighs = palette[idxHighs]

    Box(modifier = modifier.background(Color.Black)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Sqrt-boost the band energies so even quiet content paints
            // strongly — same trick the spectrum bars use. Without
            // this, ambient mic input would barely register.
            val bassA = sqrt(energies.bass * 8f).coerceIn(0f, 1f)
            val midsA = sqrt(energies.mids * 6f).coerceIn(0f, 1f)
            val highsA = sqrt(energies.highs * 6f).coerceIn(0f, 1f)
            // Overall ambient floor — even silence shows a hint of the
            // current palette so the screen isn't pitch black between
            // beats. Climbs with the pulse value for snap on every hit.
            val ambient = (0.18f + pulse * 0.55f).coerceAtMost(1f)

            // Layer 1: BASS wash filling the bottom 70% of the screen.
            // This is the dominant colour layer — bass band has the
            // most consistent ambient energy in most music.
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        colorBass.copy(alpha = bassA * 0.4f + ambient * 0.3f),
                        colorBass.copy(alpha = (bassA + ambient).coerceAtMost(1f) * 0.95f)
                    ),
                    startY = h * 0.3f,
                    endY = h
                )
            )

            // Layer 2: MIDS sweep across the middle band. Horizontal
            // gradient so the colour appears to "scroll" across mid
            // frequencies (vocals / synths) rather than just bottom-up.
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        colorMids.copy(alpha = (midsA + ambient * 0.4f).coerceAtMost(0.95f)),
                        Color.Transparent
                    ),
                    startX = 0f,
                    endX = w
                ),
                topLeft = Offset(0f, h * 0.3f),
                size = Size(w, h * 0.4f)
            )

            // Layer 3: HIGHS wash at the top — fills 50% of the screen
            // with the highs colour. Less dominant than the bass layer
            // because treble content is typically transient (cymbals,
            // sibilance) rather than sustained.
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        colorHighs.copy(alpha = (highsA + ambient * 0.5f).coerceAtMost(0.95f)),
                        colorHighs.copy(alpha = highsA * 0.2f)
                    ),
                    startY = 0f,
                    endY = h * 0.5f
                )
            )

            // Layer 4: radial colour burst on each beat. Starts at the
            // centre and expands outward; alpha tracks pulse so it
            // fades with the beat's decay. Uses bass colour + white
            // core for a flash-bulb feel.
            if (pulse > 0.05f) {
                val burstR = max(w, h) * (0.25f + pulse * 0.85f)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = pulse * 0.55f),
                            colorBass.copy(alpha = pulse * 0.45f),
                            Color.Transparent
                        ),
                        center = Offset(w / 2f, h / 2f),
                        radius = burstR
                    )
                )
            }

            // Layer 5: side strobes — two narrow gradients flush against
            // the left and right edges, lit with the mids and highs
            // colours. They flash on stronger beats and add side-light
            // depth without competing with the main colour layers.
            if (pulse > 0.15f) {
                val sideAlpha = (pulse - 0.15f) * 1.5f
                val sideW = w * 0.25f
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            colorMids.copy(alpha = sideAlpha.coerceAtMost(0.8f)),
                            Color.Transparent
                        ),
                        startX = 0f,
                        endX = sideW
                    ),
                    size = Size(sideW, h)
                )
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            colorHighs.copy(alpha = sideAlpha.coerceAtMost(0.8f))
                        ),
                        startX = w - sideW,
                        endX = w
                    ),
                    topLeft = Offset(w - sideW, 0f),
                    size = Size(sideW, h)
                )
            }

            // Layer 6: hi-hat / treble sparkle dots — same as before
            // but with the highs colour mixed in so the dots aren't
            // always plain white.
            if (energies.highs > 0.01f) {
                val boostedHighs = sqrt(energies.highs * 5f).coerceIn(0f, 1f)
                val dotCount = (boostedHighs * 100f).toInt().coerceAtMost(100)
                val timeSeed = (System.currentTimeMillis() / 60L).toInt()
                repeat(dotCount) { i ->
                    val seed = i * 173 + timeSeed
                    val xN = ((seed * 37) % 100 + 100) % 100 / 100f
                    val yN = ((seed * 91) % 100 + 100) % 100 / 100f
                    drawCircle(
                        color = if (i % 2 == 0) Color.White.copy(alpha = 0.85f)
                        else colorHighs.copy(alpha = 0.9f),
                        center = Offset(xN * w, yN * h),
                        radius = 2f + boostedHighs * 5f
                    )
                }
            }

            // Layer 7: peak strobe — sharp white wash on the loudest
            // hits. Threshold low so it fires on most beats; alpha
            // capped so it's never blinding.
            if (pulse > 0.25f) {
                drawRect(Color.White.copy(alpha = (pulse - 0.25f) * 0.5f))
            }
        }
    }
}

// Unused — kept here in case we want a polar visualizer variant later.
@Suppress("unused")
private fun polarTile(angle: Double, radius: Float, cx: Float, cy: Float): Offset {
    return Offset(
        cx + (radius * cos(angle)).toFloat(),
        cy + (radius * sin(angle)).toFloat()
    )
}

@Suppress("unused")
private fun easeOut(x: Float): Float = 1f - (1f - x).pow(2)

@Suppress("unused")
private const val TAU = 2.0 * PI
