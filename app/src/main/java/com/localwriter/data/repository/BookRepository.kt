package com.localwriter.data.repository

import androidx.lifecycle.LiveData
import com.localwriter.data.db.dao.BookDao
import com.localwriter.data.db.dao.ChapterDao
import com.localwriter.data.db.dao.VolumeDao
import com.localwriter.data.db.entity.Book
import com.localwriter.data.db.entity.Chapter
import com.localwriter.data.db.entity.Volume
import com.localwriter.utils.io.ImportResult

class BookRepository(
    private val bookDao: BookDao,
    private val volumeDao: VolumeDao,
    private val chapterDao: ChapterDao
) {

    fun observeAllBooks(userId: Long): LiveData<List<Book>> =
        bookDao.observeAllByUser(userId)

    /** M8: 按最近更新/阅读顺序排列 */
    fun observeAllBooksRecentFirst(userId: Long): LiveData<List<Book>> =
        bookDao.observeAllByUserRecentFirst(userId)

    suspend fun getAllBooks(userId: Long): List<Book> =
        bookDao.getAllByUser(userId)

    fun observeBook(bookId: Long): LiveData<Book?> =
        bookDao.observeById(bookId)

    /**
     * 创建新书，并自动创建默认卷 "第一卷"
     * @return 新书ID
     */
    suspend fun createBook(book: Book): Long {
        val bookId = bookDao.insert(book)
        // 自动创建默认卷
        volumeDao.insert(Volume(bookId = bookId, title = "第一卷", sortOrder = 0))
        return bookId
    }

    suspend fun updateBook(book: Book) {
        bookDao.update(book.copy(updatedAt = System.currentTimeMillis()))
    }

    /** 删除书籍（级联删除卷和章节）*/
    suspend fun deleteBook(book: Book) {
        bookDao.delete(book)
    }

    /** 刷新书籍总字数（根据所有非删除章节求和）*/
    suspend fun refreshWordCount(bookId: Long) {
        val total = chapterDao.sumWordCount(bookId) ?: 0
        bookDao.updateWordCount(bookId, total)
    }

    suspend fun updateLastChapter(bookId: Long, chapterId: Long) {
        bookDao.updateLastChapter(bookId, chapterId)
    }

    suspend fun searchBooks(userId: Long, query: String): List<Book> =
        bookDao.search(userId, query)

    suspend fun reorderBooks(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id ->
            bookDao.updateSortOrder(id, index)
        }
    }

    suspend fun getBookById(bookId: Long): Book? = bookDao.findById(bookId)

    fun observeVolumes(bookId: Long) = volumeDao.observeAllByBook(bookId)

    suspend fun createVolume(volume: Volume): Long {
        val max = volumeDao.maxSortOrder(volume.bookId) ?: -1
        return volumeDao.insert(volume.copy(sortOrder = max + 1))
    }

    suspend fun updateVolume(volume: Volume) = volumeDao.update(volume)

    suspend fun deleteVolume(volume: Volume) = volumeDao.delete(volume)

    suspend fun reorderVolumes(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id ->
            volumeDao.updateSortOrder(id, index)
        }
    }

    /**
     * 批量保存从 BookImporter 解析的导入结果
     * 自动创建书籍、默认卷、所有章节，并统计总字数
     * @return 新书 ID
     */
    suspend fun importBook(userId: Long, result: ImportResult): Long {
        val bookId = bookDao.insert(
            Book(
                userId      = userId,
                title       = result.title,
                author      = result.author,
                description = result.description,
                tags        = "导入",
                status      = "FINISHED"
            )
        )

        // M6: 按 volumeTitle 分组，无卷归入"正文"
        val grouped = result.chapters.groupBy { it.volumeTitle ?: "正文" }

        // 保持原始顺序：取章节首次出现的卷名顺序
        val volumeOrder = result.chapters.map { it.volumeTitle ?: "正文" }
            .distinct()

        var volSort = 0
        val allChapters = mutableListOf<Chapter>()
        for (volTitle in volumeOrder) {
            val volumeId = volumeDao.insert(
                Volume(bookId = bookId, title = volTitle, sortOrder = volSort++)
            )
            grouped[volTitle]?.forEachIndexed { index, sc ->
                allChapters.add(
                    Chapter(
                        bookId    = bookId,
                        volumeId  = volumeId,
                        title     = sc.title,
                        content   = sc.content,
                        wordCount = sc.content.length,
                        status    = "PUBLISHED",
                        sortOrder = index
                    )
                )
            }
        }

        chapterDao.insertAll(allChapters)
        val total = allChapters.sumOf { it.wordCount }
        bookDao.updateWordCount(bookId, total)
        return bookId
    }
}
