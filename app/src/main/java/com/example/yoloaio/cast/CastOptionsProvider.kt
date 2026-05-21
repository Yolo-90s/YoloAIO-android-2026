package com.example.yoloaio.cast

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.NotificationOptions

/**
 * Cast SDK requires an [OptionsProvider] referenced from the manifest's
 * `<meta-data android:name="com.google.android.gms.cast.framework.OPTIONS_PROVIDER_CLASS_NAME" />`
 * tag. This class is instantiated reflectively by the SDK on first
 * `CastContext.getSharedInstance(...)` call.
 *
 * Uses the **Default Media Receiver** (`CC1AD845`) — Google's vanilla
 * receiver that supports plain audio/video streams without us needing to
 * register a custom receiver in the Cast Developer Console. Good enough for
 * a Music-only V1; we can swap in a custom receiver later if we want
 * styled now-playing UI on the cast device.
 */
class CastOptionsProvider : OptionsProvider {

    override fun getCastOptions(context: Context): CastOptions {
        val notificationOptions = NotificationOptions.Builder()
            .setTargetActivityClassName("com.example.yoloaio.MainActivity")
            .build()

        val castMediaOptions = CastMediaOptions.Builder()
            .setNotificationOptions(notificationOptions)
            .build()

        return CastOptions.Builder()
            .setReceiverApplicationId(
                CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
            )
            .setCastMediaOptions(castMediaOptions)
            .setStopReceiverApplicationWhenEndingSession(true)
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
