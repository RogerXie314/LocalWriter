package com.localwriter

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.localwriter.data.db.AppDatabase
import com.localwriter.data.repository.*
import com.localwriter.utils.SessionManager

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
        // 当整个应用退到后台时自动触发锁定（由 onResume 中的 isLocked() 完成宽限期判断）
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                if (SessionManager.isLoggedIn(this@LocalWriterApp)) {
                    SessionManager.lock(this@LocalWriterApp)
                }
            }
        })
    }
}
