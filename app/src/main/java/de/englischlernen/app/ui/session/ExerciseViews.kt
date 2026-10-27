@file:OptIn(ExperimentalLayoutApi::class)

package de.englischlernen.app.ui.session

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.englischlernen.app.domain.AnswerChecker
import de.englischlernen.app.domain.Exercise
import de.englischlernen.app.speech.SpeechInput
import de.englischlernen.app.ui.components.LocalAppContainer
import de.englischlernen.app.ui.components.SectionCard
import de.englischlernen.app.ui.components.SpeakerButtons
import de.englischlernen.app.ui.components.VSpace
import de.englischlernen.app.ui.theme.Brand

/** Aufgabe (erste Zeile) und – falls vorhanden – Erklaerung "So geht's" (weitere Zeilen). */
@Composable
fun Instruction(text: String) {
    val title = text.substringBefore('\n')
    val help = text.substringAfter('\n', "")
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    if (help.isNotBlank()) {
        VSpace(4.dp)
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text("ℹ️ $help", Modifier.padding(10.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
    VSpace(12.dp)
}

/** Einfache Formatierung: eine Zeile pro Absatz, **fett** wird hervorgehoben (auch innerhalb einer Zeile). */
@Composable
fun RichText(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        text.split("\n").filter { it.isNotBlank() }.forEach { line ->
            val clean = line.trim().replace(Regex("^#+\\s*"), "").replace(Regex("^[-*]\\s+"), "• ")
            Text(
                buildAnnotatedString {
                    clean.split("**").forEachIndexed { i, part ->
                        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) } else append(part)
                    }
                },
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
fun InfoView(ex: Exercise.Info, onNext: () -> Unit) {
    Column {
        Text("Wissen", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        Text(ex.title, style = MaterialTheme.typography.headlineSmall)
        VSpace(12.dp)
        RichText(ex.body)
        if (ex.examples.isNotEmpty()) {
            VSpace(16.dp)
            SectionCard {
                Text("Beispiele", style = MaterialTheme.typography.titleMedium)
                ex.examples.forEach { (en, de) ->
                    VSpace(8.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(en, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(de, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        SpeakerButtons(en, showSlow = false)
                    }
                }
            }
        }
        VSpace(24.dp)
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Verstanden") }
    }
}

@Composable
fun FlashcardView(ex: Exercise.Flashcard, onNext: () -> Unit) {
    val c = LocalAppContainer.current
    var flipped by remember(ex) { mutableStateOf(false) }
    LaunchedEffect(ex) { c.tts.speak(ex.vocab.en) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Instruction("Neue Vokabel\nHör dir das Wort an und präge es dir ein. Tippe auf die Karte, um die Bedeutung zu sehen.")
        Card(
            onClick = { flipped = !flipped },
            modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = if (flipped) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedContent(flipped, label = "flip") { f ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (!f) {
                            Text(ex.vocab.en, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
                            VSpace(8.dp)
                            Text("Tippen zum Umdrehen", style = MaterialTheme.typography.labelMedium)
                        } else {
                            Text(ex.vocab.de, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                            ex.vocab.example?.let {
                                VSpace(12.dp)
                                Text(it, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
                            }
                            ex.vocab.exampleDe?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
        VSpace(16.dp)
        SpeakerButtons(ex.vocab.en)
        ex.vocab.example?.let {
            TextButton(onClick = { c.tts.speak(it) }) { Text("Beispielsatz anhören") }
        }
        VSpace(16.dp)
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Weiter") }
    }
}

@Composable
private fun ListenPrompt(text: String, revealed: Boolean) {
    val c = LocalAppContainer.current
    LaunchedEffect(text) { c.tts.speak(text) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = { c.tts.speak(text) }, modifier = Modifier.size(88.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) { Icon(Icons.AutoMirrored.Filled.VolumeUp, "Anhören", Modifier.size(44.dp)) }
        TextButton(onClick = { c.tts.speak(text, slow = true) }) { Text("Langsam anhören") }
        if (revealed) Text(text, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        TtsHint()
    }
}

@Composable
fun TtsHint() {
    val c = LocalAppContainer.current
    val status by c.tts.status.collectAsStateWithLifecycle()
    if (status == de.englischlernen.app.speech.TtsManager.Status.MISSING_DATA || status == de.englischlernen.app.speech.TtsManager.Status.UNAVAILABLE) {
        Text(
            "Keine englische Stimme gefunden. Installiere sie in den Android-Einstellungen unter „Sprachausgabe“.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ChoiceView(ex: Exercise.Choice, answered: Boolean, onAnswer: (Int) -> Unit) {
    val c = LocalAppContainer.current
    var selected by remember(ex) { mutableIntStateOf(-1) }
    Column {
        Instruction(ex.instruction)
        if (ex.listening) ListenPrompt(ex.prompt, revealed = answered)
        else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ex.prompt, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                ex.speak?.let { SpeakerButtons(it, showSlow = false) }
            }
            LaunchedEffect(ex) { ex.speak?.let { c.tts.speak(it) } }
        }
        VSpace(20.dp)
        ex.options.forEachIndexed { i, option ->
            val color = when {
                !answered -> MaterialTheme.colorScheme.surfaceContainerHigh
                i == ex.correctIndex -> Brand.Success.copy(alpha = 0.18f)
                i == selected -> Brand.Error.copy(alpha = 0.18f)
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            }
            val border = when {
                answered && i == ex.correctIndex -> BorderStroke(2.dp, Brand.Success)
                answered && i == selected -> BorderStroke(2.dp, Brand.Error)
                else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            }
            Surface(
                onClick = { if (!answered) { selected = i; onAnswer(i) } },
                shape = RoundedCornerShape(16.dp), color = color, border = border,
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
            ) {
                Text(option, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun TypeInView(ex: Exercise.TypeIn, answered: Boolean, onAnswer: (String) -> Unit) {
    var text by rememberSaveable(ex.key, ex.prompt) { mutableStateOf("") }
    var showHint by remember(ex) { mutableStateOf(false) }
    Column {
        Instruction(ex.instruction)
        if (ex.listening) ListenPrompt(ex.speak ?: ex.prompt, revealed = answered)
        else Text(ex.prompt.replace("___", "_____"), style = MaterialTheme.typography.headlineSmall)
        VSpace(20.dp)
        OutlinedTextField(
            value = text, onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(), enabled = !answered,
            label = { Text("Deine Antwort (Englisch)") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank() && !answered) onAnswer(text) }),
        )
        if (ex.hint.isNotBlank() && !answered) {
            VSpace(8.dp)
            if (showHint) Text("💡 ${ex.hint}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
            else AssistChip(onClick = { showHint = true }, label = { Text("Tipp anzeigen") }, leadingIcon = { Icon(Icons.Filled.Lightbulb, null) })
        }
        VSpace(20.dp)
        if (!answered) Button(onClick = { onAnswer(text) }, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Prüfen") }
    }
}

@Composable
fun ArrangeView(ex: Exercise.Arrange, answered: Boolean, onAnswer: (List<String>) -> Unit) {
    val chosen = remember(ex) { mutableStateListOf<Int>() }
    Column {
        Instruction("Satzbau: Bilde den englischen Satz\nTippe die Wörter unten in der richtigen Reihenfolge an. Ein Tipp auf ein Wort oben nimmt es wieder zurück.")
        Text(ex.prompt, style = MaterialTheme.typography.headlineSmall)
        VSpace(16.dp)
        Surface(
            shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
        ) {
            FlowRow(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                chosen.forEach { idx ->
                    WordChip(ex.shuffled[idx], filled = true) { if (!answered) chosen.remove(idx) }
                }
            }
        }
        VSpace(16.dp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ex.shuffled.forEachIndexed { idx, w ->
                if (idx !in chosen) WordChip(w, filled = false) { if (!answered) chosen.add(idx) }
            }
        }
        VSpace(20.dp)
        if (!answered) Button(
            onClick = { onAnswer(chosen.map { ex.shuffled[it] }) },
            enabled = chosen.size == ex.shuffled.size, modifier = Modifier.fillMaxWidth(),
        ) { Text("Prüfen") }
    }
}

@Composable
private fun WordChip(word: String, filled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(12.dp),
        color = if (filled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
    ) { Text(word, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyLarge) }
}

/** Mikrofon-Bedienelement inkl. Berechtigungsabfrage. Liefert die erkannten Alternativen. */
@Composable
fun MicControl(
    target: String,
    enabled: Boolean,
    onResult: (List<String>) -> Unit,
    unavailableText: String = "bewerte dich selbst",
    onUnavailable: @Composable () -> Unit,
) {
    val c = LocalAppContainer.current
    val context = LocalContext.current
    val state by c.speech.state.collectAsStateWithLifecycle()
    var permissionDenied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) c.speech.start() else permissionDenied = true
    }
    LaunchedEffect(target) { c.speech.reset() }
    LaunchedEffect(state) {
        (state as? SpeechInput.State.Result)?.let { onResult(it.alternatives); c.speech.reset() }
    }
    val available = c.speech.isAvailable || c.speech.isOnDevice
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (!available || permissionDenied) {
            Text(
                if (permissionDenied) "Keine Mikrofon-Berechtigung – $unavailableText."
                else "Keine Spracherkennung auf diesem Gerät – $unavailableText.",
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            )
            VSpace(8.dp)
            onUnavailable()
            return@Column
        }
        val listening = state is SpeechInput.State.Listening || state is SpeechInput.State.Partial
        FilledIconButton(
            enabled = enabled,
            onClick = {
                if (listening) c.speech.stop().also { c.speech.reset() }
                else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    c.tts.stop(); c.speech.start()
                } else launcher.launch(Manifest.permission.RECORD_AUDIO)
            },
            modifier = Modifier.size(88.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (listening) Brand.Error else MaterialTheme.colorScheme.secondary),
        ) { Icon(if (listening) Icons.Filled.Stop else Icons.Filled.Mic, "Sprechen", Modifier.size(44.dp)) }
        VSpace(8.dp)
        Text(
            when (val s = state) {
                SpeechInput.State.Listening -> "Ich höre zu …"
                is SpeechInput.State.Partial -> "„${s.text}“"
                is SpeechInput.State.Error -> s.message
                else -> if (c.speech.isOnDevice) "Tippe und sprich (offline)" else "Tippe und sprich"
            },
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = if (state is SpeechInput.State.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!c.speech.isOnDevice) Text(
            "Je nach Gerät benötigt die Spracherkennung Internet.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
fun SpeakView(ex: Exercise.Speak, answered: Boolean, vm: SessionViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Instruction("Aussprache: Sprich den Satz laut nach\nHör dir den Satz an, tippe dann auf das Mikrofon und sprich ihn deutlich auf Englisch.")
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(ex.text, style = MaterialTheme.typography.headlineSmall)
                    Text(ex.translation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            VSpace(8.dp)
            SpeakerButtons(ex.text)
        }
        VSpace(24.dp)
        if (!answered) {
            MicControl(ex.text, enabled = true, onResult = { alts ->
                val best = alts.maxBy { AnswerChecker.speechSimilarity(it, ex.text) }
                vm.answerSpeech(ex, AnswerChecker.speechSimilarity(best, ex.text), best, ex.text)
            }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.answerSelf(ex, false, ex.text) }) { Text("War schwierig") }
                    Button(onClick = { vm.answerSelf(ex, true, ex.text) }) { Text("Gut gesprochen") }
                }
            }
            VSpace(12.dp)
            TextButton(onClick = { vm.continueUnscored() }) { Text("Kann gerade nicht sprechen – überspringen") }
        }
    }
}

@Composable
fun DialogueView(ex: Exercise.DialoguePractice, answered: Boolean, vm: SessionViewModel) {
    val c = LocalAppContainer.current
    val lines = ex.dialogue.lines
    var shown by remember(ex) { mutableIntStateOf(0) }
    var reveal by remember(ex) { mutableStateOf(false) }
    val scores = remember(ex) { mutableStateListOf<Double>() }
    var selfRated by remember(ex) { mutableStateOf(false) }
    val current = lines.getOrNull(shown)
    val userTurn = current?.speaker == ex.role

    LaunchedEffect(shown) {
        if (current != null && !userTurn) c.tts.speak(current.en)
    }

    Column {
        Text("Rollenspiel", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        Text(ex.dialogue.title, style = MaterialTheme.typography.headlineSmall)
        Text(ex.dialogue.setting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Du sprichst die Rolle „${ex.role}“.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        VSpace(12.dp)
        lines.take(shown).forEach { l -> Bubble(l.speaker, l.en, l.de, mine = l.speaker == ex.role) }

        if (current != null) {
            if (!userTurn) {
                Bubble(current.speaker, current.en, current.de, mine = false)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpeakerButtons(current.en)
                    Button(onClick = { shown++; reveal = false }) { Text("Weiter") }
                }
            } else {
                SectionCard(container = MaterialTheme.colorScheme.secondaryContainer) {
                    Text("Deine Antwort – sag auf Englisch:", style = MaterialTheme.typography.labelLarge)
                    Text(current.de, style = MaterialTheme.typography.titleMedium)
                    VSpace(6.dp)
                    if (reveal) {
                        Text(current.en, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic)
                        SpeakerButtons(current.en, showSlow = false)
                    } else AssistChip(onClick = { reveal = true }, label = { Text("Englischen Text zeigen") }, leadingIcon = { Icon(Icons.Filled.TouchApp, null) })
                }
                VSpace(12.dp)
                MicControl(current.en, enabled = true, onResult = { alts ->
                    scores += alts.maxOf { AnswerChecker.speechSimilarity(it, current.en) }
                    shown++; reveal = false
                }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { selfRated = true; shown++; reveal = false }) { Text("Gesagt – weiter") }
                    }
                }
                TextButton(onClick = { shown++; reveal = false }) { Text("Diese Zeile überspringen") }
            }
        } else if (!answered) {
            VSpace(16.dp)
            Text("Gespräch beendet!", style = MaterialTheme.typography.titleLarge)
            VSpace(8.dp)
            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                when {
                    scores.isNotEmpty() -> vm.answerSpeech(ex, scores.average(), "${scores.size} Zeilen gesprochen", "Dialog")
                    selfRated -> vm.answerSelf(ex, true, "Dialog")
                    else -> vm.continueUnscored()
                }
            }) { Text("Auswerten") }
        }
    }
}

@Composable
private fun Bubble(speaker: String, en: String, de: String, mine: Boolean) {
    val c = LocalAppContainer.current
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 320.dp).clickable { c.tts.speak(en) },
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(speaker, style = MaterialTheme.typography.labelMedium, color = if (mine) MaterialTheme.colorScheme.primary else Color.Unspecified)
                Text(en, style = MaterialTheme.typography.bodyLarge)
                Text(de, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Freies Sprechen: Frage anhoeren, frei auf Englisch antworten, Bewertung durch Finn (KI) oder App. */
@Composable
fun FreeSpeakView(ex: Exercise.FreeSpeak, answered: Boolean, evaluating: Boolean, vm: SessionViewModel) {
    val c = LocalAppContainer.current
    val aiReady by c.ai.config.collectAsStateWithLifecycle()
    var transcript by rememberSaveable(ex.key) { mutableStateOf("") }
    var typing by remember(ex) { mutableStateOf(false) }
    var showSample by remember(ex) { mutableStateOf(false) }
    LaunchedEffect(ex) { c.tts.speak(ex.prompt.question) }
    Column {
        Instruction(
            "Frei sprechen: Antworte in eigenen Worten\n" +
                "Hör dir die Frage an, tippe auf das Mikrofon und antworte auf Englisch in ganzen Sätzen. " +
                if (aiReady.ready) "Finn bewertet deine Antwort und zeigt dir Verbesserungen." else "Die App prüft deine Antwort und zeigt eine Musterantwort."
        )
        SectionCard {
            Text(ex.prompt.task, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            VSpace(6.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ex.prompt.question, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                SpeakerButtons(ex.prompt.question, showSlow = false)
            }
        }
        VSpace(16.dp)
        if (!answered) {
            if (transcript.isNotBlank() && !typing) {
                SectionCard(container = MaterialTheme.colorScheme.primaryContainer) {
                    Text("Das hat die App verstanden:", style = MaterialTheme.typography.labelLarge)
                    Text("„$transcript“", style = MaterialTheme.typography.bodyLarge)
                }
                VSpace(12.dp)
            }
            if (typing) {
                OutlinedTextField(
                    transcript, { transcript = it }, modifier = Modifier.fillMaxWidth(), minLines = 2,
                    label = { Text("Deine Antwort auf Englisch") },
                )
                VSpace(8.dp)
            } else {
                val speechOk = c.speech.isAvailable || c.speech.isOnDevice
                MicControl(
                    ex.key, enabled = !evaluating, onResult = { alts -> transcript = alts.first() },
                    unavailableText = "tippe deine Antwort stattdessen ein",
                ) {
                    TextButton(onClick = { typing = true }) { Text("Antwort eintippen") }
                }
                if (speechOk) TextButton(onClick = { typing = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(if (transcript.isBlank()) "Lieber tippen" else "Erkannten Text korrigieren")
                }
            }
            if (evaluating) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.CircularProgressIndicator(Modifier.size(22.dp))
                    Text(if (aiReady.ready) "  Finn hört sich deine Antwort an …" else "  Wird geprüft …")
                }
            } else {
                Button(
                    onClick = { vm.evaluateFreeSpeech(ex, transcript) }, enabled = transcript.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (aiReady.ready) "Von Finn bewerten lassen" else "Antwort prüfen") }
            }
            VSpace(8.dp)
            if (showSample) Text("Beispiel: ${ex.prompt.sample}", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
            else TextButton(onClick = { showSample = true }) { Text("Beispielantwort zeigen") }
            TextButton(onClick = { vm.continueUnscored() }) { Text("Kann gerade nicht sprechen – überspringen") }
        }
    }
}
