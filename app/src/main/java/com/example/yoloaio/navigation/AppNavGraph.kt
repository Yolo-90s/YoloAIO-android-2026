package com.example.yoloaio.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.yoloaio.data.UserSession
import com.example.yoloaio.features.audio.AudioTrimmerScreen
import com.example.yoloaio.features.auth.AuthScreen
import com.example.yoloaio.features.chat.ChatConversationScreen
import com.example.yoloaio.features.chat.ChatScreen
import com.example.yoloaio.features.chat.UserProfileScreen
import com.example.yoloaio.features.community.CommunityChannelScreen
import com.example.yoloaio.features.videos.VideoPlayerScreen
import com.example.yoloaio.features.videos.VideosScreen
import com.example.yoloaio.features.home.HomeScreen
import com.example.yoloaio.features.movies.MovieDetailScreen
import com.example.yoloaio.features.movies.MoviePlayerScreen
import com.example.yoloaio.features.movies.MoviesScreen
import com.example.yoloaio.features.movies.TvDetailScreen
import com.example.yoloaio.features.movies.TvPlayerScreen
import com.example.yoloaio.features.music.MusicScreen
import com.example.yoloaio.features.music.MusicSettingsScreen
import com.example.yoloaio.features.beat.BeatAnalyserScreen
import com.example.yoloaio.features.books.BookFavoritesScreen
import com.example.yoloaio.features.books.BookReaderScreen
import com.example.yoloaio.features.books.BooksScreen
import com.example.yoloaio.features.ringtones.RingtoneFavoritesScreen
import com.example.yoloaio.features.ringtones.RingtonesScreen
import com.example.yoloaio.features.quotes.QuoteEditorScreen
import com.example.yoloaio.features.quotes.QuotesScreen
import com.example.yoloaio.features.settings.PrivacyScreen
import com.example.yoloaio.features.settings.SettingsScreen
import com.example.yoloaio.features.wallpaper.WallpaperDetailScreen
import com.example.yoloaio.features.wallpaper.WallpaperFavoritesScreen
import com.example.yoloaio.features.wallpaper.WallpaperScreen
import com.example.yoloaio.features.weather.WeatherScreen
import com.example.yoloaio.features.wifi.WifiLabScreen

