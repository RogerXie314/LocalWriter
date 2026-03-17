package com.localwriter.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.localwriter.LocalWriterApp
import com.localwriter.R
import com.localwriter.databinding.ActivityMainBinding
import com.localwriter.ui.auth.AuthActivity
import com.localwriter.ui.books.BookListFragment
import com.localwriter.ui.settings.SettingsActivity
import com.localwriter.utils.SessionManager
import com.localwriter.utils.ThemeManager

/**
 * 主界面
 *
 * 手机（单栏）：nav_panel_container 直接全屏显示书籍列表 / 章节列表
 *   - BookListFragment → 点击书籍 → ChapterListFragment（返回栈）
 *   - 点击章节 → EditorActivity（独立 Activity）
 *
 * 平板（双栏）：layout-w600dp 中双列布局
 *   - 左列 nav_panel_container：书籍/章节导航
 *   - 右列 content_frame：编辑器
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)

        if (!SessionManager.isLoggedIn(this)) {
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "LocalWriter"
        // 返回书架图标（可在章节列表时 Fragment 自己处理 up 导航）
        supportActionBar?.setDisplayHomeAsUpEnabled(false)

        if (savedInstanceState == null) {
            setupNavPanel()
        }
    }

    private fun setupNavPanel() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_panel_container, BookListFragment())
            .commit()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_lock -> {
                SessionManager.lock(this)
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!SessionManager.isLoggedIn(this)) {
            finish()
            return
        }
        if (SessionManager.isLocked(this)) {
            startActivity(Intent(this, AuthActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
            finish()
            return
        }
    }
}

