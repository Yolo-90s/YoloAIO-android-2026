package com.example.yoloaio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.yoloaio.cast.CastManager
import com.example.yoloaio.data.AppUpdateChecker
import com.example.yoloaio.data.LocalAppConfig
import com.example.yoloaio.data.LocationPresence
import com.example.yoloaio.data.UserSession
import com.example.yoloaio.data.rememberAppConfig
import com.example.yoloaio.navigation.AppNavGraph
import com.example.yoloaio.notifications.ChatNotifications
import com.example.yoloaio.notifications.NotificationChannels
import com.example.yoloaio.ui.components.AppBackground
import com.example.yoloaio.ui.components.LaunchAnimation
import com.example.yoloaio.ui.theme.YoloAIOTheme
import com.example.yoloaio.ui.theme.rememberThemePalette
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var deepLinkChatPartnerUid by mutableStateOf<String?>(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* user choice — we don't gate startup on the result */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Allow inspecting the player WebView from desktop Chrome (chrome://inspect)
        // — debug builds only. In release this would let anyone with adb access
        // remote-inspect users' WebViews.
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        enableEdgeToEdge()

        NotificationChannels.ensure(this)
        maybeRequestNotificationPermission()

        // Silent App Distribution update check — fires at most once per
        // 24 h. If a newer APK is available on App Distribution, the SDK
        // shows its built-in update dialog with download + install.
        AppUpdateChecker.maybeCheckOnLaunch(this)
        // Cast SDK boots its CastContext on first access. Doing it from
        // onCreate guarantees a MediaRouteButton anywhere in the app finds an
        // already-initialised framework. Safe on devices without Google Play
        // Services — init() returns false and Cast features just stay hidden.
        CastManager.get(this).init()
        // Tie the chat notification listener to auth state — restart it with
        // the new uid on sign-in, tear it down on sign-out. Idempotent on
        // the unchanged-user case.
        lifecycleScope.launch {
            UserSession.authStateFlow.collectLatest { user ->
                ChatNotifications.stop()
                if (user != null) ChatNotifications.start(this@MainActivity)
            }
        }

        deepLinkChatPartnerUid = readChatDeepLink(intent)

        setContent {
            val config by rememberAppConfig()
            val palette by rememberThemePalette()
            // Show the 3-second cyberpunk decryption splash on first
            // composition. The nav graph mounts immediately underneath
            // so any cold-start work (Firestore listeners, theme,
            // config) finishes during the animation.
            var splashDone by remember { mutableStateOf(false) }
            YoloAIOTheme(palette = palette) {
                CompositionLocalProvider(LocalAppConfig provides config) {
                    AppBackground {
                        AppNavGraph(
                            deepLinkChatPartnerUid = deepLinkChatPartnerUid,
                            onDeepLinkConsumed = { deepLinkChatPartnerUid = null }
                        )
                    }
                    if (!splashDone) {
                        LaunchAnimation(onComplete = { splashDone = true })
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Best-effort presence update — writes the current user's
        // location to /users/{uid} so chat partners can see where they
        // were last known to be when they last opened the app. No-ops
        // if location permission isn't granted or the throttle window
        // hasn't elapsed (5 min).
        LocationPresence.maybeUpdate(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readChatDeepLink(intent)?.let { deepLinkChatPartnerUid = it }
    }

    override fun onDestroy() {
        ChatNotifications.stop()
        super.onDestroy()
    }

    private fun readChatDeepLink(intent: Intent?): String? {
        if (intent == null) return null
        return intent.getStringExtra(ChatNotifications.EXTRA_OPEN_CHAT_PARTNER_UID)
            ?.takeIf { it.isNotBlank() }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
