package com.example.yoloaio.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class AppConfigRepository {
    private val firestore = FirebaseModule.firestore

    fun observeConfig(): Flow<AppConfig> = callbackFlow {
        val registration = firestore.collection("config").document("app")
            .addSnapshotListener { snap, _ ->
                val cfg = snap?.toObject(AppConfig::class.java) ?: AppConfig()
                trySend(cfg)
            }
        awaitClose { registration.remove() }
    }
}

val LocalAppConfig = compositionLocalOf<AppConfig> { AppConfig() }

@Composable
fun rememberAppConfig(): State<AppConfig> {
    val repo = remember { AppConfigRepository() }
    return repo.observeConfig().collectAsState(initial = AppConfig())
}
