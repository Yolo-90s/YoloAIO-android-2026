package com.example.yoloaio.features.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.yoloaio.ui.components.FeatureScaffold
import com.example.yoloaio.ui.components.GlassCard

/**
 * Settings screen reachable from the Music top-bar gear icon. Combines
 * the previously-bottom-sheet language selector with a new audio-quality
 * picker so users have one place to tune what JioSaavn streams.
 */
@Composable
fun MusicSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val qualityStore = remember { MusicQualityPreferences.get(context) }
    val languageStore = remember { MusicLanguagePreferences.get(context) }
    val effectsStore = remember { MusicEffectsPreferences.get(context) }
    val quality by rememberMusicQuality()
    val languages by rememberMusicLanguages()
    val effects by rememberMusicEffectSettings()

    FeatureScaffold(title = "Music settings", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            QualityCard(
                current = quality,
                onPick = { qualityStore.set(it) }
            )
            EnhancementsCard(
                settings = effects,
                onToggleEnabled = { effectsStore.setEnabled(it) },
                onApplyEnhanced = { effectsStore.applyEnhanced() },
                onReset = { effectsStore.reset() },
                onPresetChange = { effectsStore.setPreset(it) },
                onBassChange = { effectsStore.setBassStrength(it) },
                onVirtualizerChange = { effectsStore.setVirtualizerStrength(it) },
                onLoudnessChange = { effectsStore.setLoudnessGainMb(it) }
            )
            LanguageCard(
                selected = languages,
                onToggle = { lang ->
                    val next = languages.toMutableSet()
                    if (lang in next) next -= lang else next += lang
                    if (next.isEmpty()) next += MusicLanguage.Default
                    languageStore.set(next)
                }
            )
        }
    }
}

// ─────────────────────── audio quality ───────────────────────

@Composable
private fun QualityCard(
    current: MusicQuality,
    onPick: (MusicQuality) -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            HeaderRow(
                icon = Icons.Rounded.GraphicEq,
                title = "Audio quality",
                subtitle = "Streaming bitrate. Lower saves cellular data; HD sounds " +
                    "noticeably better on good headphones."
            )
            Spacer(Modifier.height(12.dp))
            MusicQuality.all.forEach { q ->
                QualityRow(
                    quality = q,
                    isSelected = q == current,
                    onClick = { onPick(q) }
                )
                if (q != MusicQuality.all.last()) Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun QualityRow(
    quality: MusicQuality,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (isSelected) Icons.Rounded.RadioButtonChecked
            else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                quality.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(
                "${quality.code} kbps · ${quality.description}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────── audio enhancements ───────────────────────

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun EnhancementsCard(
    settings: MusicEffectSettings,
    onToggleEnabled: (Boolean) -> Unit,
    onApplyEnhanced: () -> Unit,
    onReset: () -> Unit,
    onPresetChange: (EqPreset) -> Unit,
    onBassChange: (Int) -> Unit,
    onVirtualizerChange: (Int) -> Unit,
    onLoudnessChange: (Int) -> Unit
) {
    val enabled = settings.enabled
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            // Header + master toggle
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Equalizer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Audio enhancements",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Equalizer + bass + stereo widening + loudness. Applies " +
                            "on top of streaming. Works best with headphones.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggleEnabled
                )
            }

            Spacer(Modifier.height(12.dp))

            // Quick-enhance + reset buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onApplyEnhanced,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Quick enhance")
                }
                TextButton(
                    onClick = onReset,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reset to flat")
                }
            }

            Spacer(Modifier.height(8.dp))

            // Equalizer preset chips
            Text(
                "EQ preset",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EqPreset.entries.forEach { preset ->
                    val on = enabled && preset == settings.preset
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (on) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable(enabled = enabled) { onPresetChange(preset) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            preset.label,
                            color = if (on) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface.copy(
                                alpha = if (enabled) 1f else 0.4f
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Bass boost slider
            EffectSlider(
                label = "Bass boost",
                valuePct = settings.bassStrength / 10,
                range = 0..1000,
                value = settings.bassStrength,
                enabled = enabled,
                onChange = onBassChange,
                hint = "Adds low-end punch. Around 40–60% sounds best on phone speakers."
            )

            Spacer(Modifier.height(10.dp))

            // Virtualizer slider
            EffectSlider(
                label = "Stereo virtualizer",
                valuePct = settings.virtualizerStrength / 10,
                range = 0..1000,
                value = settings.virtualizerStrength,
                enabled = enabled,
                onChange = onVirtualizerChange,
                hint = "Widens the stereo image. Best with headphones — can sound thin on speakers."
            )

            Spacer(Modifier.height(10.dp))

            // Loudness slider — measured in millibels (0..2000 = 0..20 dB)
            EffectSlider(
                label = "Loudness gain",
                valuePct = settings.loudnessGainMb / 20,  // 2000 mB → 100
                range = 0..2000,
                value = settings.loudnessGainMb,
                enabled = enabled,
                onChange = onLoudnessChange,
                hint = "Boosts overall volume on top of the system level. " +
                    "Too much will clip — keep under 50% for clean output."
            )
        }
    }
}

@Composable
private fun EffectSlider(
    label: String,
    valuePct: Int,
    range: IntRange,
    value: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
    hint: String
) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f).alpha(alpha)
        )
        Text(
            "$valuePct%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.alpha(alpha)
        )
    }
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange(it.toInt()) },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        hint,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.alpha(alpha)
    )
}

// ─────────────────────── language preferences ───────────────────────

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun LanguageCard(
    selected: Set<MusicLanguage>,
    onToggle: (MusicLanguage) -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            HeaderRow(
                icon = Icons.Rounded.Language,
                title = "Language suggestions",
                subtitle = "Pick the languages you usually listen to. JioSaavn " +
                    "results are reordered so songs in your preferred languages " +
                    "appear first. Multi-select."
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MusicLanguage.all.forEach { lang ->
                    val on = lang in selected
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (on) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { onToggle(lang) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            lang.label,
                            color = if (on) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${selected.size} selected · primary is ${selected.firstOrNull()?.label ?: "—"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────── shared ───────────────────────

@Composable
private fun HeaderRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
