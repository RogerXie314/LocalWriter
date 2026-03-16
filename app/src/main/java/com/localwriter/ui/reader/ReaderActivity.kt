package com.localwriter.ui.reader

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.localwriter.LocalWriterApp
import com.localwriter.R
import com.localwriter.databinding.ActivityReaderBinding
import com.localwriter.ui.editor.EditorActivity
import com.localwriter.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 阅读器界面（只读模式）
 *
 * 功能：
 * - 按卷排序展示所有章节，支持上一章/下一章导航
 * - 记忆阅读位置（滚动位置存储在 lastCursorPos 字段）
 * - 点击正文区域切换工具栏/底部导航栏显隐（沉浸模式）
 * - 字体大小 +/- 调整（14–28sp）
 * - 章节目录快速跳转
 * - 菜单支持跳转到编辑器编辑当前章节
 */
class ReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReaderBinding

    private var chapterId: Long = 0
    private var bookId: Long = 0
    private var currentIndex: Int = -1

    /** 当前书的所有章节 ID（按卷+sortOrder 排序） */
    private var allChapterIds: List<Long> = emptyList()
    /** ID → 标题映射，用于目录 */
    private var chapterTitles: List<String> = emptyList()

    private var currentFontSize: Float = 18f
    private val fontSizeStep = 2f
    private val fontSizeMin = 14f
    private val fontSizeMax = 28f

    private var barsVisible = true

    /** 书签防抖 Handler：滚动停止 800ms 后自动保存 */
    private val bookmarkHandler = Handler(Looper.getMainLooper())
    private val bookmarkRunnable = Runnable { saveBookmark() }

    companion object {
        const val EXTRA_CHAPTER_ID = "reader_chapter_id"
        const val EXTRA_BOOK_ID    = "reader_book_id"
        private const val PREFS_READER      = "reader_prefs"
        private const val KEY_FONT_SIZE     = "font_size"
        private const val KEY_BOOKMARK_PRE  = "bookmark_ch_"
    }

    // ─────────────────── 生命周期 ───────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        chapterId = intent.getLongExtra(EXTRA_CHAPTER_ID, 0)
        bookId    = intent.getLongExtra(EXTRA_BOOK_ID, 0)
        if (chapterId == 0L || bookId == 0L) { finish(); return }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnPrevChapter.setOnClickListener { navigateChapter(-1) }
        binding.btnNextChapter.setOnClickListener { navigateChapter(+1) }

        // 点击内容区域切换工具栏显隐（沉浸阅读）
        binding.scrollView.setOnClickListener { toggleBars() }
        binding.tvContent.setOnClickListener { toggleBars() }

        // 点击书签图标跳回书签位置
        binding.ivBookmarkIndicator.setOnClickListener { jumpToBookmark() }

        // 滚动监听：向下滚动时防抖保存书签
        binding.scrollView.viewTreeObserver.addOnScrollChangedListener {
            val scrollY = binding.scrollView.scrollY
            if (scrollY > 0) {
                bookmarkHandler.removeCallbacks(bookmarkRunnable)
                bookmarkHandler.postDelayed(bookmarkRunnable, 800)
            }
        }

        // 恢复字体大小（0f 为哨兵值，表示尚未手动设置）
        currentFontSize = getSharedPreferences(PREFS_READER, MODE_PRIVATE)
            .getFloat(KEY_FONT_SIZE, 0f)

        loadBookChapters()
    }

    override fun onPause() {
        super.onPause()
        // 持久化字体大小
        getSharedPreferences(PREFS_READER, MODE_PRIVATE).edit()
            .putFloat(KEY_FONT_SIZE, currentFontSize).apply()
        saveScrollPosition()
        // 立即触发一次书签保存（若有未消费的防抖任务）
        bookmarkHandler.removeCallbacks(bookmarkRunnable)
        saveBookmark()
    }

    override fun onDestroy() {
        super.onDestroy()
        bookmarkHandler.removeCallbacks(bookmarkRunnable)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_font_size_inc -> {
                if (currentFontSize < fontSizeMax) {
                    currentFontSize += fontSizeStep
                    applyFontSize()
                } else Toast.makeText(this, "已达最大字号", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_font_size_dec -> {
                if (currentFontSize > fontSizeMin) {
                    currentFontSize -= fontSizeStep
                    applyFontSize()
                } else Toast.makeText(this, "已达最小字号", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_chapter_list -> {
                showChapterListDialog()
                true
            }
            R.id.action_edit_chapter -> {
                openEditor()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ─────────────────── 数据加载 ───────────────────

    /** 加载当前书所有章节 ID 列表（IO 线程），然后加载当前章节 */
    private fun loadBookChapters() {
        lifecycleScope.launch {
            val db = (application as LocalWriterApp).database
            val ids = mutableListOf<Long>()
            val titles = mutableListOf<String>()
            withContext(Dispatchers.IO) {
                val volumes = db.volumeDao().getAllByBook(bookId).sortedBy { it.sortOrder }
                for (vol in volumes) {
                    val chapters = db.chapterDao().getAllByVolume(vol.id)
                        .filter { it.status != "DELETED" }
                        .sortedBy { it.sortOrder }
                    for (ch in chapters) {
                        ids.add(ch.id)
                        titles.add(ch.title)
                    }
                }
            }
            allChapterIds  = ids
            chapterTitles  = titles
            currentIndex   = ids.indexOf(chapterId).coerceAtLeast(0)
            applyUserSettings()
            loadChapter(chapterId)
        }
    }

    /** 加载并展示指定章节内容 */
    private fun loadChapter(chapId: Long) {
        lifecycleScope.launch {
            val db = (application as LocalWriterApp).database
            val chapter = withContext(Dispatchers.IO) {
                db.chapterDao().findById(chapId)
            } ?: run {
                Toast.makeText(this@ReaderActivity, "章节不存在", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            chapterId    = chapId
            currentIndex = allChapterIds.indexOf(chapId).coerceAtLeast(0)

            // 更新正文 UI
            supportActionBar?.title = chapter.title
            binding.tvChapterTitle.text = chapter.title
            binding.tvContent.text = chapter.content.ifEmpty { "（本章暂无内容）" }
            applyFontSize()

            // 更新进度
            val total = allChapterIds.size
            binding.tvChapterProgress.text = "${currentIndex + 1} / $total 章"

            // 启用/禁用上下章按钮
            binding.btnPrevChapter.isEnabled = currentIndex > 0
            binding.btnNextChapter.isEnabled = currentIndex < allChapterIds.size - 1

            // 恢复上次阅读位置
            val scrollY = chapter.lastCursorPos
            binding.scrollView.post { binding.scrollView.scrollTo(0, scrollY) }

            // 更新书签指示器
            updateBookmarkIndicator()

            // 更新书籍最后阅读章节
            withContext(Dispatchers.IO) {
                (application as LocalWriterApp).bookRepository.updateLastChapter(bookId, chapId)
            }
        }
    }

    // ─────────────────── 导航 ───────────────────

    private fun navigateChapter(direction: Int) {
        val newIndex = currentIndex + direction
        if (newIndex < 0 || newIndex >= allChapterIds.size) return
        saveScrollPosition()
        loadChapter(allChapterIds[newIndex])
        binding.scrollView.scrollTo(0, 0)
    }

    private fun saveScrollPosition() {
        val scrollY  = binding.scrollView.scrollY
        val chapId   = chapterId
        lifecycleScope.launch(Dispatchers.IO) {
            (application as LocalWriterApp).database.chapterDao()
                .updateCursorPos(chapId, scrollY)
        }
    }

    // ─────────────────── 书签 ───────────────────

    /** 将当前滚动位置保存为书签，并显示书签指示器 */
    private fun saveBookmark() {
        val scrollY = binding.scrollView.scrollY
        if (scrollY <= 0) return
        getSharedPreferences(PREFS_READER, MODE_PRIVATE).edit()
            .putInt("$KEY_BOOKMARK_PRE$chapterId", scrollY).apply()
        binding.ivBookmarkIndicator.visibility = View.VISIBLE
    }

    /** 根据 SharedPreferences 更新书签指示器可见性 */
    private fun updateBookmarkIndicator() {
        val saved = getSharedPreferences(PREFS_READER, MODE_PRIVATE)
            .getInt("$KEY_BOOKMARK_PRE$chapterId", -1)
        binding.ivBookmarkIndicator.visibility =
            if (saved > 0) View.VISIBLE else View.GONE
    }

    /** 点击书签图标后跳回书签位置 */
    private fun jumpToBookmark() {
        val scrollY = getSharedPreferences(PREFS_READER, MODE_PRIVATE)
            .getInt("$KEY_BOOKMARK_PRE$chapterId", -1)
        if (scrollY > 0) {
            binding.scrollView.smoothScrollTo(0, scrollY)
            Toast.makeText(this, "已跳转到书签位置", Toast.LENGTH_SHORT).show()
        }
    }

    // ─────────────────── UI 辅助 ───────────────────

    private fun applyFontSize() {
        binding.tvContent.textSize = currentFontSize
    }

    /** H4: 应用 UserSettings 的颜色主题与默认字号 */
    private suspend fun applyUserSettings() {
        val userId = SessionManager.getUserId(this)
        val settings = withContext(Dispatchers.IO) {
            (application as LocalWriterApp).settingsRepository.getSettings(userId)
        } ?: return
        binding.tvContent.setTextColor(settings.textColor)
        binding.scrollView.setBackgroundColor(settings.backgroundColor)
        // 仅当用户尚未手动调整字号时，使用 UserSettings 的字号
        if (currentFontSize == 0f) {
            currentFontSize = settings.fontSize.toFloat()
            applyFontSize()
        } else {
            applyFontSize()
        }
    }

    private fun toggleBars() {
        barsVisible = !barsVisible
        val vis = if (barsVisible) View.VISIBLE else View.GONE
        binding.appBarLayout.visibility  = vis
        binding.bottomNavBar.visibility  = vis
    }

    /** 章节目录对话框 */
    private fun showChapterListDialog() {
        if (allChapterIds.isEmpty()) return
        val titlesArray = chapterTitles.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("章节目录")
            .setSingleChoiceItems(titlesArray, currentIndex) { dialog, which ->
                dialog.dismiss()
                if (which != currentIndex) {
                    saveScrollPosition()
                    loadChapter(allChapterIds[which])
                    binding.scrollView.scrollTo(0, 0)
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    /** 跳转到编辑器编辑当前章节 */
    private fun openEditor() {
        val intent = Intent(this, EditorActivity::class.java).apply {
            putExtra(EditorActivity.EXTRA_CHAPTER_ID, chapterId)
            putExtra(EditorActivity.EXTRA_BOOK_ID, bookId)
        }
        startActivity(intent)
    }
}
