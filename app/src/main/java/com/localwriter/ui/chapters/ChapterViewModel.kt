package com.localwriter.ui.chapters

import androidx.lifecycle.*
import com.localwriter.data.db.dao.ChapterPreview
import com.localwriter.data.db.entity.Chapter
import com.localwriter.data.db.entity.Volume
import com.localwriter.data.repository.ChapterRepository
import com.localwriter.data.repository.BookRepository
import kotlinx.coroutines.launch

class ChapterViewModel(
    private val chapterRepo: ChapterRepository,
    private val bookRepo: BookRepository
) : ViewModel() {

    private var _bookId = MutableLiveData<Long>()

    /** 当前书的所有章节预览（分卷）*/
    val allChapters: LiveData<List<ChapterPreview>> = _bookId.switchMap { bookId ->
        chapterRepo.observeAllChaptersByBook(bookId)
    }

    val volumes: LiveData<List<Volume>> = _bookId.switchMap { bookId ->
        bookRepo.observeVolumes(bookId)
    }

    private val _event = MutableLiveData<UiEvent?>(null)
    val event: LiveData<UiEvent?> = _event

    fun clearEvent() { _event.value = null }

    sealed class UiEvent {
        data class NavigateToEditor(val chapterId: Long, val bookId: Long) : UiEvent()
        data class NavigateToReader(val chapterId: Long, val bookId: Long) : UiEvent()
        data class ShowError(val msg: String) : UiEvent()
        data class ChapterCreated(val chapterId: Long) : UiEvent()
    }

    fun setBookId(bookId: Long) {
        if (_bookId.value != bookId) _bookId.value = bookId
    }

    fun createChapter(bookId: Long, volumeId: Long, title: String) {
        if (title.isBlank()) { _event.value = UiEvent.ShowError("章节标题不能为空"); return }
        viewModelScope.launch {
            val chapter = Chapter(bookId = bookId, volumeId = volumeId, title = title)
            val id = chapterRepo.createChapter(chapter)
            _event.value = UiEvent.ChapterCreated(id)
        }
    }

    fun deleteChapter(chapterId: Long, bookId: Long) {
        viewModelScope.launch { chapterRepo.softDeleteChapter(chapterId, bookId) }
    }

    fun restoreChapter(chapterId: Long, bookId: Long) {
        viewModelScope.launch { chapterRepo.restoreChapter(chapterId, bookId) }
    }

    /** 全部恢复回收站 */
    fun restoreAll(bookId: Long) {
        viewModelScope.launch { chapterRepo.restoreAllChapters(bookId) }
    }

    /** 清空回收站 */
    fun clearTrash(bookId: Long) {
        viewModelScope.launch { chapterRepo.clearTrash(bookId) }
    }

    fun reorderChapters(volumeId: Long, orderedIds: List<Long>) {
        viewModelScope.launch { chapterRepo.reorderChapters(volumeId, orderedIds) }
    }

    fun renameVolume(volume: Volume) {
        viewModelScope.launch { bookRepo.updateVolume(volume) }
    }

    fun createVolume(bookId: Long, title: String) {
        if (title.isBlank()) { _event.value = UiEvent.ShowError("卷标题不能为空"); return }
        viewModelScope.launch {
            bookRepo.createVolume(Volume(bookId = bookId, title = title))
        }
    }

    fun deleteVolume(volume: Volume) {
        viewModelScope.launch { bookRepo.deleteVolume(volume) }
    }

    fun updateTitle(chapterId: Long, newTitle: String) {
        viewModelScope.launch { chapterRepo.updateChapterTitle(chapterId, newTitle) }
    }

    /** 默认：点击章节 → 阅读模式 */
    fun openChapter(chapterId: Long, bookId: Long) {
        _event.value = UiEvent.NavigateToReader(chapterId, bookId)
    }

    /** ⋮ 菜单"编辑" → 编辑器 */
    fun editChapter(chapterId: Long, bookId: Long) {
        _event.value = UiEvent.NavigateToEditor(chapterId, bookId)
    }

    fun observeDeletedChapters(bookId: Long) = chapterRepo.observeDeletedChapters(bookId)

    fun permanentDelete(chapter: Chapter, bookId: Long) {
        viewModelScope.launch { chapterRepo.permanentDeleteChapter(chapter, bookId) }
    }

    suspend fun searchChapters(bookId: Long, query: String) =
        chapterRepo.searchChapters(bookId, query)

    class Factory(
        private val chapterRepo: ChapterRepository,
        private val bookRepo: BookRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ChapterViewModel(chapterRepo, bookRepo) as T
        }
    }
}
