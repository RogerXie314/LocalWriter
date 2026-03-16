package com.localwriter.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.localwriter.data.db.entity.Chapter

/** 章节预览（导航栏用，不加载正文内容）*/
data class ChapterPreview(
    val id: Long,
    val bookId: Long,
    val volumeId: Long,
    val title: String,
    val wordCount: Int,
    val status: String,
    val sortOrder: Int,
    val updatedAt: Long
)

@Dao
interface ChapterDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chapter: Chapter): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chapters: List<Chapter>)

    @Update
    suspend fun update(chapter: Chapter)

    @Delete
    suspend fun delete(chapter: Chapter)

    /** 获取某卷的所有非删除章节（完整内容）*/
    @Query("SELECT * FROM chapters WHERE volumeId = :volumeId AND status != 'DELETED' ORDER BY sortOrder ASC")
    suspend fun getAllByVolume(volumeId: Long): List<Chapter>

    /** 观察某卷的章节列表（导航栏使用，不含正文）*/
    @Query("""
        SELECT id, bookId, volumeId, title, wordCount, status, sortOrder, updatedAt
        FROM chapters
        WHERE volumeId = :volumeId AND status != 'DELETED'
        ORDER BY sortOrder ASC
    """)
    fun observePreviewsByVolume(volumeId: Long): LiveData<List<ChapterPreview>>

    /** 获取某书的所有章节预览（用于书籍详情）*/
    @Query("""
        SELECT id, bookId, volumeId, title, wordCount, status, sortOrder, updatedAt
        FROM chapters
        WHERE bookId = :bookId AND status != 'DELETED'
        ORDER BY sortOrder ASC
    """)
    fun observePreviewsByBook(bookId: Long): LiveData<List<ChapterPreview>>

    @Query("SELECT * FROM chapters WHERE id = :chapterId LIMIT 1")
    suspend fun findById(chapterId: Long): Chapter?

    @Query("SELECT * FROM chapters WHERE id = :chapterId LIMIT 1")
    fun observeById(chapterId: Long): LiveData<Chapter?>

    /** 软删除：移入回收站 */
    @Query("UPDATE chapters SET status = 'DELETED', deletedAt = :deletedAt WHERE id = :chapterId")
    suspend fun softDelete(chapterId: Long, deletedAt: Long = System.currentTimeMillis())

    /** 从回收站恢复 */
    @Query("UPDATE chapters SET status = 'DRAFT', deletedAt = NULL WHERE id = :chapterId")
    suspend fun restore(chapterId: Long)

    /** 观察回收站章节（LiveData，持续更新）*/
    @Query("SELECT * FROM chapters WHERE bookId = :bookId AND status = 'DELETED' ORDER BY deletedAt DESC")
    fun observeDeletedByBook(bookId: Long): LiveData<List<Chapter>>

    /** 一次性获取回收站章节快照（suspend，用于弹窗等单次查询场景）*/
    @Query("SELECT * FROM chapters WHERE bookId = :bookId AND status = 'DELETED' ORDER BY deletedAt DESC")
    suspend fun getDeletedByBook(bookId: Long): List<Chapter>

    /** 清理超过 30 天的回收站章节 */
    @Query("DELETE FROM chapters WHERE status = 'DELETED' AND deletedAt < :threshold")
    suspend fun purgeOldDeleted(threshold: Long)

    @Query("UPDATE chapters SET sortOrder = :sortOrder WHERE id = :chapterId")
    suspend fun updateSortOrder(chapterId: Long, sortOrder: Int)

    @Query("UPDATE chapters SET lastCursorPos = :pos WHERE id = :chapterId")
    suspend fun updateCursorPos(chapterId: Long, pos: Int)

    @Query("UPDATE chapters SET content = :content, wordCount = :wordCount, updatedAt = :updatedAt WHERE id = :chapterId")
    suspend fun updateContent(chapterId: Long, content: String, wordCount: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE chapters SET title = :title, updatedAt = :updatedAt WHERE id = :chapterId")
    suspend fun updateTitle(chapterId: Long, title: String, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT SUM(wordCount) FROM chapters WHERE bookId = :bookId AND status != 'DELETED'")
    suspend fun sumWordCount(bookId: Long): Int

    @Query("SELECT COUNT(*) FROM chapters WHERE bookId = :bookId AND status != 'DELETED'")
    suspend fun countByBook(bookId: Long): Int

    @Query("SELECT MAX(sortOrder) FROM chapters WHERE volumeId = :volumeId")
    suspend fun maxSortOrder(volumeId: Long): Int?

    /** 全文搜索 */
    @Query("SELECT id, bookId, volumeId, title, wordCount, status, sortOrder, updatedAt FROM chapters WHERE bookId = :bookId AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%') AND status != 'DELETED'")
    suspend fun search(bookId: Long, query: String): List<ChapterPreview>

    /** 批量恢复回收站（从回收站全部恢复）*/
    @Query("UPDATE chapters SET status = 'DRAFT', deletedAt = NULL WHERE bookId = :bookId AND status = 'DELETED'")
    suspend fun restoreAllDeleted(bookId: Long)

    /** 清空回收站（永久删除该书所有已删除章节）*/
    @Query("DELETE FROM chapters WHERE bookId = :bookId AND status = 'DELETED'")
    suspend fun deleteAllDeleted(bookId: Long)
}
