package com.mydocviewer.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mydocviewer.data.preferences.AppThemeMode
import com.mydocviewer.data.preferences.UserPreferences
import com.mydocviewer.data.preferences.UserPreferencesRepository
import com.mydocviewer.data.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefsRepo: UserPreferencesRepository,
    private val docRepo: DocumentRepository
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = prefsRepo.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserPreferences()
    )

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { prefsRepo.setThemeMode(mode) }
    }

    fun setNightReadingMode(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setNightReadingMode(enabled) }
    }

    fun setLanguage(code: String) {
        viewModelScope.launch {
            prefsRepo.setLanguageCode(code)
            val locales = LocaleListCompat.forLanguageTags(code)
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    fun clearHistory(onComplete: () -> Unit) {
        viewModelScope.launch {
            docRepo.clearHistory()
            onComplete()
        }
    }
}
