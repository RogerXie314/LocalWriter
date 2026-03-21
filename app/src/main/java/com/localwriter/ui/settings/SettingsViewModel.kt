package com.localwriter.ui.settings

import androidx.lifecycle.*
import com.localwriter.data.db.entity.User
import com.localwriter.data.db.entity.UserSettings
import com.localwriter.data.repository.AuthRepository
import com.localwriter.data.repository.SettingsRepository
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepo: SettingsRepository,
    private val authRepo: AuthRepository
) : ViewModel() {

    private val _settings = MutableLiveData<UserSettings?>()
    val settings: LiveData<UserSettings?> = _settings

    private val _user = MutableLiveData<User?>()
    val user: LiveData<User?> = _user

    private var userId: Long = 0

    fun load(userId: Long) {
        this.userId = userId
        viewModelScope.launch {
            _settings.value = settingsRepo.getOrCreate(userId)
            _user.value = authRepo.getUserById(userId)
        }
    }

    private fun update(block: UserSettings.() -> UserSettings) {
        val current = _settings.value ?: return
        val updated = current.block()
        _settings.value = updated
        viewModelScope.launch { settingsRepo.saveSettings(updated) }
    }

    fun updateFontSize(size: Int) = update { copy(fontSize = size) }
    fun updateFontFamily(family: String) = update { copy(fontFamily = family) }
    fun updateLineSpacing(spacing: Float) = update { copy(lineSpacing = spacing) }
    fun updateLetterSpacing(spacing: Float) = update { copy(letterSpacing = spacing) }
    fun updateTextColor(color: Int) = update { copy(textColor = color) }
    fun updateShowWordCount(show: Boolean) = update { copy(showWordCount = show) }
    fun updateAutoSave(seconds: Int) = update { copy(autoSaveInterval = seconds) }

    fun updateBackground(
        color: Int? = null,
        imagePath: String? = null,
        preset: String? = null
    ) = update {
        when {
            imagePath != null -> copy(
                backgroundType = "IMAGE",
                backgroundImagePath = imagePath
            )
            preset != null -> copy(
                backgroundType = "PRESET",
                backgroundPreset = preset
            )
            color != null -> copy(
                backgroundType = "COLOR",
                backgroundColor = color
            )
            else -> this
        }
    }

    fun enableBiometric(enabled: Boolean) {
        viewModelScope.launch { authRepo.enableBiometric(userId, enabled) }
    }

    class Factory(
        private val settingsRepo: SettingsRepository,
        private val authRepo: AuthRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(settingsRepo, authRepo) as T
        }
    }
}
