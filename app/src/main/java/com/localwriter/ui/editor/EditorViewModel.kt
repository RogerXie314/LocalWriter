package com.localwriter.ui.editor

import androidx.lifecycle.*
import com.localwriter.data.db.entity.Chapter
import com.localwriter.data.db.entity.UserSettings
import com.localwriter.data.repository.ChapterRepository
import com.localwriter.data.repository.SettingsRepository
import kotlinx.coroutines.*

class EditorViewModel(
    private val chapterRepo: ChapterRepository,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    private val _chapter = MutableLiveData<Chapter?>()
    val chapter: LiveData<Chapter?> = _chapter

    private val _settings = MutableLiveData<UserSettings?>()
    val settings: LiveData<UserSettings?> = _settings

    private val _wordCount = MutableLiveData(0)
    val wordCount: LiveData<Int> = _wordCount

    private val _saveStatus = MutableLiveData<SaveStatus>(SaveStatus.Idle)
    val saveStatus: LiveData<SaveStatus> = _saveStatus

    private var autoSaveJob: Job? = null
    private var currentBookId: Long = 0

    sealed class SaveStatus {
        object Idle : SaveStatus()
        object Saving : SaveStatus()
        object Saved : SaveStatus()
        data class Error(val msg: String) : SaveStatus()
    }

    fun loadChapter(chapterId: Long, bookId: Long, userId: Long) {
        currentBookId = bookId
        viewModelScope.launch {
            _chapter.value = chapterRepo.getChapter(chapterId)
            _wordCount.value = _chapter.value?.wordCount ?: 0
            _settings.value = settingsRepo.getOrCreate(userId)
        }
    }

    fun onContentChanged(content: String, chapterId: Long, autoSaveInterval: Int) {
        _wordCount.value = countWords(content)
        // 重置自动保存定时器
        autoSaveJob?.cancel()
        if (autoSaveInterval > 0) {
            _saveStatus.value = SaveStatus.Idle
            autoSaveJob = viewModelScope.launch {
                delay(autoSaveInterval * 1000L)
                saveContent(chapterId, content)
            }
        }
    }

    fun saveContent(chapterId: Long, content: String) {
        viewModelScope.launch {
            _saveStatus.value = SaveStatus.Saving
            try {
                chapterRepo.saveChapterContent(chapterId, content, currentBookId)
                _saveStatus.value = SaveStatus.Saved
            } catch (e: Exception) {
                _saveStatus.value = SaveStatus.Error("保存失败：${e.message}")
            }
        }
    }

    /** 同步保存（供 saveAndExit 等待完成后再 finish 使用）*/
    suspend fun saveContentSync(chapterId: Long, content: String) {
        _saveStatus.value = SaveStatus.Saving
        try {
            chapterRepo.saveChapterContent(chapterId, content, currentBookId)
            _saveStatus.value = SaveStatus.Saved
        } catch (e: Exception) {
            _saveStatus.value = SaveStatus.Error("保存失败：${e.message}")
        }
    }

    fun saveLastCursor(chapterId: Long, position: Int) {
        viewModelScope.launch {
            chapterRepo.saveLastCursor(chapterId, position)
        }
    }

    fun updateSettings(settings: UserSettings) {
        viewModelScope.launch {
            settingsRepo.saveSettings(settings)
            _settings.value = settings
        }
    }

    private fun countWords(text: String): Int {
        if (text.isBlank()) return 0
        var count = 0
        var inEnglishWord = false
        for (ch in text) {
            when {
                ch.code in 0x4E00..0x9FFF || ch.code in 0x3400..0x4DBF -> {
                    if (inEnglishWord) { count++; inEnglishWord = false }
                    count++
                }
                ch.isLetter() -> inEnglishWord = true
                else -> if (inEnglishWord) { count++; inEnglishWord = false }
            }
        }
        if (inEnglishWord) count++
        return count
    }

    override fun onCleared() {
        super.onCleared()
        autoSaveJob?.cancel()
    }

    class Factory(
        private val chapterRepo: ChapterRepository,
        private val settingsRepo: SettingsRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return EditorViewModel(chapterRepo, settingsRepo) as T
        }
    }
}
