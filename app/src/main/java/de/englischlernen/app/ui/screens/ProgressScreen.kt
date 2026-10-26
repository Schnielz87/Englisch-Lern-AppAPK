package de.englischlernen.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.englischlernen.app.ui.components.HSpace
import de.englischlernen.app.ui.components.LabeledProgress
import de.englischlernen.app.ui.components.SectionCard
import de.englischlernen.app.ui.components.StatTile
import de.englischlernen.app.ui.components.VSpace
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ProgressScreen(d: Dashboard) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Fortschritt", style = MaterialTheme.typography.headlineMedium)
        Text("Rang: ${d.rank.title}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
        d.rank.nextXp?.let { next ->
            VSpace(6.dp)
            LabeledProgress("Bis zum nächsten Rang", (d.totalXp - d.rank.minXp).toFloat() / (next - d.rank.minXp), "${d.totalXp} / $next")
        }
        VSpace(16.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(Icons.Filled.LocalFireDepartment, "${d.longestStreak}", "Beste Serie", MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
            StatTile(Icons.Filled.TrackChanges, "${d.accuracy} %", "Trefferquote", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            StatTile(Icons.Filled.Diamond, "${d.mastered}", "Sicher gelernt", MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
        }
        VSpace(16.dp)
        SectionCard {
            Text("Letzte 7 Tage", style = MaterialTheme.typography.titleMedium)
            VSpace(12.dp)
            WeekChart(d)
        }
        VSpace(16.dp)
        SectionCard {
            Text("Stufen", style = MaterialTheme.typography.titleMedium)
            d.levels.forEach { lv ->
                VSpace(10.dp)
                val p = d.passedCount(lv)
                val cert = if (lv.id in d.passedExams) " 🏅" else ""
                LabeledProgress("${lv.code} – ${lv.title}$cert", p.toFloat() / lv.lessons.size, "$p / ${lv.lessons.size}")
            }
        }
        VSpace(16.dp)
        if (d.exams.isNotEmpty()) {
            SectionCard {
                Text("Prüfungen", style = MaterialTheme.typography.titleMedium)
                val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
                d.exams.take(10).forEach { e ->
                    VSpace(6.dp)
                    val date = java.time.Instant.ofEpochMilli(e.takenAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    Text("${e.levelId.uppercase()} · ${e.score} % · ${if (e.passed) "bestanden" else "nicht bestanden"} · ${date.format(fmt)}")
                }
            }
            VSpace(16.dp)
        }
        Text("Schatzkammer", style = MaterialTheme.typography.titleLarge)
        Text("${d.achievements.count { it.unlocked }} von ${d.achievements.size} Funden", style = MaterialTheme.typography.bodySmall)
        VSpace(8.dp)
        d.achievements.forEach { a ->
            SectionCard(Modifier.padding(vertical = 4.dp), container = if (a.unlocked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (a.unlocked) Icons.Filled.EmojiEvents else Icons.Filled.Lock, null, Modifier.size(32.dp),
                        tint = if (a.unlocked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                    )
                    HSpace(12.dp)
                    Column(Modifier.weight(1f)) {
                        Text(a.title, style = MaterialTheme.typography.titleMedium)
                        Text(a.description, style = MaterialTheme.typography.bodySmall)
                        if (!a.unlocked && a.progress > 0f) {
                            VSpace(4.dp)
                            LabeledProgress("", a.progress, "${(a.progress * 100).toInt()} %")
                        }
                    }
                }
            }
        }
        VSpace(24.dp)
    }
}

@Composable
private fun WeekChart(d: Dashboard) {
    val today = LocalDate.now()
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val values = days.map { day -> d.activity.firstOrNull { it.epochDay == day.toEpochDay() }?.xp ?: 0 }
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(d.settings.dailyGoalXp)
    Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        days.zip(values).forEach { (day, v) ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Text("$v", style = MaterialTheme.typography.labelSmall)
                val frac = if (max == 0) 0f else v.toFloat() / max
                Box(
                    Modifier.fillMaxWidth().fillMaxHeight(0.75f * frac + 0.02f)
                        .background(
                            if (v >= d.settings.dailyGoalXp) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(6.dp),
                        )
                )
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN),
                    style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                )
            }
        }
    }
}
