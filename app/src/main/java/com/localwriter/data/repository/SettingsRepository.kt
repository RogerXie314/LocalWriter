package com.localwriter.data.repository

import androidx.lifecycle.LiveData
import com.localwriter.data.db.dao.UserSettingsDao
import com.localwriter.data.db.entity.UserSettings

class SettingsRepository(private val dao: UserSettingsDao) {

    fun observeSettings(userId: Long): LiveData<UserSettings?> =
        dao.observeByUser(userId)

    suspend fun getSettings(userId: Long): UserSettings? = dao.findByUser(userId)

    suspend fun saveSettings(settings: UserSettings) {
        if (settings.id == 0L) {
            dao.insert(settings.copy(updatedAt = System.currentTimeMillis()))
        } else {
            dao.update(settings.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun getOrCreate(userId: Long): UserSettings {
        return dao.findByUser(userId) ?: UserSettings(userId = userId).also {
            val id = dao.insert(it)
            return it.copy(id = id)
        }
    }
}
