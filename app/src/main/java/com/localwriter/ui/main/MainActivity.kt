package com.localwriter.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.localwriter.LocalWriterApp
import com.localwriter.R
import com.localwriter.databinding.ActivityMainBinding
import com.localwriter.ui.books.BookListFragment
import com.localwriter.ui.settings.SettingsActivity
import com.localwriter.utils.SessionManager
import kotlinx.coroutines.launch

/**
 * 主界面
 * 默认采用左右分栏布局：
 *  - 平板/横屏：左侧书籍/章节导航（NavigationDrawer），右侧内容编辑区
 *  - 手机竖屏：单栏，通过侧滑或按钮切换导航与内容
 *
 * 布局逻辑：
 *  左侧导航（NavPanel）：
 *    - 书籍列表（BookListFragment）
 *    - 点击书籍 → 展开章节列表（ChapterListFragment）
 *    - 点击章节 → 右侧打开编辑器（EditorFragment）
 *  右侧内容区（ContentFrame）：
 *    - 初始显示欢迎页/书籍封面
 *    - 点击章节后显示编辑器
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 验证会话
        if (!SessionManager.isLoggedIn(this)) {
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "LocalWriter"

        if (savedInstanceState == null) {
            setupNavPanel()
        }

        setupDrawer()
    }

    private fun setupNavPanel() {
        // 左侧导航：书籍列表
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_panel_container, BookListFragment())
            .commit()
    }

    private fun setupDrawer() {
        binding.toolbar.setNavigationOnClickListener {
            // 手机端：打开/关闭侧边导航（平板双栏无抽屉，强转返回 null 无操作）
            val drawer = binding.drawerLayout as? androidx.drawerlayout.widget.DrawerLayout
            if (drawer?.isDrawerOpen(binding.navPanelContainer) == true) {
                drawer.closeDrawer(binding.navPanelContainer)
            } else {
                drawer?.openDrawer(binding.navPanelContainer)
            }
        }
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

    override fun onPause() {
        super.onPause()
        // 注意：不在此处锁定！onPause 在跳转到 SettingsActivity/EditorActivity 等
        // 同 App 内 Activity 时也会触发，会导致返回后锁定状态异常。
        // 真正的后台锁定通过 ProcessLifecycleOwner 在 LocalWriterApp 中实现。
    }

    override fun onResume() {
        super.onResume()
        // 会话有效性检查（防止会话被清除后仍停留在此界面）
        if (!SessionManager.isLoggedIn(this)) {
            finish()
        }
    }
}
