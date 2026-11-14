package com.mydocviewer.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "viewer_preferences")

enum class AppThemeMode { SYSTEM, LIGHT, DARK }

data class UserPreferences(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val languageCode: String = "en",
    val lastFolderTreeUri: String? = null
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language_code")
        val LAST_FOLDER_URI = stringPreferencesKey("last_folder_uri")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            themeMode = runCatching {
                AppThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: AppThemeMode.SYSTEM.name)
            }.getOrDefault(AppThemeMode.SYSTEM),
            languageCode = prefs[Keys.LANGUAGE] ?: "en",
            lastFolderTreeUri = prefs[Keys.LAST_FOLDER_URI]
        )
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setLanguageCode(code: String) {
        context.dataStore.edit { it[Keys.LANGUAGE] = code }
    }

    suspend fun setLastFolderTreeUri(uriString: String?) {
        context.dataStore.edit {
            if (uriString == null) it.remove(Keys.LAST_FOLDER_URI)
            else it[Keys.LAST_FOLDER_URI] = uriString
        }
    }
}
