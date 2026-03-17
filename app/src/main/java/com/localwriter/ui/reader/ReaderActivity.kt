package com.localwriter.ui.reader

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.MenuItem
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.MaterialColors
import com.localwriter.LocalWriterApp
import com.localwriter.R
import com.localwriter.databinding.ActivityReaderBinding
import com.localwriter.ui.auth.AuthActivity
import com.localwriter.ui.editor.EditorActivity
import com.localwriter.utils.SessionManager
import com.localwriter.utils.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 阅读器界面（只读模式）
 *
 * 功能：
 * - 点击正文进入/退出沉浸模式（隐藏系统状态栏、导航栏及控制面板）
 * - 底部控制面板：目录、亮度、夜间模式、设置（字号/行距/背景色）
 * - 章节上下翻页 + 进度显示
 * - 书签自动保存（滚动防抖 800ms）
 */
class ReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReaderBinding
    private lateinit var insetsController: WindowInsetsControllerCompat

    private var chapterId: Long = 0
    private var bookId: Long = 0
    private var currentIndex: Int = -1

    private var allChapterIds: List<Long> = emptyList()
    private var chapterTitles: List<String> = emptyList()

    private var currentFontSize: Float = 18f
    private val fontSizeStep = 2f
    private val fontSizeMin = 14f
    private val fontSizeMax = 28f

    /** true = 控制面板可见（非沉浸模式） */
    private var barsVisible = false

    /** 0=无子面板, 1=设置面板, 2=亮度面板 */
    private var activePanel = 0

    private var currentSpacingIdx: Int = 2  // 默认"宽"(1.85x)
    private var nightModeActive: Boolean = false
    private var activeBgColorIdx: Int = -1  // -1=跟随用户设置

    private val bookmarkHandler = Handler(Looper.getMainLooper())
    private val bookmarkRunnable = Runnable { saveBookmark() }

    /** 翻页模式：0=滚动（默认），1=翻页（横向滑动切章） */
    private var pageMode: Int = 0
    private lateinit var flipGestureDetector: android.view.GestureDetector

    companion object {
        const val EXTRA_CHAPTER_ID = "reader_chapter_id"
        const val EXTRA_BOOK_ID    = "reader_book_id"

        private const val PREFS_READER     = "reader_prefs"
        private const val KEY_FONT_SIZE    = "font_size"
        private const val KEY_BOOKMARK_PRE = "bookmark_ch_"
        private const val KEY_SPACING      = "line_spacing"
        private const val KEY_NIGHT_MODE   = "night_mode"
        private const val KEY_BG_COLOR_IDX = "bg_color_idx"

        private val SPACINGS = floatArrayOf(1.2f, 1.55f, 1.85f, 2.2f)

        /** 背景色预设 (白/米黄/暖灰/豆绿/夜黑) */
        private val BG_COLORS = intArrayOf(
            0xFFFFFFFF.toInt(),
            0xFFF8F3E3.toInt(),
            0xFFEEE9DE.toInt(),
            0xFFDDE8CC.toInt(),
            0xFF1A1A2E.toInt()
        )
        private val TEXT_COLORS = intArrayOf(
            0xFF333333.toInt(),
            0xFF3A3226.toInt(),
            0xFF3A3226.toInt(),
            0xFF2E3D1A.toInt(),
            0xFFCCCCCC.toInt()
        )
    }

    // ─────────────────── 生命周期 ───────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        chapterId = intent.getLongExtra(EXTRA_CHAPTER_ID, 0)
        bookId    = intent.getLongExtra(EXTRA_BOOK_ID, 0)
        if (chapterId == 0L || bookId == 0L) { finish(); return }

        // 沉浸模式设置
        WindowCompat.setDecorFitsSystemWindows(window, false)
        insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        // 恢复持久化偏好
        val prefs = getSharedPreferences(PREFS_READER, MODE_PRIVATE)
        currentFontSize   = prefs.getFloat(KEY_FONT_SIZE, 0f)
        currentSpacingIdx = prefs.getInt(KEY_SPACING, 2)
        nightModeActive   = prefs.getBoolean(KEY_NIGHT_MODE, false)
        activeBgColorIdx  = prefs.getInt(KEY_BG_COLOR_IDX, -1)

        // 章节导航
        binding.btnPrevChapter.setOnClickListener { navigateChapter(-1) }
        binding.btnNextChapter.setOnClickListener { navigateChapter(+1) }

        // 点击正文切换沉浸模式
        binding.scrollView.setOnClickListener { toggleBars() }
        binding.tvContent.setOnClickListener  { toggleBars() }

        // 书签
        binding.ivBookmarkIndicator.setOnClickListener { jumpToBookmark() }
        binding.scrollView.viewTreeObserver.addOnScrollChangedListener {
            val scrollY = binding.scrollView.scrollY
            if (scrollY > 0) {
                bookmarkHandler.removeCallbacks(bookmarkRunnable)
                bookmarkHandler.postDelayed(bookmarkRunnable, 800)
            }
        }

        setupBottomControls()

        // 默认进入沉浸模式
        hideBars()
        loadBookChapters()
    }

    override fun onResume() {
        super.onResume()
        if (SessionManager.isLoggedIn(this) && SessionManager.isLocked(this)) {
            startActivity(Intent(this, AuthActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        getSharedPreferences(PREFS_READER, MODE_PRIVATE).edit()
            .putFloat(KEY_FONT_SIZE, currentFontSize)
            .putInt(KEY_SPACING, currentSpacingIdx)
            .putBoolean(KEY_NIGHT_MODE, nightModeActive)
            .putInt(KEY_BG_COLOR_IDX, activeBgColorIdx)
            .apply()
        saveScrollPosition()
        bookmarkHandler.removeCallbacks(bookmarkRunnable)
        saveBookmark()
    }

    override fun onDestroy() {
        super.onDestroy()
        bookmarkHandler.removeCallbacks(bookmarkRunnable)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_chapter_list -> { showChapterListDialog(); true }
            R.id.action_edit_chapter -> { openEditor(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ─────────────────── 底部控制面板 ───────────────────

    private fun setupBottomControls() {
        // 字号
        binding.btnFontDec.setOnClickListener {
            if (currentFontSize > fontSizeMin) {
                currentFontSize -= fontSizeStep
                applyFontSize()
                updateFontSizeDisplay()
            }
        }
        binding.btnFontInc.setOnClickListener {
            if (currentFontSize < fontSizeMax) {
                currentFontSize += fontSizeStep
                applyFontSize()
                updateFontSizeDisplay()
            }
        }

        // 行距
        binding.btnSpacing1.setOnClickListener { setSpacing(0) }
        binding.btnSpacing2.setOnClickListener { setSpacing(1) }
        binding.btnSpacing3.setOnClickListener { setSpacing(2) }
        binding.btnSpacing4.setOnClickListener { setSpacing(3) }

        // 背景色圆圈
        val bgViews = listOf(
            binding.vBgWhite, binding.vBgCream, binding.vBgWarm,
            binding.vBgGreen, binding.vBgDark
        )
        bgViews.forEachIndexed { i, v ->
            v.setOnClickListener {
                activeBgColorIdx = i
                nightModeActive = (i == 4)
                applyBgAndText()
                updateBgCircles()
                updateNightButton()
            }
        }

        // 亮度 SeekBar（调整窗口亮度，无需权限）
        val lp = window.attributes
        val initBrightness = if (lp.screenBrightness < 0f) 50
                             else (lp.screenBrightness * 100).toInt().coerceIn(1, 100)
        binding.sbBrightness.progress = initBrightness
        binding.sbBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val attr = window.attributes
                    attr.screenBrightness = progress.coerceAtLeast(1) / 100f
                    window.attributes = attr
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // 底部图标导航
        binding.ibNavToc.setOnClickListener {
            hideAllPanels()
            showBars()   // 确保可见
            showChapterListDialog()
        }
        binding.ibNavBrightness.setOnClickListener { togglePanel(2) }
        binding.ibNavNight.setOnClickListener { toggleNightMode() }
        binding.ibNavSettings.setOnClickListener { togglePanel(1) }

        // 翻页模式
        binding.btnPageScroll.setOnClickListener { setPageMode(0) }
        binding.btnPageFlip.setOnClickListener   { setPageMode(1) }
        updatePageModeButtons()

        // 翻页手势检测（翻页模式下横向滑动切章）
        flipGestureDetector = android.view.GestureDetector(
            this,
            object : android.view.GestureDetector.SimpleOnGestureListener() {
                private val SWIPE_MIN_DISTANCE = 80
                private val SWIPE_MIN_VELOCITY = 100
                override fun onSingleTapUp(e: android.view.MotionEvent): Boolean {
                    toggleBars()
                    return true
                }
                override fun onFling(
                    e1: android.view.MotionEvent?,
                    e2: android.view.MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    if (pageMode != 1 || e1 == null) return false
                    val dx = e2.x - e1.x
                    val dy = e2.y - e1.y
                    if (Math.abs(dx) > Math.abs(dy) &&
                        Math.abs(dx) > SWIPE_MIN_DISTANCE &&
                        Math.abs(velocityX) > SWIPE_MIN_VELOCITY) {
                        navigateChapter(if (dx < 0) 1 else -1)
                        return true
                    }
                    return false
                }
            }
        )
        // 将手势检测附加到 scrollView 的触摸事件
        binding.scrollView.setOnTouchListener { v, event ->
            val consumed = flipGestureDetector.onTouchEvent(event)
            if (!consumed) v.onTouchEvent(event) else true
        }

        // 初始化显示状态
        updateSpacingButtonStates()
        updateBgCircles()
        updateNightButton()
    }

    // ─────────────────── 沉浸模式切换 ───────────────────

    private fun toggleBars() {
        if (barsVisible) hideBars() else showBars()
    }

    private fun showBars() {
        barsVisible = true
        insetsController.show(WindowInsetsCompat.Type.systemBars())
        binding.appBarLayout.visibility         = View.VISIBLE
        binding.bottomControlOverlay.visibility = View.VISIBLE
        // 恢复 ScrollView 顶部偏移，等待 AppBarLayout 完成布局后取其高度
        binding.appBarLayout.post {
            binding.scrollView.updateLayoutParams<androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams> {
                topMargin = binding.appBarLayout.height
            }
        }
        updateFontSizeDisplay()
    }

    private fun hideBars() {
        barsVisible = false
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        binding.appBarLayout.visibility         = View.GONE
        binding.bottomControlOverlay.visibility = View.GONE
        // 沉浸模式：ScrollView 填满全屏，topMargin=0
        binding.scrollView.updateLayoutParams<androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams> {
            topMargin = 0
        }
        hideAllPanels()
    }

    // ─────────────────── 子面板切换 ───────────────────

    private fun togglePanel(id: Int) {
        if (activePanel == id) {
            activePanel = 0
            binding.panelSettings.visibility   = View.GONE
            binding.panelBrightness.visibility = View.GONE
        } else {
            activePanel = id
            binding.panelSettings.visibility   = if (id == 1) View.VISIBLE else View.GONE
            binding.panelBrightness.visibility = if (id == 2) View.VISIBLE else View.GONE
        }
    }

    private fun hideAllPanels() {
        activePanel = 0
        binding.panelSettings.visibility   = View.GONE
        binding.panelBrightness.visibility = View.GONE
    }

    // ─────────────────── 行距 ───────────────────

    private fun setSpacing(idx: Int) {
        currentSpacingIdx = idx
        binding.tvContent.setLineSpacing(0f, SPACINGS[idx])
        updateSpacingButtonStates()
    }

    private fun updateSpacingButtonStates() {
        val primary = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorPrimaryContainer, Color.LTGRAY
        )
        val btns = listOf(binding.btnSpacing1, binding.btnSpacing2,
                          binding.btnSpacing3, binding.btnSpacing4)
        btns.forEachIndexed { i, btn ->
            btn.backgroundTintList = if (i == currentSpacingIdx)
                ColorStateList.valueOf(primary) else ColorStateList.valueOf(Color.TRANSPARENT)
        }
    }

    // ─────────────────── 背景色 ───────────────────

    private fun applyBgAndText() {
        val idx = activeBgColorIdx
        if (idx < 0 || idx >= BG_COLORS.size) return
        binding.scrollView.setBackgroundColor(BG_COLORS[idx])
        binding.tvContent.setTextColor(TEXT_COLORS[idx])
        binding.tvChapterTitle.setTextColor(TEXT_COLORS[idx])
    }

    private fun updateBgCircles() {
        val primary = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorPrimary, Color.GRAY
        )
        val views = listOf(binding.vBgWhite, binding.vBgCream, binding.vBgWarm,
                           binding.vBgGreen, binding.vBgDark)
        views.forEachIndexed { i, v ->
            val shape = GradientDrawable()
            shape.shape = GradientDrawable.OVAL
            shape.setColor(BG_COLORS[i])
            shape.setStroke(if (i == activeBgColorIdx) 4 else 1,
                            if (i == activeBgColorIdx) primary else 0xFFBBBBBB.toInt())
            v.background = shape
        }
    }

    // ─────────────────── 夜间模式 ───────────────────

    private fun toggleNightMode() {
        nightModeActive = !nightModeActive
        if (nightModeActive) {
            activeBgColorIdx = 4
            applyNightColors()
        } else {
            activeBgColorIdx = -1
            lifecycleScope.launch { restoreUserThemeColors() }
        }
        updateBgCircles()
        updateNightButton()
    }

    private fun applyNightColors() {
        binding.scrollView.setBackgroundColor(0xFF1A1A2E.toInt())
        binding.tvContent.setTextColor(0xFFCCCCCC.toInt())
        binding.tvChapterTitle.setTextColor(0xFFBBBBBB.toInt())
        val attr = window.attributes
        if (attr.screenBrightness < 0 || attr.screenBrightness > 0.4f) {
            attr.screenBrightness = 0.3f
            window.attributes = attr
            binding.sbBrightness.progress = 30
        }
    }

    private fun updateNightButton() {
        binding.tvNightLabel.text = if (nightModeActive) "白天" else "夜间"
    }

    private fun setPageMode(mode: Int) {
        pageMode = mode
        // 滚动模式恢复 ScrollView 的正常滚动（移除 touch 拦截让默认处理）
        if (mode == 0) {
            binding.scrollView.setOnTouchListener(null)
            binding.scrollView.setOnClickListener { toggleBars() }
        }
        updatePageModeButtons()
    }

    private fun updatePageModeButtons() {
        val colorOn  = com.google.android.material.color.MaterialColors.getColor(
            binding.root, com.google.android.material.R.attr.colorPrimary)
        val colorOff = com.google.android.material.color.MaterialColors.getColor(
            binding.root, com.google.android.material.R.attr.colorOutline)
        val strokeOn  = android.content.res.ColorStateList.valueOf(colorOn)
        val strokeOff = android.content.res.ColorStateList.valueOf(colorOff)
        binding.btnPageScroll.strokeColor = if (pageMode == 0) strokeOn else strokeOff
        binding.btnPageScroll.setTextColor(if (pageMode == 0) colorOn else colorOff)
        binding.btnPageFlip.strokeColor   = if (pageMode == 1) strokeOn else strokeOff
        binding.btnPageFlip.setTextColor(if (pageMode == 1) colorOn else colorOff)
    }

    // ─────────────────── UI 辅助 ───────────────────

    private fun applyFontSize() {
        if (currentFontSize > 0f) binding.tvContent.textSize = currentFontSize
    }

    private fun applyCurrentSpacing() {
        binding.tvContent.setLineSpacing(0f, SPACINGS[currentSpacingIdx])
    }

    private fun updateFontSizeDisplay() {
        if (currentFontSize > 0f)
            binding.tvFontSizeVal.text = currentFontSize.toInt().toString()
    }

    private suspend fun applyUserSettings() {
        val userId = SessionManager.getUserId(this)
        val settings = withContext(Dispatchers.IO) {
            (application as LocalWriterApp).settingsRepository.getSettings(userId)
        } ?: return

        when {
            nightModeActive           -> applyNightColors()
            activeBgColorIdx >= 0     -> applyBgAndText()
            else -> {
                binding.tvContent.setTextColor(settings.textColor)
                binding.scrollView.setBackgroundColor(settings.backgroundColor)
            }
        }
        if (currentFontSize == 0f) currentFontSize = settings.fontSize.toFloat()
        applyFontSize()
        applyCurrentSpacing()
        updateNightButton()
        updateFontSizeDisplay()
        updateSpacingButtonStates()
        updateBgCircles()
    }

    private suspend fun restoreUserThemeColors() {
        val userId = SessionManager.getUserId(this)
        val settings = withContext(Dispatchers.IO) {
            (application as LocalWriterApp).settingsRepository.getSettings(userId)
        } ?: return
        binding.tvContent.setTextColor(settings.textColor)
        binding.tvChapterTitle.setTextColor(
            getColor(android.R.color.primary_text_light))
        binding.scrollView.setBackgroundColor(settings.backgroundColor)
    }

    // ─────────────────── 数据加载 ───────────────────

    private fun loadBookChapters() {
        lifecycleScope.launch {
            val db = (application as LocalWriterApp).database
            val ids    = mutableListOf<Long>()
            val titles = mutableListOf<String>()
            withContext(Dispatchers.IO) {
                val volumes = db.volumeDao().getAllByBook(bookId).sortedBy { it.sortOrder }
                for (vol in volumes) {
                    val chapters = db.chapterDao().getAllByVolume(vol.id)
                        .filter { it.status != "DELETED" }
                        .sortedBy { it.sortOrder }
                    for (ch in chapters) { ids.add(ch.id); titles.add(ch.title) }
                }
            }
            allChapterIds = ids
            chapterTitles = titles
            currentIndex  = ids.indexOf(chapterId).coerceAtLeast(0)
            applyUserSettings()
            loadChapter(chapterId)
        }
    }

    private fun loadChapter(chapId: Long) {
        lifecycleScope.launch {
            val db = (application as LocalWriterApp).database
            val chapter = withContext(Dispatchers.IO) {
                db.chapterDao().findById(chapId)
            } ?: run {
                Toast.makeText(this@ReaderActivity, "章节不存在", Toast.LENGTH_SHORT).show()
                finish(); return@launch
            }

            chapterId    = chapId
            currentIndex = allChapterIds.indexOf(chapId).coerceAtLeast(0)

            supportActionBar?.title = chapter.title
            binding.tvChapterTitle.text = chapter.title
            binding.tvContent.text = chapter.content.ifEmpty { "（本章暂无内容）" }
            applyFontSize()
            applyCurrentSpacing()

            val total = allChapterIds.size
            binding.tvChapterProgress.text = "${currentIndex + 1} / $total 章"
            binding.btnPrevChapter.isEnabled = currentIndex > 0
            binding.btnNextChapter.isEnabled = currentIndex < allChapterIds.size - 1

            val scrollY = chapter.lastCursorPos
            binding.scrollView.post { binding.scrollView.scrollTo(0, scrollY) }
            updateBookmarkIndicator()
            updateFontSizeDisplay()

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
        val scrollY = binding.scrollView.scrollY
        val chapId  = chapterId
        lifecycleScope.launch(Dispatchers.IO) {
            (application as LocalWriterApp).database.chapterDao()
                .updateCursorPos(chapId, scrollY)
        }
    }

    // ─────────────────── 书签 ───────────────────

    private fun saveBookmark() {
        val scrollY = binding.scrollView.scrollY
        if (scrollY <= 0) return
        getSharedPreferences(PREFS_READER, MODE_PRIVATE).edit()
            .putInt("$KEY_BOOKMARK_PRE$chapterId", scrollY).apply()
        binding.ivBookmarkIndicator.visibility = View.VISIBLE
    }

    private fun updateBookmarkIndicator() {
        val saved = getSharedPreferences(PREFS_READER, MODE_PRIVATE)
            .getInt("$KEY_BOOKMARK_PRE$chapterId", -1)
        binding.ivBookmarkIndicator.visibility = if (saved > 0) View.VISIBLE else View.GONE
    }

    private fun jumpToBookmark() {
        val scrollY = getSharedPreferences(PREFS_READER, MODE_PRIVATE)
            .getInt("$KEY_BOOKMARK_PRE$chapterId", -1)
        if (scrollY > 0) {
            binding.scrollView.smoothScrollTo(0, scrollY)
            Toast.makeText(this, "已跳转到书签位置", Toast.LENGTH_SHORT).show()
        }
    }

    // ─────────────────── 目录/编辑 ───────────────────

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

    private fun openEditor() {
        val intent = Intent(this, EditorActivity::class.java).apply {
            putExtra(EditorActivity.EXTRA_CHAPTER_ID, chapterId)
            putExtra(EditorActivity.EXTRA_BOOK_ID, bookId)
        }
        startActivity(intent)
    }
}
