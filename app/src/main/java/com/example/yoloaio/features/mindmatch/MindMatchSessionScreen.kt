package com.example.yoloaio.features.mindmatch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.yoloaio.data.FirebaseModule
import com.example.yoloaio.ui.components.GlassCard
import kotlinx.coroutines.launch

/**
 * Owns the whole MindMatch session lifecycle for one [code]: role
 * resolution (host / guest / already-full), the 5-question quiz, waiting
 * for the other person, and the final result. See the MindMatch plan's
 * "state machine" section for the full reasoning — this composable is a
 * fairly direct transcription of it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MindMatchSessionScreen(
    code: String,
    onBack: () -> Unit,
    onPlayAgain: (newCode: String) -> Unit
) {
    val repository = remember { MindMatchRepository() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val myUid = remember { FirebaseModule.auth.currentUser?.uid }
    val myDisplayName = remember {
        FirebaseModule.auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Unknown"
    }

    var loaded by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf<MindMatchSessionDoc?>(null) }
    var role by remember { mutableStateOf<MindMatchRole?>(null) }
    var sessionFull by remember { mutableStateOf(false) }
    var claiming by remember { mutableStateOf(false) }

    LaunchedEffect(code) {
        repository.observeSession(code).collect {
            session = it
            loaded = true
        }
    }

    // Role resolution — re-runs whenever the live doc changes, so a
    // successful guest-slot claim (which triggers a fresh snapshot with
    // guestUid == me) naturally settles into the GUEST branch on the next
    // pass, and a lost race settles into SessionFull once the doc reflects
    // someone else's claim. See the plan's state-machine section.
    LaunchedEffect(loaded, session) {
        if (!loaded) return@LaunchedEffect
        val s = session ?: return@LaunchedEffect
        val me = myUid ?: return@LaunchedEffect
        when {
            s.hostUid == me -> role = MindMatchRole.HOST
            s.guestUid == me -> role = MindMatchRole.GUEST
            s.guestUid.isBlank() -> {
                if (!claiming) {
                    claiming = true
                    repository.claimGuestSlot(code, myDisplayName)
                    claiming = false
                }
            }
            else -> sessionFull = true
        }
    }

    val currentSession = session
    val currentRole = role

    when {
        !loaded || (currentRole == null && !sessionFull && currentSession != null) -> LoadingContent(onBack)
        currentSession == null -> MessageContent(
            onBack = onBack,
            icon = Icons.Rounded.SearchOff,
            title = "Code not found",
            body = "This code doesn't look right — check with your friend and try again."
        )
        sessionFull -> MessageContent(
            onBack = onBack,
            icon = Icons.Rounded.Close,
            title = "This MindMatch is full",
            body = "It already has two players."
        )
        currentRole == MindMatchRole.HOST && currentSession.guestUid.isBlank() -> WaitingForGuestContent(
            code = code,
            onBack = onBack,
            onCopy = { clipboard.setText(AnnotatedString(code)) }
        )
        else -> {
            val myAnswers = if (currentRole == MindMatchRole.HOST) currentSession.hostAnswers else currentSession.guestAnswers
            val otherAnswers = if (currentRole == MindMatchRole.HOST) currentSession.guestAnswers else currentSession.hostAnswers
            val otherName = (if (currentRole == MindMatchRole.HOST) currentSession.guestDisplayName else currentSession.hostDisplayName)
                .takeIf { it.isNotBlank() } ?: "your friend"

            when {
                myAnswers.isEmpty() -> AnsweringQuestionsContent(
                    onBack = onBack,
                    onSubmit = { answers ->
                        scope.launch {
                            if (currentRole == MindMatchRole.HOST) repository.submitHostAnswers(code, answers)
                            else repository.submitGuestAnswers(code, answers)
                        }
                    }
                )
                otherAnswers.isEmpty() -> WaitingForPeerContent(otherName = otherName, onBack = onBack)
                else -> ResultContent(
                    myAnswers = myAnswers,
                    otherAnswers = otherAnswers,
                    otherName = otherName,
                    onBack = onBack,
                    onPlayAgain = {
                        scope.launch {
                            repository.createSession().onSuccess { newCode -> onPlayAgain(newCode) }
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MindMatchTopBar(onBack: () -> Unit) {
    CenterAlignedTopAppBar(
        title = { Text("MindMatch") },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, contentDescription = "Back")
            }
        }
    )
}

@Composable
private fun LoadingContent(onBack: () -> Unit) {
    Scaffold(topBar = { MindMatchTopBar(onBack) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun MessageContent(
    onBack: () -> Unit,
    icon: ImageVector,
    title: String,
    body: String
) {
    Scaffold(topBar = { MindMatchTopBar(onBack) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
    }
}

@Composable
private fun WaitingForGuestContent(code: String, onBack: () -> Unit, onCopy: () -> Unit) {
    Scaffold(topBar = { MindMatchTopBar(onBack) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Waiting for your friend to join…",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "Your code",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    MindMatchId.format(code),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onCopy) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy code")
                }
            }
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun WaitingForPeerContent(otherName: String, onBack: () -> Unit) {
    Scaffold(topBar = { MindMatchTopBar(onBack) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "You're done! Waiting for $otherName…",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnsweringQuestionsContent(onBack: () -> Unit, onSubmit: (List<Int>) -> Unit) {
    var selections by remember { mutableStateOf(List(MIND_MATCH_QUESTIONS.size) { -1 }) }
    var submitted by remember { mutableStateOf(false) }
    val allAnswered = selections.all { it >= 0 }

    Scaffold(topBar = { MindMatchTopBar(onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 20.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(MIND_MATCH_QUESTIONS) { index, question ->
                    QuestionCard(
                        index = index,
                        question = question,
                        selected = selections[index],
                        onSelect = { option ->
                            selections = selections.toMutableList().also { it[index] = option }
                        }
                    )
                }
            }
            Button(
                onClick = { submitted = true; onSubmit(selections) },
                enabled = allAnswered && !submitted,
                modifier = Modifier.fillMaxWidth().padding(20.dp).height(56.dp)
            ) {
                if (submitted) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                else Text("Submit")
            }
        }
    }
}

@Composable
private fun QuestionCard(index: Int, question: MindMatchQuestion, selected: Int, onSelect: (Int) -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth(), enableTilt = false) {
        Column {
            Text(
                "${index + 1}. ${question.text}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            question.options.forEachIndexed { optionIndex, option ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected == optionIndex, onClick = { onSelect(optionIndex) })
                    Text(option, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun ResultContent(
    myAnswers: List<Long>,
    otherAnswers: List<Long>,
    otherName: String,
    onBack: () -> Unit,
    onPlayAgain: () -> Unit
) {
    val percent = remember(myAnswers, otherAnswers) { computeMatchPercent(myAnswers, otherAnswers) }
    val (label, flavor) = remember(percent) { matchLabel(percent) }

    Scaffold(topBar = { MindMatchTopBar(onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "$percent%",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    flavor,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 20.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(MIND_MATCH_QUESTIONS.size) { index ->
                    val question = MIND_MATCH_QUESTIONS[index]
                    val myPick = myAnswers.getOrNull(index)?.toInt()
                    val otherPick = otherAnswers.getOrNull(index)?.toInt()
                    val matched = myPick != null && myPick == otherPick
                    ComparisonCard(
                        question = question,
                        matched = matched,
                        otherName = otherName,
                        myPick = myPick,
                        otherPick = otherPick
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).height(56.dp)) {
                    Text("Done")
                }
                Button(onClick = onPlayAgain, modifier = Modifier.weight(1f).height(56.dp)) {
                    Text("Play again")
                }
            }
        }
    }
}

@Composable
private fun ComparisonCard(
    question: MindMatchQuestion,
    matched: Boolean,
    otherName: String,
    myPick: Int?,
    otherPick: Int?
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), enableTilt = false) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (matched) Icons.Rounded.Check else Icons.Rounded.Close,
                    contentDescription = null,
                    tint = if (matched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(question.text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "You: ${myPick?.let { question.options.getOrNull(it) } ?: "—"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "$otherName: ${otherPick?.let { question.options.getOrNull(it) } ?: "—"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
