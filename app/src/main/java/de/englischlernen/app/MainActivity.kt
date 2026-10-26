package de.englischlernen.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.englischlernen.app.data.prefs.Settings
import de.englischlernen.app.data.prefs.ThemeMode
import de.englischlernen.app.ui.AppRoot
import de.englischlernen.app.ui.theme.FundbruederTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as EnglishApp).container
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null as Settings?)
            val dark = when (settings?.themeMode ?: ThemeMode.SYSTEM) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            FundbruederTheme(darkTheme = dark) {
                settings?.let { AppRoot(container, it) }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        (application as EnglishApp).container.tts.stop()
    }
}
