package com.localwriter.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.localwriter.data.db.entity.Book

@Dao
interface BookDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: Book): Long

    @Update
    suspend fun update(book: Book)

    @Delete
    suspend fun delete(book: Book)

    @Query("SELECT * FROM books WHERE userId = :userId AND status != 'DELETED' ORDER BY sortOrder ASC, updatedAt DESC")
    fun observeAllByUser(userId: Long): LiveData<List<Book>>

    /** M8: 按最近阅读（updatedAt 降序）排列 */
    @Query("SELECT * FROM books WHERE userId = :userId AND status != 'DELETED' ORDER BY updatedAt DESC")
    fun observeAllByUserRecentFirst(userId: Long): LiveData<List<Book>>

    @Query("SELECT * FROM books WHERE userId = :userId AND status != 'DELETED' ORDER BY sortOrder ASC, updatedAt DESC")
    suspend fun getAllByUser(userId: Long): List<Book>

    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    suspend fun findById(bookId: Long): Book?

    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    fun observeById(bookId: Long): LiveData<Book?>

    @Query("UPDATE books SET wordCount = :wordCount, updatedAt = :updatedAt WHERE id = :bookId")
    suspend fun updateWordCount(bookId: Long, wordCount: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE books SET lastChapterId = :chapterId, updatedAt = :updatedAt WHERE id = :bookId")
    suspend fun updateLastChapter(bookId: Long, chapterId: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE books SET sortOrder = :sortOrder WHERE id = :bookId")
    suspend fun updateSortOrder(bookId: Long, sortOrder: Int)

    @Query("SELECT * FROM books WHERE userId = :userId AND (title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%')")
    suspend fun search(userId: Long, query: String): List<Book>

    @Query("SELECT SUM(wordCount) FROM books WHERE userId = :userId")
    suspend fun totalWordCount(userId: Long): Int
}