@Composable
fun AppNavGraph(
    deepLinkChatPartnerUid: String? = null,
    onDeepLinkConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val startDestination = remember {
        if (UserSession.isSignedIn) Routes.HOME else Routes.AUTH
    }

    // Tap-to-open from a chat notification. We only honour the deep link
    // when the user is already signed in — otherwise the AuthScreen would
    // try to pop a back-stack entry that doesn't exist.
    LaunchedEffect(deepLinkChatPartnerUid) {
        val partner = deepLinkChatPartnerUid ?: return@LaunchedEffect
        if (UserSession.isSignedIn) {
            navController.navigate(Routes.chatConversation(partner)) {
                launchSingleTop = true
            }
        }
        onDeepLinkConsumed()
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.AUTH) {
            AuthScreen(
                onAuthenticated = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onTileClick = { route -> navController.navigate(route) },
                onUserIconClick = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onPrivacyClick = { navController.navigate(Routes.PRIVACY) },
                onSignOut = {
                    navController.navigate(Routes.AUTH) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.PRIVACY) {
            PrivacyScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.AUDIO_TRIMMER) {
            AudioTrimmerScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.CHAT) {
            ChatScreen(
                onBack = { navController.popBackStack() },
                onUserClick = { userId ->
                    navController.navigate(Routes.chatConversation(userId))
                },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(
            route = Routes.CHAT_CONVERSATION,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId").orEmpty()
            ChatConversationScreen(
                userId = userId,
                onBack = { navController.popBackStack() },
                onOpenProfile = { uid -> navController.navigate(Routes.userProfile(uid)) }
            )
        }
        composable(
            route = Routes.USER_PROFILE,
            arguments = listOf(navArgument("uid") { type = NavType.StringType })
        ) { backStackEntry ->
            val uid = backStackEntry.arguments?.getString("uid").orEmpty()
            UserProfileScreen(uid = uid, onBack = { navController.popBackStack() })
        }
        composable(Routes.MUSIC) {
            MusicScreen(
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Routes.MUSIC_SETTINGS) }
            )
        }
        composable(Routes.MUSIC_SETTINGS) {
            MusicSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.MOVIES) {
            MoviesScreen(
                onBack = { navController.popBackStack() },
                onTitleClick = { mediaType, id ->
                    val route = if (mediaType == "tv") Routes.tvDetail(id)
                    else Routes.movieDetail(id)
                    navController.navigate(route)
                }
            )
        }
        composable(
            route = Routes.MOVIE_DETAIL,
            arguments = listOf(navArgument("movieId") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("movieId").orEmpty()
            MovieDetailScreen(
                movieId = id,
                onBack = { navController.popBackStack() },
                onPlay = { mid -> navController.navigate(Routes.moviePlayer(mid)) }
            )
        }
        composable(
            route = Routes.MOVIE_PLAYER,
            arguments = listOf(navArgument("movieId") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("movieId").orEmpty()
            MoviePlayerScreen(movieId = id, onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.TV_DETAIL,
            arguments = listOf(navArgument("tvId") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("tvId").orEmpty()
            TvDetailScreen(
                tvId = id,
                onBack = { navController.popBackStack() },
                onPlayEpisode = { showId, season, episode ->
                    navController.navigate(Routes.tvPlayer(showId, season, episode))
                }
            )
        }
        composable(
            route = Routes.TV_PLAYER,
            arguments = listOf(
                navArgument("tvId") { type = NavType.StringType },
                navArgument("season") { type = NavType.IntType },
                navArgument("episode") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("tvId").orEmpty()
            val season = backStackEntry.arguments?.getInt("season") ?: 1
            val episode = backStackEntry.arguments?.getInt("episode") ?: 1
            TvPlayerScreen(
                tvId = id,
                season = season,
                episode = episode,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.QUOTES) {
            QuotesScreen(
                onBack = { navController.popBackStack() },
                onCreateQuote = { navController.navigate(Routes.QUOTE_EDITOR) }
            )
        }
        composable(Routes.QUOTE_EDITOR) {
            QuoteEditorScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }
        composable(Routes.WALLPAPER) {
            WallpaperScreen(
                onBack = { navController.popBackStack() },
                onWallpaperClick = { id -> navController.navigate(Routes.wallpaperDetail(id)) },
                onFavoritesClick = { navController.navigate(Routes.WALLPAPER_FAVORITES) }
            )
        }
        composable(Routes.WALLPAPER_FAVORITES) {
            WallpaperFavoritesScreen(
                onBack = { navController.popBackStack() },
                onWallpaperClick = { id -> navController.navigate(Routes.wallpaperDetail(id)) }
            )
        }
        composable(
            route = Routes.WALLPAPER_DETAIL,
            arguments = listOf(navArgument("wallpaperId") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("wallpaperId").orEmpty()
            WallpaperDetailScreen(
                wallpaperId = id,
                onBack = { navController.popBackStack() },
                onRelatedClick = { newId -> navController.navigate(Routes.wallpaperDetail(newId)) }
            )
        }
        composable(Routes.RINGTONES) {
            RingtonesScreen(
                onBack = { navController.popBackStack() },
                onFavoritesClick = { navController.navigate(Routes.RINGTONE_FAVORITES) }
            )
        }
        composable(Routes.RINGTONE_FAVORITES) {
            RingtoneFavoritesScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.BOOKS) {
            BooksScreen(
                onBack = { navController.popBackStack() },
                onBookClick = { id -> navController.navigate(Routes.bookReader(id)) },
                onFavoritesClick = { navController.navigate(Routes.BOOK_FAVORITES) }
            )
        }
        composable(Routes.BOOK_FAVORITES) {
            BookFavoritesScreen(
                onBack = { navController.popBackStack() },
                onBookClick = { id -> navController.navigate(Routes.bookReader(id)) }
            )
        }
        composable(
            route = Routes.BOOK_READER,
            arguments = listOf(navArgument("bookId") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("bookId").orEmpty()
            BookReaderScreen(
                bookId = id,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.BEAT_ANALYSER) {
            BeatAnalyserScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.WEATHER) {
            WeatherScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.WIFI_LAB) {
            WifiLabScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.COMMUNITY) {
            CommunityChannelScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.VIDEOS) {
            VideosScreen(
                onBack = { navController.popBackStack() },
                onOpenVideo = { videoId ->
                    navController.navigate(Routes.videoPlayer(videoId))
                }
            )
        }
        composable(
            route = Routes.VIDEO_PLAYER,
            arguments = listOf(navArgument("videoId") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("videoId").orEmpty()
            VideoPlayerScreen(
                videoId = id,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
