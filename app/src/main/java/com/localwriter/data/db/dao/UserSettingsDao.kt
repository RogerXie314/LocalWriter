package com.localwriter.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.localwriter.data.db.entity.UserSettings

@Dao
interface UserSettingsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(settings: UserSettings): Long

    @Update
    suspend fun update(settings: UserSettings)

    @Query("SELECT * FROM user_settings WHERE userId = :userId LIMIT 1")
    suspend fun findByUser(userId: Long): UserSettings?

    @Query("SELECT * FROM user_settings WHERE userId = :userId LIMIT 1")
    fun observeByUser(userId: Long): LiveData<UserSettings?>
}
