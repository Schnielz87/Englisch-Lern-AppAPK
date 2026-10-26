package de.englischlernen.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.englischlernen.app.data.content.Lesson
import de.englischlernen.app.ui.components.HSpace
import de.englischlernen.app.ui.components.LabeledProgress
import de.englischlernen.app.ui.components.LocalAppContainer
import de.englischlernen.app.ui.components.SectionCard
import de.englischlernen.app.ui.components.SpeakerButtons
import de.englischlernen.app.ui.components.VSpace
import de.englischlernen.app.ui.session.SessionMode
import de.englischlernen.app.ui.session.RichText

/** Expeditionskarte: die sechs GER-Stufen als Stationen. */
@Composable
fun CourseScreen(d: Dashboard, onOpenLevel: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Expeditionskarte", style = MaterialTheme.typography.headlineMedium)
            Text("Sechs Stationen vom ersten Wort bis zum muttersprachlichen Niveau.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        itemsIndexed(d.levels) { i, lv ->
            val unlocked = d.levelUnlocked(i)
            val passed = d.passedCount(lv)
            val certified = lv.id in d.passedExams
            Card(
                onClick = { if (unlocked) onOpenLevel(lv.id) },
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (unlocked) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape, modifier = Modifier.size(56.dp),
                        color = when {
                            certified -> MaterialTheme.colorScheme.secondary
                            unlocked -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outline
                        },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (unlocked) Text(lv.code, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                            else Icon(Icons.Filled.Lock, "gesperrt", tint = MaterialTheme.colorScheme.surface)
                        }
                    }
                    HSpace(14.dp)
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(lv.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (certified) Icon(Icons.Filled.WorkspacePremium, "Zertifikat", tint = MaterialTheme.colorScheme.secondary)
                        }
                        Text(lv.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        VSpace(8.dp)
                        if (unlocked) LabeledProgress("Lektionen", passed.toFloat() / lv.lessons.size, "$passed / ${lv.lessons.size}")
                        else Text("Bestehe die Prüfung der vorherigen Stufe oder den Einstufungstest.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelScreen(d: Dashboard, levelId: String, onBack: () -> Unit, onStart: (SessionMode, String) -> Unit, onTheory: (String) -> Unit) {
    val li = d.levels.indexOfFirst { it.id == levelId }
    val lv = d.levels.getOrNull(li) ?: return
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("${lv.code} · ${lv.title}") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
        )
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(lv.description, style = MaterialTheme.typography.bodyMedium)
                VSpace(6.dp)
            }
            itemsIndexed(lv.lessons) { i, lesson ->
                LessonRow(i, lesson, d.lessonUnlocked(li, i), d.progress[lesson.id], onStart, onTheory)
            }
            item {
                val available = d.examAvailable(li)
                val best = d.exams.filter { it.levelId == lv.id }.maxOfOrNull { it.score }
                SectionCard(container = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.WorkspacePremium, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(36.dp))
                        HSpace(10.dp)
                        Column(Modifier.weight(1f)) {
                            Text("Abschlussprüfung ${lv.code}", style = MaterialTheme.typography.titleMedium)
                            Text(
                                when {
                                    lv.id in d.passedExams -> "Bestanden ✔ – bestes Ergebnis $best %"
                                    available -> "20 Aufgaben · ab 70 % bestanden · schaltet die nächste Stufe frei"
                                    else -> "Schließe zuerst alle Lektionen ab."
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    VSpace(10.dp)
                    Button(onClick = { onStart(SessionMode.EXAM, lv.id) }, enabled = available, modifier = Modifier.fillMaxWidth()) {
                        Text(if (lv.id in d.passedExams) "Prüfung wiederholen" else "Prüfung starten")
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonRow(
    index: Int, lesson: Lesson, unlocked: Boolean,
    progress: de.englischlernen.app.data.db.LessonProgressEntity?,
    onStart: (SessionMode, String) -> Unit, onTheory: (String) -> Unit,
) {
    SectionCard(container = if (unlocked) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = if (progress?.passed == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    when {
                        progress?.passed == true -> Icon(Icons.Filled.CheckCircle, "abgeschlossen", tint = MaterialTheme.colorScheme.onPrimary)
                        !unlocked -> Icon(Icons.Filled.Lock, "gesperrt", Modifier.size(18.dp))
                        else -> Text("${index + 1}", fontWeight = FontWeight.Bold)
                    }
                }
            }
            HSpace(12.dp)
            Column(Modifier.weight(1f)) {
                Text(lesson.title, style = MaterialTheme.typography.titleMedium)
                Text(lesson.goal, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                progress?.let { Text("Bestes Ergebnis: ${it.bestScore} %", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary) }
            }
        }
        if (unlocked) {
            VSpace(8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { onStart(SessionMode.LESSON, lesson.id) }) {
                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp)); HSpace(4.dp)
                    Text(if (progress == null) "Starten" else "Wiederholen")
                }
                OutlinedButton(onClick = { onTheory(lesson.id) }) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, null, Modifier.size(18.dp)); HSpace(4.dp); Text("Nachlesen")
                }
                if (lesson.dialogue != null) IconButton(onClick = { onStart(SessionMode.DIALOGUE, lesson.id) }) {
                    Icon(Icons.Filled.Forum, "Gespräch üben")
                }
            }
        }
    }
}

/** Nachschlagen: Theorie, Vokabeln und Dialog einer Lektion. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TheoryScreen(lessonId: String, onBack: () -> Unit) {
    val c = LocalAppContainer.current
    var lesson by remember { mutableStateOf<Lesson?>(null) }
    LaunchedEffect(lessonId) { lesson = c.content.lesson(lessonId)?.second }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(lesson?.title ?: "") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
        )
    }) { pad ->
        val l = lesson ?: return@Scaffold
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(l.goal, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic)
            l.theory.forEach { t ->
                VSpace(16.dp)
                Text(t.heading, style = MaterialTheme.typography.titleLarge)
                VSpace(6.dp)
                RichText(t.text)
                t.examples.forEach { e ->
                    VSpace(6.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.en, fontWeight = FontWeight.SemiBold)
                            Text(e.de, style = MaterialTheme.typography.bodySmall)
                        }
                        SpeakerButtons(e.en, showSlow = false)
                    }
                }
            }
            VSpace(20.dp)
            Text("Vokabeln", style = MaterialTheme.typography.titleLarge)
            l.vocab.forEach { v ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(v.en, fontWeight = FontWeight.SemiBold)
                        Text(v.de, style = MaterialTheme.typography.bodySmall)
                    }
                    SpeakerButtons(v.en, showSlow = false)
                }
            }
            l.dialogue?.let { dlg ->
                VSpace(20.dp)
                Text("Dialog: ${dlg.title}", style = MaterialTheme.typography.titleLarge)
                Text(dlg.setting, style = MaterialTheme.typography.bodySmall)
                dlg.lines.forEach { line ->
                    VSpace(6.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${line.speaker}: ${line.en}", fontWeight = FontWeight.SemiBold)
                            Text(line.de, style = MaterialTheme.typography.bodySmall)
                        }
                        SpeakerButtons(line.en, showSlow = false)
                    }
                }
            }
            VSpace(32.dp)
        }
    }
}
