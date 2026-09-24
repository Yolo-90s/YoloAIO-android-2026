package com.example.yoloaio.data

data class AppConfig(
    val admin: Boolean = false,
    val moviesUrl: String = "",
    val showMoviesMenu: Boolean = true,
    val showMusicMenu: Boolean = true,
    val showNewsMenu: Boolean = true,
    val showSettingsMenu: Boolean = true,
    val showWallpapersMenu: Boolean = true,
    val showWeatherMenu: Boolean = true,
    val showBooksMenu: Boolean = true,
    val showBeatAnalyserMenu: Boolean = true,
    val showWalkieTalkieMenu: Boolean = true,
    val showThreeDMenuMenu: Boolean = true,
    val showMindMatchMenu: Boolean = true,
    val unsplashAccessKey: String = "",
    val unsplashSecretKey: String = "",
    val wallpapersUrl: String = "",
    val weatherApiKey: String = "",
    val weatherWebUrl: String = "",
    val tmdbApiKey: String = "",
    val tmdbAccessToken: String = "",
    val freesoundApiKey: String = "",
    val googleWebClientId: String = "",
    // Base URL of the Vercel-hosted videos proxy that lists + streams videos
    // from a private Google Drive folder. Same proxy + Firestore config value
    // used by the YoloAIO web app at the `/videos` route — set it once in
    // Firestore `config/app` and both clients pick it up.
    val videosApiBaseUrl: String = "",
    // Jitsi Meet server URL used by the in-app call feature. Defaults to
    // `meet.jit.si` when blank, BUT that instance now requires the first
    // participant to be an authenticated moderator (you hit the "waiting
    // for moderator" lobby). Override with a community / self-hosted
    // instance that allows open conference creation. Set in Firestore
    // `config/app.jitsiServerUrl` — change takes effect on next call,
    // no rebuild needed.
    val jitsiServerUrl: String = "",
    // TURN relay for the raw-WebRTC WalkieTalkie feature. Google's public
    // STUN (stun.l.google.com) is always used as a baseline; these three
    // fields add a TURN server on top, required for two devices on
    // different cellular networks to reliably connect (STUN alone often
    // fails behind carrier-grade NAT). Get a free TURN endpoint from
    // Metered.ca (or self-host coturn) and set these three in Firestore
    // `config/app` — no rebuild needed.
    val turnUrl: String = "",
    val turnUsername: String = "",
    val turnCredential: String = "",
    // Published Spline scene URL (Spline Editor → Export → Public URL,
    // ends in .splinecontent) rendered full-screen by ThreeDMenuScreen.
    // Blank by default — the screen shows a "not configured" state rather
    // than trying to load an empty URL. Set in Firestore `config/app`, no
    // rebuild needed.
    val threeDMenuSceneUrl: String = "",
    // Minimum Role (by wireValue: "guest"/"user"/"admin") required to see
    // each Home menu tile, keyed by the tile's `key` (e.g. "movies") — see
    // HomeScreen.kt's `allTiles`. Admin-editable from Settings → Menu
    // Access. A key absent from this map falls back to DEFAULT_MIN_ROLE,
    // then USER. ADMIN/DEVELOPER always see every tile regardless of this
    // map — see AppConfig.minRoleFor's callers.
    val menuMinRole: Map<String, String> = emptyMap()
) {
    val unsplashQuery: String
        get() = parseUnsplashQuery(wallpapersUrl) ?: "nature"

    /**
     * Best TMDB credential. Prefers the v4 bearer token (long string starting with "eyJ")
     * when supplied — the TmdbClient auto-detects bearer vs v3 by the prefix.
     */
    val tmdbAuth: String
        get() = tmdbAccessToken.takeIf { it.isNotBlank() } ?: tmdbApiKey

    fun effectiveGoogleWebClientId(fallback: String? = null): String {
        return googleWebClientId.takeIf { it.isNotBlank() }
            ?: fallback?.takeIf { it.isNotBlank() }
            ?: ""
    }

    /** The minimum [Role] required to see the Home tile with this [key]. */
    fun minRoleFor(key: String): Role {
        menuMinRole[key]?.let { return Role.fromWire(it) }
        return DEFAULT_MIN_ROLE[key] ?: Role.USER
    }

    companion object {
        // Menus that don't follow the blanket "USER sees everything else"
        // default: the basics stay open to GUEST, and the experimental 3D
        // Menu starts admin-only until an admin chooses to open it up.
        private val DEFAULT_MIN_ROLE = mapOf(
            "movies" to Role.GUEST,
            "music" to Role.GUEST,
            "chat" to Role.GUEST,
            "three_d_menu" to Role.ADMIN
        )
    }
}

private fun parseUnsplashQuery(url: String): String? {
    if (url.isBlank()) return null
    val markers = listOf("/s/photos/", "/photos/")
    for (marker in markers) {
        val idx = url.indexOf(marker)
        if (idx >= 0) {
            return url.substring(idx + marker.length)
                .trim('/')
                .substringBefore('/')
                .substringBefore('?')
                .takeIf { it.isNotBlank() }
        }
    }
    return null
}
