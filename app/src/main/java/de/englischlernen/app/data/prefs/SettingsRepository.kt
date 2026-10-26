package de.englischlernen.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class Accent(val label: String) { US("Amerikanisch"), GB("Britisch") }
enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Hell"), DARK("Dunkel") }

data class Settings(
    val onboardingDone: Boolean = false,
    val userName: String = "",
    /** Hoechstes freigeschaltetes Niveau (Index 0 = A1 ... 5 = C2). */
    val unlockedLevel: Int = 0,
    val dailyGoalXp: Int = 50,
    val speechRate: Float = 0.9f,
    val accent: Accent = Accent.US,
    val remindersEnabled: Boolean = false,
    val reminderHour: Int = 18,
    val freeMode: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val onboarding = booleanPreferencesKey("onboarding_done")
        val name = stringPreferencesKey("user_name")
        val unlocked = intPreferencesKey("unlocked_level")
        val goal = intPreferencesKey("daily_goal")
        val rate = floatPreferencesKey("speech_rate")
        val accent = stringPreferencesKey("accent")
        val reminders = booleanPreferencesKey("reminders")
        val reminderHour = intPreferencesKey("reminder_hour")
        val freeMode = booleanPreferencesKey("free_mode")
        val theme = stringPreferencesKey("theme")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            onboardingDone = p[Keys.onboarding] ?: false,
            userName = p[Keys.name] ?: "",
            unlockedLevel = p[Keys.unlocked] ?: 0,
            dailyGoalXp = p[Keys.goal] ?: 50,
            speechRate = p[Keys.rate] ?: 0.9f,
            accent = p[Keys.accent]?.let { runCatching { Accent.valueOf(it) }.getOrNull() } ?: Accent.US,
            remindersEnabled = p[Keys.reminders] ?: false,
            reminderHour = p[Keys.reminderHour] ?: 18,
            freeMode = p[Keys.freeMode] ?: false,
            themeMode = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun completeOnboarding(name: String, level: Int) = context.dataStore.edit {
        it[Keys.name] = name.trim()
        it[Keys.unlocked] = maxOf(level, it[Keys.unlocked] ?: 0)
        it[Keys.onboarding] = true
    }

    /** Schaltet Niveau [level] frei (niedrigere bleiben frei). */
    suspend fun unlockLevel(level: Int) = context.dataStore.edit {
        it[Keys.unlocked] = maxOf(level.coerceIn(0, 5), it[Keys.unlocked] ?: 0)
    }

    suspend fun setName(v: String) = context.dataStore.edit { it[Keys.name] = v.trim() }
    suspend fun setDailyGoal(v: Int) = context.dataStore.edit { it[Keys.goal] = v }
    suspend fun setSpeechRate(v: Float) = context.dataStore.edit { it[Keys.rate] = v }
    suspend fun setAccent(v: Accent) = context.dataStore.edit { it[Keys.accent] = v.name }
    suspend fun setReminders(enabled: Boolean, hour: Int) = context.dataStore.edit {
        it[Keys.reminders] = enabled
        it[Keys.reminderHour] = hour
    }
    suspend fun setFreeMode(v: Boolean) = context.dataStore.edit { it[Keys.freeMode] = v }
    suspend fun setThemeMode(v: ThemeMode) = context.dataStore.edit { it[Keys.theme] = v.name }
    suspend fun setUnlockedExactly(level: Int) = context.dataStore.edit { it[Keys.unlocked] = level.coerceIn(0, 5) }

    suspend fun reset() = context.dataStore.edit { it.clear() }
}
