package rocks.talon.marrow.phone.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "marrow_settings")

private val THEME_KEY = stringPreferencesKey("theme_mode")
private val REFRESH_KEY = intPreferencesKey("refresh_interval_seconds")

enum class ThemeMode { SYSTEM, DYNAMIC, BRAND }

data class Settings(
    val themeMode: ThemeMode = ThemeMode.DYNAMIC,
    val refreshIntervalSeconds: Int = 3,
)

class SettingsRepository private constructor(private val context: Context) {

    val settings: Flow<Settings> = context.settingsDataStore.data.map { prefs ->
        val mode = prefs[THEME_KEY]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.DYNAMIC
        val interval = (prefs[REFRESH_KEY] ?: 3).coerceIn(1, 60)
        Settings(mode, interval)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[THEME_KEY] = mode.name }
    }

    suspend fun setRefreshIntervalSeconds(seconds: Int) {
        context.settingsDataStore.edit { it[REFRESH_KEY] = seconds.coerceIn(1, 60) }
    }

    companion object {
        @Volatile private var instance: SettingsRepository? = null
        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }
}
