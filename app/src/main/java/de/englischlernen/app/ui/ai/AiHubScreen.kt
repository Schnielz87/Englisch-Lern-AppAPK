package de.englischlernen.app.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.englischlernen.app.ai.Prompts
import de.englischlernen.app.ui.components.FundbruederLogo
import de.englischlernen.app.ui.components.HSpace
import de.englischlernen.app.ui.components.LocalAppContainer
import de.englischlernen.app.ui.components.OnlineBadge
import de.englischlernen.app.ui.components.VSpace

@Composable
fun AiHubScreen(onOpen: (AiMode, Int) -> Unit) {
    val c = LocalAppContainer.current
    val config by c.ai.config.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FundbruederLogo(48.dp)
            HSpace(12.dp)
            Column {
                Text("Finn – dein KI-Tutor", style = MaterialTheme.typography.headlineSmall)
                OnlineBadge()
            }
        }
        if (!config.ready) {
            Text(
                "Richte einmalig einen KI-Anbieter ein. Am einfachsten: „Mit ChatGPT anmelden“ (mit ChatGPT Plus/Pro) – " +
                    "oder kostenlos mit einem Google-Gemini-Schlüssel.",
                style = MaterialTheme.typography.bodyMedium,
            )
            AiSetupCard()
            return@Column
        }
        HubCard(Icons.Filled.QuestionAnswer, "Frag Finn", "Fragen zu Grammatik, Wörtern, Aussprache und zum Lernen – Antworten auf Deutsch.") { onOpen(AiMode.TUTOR, 0) }
        HubCard(Icons.Filled.EditNote, "Schreibtrainer", "Text oder E-Mail eingeben – Finn korrigiert und erklärt jeden Fehler.") { onOpen(AiMode.WRITING, 0) }
        HubCard(Icons.Filled.Insights, "Lernberater", "Finn analysiert deinen Fortschritt und erstellt deinen 7-Tage-Plan.") { onOpen(AiMode.COACH, 0) }
        VSpace(4.dp)
        Text("Gesprächspartner", style = MaterialTheme.typography.titleLarge)
        Text("Freie Rollenspiele auf deinem Niveau – sprechen oder tippen, mit Korrektur nach jeder Antwort.", style = MaterialTheme.typography.bodySmall)
        Prompts.scenarios.forEachIndexed { i, s ->
            HubCard(Icons.Filled.AutoAwesome, "${s.emoji} ${s.title}", null) { onOpen(AiMode.ROLEPLAY, i) }
        }
        VSpace(8.dp)
        AiSetupCard()
    }
}

@Composable
private fun HubCard(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.secondary)
            HSpace(14.dp)
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
