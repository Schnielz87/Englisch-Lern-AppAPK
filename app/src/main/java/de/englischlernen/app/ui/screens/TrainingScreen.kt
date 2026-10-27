package de.englischlernen.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import de.englischlernen.app.ui.components.HSpace
import de.englischlernen.app.ui.components.VSpace
import de.englischlernen.app.ui.session.SessionMode

@Composable
fun TrainingScreen(d: Dashboard, onStart: (SessionMode, String) -> Unit) {
    val dialogues = d.levels.withIndex()
        .filter { d.levelUnlocked(it.index) }
        .flatMap { (li, lv) -> lv.lessons.withIndex().filter { (i, l) -> l.dialogue != null && d.lessonUnlocked(li, i) }.map { lv.code to it.value } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Trainingslager", style = MaterialTheme.typography.headlineMedium)
            Text("Gezielt üben – alles offline verfügbar.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            TrainingCard(Icons.AutoMirrored.Filled.MenuBook, "Fundbuch wiederholen",
                if (d.due > 0) "${d.due} Vokabeln fällig · ${d.vocabCount} gesamt" else "Gerade nichts fällig · ${d.vocabCount} Vokabeln gesammelt",
                MaterialTheme.colorScheme.secondaryContainer) { onStart(SessionMode.REVIEW, "") }
        }
        item {
            TrainingCard(Icons.Filled.Psychology, "Schwachstellen-Training",
                if (d.weak > 0) "${d.weak} Aufgaben, die dir schwerfallen" else "Noch keine Schwachstellen erkannt",
                MaterialTheme.colorScheme.tertiaryContainer) { onStart(SessionMode.WEAK, "") }
        }
        item {
            TrainingCard(Icons.Filled.Headphones, "Hörtraining", "Sätze hören, verstehen und mitschreiben",
                MaterialTheme.colorScheme.primaryContainer) { onStart(SessionMode.LISTENING, "") }
        }
        item {
            TrainingCard(Icons.Filled.RecordVoiceOver, "Frei sprechen mit Feedback", "Fragen in eigenen Worten beantworten – Finn bewertet und verbessert",
                MaterialTheme.colorScheme.secondaryContainer) { onStart(SessionMode.FREE_SPEAKING, "") }
        }
        item {
            TrainingCard(Icons.Filled.RecordVoiceOver, "Aussprache-Training", "Sätze nachsprechen und Feedback bekommen",
                MaterialTheme.colorScheme.primaryContainer) { onStart(SessionMode.SPEAKING, "") }
        }
        item {
            VSpace(8.dp)
            Text("Gesprächstrainer", style = MaterialTheme.typography.titleLarge)
            Text("Rollenspiele aus deinen freigeschalteten Lektionen", style = MaterialTheme.typography.bodySmall)
        }
        items(dialogues) { (code, lesson) ->
            TrainingCard(Icons.Filled.Forum, "$code · ${lesson.dialogue!!.title}", lesson.dialogue.setting,
                MaterialTheme.colorScheme.surfaceContainerHigh) { onStart(SessionMode.DIALOGUE, lesson.id) }
        }
    }
}

@Composable
private fun TrainingCard(icon: ImageVector, title: String, subtitle: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = color)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(32.dp))
            HSpace(14.dp)
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
