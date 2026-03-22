package com.localwriter.ui.reader

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.MaterialColors
import com.localwriter.LocalWriterApp
import com.localwriter.R
import com.localwriter.databinding.ActivityReaderBinding
import com.localwriter.databinding.ItemTocChapterBinding
import com.localwriter.databinding.LayoutTocBottomSheetBinding
import com.localwriter.ui.auth.AuthActivity
import com.localwriter.ui.editor.EditorActivity
import com.localwriter.utils.SessionManager
import com.localwriter.utils.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 阅读器（番茄小说/掌阅方案）
 *
 * 核心不变量：
 *  - PagedTextView 始终占满全屏，contentPaddingTop/Bottom 由 WindowInsets 一次性设定，之后永不改变
 *  - 工具栏、信息栏均为同尺寸覆盖层，切换时只改 visibility，PagedTextView 尺寸/分页完全不受影响
 *  - 系统栏在 onCreate 一次性隐藏，阅读全程不再触碰
 */
class ReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReaderBinding
    private lateinit var insetsController: WindowInsetsControllerCompat

    //  固定值，从 WindowInsets 计算一次后不再改变 
    private var contentPaddingTop    = 0
    private var contentPaddingBottom = 0
    private var insetsApplied        = false

    //  章节数据 
    private var chapterId: Long = 0
    private var bookId:    Long = 0
    private var currentIndex: Int = -1
    private var allChapterIds:    List<Long>   = emptyList()
    private var chapterTitles:    List<String> = emptyList()
    private var chapterWordCounts: List<Int>   = emptyList()
    private var bookTitle: String = ""

    //  阅读偏好 
    private var currentFontSize      = 18f
    private val fontSizeStep = 2f
    private val fontSizeMin  = 14f
    private val fontSizeMax  = 28f
    private var currentSpacingIdx    = 1
    private var currentFontFamilyIdx = 0
    private var nightModeActive   = false
    private var activeBgColorIdx  = -1

    //  控制栏状态 
    private var barsVisible = false
    private var activePanel = 0

    //  自动隐藏 
    private val autoHideHandler = Handler(Looper.getMainLooper())
    private val autoHideRunnable = Runnable { if (activePanel == 0) hideBars() }
    private val AUTO_HIDE_DELAY  = 4_000L
    private val ANIM_DURATION    = 200L

    //  时间刷新 
    private val timeHandler  = Handler(Looper.getMainLooper())
    private val timeRunnable = object : Runnable {
        override fun run() {
            timeHandler.postDelayed(this, 60_000L)
        }
    }

    companion object {
        const val EXTRA_CHAPTER_ID = "reader_chapter_id"
        const val EXTRA_BOOK_ID    = "reader_book_id"
        private const val PREFS_READER      = "reader_prefs"
        private const val KEY_FONT_SIZE     = "font_size"
        private const val KEY_BOOKMARK_PRE  = "bookmark_ch_"
        private const val KEY_SPACING       = "line_spacing"
        private const val KEY_NIGHT_MODE    = "night_mode"
        private const val KEY_BG_COLOR_IDX  = "bg_color_idx"
        private const val KEY_FONT_FAMILY   = "font_family"
        private const val INFO_TOP_DP  = 44f
        private const val INFO_BOT_DP  = 8f
        private val SPACINGS = floatArrayOf(1.2f, 1.55f, 1.85f, 2.2f)
        private val BG_COLORS = intArrayOf(
            0xFFFFFFFF.toInt(), 0xFFF8F3E3.toInt(), 0xFFEEE9DE.toInt(),
            0xFFDDE8CC.toInt(), 0xFFF5E8C0.toInt(), 0xFF1A1A2E.toInt(), 0xFF111111.toInt()
        )
        private val TEXT_COLORS = intArrayOf(
            0xFF333333.toInt(), 0xFF3A3226.toInt(), 0xFF3A3226.toInt(),
            0xFF2E3D1A.toInt(), 0xFF3B2A0A.toInt(), 0xFFCCCCCC.toInt(), 0xFFBBBBBB.toInt()
        )
    }

    //  生命周期 

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        chapterId = intent.getLongExtra(EXTRA_CHAPTER_ID, 0)
        bookId    = intent.getLongExtra(EXTRA_BOOK_ID, 0)
        if (chapterId == 0L || bookId == 0L) { finish(); return }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes = window.attributes.also {
                it.layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.readerRoot) { _, insets ->
            if (!insetsApplied) {
                insetsApplied = true
                val density = resources.displayMetrics.density
                val statusH = maxOf(
                    insets.getInsets(WindowInsetsCompat.Type.statusBars()).top,
                    insets.getInsets(WindowInsetsCompat.Type.displayCutout()).top,
                    (24 * density).toInt()
                )
                val navH = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                contentPaddingTop    = statusH + (INFO_TOP_DP * density + 0.5f).toInt()
                contentPaddingBottom = navH    + (INFO_BOT_DP * density + 0.5f).toInt()
                binding.pagedTextView.setContentPadding(contentPaddingTop, contentPaddingBottom)

                binding.toolbarPanel.layoutParams.height = contentPaddingTop
                binding.toolbarPanel.setPadding((4 * density).toInt(), statusH, (4 * density).toInt(), 0)
                binding.toolbarPanel.requestLayout()

                showImmersiveBars()
            }
            insets
        }

        val prefs = getSharedPreferences(PREFS_READER, MODE_PRIVATE)
        currentFontSize      = prefs.getFloat(KEY_FONT_SIZE, 18f)
        currentSpacingIdx    = prefs.getInt(KEY_SPACING, 1)
        nightModeActive      = prefs.getBoolean(KEY_NIGHT_MODE, false)
        activeBgColorIdx     = prefs.getInt(KEY_BG_COLOR_IDX, -1)
        currentFontFamilyIdx = prefs.getInt(KEY_FONT_FAMILY, 0)

        setupControls()
        setupPagedView()
        loadBookChapters()
        timeHandler.postDelayed(timeRunnable, 60_000L)
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
            .putInt(KEY_FONT_FAMILY, currentFontFamilyIdx)
            .apply()
        savePagePosition()
    }

    override fun onDestroy() {
        super.onDestroy()
        autoHideHandler.removeCallbacks(autoHideRunnable)
        timeHandler.removeCallbacks(timeRunnable)
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        return when (keyCode) {
            android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> { binding.pagedTextView.flipPage(1);  true }
            android.view.KeyEvent.KEYCODE_VOLUME_UP   -> { binding.pagedTextView.flipPage(-1); true }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent?): Boolean =
        when (keyCode) {
            android.view.KeyEvent.KEYCODE_VOLUME_DOWN,
            android.view.KeyEvent.KEYCODE_VOLUME_UP -> true
            else -> super.onKeyUp(keyCode, event)
        }

    //  PagedTextView 设置 

    private fun setupPagedView() {
        binding.pagedTextView.onCenterTap    = { toggleBars() }
        binding.pagedTextView.onPrevChapter  = { navigateChapter(-1) }
        binding.pagedTextView.onNextChapter  = { navigateChapter(1) }
        binding.pagedTextView.onPageChanged  = { _, _ -> updateBookmarkIndicator() }
    }

    //  沉浸/控制 切换 

    private fun toggleBars() { if (barsVisible) hideBars() else showBars() }

    private fun hideBars() {
        barsVisible = false
        autoHideHandler.removeCallbacks(autoHideRunnable)
        binding.toolbarPanel.animate().alpha(0f).setDuration(ANIM_DURATION)
            .withEndAction { binding.toolbarPanel.visibility = View.GONE }.start()
        binding.bottomControlOverlay.animate().alpha(0f).setDuration(ANIM_DURATION)
            .withEndAction { binding.bottomControlOverlay.visibility = View.GONE; hideAllPanels() }.start()
        updateBookmarkIndicator()
    }

    private fun showBars() {
        barsVisible = true
        binding.toolbarPanel.alpha = 0f; binding.toolbarPanel.visibility = View.VISIBLE
        binding.toolbarPanel.animate().alpha(1f).setDuration(ANIM_DURATION).start()
        binding.bottomControlOverlay.alpha = 0f; binding.bottomControlOverlay.visibility = View.VISIBLE
        binding.bottomControlOverlay.animate().alpha(1f).setDuration(ANIM_DURATION).start()
        updateFontSizeDisplay(); updateBookmarkIndicator(); resetAutoHideTimer()
    }

    private fun showImmersiveBars() {
        binding.toolbarPanel.visibility = View.GONE
        binding.bottomControlOverlay.visibility = View.GONE
        hideAllPanels()
    }

    //  子面板 

    private fun togglePanel(id: Int) {
        val np = if (activePanel == id) 0 else id; activePanel = np
        fun anim(v: View, show: Boolean) {
            if (show) { v.alpha = 0f; v.visibility = View.VISIBLE; v.animate().alpha(1f).setDuration(120L).start() }
            else if (v.visibility == View.VISIBLE) v.animate().alpha(0f).setDuration(100L)
                .withEndAction { v.visibility = View.GONE; v.alpha = 1f }.start()
        }
        anim(binding.panelSettings, np == 1); anim(binding.panelBrightness, np == 2)
        if (np == 0) resetAutoHideTimer() else autoHideHandler.removeCallbacks(autoHideRunnable)
    }

    private fun hideAllPanels() {
        activePanel = 0
        binding.panelSettings.visibility = View.GONE; binding.panelBrightness.visibility = View.GONE
    }

    //  控制面板 UI 

    private fun setupControls() {
        binding.ibBack.setOnClickListener { finish() }
        binding.ibEditChapter.setOnClickListener { openEditor() }
        binding.ibChapterList.setOnClickListener { showChapterListDialog() }
        binding.ivBookmarkIndicator.setOnClickListener { toggleBookmark() }

        binding.btnFontDec.setOnClickListener {
            if (currentFontSize > fontSizeMin) { currentFontSize -= fontSizeStep; applyTextConfig() }
        }
        binding.btnFontInc.setOnClickListener {
            if (currentFontSize < fontSizeMax) { currentFontSize += fontSizeStep; applyTextConfig() }
        }
        binding.btnSpacing1.setOnClickListener { setSpacing(0) }
        binding.btnSpacing2.setOnClickListener { setSpacing(1) }
        binding.btnSpacing3.setOnClickListener { setSpacing(2) }
        binding.btnSpacing4.setOnClickListener { setSpacing(3) }

        listOf(binding.vBgWhite, binding.vBgCream, binding.vBgWarm,
               binding.vBgGreen, binding.vBgWarmYellow, binding.vBgNight, binding.vBgBlack)
            .forEachIndexed { i, v ->
                v.setOnClickListener {
                    activeBgColorIdx = i; nightModeActive = (i >= 5)
                    applyBgAndText(); updateBgCircles(); updateNightButton()
                }
            }

        val lp = window.attributes
        binding.sbBrightness.progress = if (lp.screenBrightness < 0f) 50
            else (lp.screenBrightness * 100).toInt().coerceIn(1, 100)
        binding.sbBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                window.attributes = window.attributes.also { it.screenBrightness = p.coerceAtLeast(1) / 100f }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        binding.ibNavToc.setOnClickListener { hideAllPanels(); showBars(); showChapterListDialog() }
        binding.ibNavBrightness.setOnClickListener { togglePanel(2) }
        binding.ibNavNight.setOnClickListener { toggleNightMode() }
        binding.ibNavSettings.setOnClickListener { togglePanel(1) }

        binding.btnFontFamily1.setOnClickListener { setFontFamily(0) }
        binding.btnFontFamily2.setOnClickListener { setFontFamily(1) }
        binding.btnFontFamily3.setOnClickListener { setFontFamily(2) }
        binding.btnFontFamily4.setOnClickListener { setFontFamily(3) }

        updateSpacingButtonStates(); updateBgCircles(); updateNightButton(); updateFontSizeDisplay()
        updateFontFamilyButtons()
    }

    //  文字主题 

    private fun applyTextConfig() {
        val px = currentFontSize * resources.displayMetrics.density
        binding.pagedTextView.setTypeface(getReaderTypeface(currentFontFamilyIdx))
        binding.pagedTextView.setTextConfig(px, SPACINGS[currentSpacingIdx], currentTextColor())
        updateFontSizeDisplay()
    }

    private fun setSpacing(idx: Int) { currentSpacingIdx = idx; updateSpacingButtonStates(); applyTextConfig() }

    private fun setFontFamily(idx: Int) { currentFontFamilyIdx = idx; updateFontFamilyButtons(); applyTextConfig() }

    private fun getReaderTypeface(idx: Int): android.graphics.Typeface = when (idx) {
        1    -> android.graphics.Typeface.SERIF          // 宋体
        2    -> android.graphics.Typeface.create("cursive", android.graphics.Typeface.NORMAL)  // 楷体
        3    -> android.graphics.Typeface.SANS_SERIF     // 黑体
        else -> android.graphics.Typeface.DEFAULT        // 默认
    }

    private fun updateFontFamilyButtons() {
        val on = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer, Color.LTGRAY)
        listOf(binding.btnFontFamily1, binding.btnFontFamily2, binding.btnFontFamily3, binding.btnFontFamily4)
            .forEachIndexed { i, btn ->
                btn.backgroundTintList = if (i == currentFontFamilyIdx) ColorStateList.valueOf(on) else ColorStateList.valueOf(Color.TRANSPARENT)
            }
    }

    private fun applyBgAndText() {
        val bg = currentBgColor(); val tc = currentTextColor()
        binding.pagedTextView.setBgColor(bg)
        binding.pagedTextView.setTypeface(getReaderTypeface(currentFontFamilyIdx))
        binding.pagedTextView.setTextConfig(currentFontSize * resources.displayMetrics.density, SPACINGS[currentSpacingIdx], tc)
        applyControlPanelTheme(bg, tc)
    }

    private fun currentBgColor()   = if (activeBgColorIdx in BG_COLORS.indices)   BG_COLORS[activeBgColorIdx]   else 0xFFF8F3E3.toInt()
    private fun currentTextColor() = if (activeBgColorIdx in TEXT_COLORS.indices) TEXT_COLORS[activeBgColorIdx] else 0xFF3A3226.toInt()

    private fun toggleNightMode() {
        nightModeActive = !nightModeActive; activeBgColorIdx = if (nightModeActive) 5 else -1
        applyBgAndText()
        if (nightModeActive) {
            val attr = window.attributes
            if (attr.screenBrightness < 0 || attr.screenBrightness > 0.4f) {
                attr.screenBrightness = 0.3f; window.attributes = attr; binding.sbBrightness.progress = 30
            }
        }
        updateBgCircles(); updateNightButton()
    }

    private fun applyControlPanelTheme(bgColor: Int, textColor: Int) {
        val lum = Color.luminance(bgColor); val scrim = if (lum > 0.3f) 0.07f else 0.09f
        val ov = if (lum > 0.3f) Color.BLACK else Color.WHITE
        binding.bottomControlOverlay.setBackgroundColor(Color.rgb(
            (Color.red(bgColor)   * (1 - scrim) + Color.red(ov)   * scrim).toInt(),
            (Color.green(bgColor) * (1 - scrim) + Color.green(ov) * scrim).toInt(),
            (Color.blue(bgColor)  * (1 - scrim) + Color.blue(ov)  * scrim).toInt()
        ))
        val tint = ColorStateList.valueOf(textColor)
        val sec  = Color.argb((255 * 0.6f).toInt(), Color.red(textColor), Color.green(textColor), Color.blue(textColor))
        listOf(binding.ibNavToc, binding.ibNavBrightness, binding.ibNavNight, binding.ibNavSettings)
            .forEach { c -> for (i in 0 until c.childCount) when (val v = c.getChildAt(i)) {
                is ImageView -> v.imageTintList = tint; is TextView -> v.setTextColor(textColor)
            }}
        binding.tvChapterProgress.setTextColor(sec)
        val tc2 = if (lum > 0.3f) 0xFF333333.toInt() else 0xFFCCCCCC.toInt()
        binding.tvChapterTitle.setTextColor(tc2)
        binding.ibBack.imageTintList = ColorStateList.valueOf(tc2)
        binding.ibEditChapter.imageTintList = ColorStateList.valueOf(tc2)
        binding.ibChapterList.imageTintList = ColorStateList.valueOf(tc2)
        binding.toolbarPanel.setBackgroundColor(bgColor)
    }

    private fun updateBgCircles() {
        val primary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, Color.GRAY)
        val density = resources.displayMetrics.density
        val r = (7 * density + 0.5f).toInt()
        listOf(binding.vBgWhite, binding.vBgCream, binding.vBgWarm,
               binding.vBgGreen, binding.vBgWarmYellow, binding.vBgNight, binding.vBgBlack)
            .forEachIndexed { i, v ->
                val isActive = i == activeBgColorIdx
                v.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = r.toFloat()
                    setColor(BG_COLORS[i])
                    setStroke(
                        if (isActive) (3 * density + 0.5f).toInt() else (1 * density + 0.5f).toInt(),
                        if (isActive) primary else 0xFFCCCCCC.toInt()
                    )
                }
            }
    }

    private fun updateNightButton() { binding.tvNightLabel.text = if (nightModeActive) "白天" else "夜间" }

    private fun updateSpacingButtonStates() {
        val on = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer, Color.LTGRAY)
        listOf(binding.btnSpacing1, binding.btnSpacing2, binding.btnSpacing3, binding.btnSpacing4)
            .forEachIndexed { i, btn ->
                btn.backgroundTintList = if (i == currentSpacingIdx) ColorStateList.valueOf(on) else ColorStateList.valueOf(Color.TRANSPARENT)
            }
    }

    private fun updateFontSizeDisplay() { binding.tvFontSizeVal.text = currentFontSize.toInt().toString() }

    private fun readBatteryPct(): Int {
        val i = registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else 0
    }

    private fun resetAutoHideTimer() {
        autoHideHandler.removeCallbacks(autoHideRunnable)
        if (activePanel == 0) autoHideHandler.postDelayed(autoHideRunnable, AUTO_HIDE_DELAY)
    }

    //  数据加载 

    private fun loadBookChapters() {
        lifecycleScope.launch {
            val db = (application as LocalWriterApp).database
            val ids = mutableListOf<Long>(); val titles = mutableListOf<String>(); val wcs = mutableListOf<Int>()
            var bTitle = ""
            withContext(Dispatchers.IO) {
                val book = db.bookDao().findById(bookId); bTitle = book?.title ?: ""
                val volumes = db.volumeDao().getAllByBook(bookId).sortedBy { it.sortOrder }
                for (vol in volumes) { val chapters = db.chapterDao().getPreviewsByVolume(vol.id)
                    for (ch in chapters) { ids.add(ch.id); titles.add(ch.title); wcs.add(ch.wordCount) } }
            }
            bookTitle = bTitle; allChapterIds = ids; chapterTitles = titles; chapterWordCounts = wcs
            currentIndex = ids.indexOf(chapterId).coerceAtLeast(0)
            applyUserSettings()
            loadChapter(chapterId)
        }
    }

    private fun loadChapter(chapId: Long, startAtEnd: Boolean = false) {
        lifecycleScope.launch {
            val db = (application as LocalWriterApp).database
            val chapter = withContext(Dispatchers.IO) { db.chapterDao().findById(chapId) }
                ?: run { Toast.makeText(this@ReaderActivity, "章节不存在", Toast.LENGTH_SHORT).show(); finish(); return@launch }
            chapterId = chapId; currentIndex = allChapterIds.indexOf(chapId).coerceAtLeast(0)
            binding.tvChapterTitle.text = chapter.title
            binding.tvChapterProgress.text = "${currentIndex + 1} / ${allChapterIds.size} 章"
            val startPage = if (startAtEnd) 0 else chapter.lastScrollPos.coerceAtLeast(0)
            binding.pagedTextView.loadChapter(chapter.title, chapter.content.ifEmpty { "（本章暂无内容）" }, startPage)
            if (startAtEnd) binding.pagedTextView.post { binding.pagedTextView.goToLastPage() }
            updateBookmarkIndicator()
            withContext(Dispatchers.IO) { (application as LocalWriterApp).bookRepository.updateLastChapter(bookId, chapId) }
        }
    }

    private suspend fun applyUserSettings() {
        val userId = SessionManager.getUserId(this)
        val settings = withContext(Dispatchers.IO) { (application as LocalWriterApp).settingsRepository.getSettings(userId) }
        if (activeBgColorIdx < 0 && !nightModeActive && settings != null) {
            binding.pagedTextView.setBgColor(settings.backgroundColor)
            binding.pagedTextView.setTextConfig(currentFontSize * resources.displayMetrics.density, SPACINGS[currentSpacingIdx], settings.textColor)
            applyControlPanelTheme(settings.backgroundColor, settings.textColor)
        } else { applyBgAndText() }
        if (settings != null && currentFontSize == 18f) currentFontSize = settings.fontSize.toFloat().coerceIn(fontSizeMin, fontSizeMax)
        applyTextConfig(); updateFontSizeDisplay(); updateSpacingButtonStates(); updateFontFamilyButtons(); updateBgCircles(); updateNightButton()
    }

    //  章节导航 

    private fun navigateChapter(direction: Int) {
        val newIndex = currentIndex + direction
        if (newIndex < 0 || newIndex >= allChapterIds.size) {
            Toast.makeText(this, if (direction < 0) "已是第一章" else "已是最后一章", Toast.LENGTH_SHORT).show(); return
        }
        savePagePosition(); loadChapter(allChapterIds[newIndex], startAtEnd = direction < 0)
    }

    private fun savePagePosition() {
        val page = binding.pagedTextView.currentPage; val chapId = chapterId
        lifecycleScope.launch(Dispatchers.IO) { (application as LocalWriterApp).database.chapterDao().updateScrollPos(chapId, page) }
    }

    //  书签 

    private fun toggleBookmark() {
        val page = binding.pagedTextView.currentPage
        val prefs = getSharedPreferences(PREFS_READER, MODE_PRIVATE)
        val saved = prefs.getInt("$KEY_BOOKMARK_PRE$chapterId", -1)
        if (saved >= 0 && Math.abs(page - saved) <= 1) {
            prefs.edit().remove("$KEY_BOOKMARK_PRE$chapterId").apply()
            Toast.makeText(this, "已删除书签", Toast.LENGTH_SHORT).show()
        } else {
            prefs.edit().putInt("$KEY_BOOKMARK_PRE$chapterId", page).apply()
            Toast.makeText(this, "已添加书签", Toast.LENGTH_SHORT).show()
        }
        updateBookmarkIndicator()
    }

    private fun updateBookmarkIndicator() {
        val page = binding.pagedTextView.currentPage
        val saved = getSharedPreferences(PREFS_READER, MODE_PRIVATE).getInt("$KEY_BOOKMARK_PRE$chapterId", -1)
        val onBm = saved >= 0 && Math.abs(page - saved) <= 1
        binding.ivBookmarkIndicator.apply {
            when {
                onBm        -> { imageTintList = ColorStateList.valueOf(0xFFE53935.toInt()); visibility = View.VISIBLE }
                barsVisible -> { imageTintList = ColorStateList.valueOf(0x99888888.toInt()); visibility = View.VISIBLE }
                else        -> visibility = View.GONE
            }
        }
    }

    //  目录对话框 

    private fun showChapterListDialog() {
        if (allChapterIds.isEmpty()) return
        val dialog = BottomSheetDialog(this)
        val sb = LayoutTocBottomSheetBinding.inflate(layoutInflater)
        dialog.setContentView(sb.root)
        val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            inner class VH(val b: ItemTocChapterBinding) : RecyclerView.ViewHolder(b.root)
            override fun onCreateViewHolder(p: android.view.ViewGroup, t: Int) = VH(ItemTocChapterBinding.inflate(layoutInflater, p, false))
            override fun getItemCount() = chapterTitles.size
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, pos: Int) {
                val b = (holder as VH).b
                b.tvTocChapterTitle.text = chapterTitles[pos]
                b.tvTocChapterTitle.setTextColor(
                    if (pos == currentIndex) MaterialColors.getColor(b.root, com.google.android.material.R.attr.colorPrimary)
                    else MaterialColors.getColor(b.root, android.R.attr.textColorPrimary)
                )
                b.root.setOnClickListener { savePagePosition(); loadChapter(allChapterIds[pos]); dialog.dismiss(); if (barsVisible) hideBars() }
            }
        }
        sb.rvTocChapters.layoutManager = LinearLayoutManager(this)
        sb.rvTocChapters.adapter = adapter
        sb.rvTocChapters.scrollToPosition(currentIndex)
        dialog.show()
    }

    private fun openEditor() {
        startActivity(Intent(this, EditorActivity::class.java).apply { putExtra(EditorActivity.EXTRA_CHAPTER_ID, chapterId) })
    }
}