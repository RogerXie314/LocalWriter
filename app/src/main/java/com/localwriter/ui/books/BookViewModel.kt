package com.localwriter.ui.books

import androidx.lifecycle.*
import com.localwriter.data.db.entity.Book
import com.localwriter.data.db.entity.Volume
import com.localwriter.data.repository.BookRepository
import com.localwriter.utils.SessionManager
import android.content.Context
import kotlinx.coroutines.launch

class BookViewModel(
    private val repo: BookRepository,
    private val context: Context   // 构造时传入 applicationContext，不持有 Activity 引用
) : ViewModel() {

    private val userId: Long = SessionManager.getUserId(context)

    /** M8: 排序模式切换（false=默认排序, true=最近阅读） */
    private val _sortByRecent = MutableLiveData(false)
    val isSortByRecent: LiveData<Boolean> = _sortByRecent

    val books: LiveData<List<Book>> = _sortByRecent.switchMap { recent ->
        if (recent) repo.observeAllBooksRecentFirst(userId)
        else repo.observeAllBooks(userId)
    }

    fun toggleSort() {
        _sortByRecent.value = !(_sortByRecent.value ?: false)
    }

    private val _currentBook = MutableLiveData<Book?>()
    val currentBook: LiveData<Book?> = _currentBook

    val volumes: LiveData<List<Volume>> = _currentBook.switchMap { book ->
        if (book != null) repo.observeVolumes(book.id)
        else MutableLiveData(emptyList())
    }

    private val _uiEvent = MutableLiveData<UiEvent>()
    val uiEvent: LiveData<UiEvent> = _uiEvent

    sealed class UiEvent {
        data class ShowError(val msg: String) : UiEvent()
        data class BookCreated(val bookId: Long) : UiEvent()
        data class NavigateToEditor(val chapterId: Long, val bookId: Long) : UiEvent()
    }

    fun selectBook(book: Book) {
        _currentBook.value = book
    }

    fun createBook(title: String, author: String = "", description: String = "") {
        if (title.isBlank()) {
            _uiEvent.value = UiEvent.ShowError("书名不能为空")
            return
        }
        viewModelScope.launch {
            val book = Book(userId = userId, title = title, author = author, description = description)
            val bookId = repo.createBook(book)
            _uiEvent.value = UiEvent.BookCreated(bookId)
        }
    }

    fun updateBook(book: Book) {
        viewModelScope.launch { repo.updateBook(book) }
    }

    fun deleteBook(book: Book) {
        viewModelScope.launch { repo.deleteBook(book) }
    }

    fun createVolume(bookId: Long, title: String) {
        viewModelScope.launch {
            repo.createVolume(Volume(bookId = bookId, title = title))
        }
    }

    fun updateVolume(volume: Volume) {
        viewModelScope.launch { repo.updateVolume(volume) }
    }

    fun deleteVolume(volume: Volume) {
        viewModelScope.launch { repo.deleteVolume(volume) }
    }

    fun reorderBooks(orderedIds: List<Long>) {
        viewModelScope.launch { repo.reorderBooks(orderedIds) }
    }

    fun reorderVolumes(orderedIds: List<Long>) {
        viewModelScope.launch { repo.reorderVolumes(orderedIds) }
    }

    class Factory(private val repo: BookRepository, private val context: Context) :
        ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return BookViewModel(repo, context) as T
        }
    }
}
