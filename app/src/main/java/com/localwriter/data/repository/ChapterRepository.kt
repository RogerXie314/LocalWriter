package com.localwriter.data.repository

import androidx.lifecycle.LiveData
import com.localwriter.data.db.dao.BookDao
import com.localwriter.data.db.dao.ChapterDao
import com.localwriter.data.db.dao.ChapterPreview
import com.localwriter.data.db.entity.Chapter

class ChapterRepository(
    private val chapterDao: ChapterDao,
    private val bookDao: BookDao
) {

    fun observeChapterPreviews(volumeId: Long): LiveData<List<ChapterPreview>> =
        chapterDao.observePreviewsByVolume(volumeId)

    fun observeAllChaptersByBook(bookId: Long): LiveData<List<ChapterPreview>> =
        chapterDao.observePreviewsByBook(bookId)

    fun observeChapter(chapterId: Long): LiveData<Chapter?> =
        chapterDao.observeById(chapterId)

    suspend fun getChapter(chapterId: Long): Chapter? =
        chapterDao.findById(chapterId)

    /**
     * 新建章节
     * @return 章节ID
     */
    suspend fun createChapter(chapter: Chapter): Long {
        val max = chapterDao.maxSortOrder(chapter.volumeId) ?: -1
        val newChapter = chapter.copy(
            sortOrder = max + 1,
            wordCount = chapter.content.replace("\n", "").length
        )
        val chapterId = chapterDao.insert(newChapter)
        // 更新书籍字数
        refreshBookWordCount(chapter.bookId)
        return chapterId
    }

    /** 保存章节内容（自动统计字数）*/
    suspend fun saveChapterContent(chapterId: Long, content: String, bookId: Long) {
        val wordCount = countWords(content)
        chapterDao.updateContent(chapterId, content, wordCount)
        refreshBookWordCount(bookId)
    }

    suspend fun updateChapterTitle(chapterId: Long, title: String) {
        chapterDao.updateTitle(chapterId, title)
    }

    /** 软删除（移入回收站）*/
    suspend fun softDeleteChapter(chapterId: Long, bookId: Long) {
        chapterDao.softDelete(chapterId)
        refreshBookWordCount(bookId)
    }

    /** 从回收站恢复 */
    suspend fun restoreChapter(chapterId: Long, bookId: Long) {
        chapterDao.restore(chapterId)
        refreshBookWordCount(bookId)
    }

    /** 永久删除 */
    suspend fun permanentDeleteChapter(chapter: Chapter, bookId: Long) {
        chapterDao.delete(chapter)
        refreshBookWordCount(bookId)
    }

    /** 批量恢复回收站（全部恢复）*/
    suspend fun restoreAllChapters(bookId: Long) {
        chapterDao.restoreAllDeleted(bookId)
        refreshBookWordCount(bookId)
    }

    /** 清空回收站（永久删除所有已删除章节）*/
    suspend fun clearTrash(bookId: Long) {
        chapterDao.deleteAllDeleted(bookId)
        refreshBookWordCount(bookId)
    }

    suspend fun saveLastCursor(chapterId: Long, pos: Int) {
        chapterDao.updateCursorPos(chapterId, pos)
    }

    fun observeDeletedChapters(bookId: Long): LiveData<List<Chapter>> =
        chapterDao.observeDeletedByBook(bookId)

    suspend fun purgeOldDeletedChapters() {
        val threshold = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
        chapterDao.purgeOldDeleted(threshold)
    }

    suspend fun reorderChapters(volumeId: Long, orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id ->
            chapterDao.updateSortOrder(id, index)
        }
    }

    suspend fun searchChapters(bookId: Long, query: String): List<ChapterPreview> =
        chapterDao.search(bookId, query)

    private suspend fun refreshBookWordCount(bookId: Long) {
        val total = chapterDao.sumWordCount(bookId) ?: 0
        bookDao.updateWordCount(bookId, total)
    }

    /**
     * 汉字/英文单词字数统计
     * - CJK字符每个计1字
     * - 英文按空格分词
     */
    private fun countWords(text: String): Int {
        if (text.isBlank()) return 0
        var count = 0
        var inEnglishWord = false
        for (ch in text) {
            when {
                ch.code in 0x4E00..0x9FFF ||   // CJK统一汉字
                ch.code in 0x3400..0x4DBF ||   // CJK扩展A
                ch.code in 0x20000..0x2A6DF -> {// CJK扩展B
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
}
