package com.localwriter.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.localwriter.data.db.entity.Volume

@Dao
interface VolumeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(volume: Volume): Long

    @Update
    suspend fun update(volume: Volume)

    @Delete
    suspend fun delete(volume: Volume)

    @Query("SELECT * FROM volumes WHERE bookId = :bookId ORDER BY sortOrder ASC")
    fun observeAllByBook(bookId: Long): LiveData<List<Volume>>

    @Query("SELECT * FROM volumes WHERE bookId = :bookId ORDER BY sortOrder ASC")
    suspend fun getAllByBook(bookId: Long): List<Volume>

    @Query("SELECT * FROM volumes WHERE id = :volumeId LIMIT 1")
    suspend fun findById(volumeId: Long): Volume?

    @Query("SELECT COUNT(*) FROM volumes WHERE bookId = :bookId")
    suspend fun countByBook(bookId: Long): Int

    @Query("SELECT MAX(sortOrder) FROM volumes WHERE bookId = :bookId")
    suspend fun maxSortOrder(bookId: Long): Int?

    @Query("UPDATE volumes SET sortOrder = :sortOrder WHERE id = :volumeId")
    suspend fun updateSortOrder(volumeId: Long, sortOrder: Int)
}
