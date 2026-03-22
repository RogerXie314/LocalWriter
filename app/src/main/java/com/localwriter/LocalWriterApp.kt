package com.localwriter
// build-trigger
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
        // 当整个应用退到后台时，根据用户设置的超时策略决定是否触发锁定。
        // timeout=-1（从不自动锁定）时跳过 lock()，避免"重新进入时闪现认证界面"的问题。
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                if (SessionManager.isLoggedIn(this@LocalWriterApp)) {
                    val timeout = SessionManager.getLockTimeout(this@LocalWriterApp)
                    if (timeout >= 0) {  // 仅在"立即锁定"或"N分钟"策略下才记录锁定
                        SessionManager.lock(this@LocalWriterApp)
                    }
                }
            }
        })
    }
}
