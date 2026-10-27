package de.englischlernen.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.englischlernen.app.ui.components.FundbruederLogo
import de.englischlernen.app.ui.components.HSpace
import de.englischlernen.app.ui.components.LabeledProgress
import de.englischlernen.app.ui.components.SectionCard
import de.englischlernen.app.ui.components.StatTile
import de.englischlernen.app.ui.components.VSpace
import de.englischlernen.app.ui.session.SessionMode
import java.time.LocalTime

@Composable
fun HomeScreen(d: Dashboard, onStart: (SessionMode, String) -> Unit, onOpenCourse: () -> Unit, onSettings: () -> Unit, onHelp: () -> Unit) {
    val hour = LocalTime.now().hour
    val greeting = when (hour) { in 5..10 -> "Guten Morgen"; in 11..17 -> "Hallo"; else -> "Guten Abend" }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FundbruederLogo(52.dp)
            }
            HSpace(12.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    if (d.settings.userName.isBlank()) "$greeting!" else "$greeting, ${d.settings.userName}!",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(d.rank.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            }
            androidx.compose.material3.IconButton(onClick = onHelp) { Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.HelpOutline, "Hilfe") }
            androidx.compose.material3.IconButton(onClick = onSettings) { Icon(androidx.compose.material.icons.Icons.Filled.Settings, "Einstellungen") }
        }
        VSpace(4.dp)
        de.englischlernen.app.ui.components.DedicationNote()
        VSpace(16.dp)
        de.englischlernen.app.ui.components.UpdateBanner()
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(Icons.Filled.LocalFireDepartment, "${d.streak}", "Tage Serie", MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
            StatTile(Icons.Filled.Diamond, "${d.totalXp}", "Fundpunkte", MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
            StatTile(Icons.AutoMirrored.Filled.MenuBook, "${d.vocabCount}", "Im Fundbuch", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        }
        VSpace(16.dp)
        SectionCard {
            LabeledProgress(
                "Tagesziel", d.todayXp.toFloat() / d.settings.dailyGoalXp,
                "${d.todayXp} / ${d.settings.dailyGoalXp} Punkte",
            )
            if (d.todayXp >= d.settings.dailyGoalXp) {
                VSpace(6.dp)
                Text("🎉 Tagesziel erreicht – stark!", style = MaterialTheme.typography.bodyMedium)
            }
        }
        VSpace(16.dp)
        d.recommendation?.let { r ->
            SectionCard(container = MaterialTheme.colorScheme.primaryContainer) {
                Text("Dein nächster Fund", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(r.title, style = MaterialTheme.typography.headlineSmall)
                Text(r.subtitle, style = MaterialTheme.typography.bodyMedium)
                VSpace(12.dp)
                Button(onClick = { onStart(r.mode, r.arg) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.PlayArrow, null); HSpace(6.dp); Text("Los geht's")
                }
            }
        } ?: CircularProgressIndicator()
        VSpace(16.dp)
        if (d.due > 0) {
            SectionCard(container = MaterialTheme.colorScheme.secondaryContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Fundbuch", style = MaterialTheme.typography.titleMedium)
                        Text("${d.due} Vokabeln warten auf Wiederholung", style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(
                        onClick = { onStart(SessionMode.REVIEW, "") },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary),
                    ) { Text("Wiederholen") }
                }
            }
            VSpace(16.dp)
        }
        val lv = d.levels.getOrNull(d.currentLevelIndex)
        if (lv != null) SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Explore, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                HSpace(10.dp)
                Column(Modifier.weight(1f)) {
                    Text("Expedition ${lv.code}", style = MaterialTheme.typography.titleMedium)
                    Text(lv.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            VSpace(10.dp)
            val passed = d.passedCount(lv)
            LabeledProgress("Lektionen", passed.toFloat() / lv.lessons.size, "$passed / ${lv.lessons.size}")
            VSpace(10.dp)
            Button(onClick = onOpenCourse, modifier = Modifier.fillMaxWidth()) { Text("Zur Expeditionskarte") }
        }
    }
}
