package de.englischlernen.app.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.englischlernen.app.BuildConfig
import de.englischlernen.app.data.prefs.Accent
import de.englischlernen.app.data.prefs.Settings
import de.englischlernen.app.data.prefs.ThemeMode
import de.englischlernen.app.ui.components.FundbruederLogo
import de.englischlernen.app.ui.components.LocalAppContainer
import de.englischlernen.app.ui.components.OnlineBadge
import de.englischlernen.app.ui.components.SectionCard
import de.englischlernen.app.ui.components.VSpace
import de.englischlernen.app.work.Reminders
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(s: Settings, onBack: () -> Unit, onPlacement: () -> Unit) {
    val c = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember(s.userName) { mutableStateOf(s.userName) }
    var rate by remember(s.speechRate) { mutableStateOf(s.speechRate) }
    var confirmReset by remember { mutableStateOf(false) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scope.launch {
            c.settings.setReminders(granted, s.reminderHour)
            if (granted) Reminders.schedule(context, s.reminderHour) else toast("Ohne Berechtigung keine Erinnerungen.")
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { scope.launch { runCatching { c.backup.export(it) }.onSuccess { toast("Sicherung gespeichert.") }.onFailure { e -> toast("Fehler: ${e.message}") } } }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { scope.launch { runCatching { c.backup.import(it) }.onSuccess { toast("Fortschritt wiederhergestellt.") }.onFailure { e -> toast("Ungültige Datei: ${e.message}") } } }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.IconButton(onClick = onBack) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
            }
            Text("Einstellungen", style = MaterialTheme.typography.headlineMedium)
        }

        SectionCard {
            Text("Profil", style = MaterialTheme.typography.titleMedium)
            VSpace(8.dp)
            OutlinedTextField(name, { name = it }, label = { Text("Dein Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            TextButton(onClick = { scope.launch { c.settings.setName(name) } }, enabled = name != s.userName) { Text("Speichern") }
            Text("Tagesziel", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(20 to "Locker", 50 to "Normal", 100 to "Intensiv", 200 to "Profi").forEach { (xp, label) ->
                    FilterChip(selected = s.dailyGoalXp == xp, onClick = { scope.launch { c.settings.setDailyGoal(xp) } }, label = { Text("$label\n$xp") })
                }
            }
        }

        de.englischlernen.app.ui.ai.AiSetupCard()

        SectionCard {
            Text("Aussprache & Stimme", style = MaterialTheme.typography.titleMedium)
            Text("Sprechtempo: ${"%.1f".format(rate)}x", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = rate, onValueChange = { rate = it }, valueRange = 0.5f..1.3f, steps = 7,
                onValueChangeFinished = { scope.launch { c.settings.setSpeechRate(rate); c.tts.configure(rate, s.accent); c.tts.speak("This is how I sound.") } },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Accent.entries.forEach { a ->
                    FilterChip(selected = s.accent == a, onClick = { scope.launch { c.settings.setAccent(a) } }, label = { Text(a.label) })
                }
            }
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { toast("Bitte in den Android-Einstellungen unter „Sprachausgabe“ installieren.") }
            }) { Text("Englische Stimme installieren / verwalten") }
        }

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Tägliche Erinnerung", style = MaterialTheme.typography.titleMedium)
                    Text("Um ${s.reminderHour}:00 Uhr, falls das Tagesziel noch offen ist", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = s.remindersEnabled, onCheckedChange = { on ->
                    if (on && Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else scope.launch {
                        c.settings.setReminders(on, s.reminderHour)
                        if (on) Reminders.schedule(context, s.reminderHour) else Reminders.cancel(context)
                    }
                })
            }
            if (s.remindersEnabled) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(8, 12, 18, 20).forEach { h ->
                    FilterChip(selected = s.reminderHour == h, onClick = {
                        scope.launch { c.settings.setReminders(true, h); Reminders.schedule(context, h) }
                    }, label = { Text("$h Uhr") })
                }
            }
        }

        SectionCard {
            Text("Darstellung", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { m ->
                    FilterChip(selected = s.themeMode == m, onClick = { scope.launch { c.settings.setThemeMode(m) } }, label = { Text(m.label) })
                }
            }
        }

        SectionCard {
            Text("Lernweg", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Freier Modus", style = MaterialTheme.typography.bodyLarge)
                    Text("Alle Stufen und Lektionen ohne Prüfung öffnen", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = s.freeMode, onCheckedChange = { scope.launch { c.settings.setFreeMode(it) } })
            }
            OutlinedButton(onClick = onPlacement, modifier = Modifier.fillMaxWidth()) { Text("Einstufungstest wiederholen") }
        }

        SectionCard {
            Text("Sicherung", style = MaterialTheme.typography.titleMedium)
            Text("Speichere deinen Fortschritt als Datei, z. B. vor einer Neuinstallation.", style = MaterialTheme.typography.bodySmall)
            VSpace(8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exportLauncher.launch("fundbrueder-english-sicherung.json") }) { Text("Exportieren") }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Importieren") }
            }
        }

        de.englischlernen.app.ui.components.UpdateSection()

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FundbruederLogo(48.dp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Fundbrüder English", style = MaterialTheme.typography.titleMedium)
                    Text("Version ${BuildConfig.VERSION_NAME} · kostenlos & werbefrei", style = MaterialTheme.typography.bodySmall)
                }
            }
            VSpace(8.dp)
            Text(
                "Datenschutz: Alle Lerndaten bleiben auf deinem Gerät. Es gibt kein Benutzerkonto und kein Tracking. " +
                    "Internet wird nur für die Update-Prüfung, die optionalen KI-Funktionen und – je nach Gerät – für die Spracherkennung genutzt.",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${BuildConfig.GITHUB_REPO}"))) }) {
                Text("Projekt auf GitHub")
            }
        }

        OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Gesamten Fortschritt zurücksetzen", color = MaterialTheme.colorScheme.error)
        }
        VSpace(24.dp)
    }

    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Alles zurücksetzen?") },
        text = { Text("Lektionen, Fundbuch, Prüfungen und Statistik werden gelöscht. Das kann nicht rückgängig gemacht werden.") },
        confirmButton = {
            TextButton(onClick = {
                confirmReset = false
                scope.launch { c.progress.clearAll(); Reminders.cancel(context); c.settings.reset() }
            }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Abbrechen") } },
    )
}
