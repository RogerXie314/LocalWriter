package com.localwriter.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.localwriter.R
import com.localwriter.databinding.ActivityMainBinding
import com.localwriter.ui.auth.AuthActivity
import com.localwriter.ui.books.BookListFragment
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
 *   - 右列 content_frame：编辑器（无内容时显示占位提示）
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** 是否是宽屏双栏模式（layout-w600dp 中 content_frame 可见） */
    val isTwoPane: Boolean get() = findViewById<View?>(R.id.content_frame)?.visibility == View.VISIBLE

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)

        if (!SessionManager.isLoggedIn(this)) {
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            setupNavPanel()
        }
    }

    private fun setupNavPanel() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_panel_container, BookListFragment())
            .commit()
    }

    /** 在宽屏右侧加载 fragment，同时隐藏占位提示 */
    fun showInDetailPane(fragment: androidx.fragment.app.Fragment) {
        findViewById<View?>(R.id.empty_detail_hint)?.visibility = View.GONE
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, fragment)
            .commit()
    }

    /** 宽屏右侧清空内容，恢复占位提示 */
    fun clearDetailPane() {
        val f = supportFragmentManager.findFragmentById(R.id.content_frame)
        if (f != null) supportFragmentManager.beginTransaction().remove(f).commit()
        findViewById<View?>(R.id.empty_detail_hint)?.visibility = View.VISIBLE
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

