package com.example.yoloaio.features.mindmatch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.Button
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * MindMatch lobby: start a fresh session (as host) or join one with a
 * peer's code (as guest). Both paths just navigate to the session route —
 * the actual host/guest role resolution and guest-slot claiming happens
 * once inside [MindMatchSessionScreen], so this screen, the Chat
 * contact-row shortcut, and a tapped chat invite bubble all funnel
 * through one single place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MindMatchScreen(onBack: () -> Unit, onOpenSession: (code: String) -> Unit) {
    val repository = remember { MindMatchRepository() }
    val scope = rememberCoroutineScope()

    var starting by remember { mutableStateOf(false) }
    var codeInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun onStart() {
        if (starting) return
        starting = true
        error = null
        scope.launch {
            repository.createSession()
                .onSuccess { code -> onOpenSession(code) }
                .onFailure { error = it.message ?: "Couldn't start a MindMatch" }
            starting = false
        }
    }

    fun onJoin() {
        val code = MindMatchId.normalize(codeInput)
        if (code.length == 6) onOpenSession(code)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("MindMatch") },
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier
                    .size(72.dp)
                    .padding(bottom = 4.dp),
            ) {
                Icon(
                    Icons.Rounded.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "See how you match",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "You and a friend each answer the same 5 questions. We'll tell you how much you think alike.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))

            Button(
                onClick = ::onStart,
                enabled = !starting,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (starting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text("Start a new MindMatch")
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "or",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = codeInput,
                onValueChange = { codeInput = MindMatchId.normalize(it) },
                label = { Text("Friend's code") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = ::onJoin,
                enabled = MindMatchId.normalize(codeInput).length == 6,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Join")
            }

            error?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
