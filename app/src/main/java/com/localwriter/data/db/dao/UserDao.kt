package com.localwriter.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.localwriter.data.db.entity.User

@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(user: User): Long

    @Update
    suspend fun update(user: User)

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun findByUsername(username: String): User?

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun findById(userId: Long): User?

    @Query("SELECT COUNT(*) FROM users")
    suspend fun count(): Int

    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getFirst(): User?

    @Query("SELECT * FROM users LIMIT 1")
    fun observeFirstUser(): LiveData<User?>
}
