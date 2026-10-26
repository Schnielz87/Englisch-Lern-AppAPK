package de.englischlernen.app.ui.session

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.englischlernen.app.domain.Exercise
import de.englischlernen.app.ui.components.FundbruederLogo
import de.englischlernen.app.ui.components.LocalAppContainer
import de.englischlernen.app.ui.components.SectionCard
import de.englischlernen.app.ui.components.VSpace
import de.englischlernen.app.ui.theme.Brand

@Composable
fun SessionScreen(
    mode: SessionMode,
    arg: String,
    onClose: () -> Unit,
    onOpenLesson: (String) -> Unit,
) {
    val c = LocalAppContainer.current
    val vm: SessionViewModel = viewModel(key = "$mode:$arg") { SessionViewModel(c, mode, arg) }
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmExit by remember { mutableStateOf(false) }

    BackHandler(enabled = state.summary == null && !state.loading) { confirmExit = true }
    if (confirmExit) AlertDialog(
        onDismissRequest = { confirmExit = false },
        title = { Text("Wirklich beenden?") },
        text = { Text("Dein Fortschritt in dieser Einheit geht verloren.") },
        confirmButton = { TextButton(onClick = onClose) { Text("Beenden") } },
        dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Weiterlernen") } },
    )

    Scaffold(
        topBar = {
            if (state.summary == null) Row(
                Modifier.statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { if (state.loading || state.empty || state.error != null) onClose() else confirmExit = true }) {
                    Icon(Icons.Filled.Close, "Schließen")
                }
                LinearProgressIndicator(
                    progress = { state.progress }, modifier = Modifier.weight(1f).height(10.dp),
                    strokeCap = StrokeCap.Round, color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant, gapSize = 0.dp, drawStopIndicator = {},
                )
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Diamond, null, tint = MaterialTheme.colorScheme.secondary)
                    Text(" ${state.xp}", fontWeight = FontWeight.Bold)
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(state.feedback != null, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                state.feedback?.let { FeedbackPanel(it, vm::next, vm::explainWithAi) }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> Message("Etwas ist schiefgelaufen", state.error!!, onClose)
                state.empty -> Message(
                    "Hier gibt es gerade nichts zu tun",
                    when (mode) {
                        SessionMode.REVIEW -> "Keine Vokabeln fällig. Schließe Lektionen ab – dann füllt sich dein Fundbuch."
                        SessionMode.WEAK -> "Keine Schwachstellen gefunden. Stark!"
                        else -> "Für diesen Bereich sind noch keine Inhalte freigeschaltet."
                    },
                    onClose,
                )
                state.summary != null -> SummaryView(
                    state.summary!!, onClose = onClose, onRetry = vm::restart,
                    onNext = state.summary!!.nextLessonId?.let { id -> { onOpenLesson(id) } },
                )
                else -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp),
                ) {
                    val answered = state.feedback != null
                    when (val ex = state.current) {
                        is Exercise.Info -> InfoView(ex, vm::continueUnscored)
                        is Exercise.Flashcard -> FlashcardView(ex, vm::continueUnscored)
                        is Exercise.Choice -> ChoiceView(ex, answered, vm::answerChoice)
                        is Exercise.TypeIn -> TypeInView(ex, answered, vm::answerText)
                        is Exercise.Arrange -> ArrangeView(ex, answered, vm::answerArrange)
                        is Exercise.Speak -> SpeakView(ex, answered, vm)
                        is Exercise.DialoguePractice -> DialogueView(ex, answered, vm)
                        null -> CircularProgressIndicator()
                    }
                    VSpace(120.dp)
                }
            }
        }
    }
}

@Composable
private fun FeedbackPanel(f: Feedback, onNext: () -> Unit, onExplain: () -> Unit) {
    val color = if (f.correct) Brand.Success else Brand.Error
    val aiReady = LocalAppContainer.current.ai.config.collectAsStateWithLifecycle().value.ready
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (f.correct) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline, null, tint = color)
                Text("  ${f.title}", style = MaterialTheme.typography.titleLarge, color = color)
            }
            f.expected?.let {
                VSpace(6.dp)
                Text("Richtig ist: $it", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
            if (f.explanation.isNotBlank()) {
                VSpace(4.dp)
                Text(f.explanation, style = MaterialTheme.typography.bodyMedium)
            }
            if (!f.correct && aiReady) {
                when {
                    f.aiLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.height(20.dp)); Text("  Finn überlegt …")
                    }
                    f.aiExplanation != null -> Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                        VSpace(6.dp); Text("💡 Finn erklärt:", style = MaterialTheme.typography.labelLarge); RichText(f.aiExplanation)
                    }
                    else -> TextButton(onClick = onExplain) { Text("💡 Warum? – Finn erklärt's (KI)") }
                }
            }
            VSpace(12.dp)
            Button(
                onClick = onNext, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = color),
            ) { Text("Weiter") }
        }
    }
}

@Composable
private fun Message(title: String, text: String, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        FundbruederLogo(80.dp)
        VSpace(16.dp)
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        VSpace(8.dp)
        Text(text, textAlign = TextAlign.Center)
        VSpace(24.dp)
        Button(onClick = onClose) { Text("Zurück") }
    }
}

@Composable
private fun SummaryView(s: SessionSummary, onClose: () -> Unit, onRetry: () -> Unit, onNext: (() -> Unit)?) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        VSpace(24.dp)
        Icon(
            Icons.Filled.EmojiEvents, null, Modifier.height(72.dp).fillMaxWidth(),
            tint = if (s.passed == false) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.secondary,
        )
        Text(s.headline, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(s.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        VSpace(16.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryNumber("${s.scorePercent} %", "Ergebnis", Modifier.weight(1f))
            SummaryNumber("${s.correct}/${s.total}", "Richtig", Modifier.weight(1f))
            SummaryNumber("+${s.xp}", "Fundpunkte", Modifier.weight(1f))
        }
        VSpace(16.dp)
        Text(s.message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        if (s.mistakes.isNotEmpty()) {
            VSpace(20.dp)
            SectionCard {
                Text("Das üben wir noch einmal", style = MaterialTheme.typography.titleMedium)
                s.mistakes.take(12).forEach { m ->
                    VSpace(8.dp)
                    Text(promptOf(m.exercise), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("✔ ${m.expected}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = Brand.Success)
                    if (m.given.isNotBlank()) Text("✘ ${m.given}", style = MaterialTheme.typography.bodySmall, color = Brand.Error)
                }
            }
        }
        VSpace(24.dp)
        onNext?.let { Button(onClick = it, modifier = Modifier.fillMaxWidth()) { Text("Nächste Lektion") }; VSpace(8.dp) }
        if (s.passed == false) { Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Nochmal versuchen") }; VSpace(8.dp) }
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Fertig") }
    }
}

@Composable
private fun SummaryNumber(value: String, label: String, modifier: Modifier) {
    SectionCard(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

private fun promptOf(e: Exercise): String = when (e) {
    is Exercise.Choice -> if (e.listening) "Hörverstehen" else e.prompt
    is Exercise.TypeIn -> if (e.listening) "Diktat" else e.prompt
    is Exercise.Arrange -> e.prompt
    is Exercise.Speak -> "Aussprache: ${e.translation}"
    is Exercise.DialoguePractice -> "Rollenspiel: ${e.dialogue.title}"
    else -> ""
}
