package com.localwriter

import android.app.Application
import com.localwriter.data.db.AppDatabase
import com.localwriter.data.repository.*

class LocalWriterApp : Application() {

    val database by lazy { AppDatabase.getInstance(this) }

    val authRepository by lazy {
        AuthRepository(database.userDao(), database.userSettingsDao())
    }

    val bookRepository by lazy {
        BookRepository(database.bookDao(), database.volumeDao(), database.chapterDao())
    }

    val chapterRepository by lazy {
        ChapterRepository(database.chapterDao(), database.bookDao())
    }

    val settingsRepository by lazy {
        SettingsRepository(database.userSettingsDao())
    }

    override fun onCreate() {
        super.onCreate()
    }
}
