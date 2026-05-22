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
    val jitsiServerUrl: String = ""
) {
    val unsplashQuery: String
        get() = parseUnsplashQuery(wallpapersUrl) ?: "nature"

    /**
     * Best TMDB credential. Prefers the v4 bearer token (long string starting with "eyJ")
     * when supplied — the TmdbClient auto-detects bearer vs v3 by the prefix.
     */
    val tmdbAuth: String
        get() = tmdbAccessToken.takeIf { it.isNotBlank() } ?: tmdbApiKey
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
