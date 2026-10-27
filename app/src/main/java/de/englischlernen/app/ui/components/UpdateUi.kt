package de.englischlernen.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.englischlernen.app.BuildConfig
import de.englischlernen.app.update.AppUpdater

/** Hinweis auf der Startseite, sobald eine neue Version bereitsteht. */
@Composable
fun UpdateBanner() {
    val updater = LocalAppContainer.current.updater
    val state by updater.state.collectAsStateWithLifecycle()
    val visible = state is AppUpdater.State.Available || state is AppUpdater.State.NeedsPermission ||
        state is AppUpdater.State.Downloading || state is AppUpdater.State.Installing
    if (!visible) return
    SectionCard(container = MaterialTheme.colorScheme.secondaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.SystemUpdate, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.secondary)
            Text("  Neue Version verfügbar", style = MaterialTheme.typography.titleMedium)
        }
        VSpace(6.dp)
        UpdateControls(state, updater)
    }
    VSpace(16.dp)
}

/** Bereich in den Einstellungen. */
@Composable
fun UpdateSection() {
    val updater = LocalAppContainer.current.updater
    val state by updater.state.collectAsStateWithLifecycle()
    SectionCard {
        Text("App-Aktualisierung", style = MaterialTheme.typography.titleMedium)
        OnlineBadge()
        Text("Installierte Version: ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
        VSpace(8.dp)
        when (state) {
            AppUpdater.State.Idle, is AppUpdater.State.UpToDate, is AppUpdater.State.Error -> {
                (state as? AppUpdater.State.UpToDate)?.let { Text("✔ Du hast die neueste Version.", style = MaterialTheme.typography.bodySmall) }
                (state as? AppUpdater.State.Error)?.let { Text(it.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                VSpace(6.dp)
                Button(onClick = updater::check) { Text("Nach Updates suchen") }
            }
            AppUpdater.State.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp)); Text("  Suche nach Updates …")
            }
            else -> UpdateControls(state, updater)
        }
    }
}

@Composable
private fun UpdateControls(state: AppUpdater.State, updater: AppUpdater) {
    val context = LocalContext.current
    when (val s = state) {
        is AppUpdater.State.Available -> {
            Text("Version ${s.version} ist bereit. Dein Fortschritt bleibt erhalten.", style = MaterialTheme.typography.bodyMedium)
            VSpace(8.dp)
            Button(onClick = updater::downloadAndInstall, modifier = Modifier.fillMaxWidth()) { Text("Jetzt aktualisieren") }
        }
        is AppUpdater.State.NeedsPermission -> {
            Text(
                "Einmalig nötig: Erlaube „Fundbrüder English“, Updates zu installieren. " +
                    "Tippe auf den Knopf, aktiviere den Schalter und komm zurück in die App.",
                style = MaterialTheme.typography.bodyMedium,
            )
            VSpace(8.dp)
            Button(onClick = { updater.openInstallPermissionSettings(context) }, modifier = Modifier.fillMaxWidth()) { Text("Erlaubnis erteilen") }
            OutlinedButton(onClick = updater::downloadAndInstall, modifier = Modifier.fillMaxWidth()) { Text("Erledigt – jetzt aktualisieren") }
        }
        is AppUpdater.State.Downloading -> {
            Text("Lade Version ${s.version} … ${(s.progress * 100).toInt()} %", style = MaterialTheme.typography.bodyMedium)
            VSpace(6.dp)
            LinearProgressIndicator(
                progress = { s.progress }, modifier = Modifier.fillMaxWidth().height(8.dp),
                strokeCap = StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {},
            )
        }
        AppUpdater.State.Installing -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp))
            Text("  Installation läuft – bitte im Android-Dialog „Aktualisieren“ tippen.", style = MaterialTheme.typography.bodyMedium)
        }
        else -> {}
    }
}
