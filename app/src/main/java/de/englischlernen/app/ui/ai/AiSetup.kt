package de.englischlernen.app.ui.ai

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.englischlernen.app.ai.AiModel
import de.englischlernen.app.ai.AiProvider
import de.englischlernen.app.ai.ChatGptAuth
import de.englischlernen.app.ui.components.LocalAppContainer
import de.englischlernen.app.ui.components.OnlineBadge
import de.englischlernen.app.ui.components.SectionCard
import de.englischlernen.app.ui.components.VSpace
import kotlinx.coroutines.launch

/** Einrichtung des KI-Anbieters: ChatGPT-Anmeldung oder eigener API-Schluessel, dann Modellwahl. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSetupCard() {
    val c = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config by c.ai.config.collectAsStateWithLifecycle()
    val auth by c.chatGptAuth.state.collectAsStateWithLifecycle()
    var keyInput by remember(config.provider) { mutableStateOf("") }
    var models by remember(config.provider) { mutableStateOf<List<AiModel>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }

    fun loadModels() {
        loading = true; message = null
        scope.launch {
            runCatching { c.ai.listModels(config.provider) }
                .onSuccess { list ->
                    models = list
                    if (list.isEmpty()) message = "Keine passenden Modelle gefunden."
                    else if (config.model == null || list.none { it.id == config.model }) c.ai.setModel(config.provider, list.first().id)
                }
                .onFailure { message = it.message }
            loading = false
        }
    }

    val connected = when (config.provider) {
        AiProvider.CHATGPT -> auth is ChatGptAuth.State.SignedIn
        AiProvider.NONE -> false
        else -> c.ai.hasKey(config.provider)
    }
    LaunchedEffect(config.provider, connected) { if (connected && models.isEmpty()) loadModels() }

    SectionCard {
        Text("KI-Anbieter", style = MaterialTheme.typography.titleMedium)
        OnlineBadge("KI-Funktionen benötigen Internet")
        Text(
            "Optional: Die KI macht Finn zum Gesprächspartner, korrigiert Texte und erklärt Fehler. Deine Eingaben werden dafür an den gewählten Anbieter gesendet.",
            style = MaterialTheme.typography.bodySmall,
        )
        VSpace(8.dp)
        AiProvider.entries.forEach { p ->
            Row(
                Modifier.fillMaxWidth().selectable(selected = config.provider == p, onClick = { c.ai.setProvider(p) }).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = config.provider == p, onClick = { c.ai.setProvider(p) })
                Column {
                    Text(p.label, style = MaterialTheme.typography.bodyLarge)
                    Text(p.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        when (config.provider) {
            AiProvider.NONE -> {}
            AiProvider.CHATGPT -> {
                VSpace(8.dp)
                when (val a = auth) {
                    is ChatGptAuth.State.SignedIn -> {
                        Text("Angemeldet als ${a.email}", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { scope.launch { c.chatGptAuth.signOut() } }) { Text("Abmelden") }
                            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/settings/usage"))) }) {
                                Text("Nutzung in ChatGPT")
                            }
                        }
                    }
                    ChatGptAuth.State.InProgress -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.padding(end = 12.dp))
                        Column {
                            Text("Anmeldung im Browser läuft …", style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { c.chatGptAuth.cancel() }) { Text("Abbrechen") }
                        }
                    }
                    else -> {
                        if (a is ChatGptAuth.State.Error) Text(a.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        Button(
                            onClick = { c.chatGptAuth.signIn() }, modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface),
                        ) { Text("Continue with ChatGPT") }
                        Text(
                            "Erfordert ChatGPT Plus oder Pro. Die Nutzung zählt zu deinem Abo-Kontingent; Limits kannst du in den ChatGPT-Einstellungen festlegen.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            else -> {
                VSpace(8.dp)
                if (c.ai.hasKey(config.provider)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("✔ Schlüssel gespeichert (verschlüsselt)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { c.ai.setKey(config.provider, null); models = emptyList() }) { Text("Entfernen") }
                    }
                } else {
                    OutlinedTextField(
                        keyInput, { keyInput = it }, label = { Text(config.provider.keyHint ?: "API-Schlüssel") },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { c.ai.setKey(config.provider, keyInput); keyInput = "" }, enabled = keyInput.length > 10) { Text("Speichern") }
                        TextButton(onClick = {
                            val url = when (config.provider) {
                                AiProvider.GEMINI -> "https://aistudio.google.com/apikey"
                                AiProvider.OPENAI -> "https://platform.openai.com/api-keys"
                                else -> "https://console.anthropic.com/settings/keys"
                            }
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }) { Text("Schlüssel besorgen") }
                    }
                }
            }
        }

        if (connected) {
            VSpace(8.dp)
            Text("Modell", style = MaterialTheme.typography.labelLarge)
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = models.firstOrNull { it.id == config.model }?.name ?: config.model ?: "–",
                    onValueChange = {}, readOnly = true, singleLine = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    models.forEach { m ->
                        DropdownMenuItem(text = { Text(m.name) }, onClick = { c.ai.setModel(config.provider, m.id); expanded = false })
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = ::loadModels, enabled = !loading) { Text(if (loading) "Lade …" else "Modelle neu laden") }
                if (config.ready) Text("✔ Bereit", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}
