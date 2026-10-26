package de.englischlernen.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.englischlernen.app.ui.components.FundbruederLogo
import de.englischlernen.app.ui.components.LocalAppContainer
import de.englischlernen.app.ui.components.SectionCard
import de.englischlernen.app.ui.components.VSpace
import kotlinx.coroutines.launch

/** Erster Start: Begruessung, Name, Tagesziel und Einstieg (bei Null starten oder Einstufungstest). */
@Composable
fun OnboardingScreen(onDone: (startPlacement: Boolean) -> Unit) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var goal by rememberSaveable { mutableIntStateOf(50) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        FundbruederLogo(120.dp)
        VSpace(6.dp)
        de.englischlernen.app.ui.components.DedicationNote()
        VSpace(12.dp)
        when (step) {
            0 -> {
                Text("Willkommen bei\nFundbrüder English", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                VSpace(12.dp)
                Text(
                    "Dein persönlicher Englischlehrer für unterwegs. Entdecke die Sprache Schritt für Schritt – " +
                        "von den ersten Wörtern (A1) bis zum nahezu muttersprachlichen Niveau (C2).",
                    textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge,
                )
                VSpace(16.dp)
                SectionCard {
                    listOf(
                        "🎧 Hören, 🗣️ Sprechen, 📖 Lesen und ✍️ Schreiben",
                        "🧭 Lernweg passt sich deinem Können an",
                        "🏅 Prüfungen und Zertifikate für jede Stufe",
                        "📴 Funktioniert offline – ohne Konto, ohne Abo",
                    ).forEach { Text(it, Modifier.padding(vertical = 3.dp)) }
                }
                VSpace(24.dp)
                Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) { Text("Expedition beginnen") }
            }
            1 -> {
                Text("Wie dürfen wir dich nennen?", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                VSpace(16.dp)
                OutlinedTextField(name, { name = it }, label = { Text("Vorname (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                VSpace(20.dp)
                Text("Wie viel möchtest du täglich lernen?", style = MaterialTheme.typography.titleMedium)
                VSpace(8.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(20 to "5 Min", 50 to "10 Min", 100 to "20 Min").forEach { (xp, label) ->
                        FilterChip(selected = goal == xp, onClick = { goal = xp }, label = { Text(label) })
                    }
                }
                VSpace(24.dp)
                Button(onClick = { step = 2 }, modifier = Modifier.fillMaxWidth()) { Text("Weiter") }
            }
            else -> {
                Text("Wo startest du?", style = MaterialTheme.typography.headlineSmall)
                VSpace(16.dp)
                SectionCard {
                    Text("Ich fange bei null an", style = MaterialTheme.typography.titleMedium)
                    Text("Starte bei A1 mit Alphabet, Begrüßungen und den ersten Sätzen.", style = MaterialTheme.typography.bodyMedium)
                    VSpace(8.dp)
                    Button(onClick = {
                        scope.launch { c.settings.setDailyGoal(goal); c.settings.completeOnboarding(name, 0); onDone(false) }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Bei A1 starten") }
                }
                VSpace(12.dp)
                SectionCard {
                    Text("Ich kann schon etwas Englisch", style = MaterialTheme.typography.titleMedium)
                    Text("Der kurze Einstufungstest (ca. 5 Minuten) findet dein passendes Niveau.", style = MaterialTheme.typography.bodyMedium)
                    VSpace(8.dp)
                    OutlinedButton(onClick = {
                        scope.launch { c.settings.setDailyGoal(goal); c.settings.completeOnboarding(name, 0); onDone(true) }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Einstufungstest starten") }
                }
            }
        }
    }
}
