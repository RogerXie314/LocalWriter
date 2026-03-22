package com.localwriter.ui.reader

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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

    /** 系统状态栏/导航栏高度（沉浸模式增加内边距，防止内容躲在刘海/摄像头后面） */
    private var systemStatusBarHeight = 0
    private var systemNavBarHeight    = 0

    private var chapterId: Long = 0
    private var bookId: Long = 0
    private var currentIndex: Int = -1

    private var allChapterIds: List<Long> = emptyList()
    private var chapterTitles: List<String> = emptyList()
    /** 各章节字数，用于全书总页码估算 */
    private var chapterWordCounts: List<Int> = emptyList()
    /** 书名，用于沉浸模式顶部信息栏（避免与正文中的章节标题重复显示） */
    private var bookTitle: String = ""
    /** 行高像素，由 alignPaddingToLines 测量，用于全书页码估算 */
    private var storedLineH: Int = 0
    /** 经行高对齐后的稳定页高（像素），翻页导航全程使用此值 */
    private var stablePageHeight: Int = 0
    /**
     * 预分页断点：每一页首行的 scrollY 绝对坐标，由 computePageBreaks() 在布局稳定后一次性计算。
     * 翻页时直接查表，无需实时 snap 计算，彻底消除来回翻页的位置漂移。
     */
    private var pageBreaks: List<Int> = emptyList()

    private var currentFontSize: Float = 18f
    private val fontSizeStep = 2f
    private val fontSizeMin = 14f
    private val fontSizeMax = 28f

    /** true = 控制面板可见（非沉浸模式） */
    private var barsVisible = false

    /** 0=无子面板, 1=设置面板, 2=亮度面板 */
    private var activePanel = 0

    private var currentSpacingIdx: Int = 1  // 默认"适中"(1.55x)
    private var nightModeActive: Boolean = false
    private var activeBgColorIdx: Int = -1  // -1=跟随用户设置

    private val bookmarkHandler = Handler(Looper.getMainLooper())
    private val bookmarkRunnable = Runnable { saveBookmark() }

    private val autoHideHandler = Handler(Looper.getMainLooper())
    private val autoHideRunnable = Runnable { if (activePanel == 0) hideBars() }
    private val AUTO_HIDE_DELAY = 4_000L
    private val ANIM_DURATION = 240L

    /** 翻页动画进行中时屏蔽重复触发 */
    private var isPageAnimating = false

    /** 每分钟刷新沉浸模式时间显示 */
    private val timeRefreshHandler = Handler(Looper.getMainLooper())
    private val timeRefreshRunnable: Runnable = object : Runnable {
        override fun run() {
            if (!barsVisible) updateImmersiveInfo()
            timeRefreshHandler.postDelayed(this, 60_000L)
        }
    }

    /** 动态获取操作栏高度，用于工具栏入场动画偏移量 */
    private val actionBarHeight: Int by lazy {
        val tv = android.util.TypedValue()
        if (theme.resolveAttribute(android.R.attr.actionBarSize, tv, true))
            android.util.TypedValue.complexToDimensionPixelSize(tv.data, resources.displayMetrics)
        else (56 * resources.displayMetrics.density).toInt()
    }

    /** 翻页模式：0=滚动，1=翻页（默认，左右点击区域翻页） */
    private var pageMode: Int = 1
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
        private const val KEY_PAGE_MODE    = "page_mode"

        /** 判断是否位于章节开头/结尾时允许的滚动容差（像素） */
        private const val SCROLL_TOLERANCE = 8
        /** 沉浸顶栏：状态栏以下内容区域固定高度，与 scrollView.paddingTop 严格对齐（零遮挡） */
        private const val IMMERSIVE_TOP_DP = 44f
        /** 沉浸底栏：导航栏以上内容区域固定高度，与 scrollView.paddingBottom 严格对齐（零遮挡） */
        private const val IMMERSIVE_BOT_DP = 36f

        private val SPACINGS = floatArrayOf(1.2f, 1.55f, 1.85f, 2.2f)

        /** 背景色预设 (白纸/米黄/暖灰/豆绿/暖黄/深蓝夜/纯黑) */
        private val BG_COLORS = intArrayOf(
            0xFFFFFFFF.toInt(),
            0xFFF8F3E3.toInt(),
            0xFFEEE9DE.toInt(),
            0xFFDDE8CC.toInt(),
            0xFFF5E8C0.toInt(),
            0xFF1A1A2E.toInt(),
            0xFF111111.toInt()
        )
        private val TEXT_COLORS = intArrayOf(
            0xFF333333.toInt(),
            0xFF3A3226.toInt(),
            0xFF3A3226.toInt(),
            0xFF2E3D1A.toInt(),
            0xFF3B2A0A.toInt(),
            0xFFCCCCCC.toInt(),
            0xFFBBBBBB.toInt()
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

        // 打孔屏/刘海屏：强制内容延伸到挖孔区域两侧（shortEdges），在沉浸模式下
        // 仍然通过 scrollView 的 padding 保护文字不被摄像头遮挡
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes = window.attributes.also {
                it.layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        // 监听系统栏 insets，记录高度用于沉浸模式下的安全内边距。
        // 使用 statusBars 与 displayCutout 的最大值确保挖空屏/刘海屏上内容不被遮挡：
        // 状态栏可见时 statusBars() > cutout；状态栏隐藏时取 cutout 保证安全区。
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.readerRoot) { _, insets ->
            val statusTop = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top
            val cutoutTop = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.displayCutout()).top
            systemStatusBarHeight = maxOf(statusTop, cutoutTop)
            systemNavBarHeight    = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom
            insets
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        // 恢复持久化偏好
        val prefs = getSharedPreferences(PREFS_READER, MODE_PRIVATE)
        currentFontSize   = prefs.getFloat(KEY_FONT_SIZE, 18f)
        currentSpacingIdx = prefs.getInt(KEY_SPACING, 1)
        nightModeActive   = prefs.getBoolean(KEY_NIGHT_MODE, false)
        activeBgColorIdx  = prefs.getInt(KEY_BG_COLOR_IDX, -1)
        pageMode          = prefs.getInt(KEY_PAGE_MODE, 1)

        // 书签（手动点击添加/删除）
        binding.ivBookmarkIndicator.setOnClickListener { toggleBookmark() }
        binding.scrollView.viewTreeObserver.addOnScrollChangedListener {
            // 沉浸模式下实时更新阅读进度百分比
            if (!barsVisible) updateImmersiveInfo()
            // 检查当前页是否在书签附近，更新书签指示器可见性
            updateBookmarkIndicatorByPosition()
        }

        setupBottomControls()

        // 默认进入沉浸模式
        hideBars()
        loadBookChapters()

        // 启动每分钟刷新时间的定时器
        timeRefreshHandler.postDelayed(timeRefreshRunnable, 60_000L)
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
            .putInt(KEY_PAGE_MODE, pageMode)
            .apply()
        saveScrollPosition()
        bookmarkHandler.removeCallbacks(bookmarkRunnable)
        saveBookmark()
    }

    override fun onDestroy() {
        super.onDestroy()
        bookmarkHandler.removeCallbacks(bookmarkRunnable)
        autoHideHandler.removeCallbacks(autoHideRunnable)
        timeRefreshHandler.removeCallbacks(timeRefreshRunnable)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_chapter_list -> { showChapterListDialog(); true }
            R.id.action_edit_chapter -> { openEditor(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /** 音量键翻页：音量下键 = 下一页，音量上键 = 上一页 */
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        return when (keyCode) {
            android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> { navigatePage(1); true }
            android.view.KeyEvent.KEYCODE_VOLUME_UP   -> { navigatePage(-1); true }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    /** 消费音量键 Up 事件，防止系统同时调节音量 */
    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        return when (keyCode) {
            android.view.KeyEvent.KEYCODE_VOLUME_DOWN,
            android.view.KeyEvent.KEYCODE_VOLUME_UP -> true
            else -> super.onKeyUp(keyCode, event)
        }
    }

    /**
     * 拦截正文区域的触摸事件：
     * - 在阅读内容区（非底部控制面板、非顶部工具栏）分派给手势检测器
     * - 翻页模式下阻止 ScrollView 滚动
     * - 滚动模式下允许 ScrollView 自由滚动
     */
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        val sv = binding.scrollView
        if (sv.isShown) {
            val svRect = android.graphics.Rect()
            sv.getGlobalVisibleRect(svRect)
            val inContent = svRect.contains(ev.rawX.toInt(), ev.rawY.toInt())

            // 当底部控制面板可见时，不拦截落在面板区域的触摸
            val inPanel = barsVisible && binding.bottomControlOverlay.isShown && run {
                val panelRect = android.graphics.Rect()
                binding.bottomControlOverlay.getGlobalVisibleRect(panelRect)
                panelRect.contains(ev.rawX.toInt(), ev.rawY.toInt())
            }

            // 工具栏可见时，不拦截落在工具栏区域的触摸（保证返回按钮可点击）
            val inAppBar = barsVisible && binding.appBarLayout.isShown && run {
                val appBarRect = android.graphics.Rect()
                binding.appBarLayout.getGlobalVisibleRect(appBarRect)
                appBarRect.contains(ev.rawX.toInt(), ev.rawY.toInt())
            }

            if (inContent && !inPanel && !inAppBar) {
                val gestureHandled = flipGestureDetector.onTouchEvent(ev)
                if (gestureHandled) return true   // 手势已处理（点击/滑动），阻止子 View 重复处理
                if (pageMode == 1) return true    // 翻页模式：阻止 ScrollView 滚动
            } else if (inPanel && barsVisible && ev.action == android.view.MotionEvent.ACTION_DOWN) {
                // 用户操作控制面板时重置自动隐藏计时器
                resetAutoHideTimer()
            }
        }
        return super.dispatchTouchEvent(ev)
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
            binding.vBgGreen, binding.vBgWarmYellow, binding.vBgNight, binding.vBgBlack
        )
        bgViews.forEachIndexed { i, v ->
            v.setOnClickListener {
                activeBgColorIdx = i
                nightModeActive = (i >= 5)  // 深蓝夜(5) 和 纯黑(6) 均为夜间模式
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

        // 沉浸模式顶部返回按钮
        binding.ivImmersiveBack.setOnClickListener { finish() }

        // 手势检测：左右点击区域翻页，中间点击呼出/隐藏工具栏
        flipGestureDetector = android.view.GestureDetector(
            this,
            object : android.view.GestureDetector.SimpleOnGestureListener() {
                private val SWIPE_MIN_DISTANCE = 80
                // 提高滑动速度阈值，避免将慢速划动误判为章节切换
                private val SWIPE_MIN_VELOCITY = 200
                override fun onDown(e: android.view.MotionEvent): Boolean = true
                override fun onSingleTapUp(e: android.view.MotionEvent): Boolean {
                    val width = binding.scrollView.width.toFloat()
                    val x = e.x
                    if (pageMode == 0) {
                        // 滚动模式：仅中间区域呼出/隐藏工具栏，侧边点击不翻页（保留自由滚动体验）
                        if (x in (width / 3f)..(width * 2f / 3f)) toggleBars()
                        return true
                    }
                    // 翻页模式：左 1/3 = 上一页，右 1/3 = 下一页，中间 = 工具栏
                    when {
                        x < width / 3f      -> navigatePage(-1)
                        x > width * 2f / 3f -> navigatePage(1)
                        else                -> toggleBars()
                    }
                    return true
                }
                override fun onFling(
                    e1: android.view.MotionEvent?,
                    e2: android.view.MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    if (e1 == null) return false
                    val dx = e2.x - e1.x
                    val dy = e2.y - e1.y
                    if (Math.abs(dx) > Math.abs(dy) &&
                        Math.abs(dx) > SWIPE_MIN_DISTANCE &&
                        Math.abs(velocityX) > SWIPE_MIN_VELOCITY) {
                        // 左滑=向后翻页，右滑=向前翻页；翻到章节末尾/开头时自动切章
                        navigatePage(if (dx < 0) 1 else -1)
                        return true
                    }
                    return false
                }
            }
        )

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

        // 记录当前 padding 和 scrollY，后面补偿，保证内容视觉位置不变
        val prevPaddingTop = binding.scrollView.paddingTop
        val prevScrollY    = binding.scrollView.scrollY

        // 使用已测量高度或预估值立即应用 padding 补偿（避免 post{} 导致的单帧跳动）
        val estimatedToolbarH = if (binding.appBarLayout.height > 0) {
            binding.appBarLayout.height
        } else {
            actionBarHeight + systemStatusBarHeight
        }
        val delta = estimatedToolbarH - prevPaddingTop
        binding.scrollView.apply {
            clipToPadding = false
            setPadding(0, estimatedToolbarH, 0, 0)
            if (delta != 0) scrollTo(0, (prevScrollY + delta).coerceAtLeast(0))
        }

        // 立即隐藏沉浸信息栏，避免与工具栏同时可见形成双重遮挡
        binding.immersiveHeaderBar.apply { animate().cancel(); alpha = 1f; visibility = View.GONE }
        binding.immersiveStatusBar.apply { animate().cancel(); alpha = 1f; visibility = View.GONE }

        // 工具栏从顶部滑入（覆盖在 scrollView 上方，不推挤内容）
        binding.appBarLayout.apply {
            val startY = -(height.takeIf { it > 0 } ?: estimatedToolbarH).toFloat()
            alpha = 0f
            translationY = startY
            visibility = View.VISIBLE
            animate().alpha(1f).translationY(0f).setDuration(ANIM_DURATION).start()
        }

        // 底部控制面板从底部滑入
        binding.bottomControlOverlay.apply {
            alpha = 0f
            visibility = View.VISIBLE
            post {
                translationY = height.toFloat()
                animate().alpha(1f).translationY(0f).setDuration(ANIM_DURATION).start()
            }
        }

        updateFontSizeDisplay()
        updateBookmarkIndicatorByPosition()  // 控制栏出现：刷新为灰色可添加状态
        resetAutoHideTimer()
    }

    private fun hideBars() {
        barsVisible = false
        autoHideHandler.removeCallbacks(autoHideRunnable)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())

        // 立即应用沉浸 padding，消除后续动画期间的内容位置跳动
        val snapshotPaddingTop = binding.scrollView.paddingTop
        val snapshotScrollY    = binding.scrollView.scrollY
        applyImmersivePaddingCompensated(snapshotPaddingTop, snapshotScrollY)

        // 工具栏先滑出，结束后再显示沉浸信息栏，避免两层 UI 同时可见形成双重遮挡
        if (binding.appBarLayout.visibility == View.VISIBLE && binding.appBarLayout.height > 0) {
            binding.appBarLayout.animate()
                .alpha(0f).translationY(-binding.appBarLayout.height.toFloat())
                .setDuration(ANIM_DURATION)
                .withEndAction {
                    binding.appBarLayout.visibility = View.GONE
                    showImmersiveChapterHeader()
                    binding.immersiveHeaderBar.alpha = 0f
                    binding.immersiveStatusBar.alpha = 0f
                    binding.immersiveHeaderBar.animate().alpha(1f).setDuration(ANIM_DURATION).start()
                    binding.immersiveStatusBar.animate().alpha(1f).setDuration(ANIM_DURATION).start()
                }.start()
        } else {
            binding.appBarLayout.visibility = View.GONE
            showImmersiveChapterHeader()
        }

        // 底部控制面板滑出到底部
        if (binding.bottomControlOverlay.visibility == View.VISIBLE && binding.bottomControlOverlay.height > 0) {
            binding.bottomControlOverlay.animate()
                .alpha(0f).translationY(binding.bottomControlOverlay.height.toFloat())
                .setDuration(ANIM_DURATION)
                .withEndAction {
                    binding.bottomControlOverlay.visibility = View.GONE
                    hideAllPanels()
                }.start()
        } else {
            binding.bottomControlOverlay.visibility = View.GONE
            hideAllPanels()
        }

        updateImmersiveInfo()
        updateBookmarkIndicatorByPosition()  // 进入沉浸模式：隐藏灰色书签图标
    }

    /**
     * 沉浸模式下为 scrollView 加上安全内边距，并补偿 scrollY 使可见文字不跳动：
     * - paddingTop  = 状态栏高度 + 顶部头信息区（章节标题行 + 适当留白）
     * - paddingBottom = 导航栏高度 + 底部信息区（进度/时间行 + 适当留白）
     * - scrollY 按 (prevPaddingTop - newPaddingTop) 补偿，视觉位置不变
     * clipToPadding=false 确保背景色仍绘制到屏幕边缘，不留白条。
     */
    private fun applyImmersivePaddingCompensated(prevPaddingTop: Int, prevScrollY: Int) {
        val density = resources.displayMetrics.density
        val statusH = if (systemStatusBarHeight > 0) systemStatusBarHeight
                      else (24 * density + 0.5f).toInt()
        val navH = if (systemNavBarHeight > 0) systemNavBarHeight else 0
        // 与信息栏 layoutParams.height 严格一致，内容起点恰在信息栏下方，零遮挡零间隙
        val top = statusH + (IMMERSIVE_TOP_DP * density + 0.5f).toInt()
        val bot = navH   + (IMMERSIVE_BOT_DP * density + 0.5f).toInt()
        val newScrollY = if (prevPaddingTop > top) {
            (prevScrollY - (prevPaddingTop - top)).coerceAtLeast(0)
        } else {
            prevScrollY
        }
        binding.scrollView.apply {
            clipToPadding = false
            setPadding(0, top, 0, bot)
            scrollTo(0, newScrollY)
        }
    }

    private fun applyImmersivePadding() {
        // 初始进入沉浸模式，无需补偿 scrollY（章节加载后会独立设置 scrollY）
        applyImmersivePaddingCompensated(0, 0)
    }

    /**
     * 等下一次 layout pass 完成后，调整 scrollView paddingBottom，使 pageHeight 是整行高的倍数：
     * - 消除底部半行：最后一行不再被截断
     * - 额外保留 1 行空白：与底部信息栏之间有呼吸感
     */
    private fun scheduleAlignPadding() {
        binding.scrollView.viewTreeObserver.addOnGlobalLayoutListener(
            object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    binding.scrollView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    alignPaddingToLines()
                }
            }
        )
    }

    private fun alignPaddingToLines() {
        val layout = binding.tvContent.layout ?: return
        if (layout.lineCount < 2) return
        val lineH = layout.getLineBottom(0) - layout.getLineTop(0)
        if (lineH <= 0) return
        val sv = binding.scrollView
        val density = resources.displayMetrics.density
        val navH = if (systemNavBarHeight > 0) systemNavBarHeight else 0
        // 底部信息栏基准高度（与 showImmersiveChapterHeader 保持一致）
        val baseBot = navH + (IMMERSIVE_BOT_DP * density + 0.5f).toInt()
        val pageH = sv.height - sv.paddingTop - baseBot
        // 消除底部半行余量，再额外留 1 整行空白
        val remainder = pageH % lineH
        val halfLineFix = if (remainder == 0) 0 else lineH - remainder
        val newBot = baseBot + halfLineFix + lineH
        if (sv.paddingBottom != newBot) {
            sv.setPadding(sv.paddingLeft, sv.paddingTop, sv.paddingRight, newBot)
        }
        // 稳定存储行高与页高，供翻页导航和全书页码估算使用
        storedLineH = lineH
        stablePageHeight = (sv.height - sv.paddingTop - newBot).coerceAtLeast(1)
        // 布局稳定后立即预分页
        computePageBreaks()
    }

    /**
     * 预分页：在布局稳定（alignPaddingToLines 完成）后一次性计算每一页的起始 scrollY。
     *
     * 原理：stablePageHeight 已被对齐为 storedLineH 的整数倍，
     * 每隔 linesPerPage 行取一个断点，直接映射为 scrollY 坐标。
     * 翻页时查表跳转，零误差，无累积漂移。
     */
    private fun computePageBreaks() {
        val layout   = binding.tvContent.layout ?: return
        val lineH    = storedLineH
        val ph       = stablePageHeight
        if (lineH <= 0 || ph <= 0) return

        val tvTop    = binding.tvContent.top
        val tvPadTop = binding.tvContent.paddingTop
        val linesPerPage = ph / lineH  // stablePageHeight 已对齐，整除无余数
        val totalLines   = layout.lineCount

        val breaks = mutableListOf<Int>()
        var lineIdx = 0
        while (lineIdx < totalLines) {
            val layoutY = layout.getLineTop(lineIdx)
            breaks.add((layoutY + tvTop + tvPadTop).coerceAtLeast(0))
            lineIdx += linesPerPage
        }
        pageBreaks = breaks
    }

    /**
     * 显示沉浸模式顶部/底部信息栏并填充内容到显示状态（visibility=VISIBLE）。
     * 调用方在需要淡入时可用 alpha=0 → animate(1f)；直接显示时直接调用即可。
     */
    private fun showImmersiveChapterHeader() {
        val density = resources.displayMetrics.density
        val statusH = if (systemStatusBarHeight > 0) systemStatusBarHeight
                      else (24 * density + 0.5f).toInt()
        val navH    = if (systemNavBarHeight > 0) systemNavBarHeight else 0
        val textColor = binding.tvContent.currentTextColor
        val bgColor = (binding.scrollView.background as? android.graphics.drawable.ColorDrawable)?.color
            ?: 0xFFF8F3E3.toInt()
        val horizPad = (20 * density + 0.5f).toInt()

        // 顶部信息栏：高度精确 = 状态栏 + IMMERSIVE_TOP_DP，与 scrollView.paddingTop 严格对齐
        val headerH = statusH + (IMMERSIVE_TOP_DP * density + 0.5f).toInt()
        val footerH = navH   + (IMMERSIVE_BOT_DP * density + 0.5f).toInt()
        binding.immersiveHeaderBar.apply {
            setBackgroundColor(bgColor)
            layoutParams.height = headerH
            requestLayout()
            setPadding(horizPad, statusH, horizPad, 0)
            visibility = View.VISIBLE
        }
        binding.tvImmersiveChapterHeader.apply {
            // 显示书名而非章节名，章节名已在正文内容区显示
            text = if (bookTitle.isNotEmpty()) bookTitle else binding.tvChapterTitle.text
            setTextColor(textColor)
        }
        binding.tvImmersiveBattery.setTextColor(textColor)
        binding.tvImmersiveTime.setTextColor(textColor)
        binding.ivImmersiveBack.imageTintList = ColorStateList.valueOf(textColor)

        // 底部信息栏：高度精确 = 导航栏 + IMMERSIVE_BOT_DP，与 scrollView.paddingBottom 严格对齐
        binding.immersiveStatusBar.apply {
            setBackgroundColor(bgColor)
            layoutParams.height = footerH
            requestLayout()
            setPadding(horizPad, 0, horizPad, navH)
            visibility = View.VISIBLE
        }
        binding.tvImmersiveProgress.setTextColor(textColor)
        binding.tvImmersiveChapterIdx.setTextColor(textColor)

        // 立即填充电量/时间/进度文字
        updateImmersiveInfo()
    }

    /** 在背景色或文字色变化后，同步更新沉浸模式页眉/页脚背景颜色 */
    private fun syncImmersiveBarColors() {
        if (barsVisible) return
        val bgColor = (binding.scrollView.background as? android.graphics.drawable.ColorDrawable)?.color
            ?: return
        val textColor = binding.tvContent.currentTextColor
        if (binding.immersiveHeaderBar.visibility == View.VISIBLE) {
            binding.immersiveHeaderBar.setBackgroundColor(bgColor)
            binding.tvImmersiveChapterHeader.setTextColor(textColor)
            binding.tvImmersiveBattery.setTextColor(textColor)
            binding.tvImmersiveTime.setTextColor(textColor)
            binding.ivImmersiveBack.imageTintList = ColorStateList.valueOf(textColor)
        }
        if (binding.immersiveStatusBar.visibility == View.VISIBLE) {
            binding.immersiveStatusBar.setBackgroundColor(bgColor)
            binding.tvImmersiveProgress.setTextColor(textColor)
            binding.tvImmersiveChapterIdx.setTextColor(textColor)
        }
    }

    // ─────────────────── 子面板切换 ───────────────────

    private fun togglePanel(id: Int) {
        val newPanel = if (activePanel == id) 0 else id
        activePanel = newPanel

        // 淡入/淡出子面板，避免高度突变引起视觉跳动
        fun animatePanel(view: View, show: Boolean) {
            if (show) {
                view.alpha = 0f
                view.visibility = View.VISIBLE
                view.animate().alpha(1f).setDuration(120L).start()
            } else if (view.visibility == View.VISIBLE) {
                view.animate().alpha(0f).setDuration(100L)
                    .withEndAction { view.visibility = View.GONE; view.alpha = 1f }.start()
            }
        }
        animatePanel(binding.panelSettings, newPanel == 1)
        animatePanel(binding.panelBrightness, newPanel == 2)

        if (newPanel == 0) resetAutoHideTimer()
        else autoHideHandler.removeCallbacks(autoHideRunnable)
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
        scheduleAlignPadding()
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
        applyControlPanelTheme(BG_COLORS[idx], TEXT_COLORS[idx])
        // 同步更新沉浸模式页眉页脚背景色
        syncImmersiveBarColors()
    }

    /**
     * 将底部控制面板的背景和文字颜色调整为与阅读背景一致的色调。
     * 面板背景在原始 bgColor 上叠加一层轻微明暗偏移，产生「悬浮层」视觉效果。
     */
    private fun applyControlPanelTheme(bgColor: Int, textColor: Int) {
        val lum = Color.luminance(bgColor)
        val overlay = if (lum > 0.3f) Color.BLACK else Color.WHITE
        val scrim   = if (lum > 0.3f) 0.07f else 0.09f
        val panelBg = Color.rgb(
            (Color.red(bgColor)   * (1 - scrim) + Color.red(overlay)   * scrim).toInt(),
            (Color.green(bgColor) * (1 - scrim) + Color.green(overlay) * scrim).toInt(),
            (Color.blue(bgColor)  * (1 - scrim) + Color.blue(overlay)  * scrim).toInt()
        )
        binding.bottomControlOverlay.setBackgroundColor(panelBg)

        val secondaryColor = Color.argb(
            (255 * 0.6f).toInt(),
            Color.red(textColor), Color.green(textColor), Color.blue(textColor)
        )
        val tintList = ColorStateList.valueOf(textColor)

        // 导航栏四图标及标签
        listOf(binding.ibNavToc, binding.ibNavBrightness, binding.ibNavNight, binding.ibNavSettings)
            .forEach { container ->
                for (i in 0 until container.childCount) {
                    when (val child = container.getChildAt(i)) {
                        is ImageView -> child.imageTintList = tintList
                        is TextView  -> child.setTextColor(textColor)
                    }
                }
            }

        // 章节进度文字
        binding.tvChapterProgress.setTextColor(secondaryColor)
    }

    private fun updateBgCircles() {
        val primary = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorPrimary, Color.GRAY
        )
        val views = listOf(binding.vBgWhite, binding.vBgCream, binding.vBgWarm,
                           binding.vBgGreen, binding.vBgWarmYellow, binding.vBgNight, binding.vBgBlack)
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
            activeBgColorIdx = 5  // 默认切换到深蓝夜模式（index 5）
            applyNightColors()
        } else {
            activeBgColorIdx = -1
            lifecycleScope.launch { restoreUserThemeColors() }
        }
        updateBgCircles()
        updateNightButton()
    }

    private fun applyNightColors() {
        // 根据当前夜间背景索引选择不同夜间色（5=深蓝夜，6=纯黑）
        val nightBg   = if (activeBgColorIdx >= 0 && activeBgColorIdx < BG_COLORS.size)
                            BG_COLORS[activeBgColorIdx] else 0xFF1A1A2E.toInt()
        val nightText = if (activeBgColorIdx >= 0 && activeBgColorIdx < TEXT_COLORS.size)
                            TEXT_COLORS[activeBgColorIdx] else 0xFFCCCCCC.toInt()
        binding.scrollView.setBackgroundColor(nightBg)
        binding.tvContent.setTextColor(nightText)
        binding.tvChapterTitle.setTextColor(nightText)
        applyControlPanelTheme(nightBg, nightText)
        syncImmersiveBarColors()
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

    /** 重置自动隐藏计时器（主流阅读 App 交互：控制栏显示后 4 秒无操作自动隐藏） */
    private fun resetAutoHideTimer() {
        autoHideHandler.removeCallbacks(autoHideRunnable)
        if (activePanel == 0) {
            autoHideHandler.postDelayed(autoHideRunnable, AUTO_HIDE_DELAY)
        }
    }

    /** 更新沉浸模式顶部（电量 + 时间）和底部（阅读进度% + 当前页/总页 数）信息栏 */
    private fun updateImmersiveInfo() {
        // 全书页码
        val sv = binding.scrollView
        val child = sv.getChildAt(0)
        val pageH = getPageHeight()
        val pageText = if (child != null && pageH > 0 && pageBreaks.isNotEmpty() && chapterWordCounts.isNotEmpty()) {
            // 当前章：从预分页表精确查询
            val currentPageInChapter = pageBreaks.indexOfLast { it <= sv.scrollY + SCROLL_TOLERANCE }
                .coerceAtLeast(0) + 1
            val totalChapterPages = pageBreaks.size

            // 其他章：用字数估算页数（保持与当前章相同的参数设定）
            val linesPerPage  = (pageH / storedLineH).coerceAtLeast(1)
            val density       = resources.displayMetrics.density
            val charW         = (currentFontSize * density + 0.5f).toInt().coerceAtLeast(1)
            val contentW      = (binding.tvContent.width
                    - binding.tvContent.paddingLeft - binding.tvContent.paddingRight)
                    .coerceAtLeast(1)
            val charsPerLine  = (contentW / charW).coerceAtLeast(1)
            val charsPerPage  = (linesPerPage * charsPerLine).coerceAtLeast(1)

            val pagesBeforeCurrent = chapterWordCounts.take(currentIndex).sumOf { wc ->
                (wc / charsPerPage) + 1
            }
            // 当前章用精确值，其余章用估算值，加总全书
            val pagesAfterCurrent = chapterWordCounts.drop(currentIndex + 1).sumOf { wc ->
                (wc / charsPerPage) + 1
            }
            val totalBookPages = (pagesBeforeCurrent + totalChapterPages + pagesAfterCurrent)
                .coerceAtLeast(1)

            "${pagesBeforeCurrent + currentPageInChapter}/$totalBookPages 页"
        } else if (child != null && pageH > 0) {
            val maxScroll = (child.measuredHeight + sv.paddingTop + sv.paddingBottom - sv.height).coerceAtLeast(0)
            val currentPage = (sv.scrollY / pageH) + 1
            val totalPages  = (maxScroll / pageH) + 1
            "$currentPage/$totalPages 页"
        } else "-/- 页"

        // 全书阅读进度
        val progressText = if (child != null && allChapterIds.isNotEmpty()) {
            val maxScroll = (child.height + sv.paddingTop + sv.paddingBottom - sv.height).coerceAtLeast(1)
            val withinPct = ((sv.scrollY.toFloat() / maxScroll) * 100).toInt().coerceIn(0, 100)
            val globalPct = ((currentIndex + withinPct / 100.0) / allChapterIds.size * 100)
                .toInt().coerceIn(0, 100)
            "全书 $globalPct%"
        } else "全书 0%"

        // 底部：全书进度% 左 / 页码 右
        binding.tvImmersiveProgress.text = progressText
        binding.tvImmersiveChapterIdx.text = pageText

        // 顶部右侧：电量 + 时间
        binding.tvImmersiveBattery.text = "${readBatteryPct()}%"
        val cal = java.util.Calendar.getInstance()
        binding.tvImmersiveTime.text = String.format(
            java.util.Locale.getDefault(), "%02d:%02d",
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE)
        )

        // 所有信息文字颜色跟随阅读主题，确保与背景对比
        val textColor = binding.tvContent.currentTextColor
        binding.tvImmersiveProgress.setTextColor(textColor)
        binding.tvImmersiveChapterIdx.setTextColor(textColor)
        binding.tvImmersiveBattery.setTextColor(textColor)
        binding.tvImmersiveTime.setTextColor(textColor)
    }

    /**
     * 读取当前电池电量百分比（0-100）。
     * 使用粘性广播一次性读取，无需注册接收器。
     */
    private fun readBatteryPct(): Int {
        val intent = registerReceiver(
            null,
            android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        )
        val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else 0
    }

    private fun applyFontSize() {
        if (currentFontSize > 0f) {
            binding.tvContent.textSize = currentFontSize
            scheduleAlignPadding()
        }
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
                binding.tvChapterTitle.setTextColor(settings.textColor)
                binding.scrollView.setBackgroundColor(settings.backgroundColor)
                applyControlPanelTheme(settings.backgroundColor, settings.textColor)
            }
        }
        if (currentFontSize == 0f) currentFontSize = settings.fontSize.toFloat()
        applyFontSize()
        applyCurrentSpacing()
        updateNightButton()
        updateFontSizeDisplay()
        updateSpacingButtonStates()
        updateBgCircles()
        syncImmersiveBarColors()
    }

    private suspend fun restoreUserThemeColors() {
        val userId = SessionManager.getUserId(this)
        val settings = withContext(Dispatchers.IO) {
            (application as LocalWriterApp).settingsRepository.getSettings(userId)
        } ?: return
        binding.tvContent.setTextColor(settings.textColor)
        binding.tvChapterTitle.setTextColor(settings.textColor)
        binding.scrollView.setBackgroundColor(settings.backgroundColor)
        applyControlPanelTheme(settings.backgroundColor, settings.textColor)
    }

    // ─────────────────── 数据加载 ───────────────────

    private fun loadBookChapters() {
        lifecycleScope.launch {
            val db = (application as LocalWriterApp).database
            val ids       = mutableListOf<Long>()
            val titles    = mutableListOf<String>()
            val wordCounts = mutableListOf<Int>()
            withContext(Dispatchers.IO) {
                val volumes = db.volumeDao().getAllByBook(bookId).sortedBy { it.sortOrder }
                for (vol in volumes) {
                    val chapters = db.chapterDao().getPreviewsByVolume(vol.id)
                    for (ch in chapters) { ids.add(ch.id); titles.add(ch.title); wordCounts.add(ch.wordCount) }
                }
            }
            allChapterIds     = ids
            chapterTitles     = titles
            chapterWordCounts = wordCounts
            currentIndex  = ids.indexOf(chapterId).coerceAtLeast(0)
            applyUserSettings()
            loadChapter(chapterId)
        }
    }

    /**
     * 加载指定章节内容。
     * @param startAtEnd true 时章节加载完成后滚动到末尾（向前翻越过章节边界时使用）。
     */
    private fun loadChapter(chapId: Long, startAtEnd: Boolean = false) {
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
            // 顶部信息栏显示书名（固定不变），无需随章节切换更新
            // 每段首行添加全角空格缩进（仿真实书籍排版）
            val indented = chapter.content.ifEmpty { "（本章暂无内容）" }
                .lines()
                .joinToString("\n") { line ->
                    if (line.trim().isNotEmpty()) "\u3000\u3000$line" else line
                }
            binding.tvContent.text = indented
            applyFontSize()
            applyCurrentSpacing()
            // 内容/字号/行距变更后，重新对齐底部 padding 以消除半行
            scheduleAlignPadding()

            val total = allChapterIds.size
            binding.tvChapterProgress.text = "${currentIndex + 1} / $total 章"

            if (startAtEnd) {
                // 向前翻越过章节边界：等待 Layout 完成后跳到章节末尾
                binding.scrollView.post {
                    val sv   = binding.scrollView
                    val child = sv.getChildAt(0)
                    if (child != null) {
                        val pageH = (sv.height - sv.paddingTop - sv.paddingBottom).coerceAtLeast(1)
                        sv.scrollTo(0, (child.height - pageH).coerceAtLeast(0))
                    }
                }
            } else {
                val scrollY = chapter.lastScrollPos
                binding.scrollView.post { binding.scrollView.scrollTo(0, scrollY) }
            }
            updateBookmarkIndicator()
            updateFontSizeDisplay()
            if (!barsVisible) updateImmersiveInfo()

            withContext(Dispatchers.IO) {
                (application as LocalWriterApp).bookRepository.updateLastChapter(bookId, chapId)
            }
        }
    }

    // ─────────────────── 导航 ───────────────────

    /**
     * 跳转到上/下相邻章节。
     * 向后翻（direction>0）→ 新章节从顶部开始阅读；
     * 向前翻（direction<0）→ 跳到新章节末尾（符合翻页阅读直觉：可继续向前翻页）。
     */
    private fun navigateChapter(direction: Int) {
        val newIndex = currentIndex + direction
        if (newIndex < 0 || newIndex >= allChapterIds.size) return
        saveScrollPosition()
        loadChapter(allChapterIds[newIndex], startAtEnd = direction < 0)
        binding.scrollView.scrollTo(0, 0)
    }

    /**
     * 翻页导航（预分页版）：直接查 pageBreaks 表，零误差，来回翻页落点完全一致。
     * 仅在 pageBreaks 为空（布局尚未稳定）时回退到滚动模式。
     * direction > 0 = 向后翻；direction < 0 = 向前翻。
     */
    private fun navigatePage(direction: Int) {
        val sv = binding.scrollView
        if (pageBreaks.isEmpty()) {
            // 布局尚未就绪，简单滚一屏作为兜底
            val ph = getPageHeight()
            val targetY = (sv.scrollY + direction * ph).coerceAtLeast(0)
            sv.smoothScrollTo(0, targetY)
            return
        }
        val currentY = sv.scrollY
        // 在 pageBreaks 中找当前所在页的索引（最后一个 ≤ currentY 的断点）
        val currentIdx = pageBreaks.indexOfLast { it <= currentY + SCROLL_TOLERANCE }
            .coerceAtLeast(0)
        val targetIdx = currentIdx + direction
        when {
            targetIdx < 0               -> navigateChapter(-1)
            targetIdx >= pageBreaks.size -> navigateChapter(1)
            else -> {
                val targetY = pageBreaks[targetIdx]
                if (pageMode == 1) slideAnimatePage(targetY, direction)
                else sv.smoothScrollTo(0, targetY)
            }
        }
    }

    /**
     * 将 scrollY 对齐到 tvContent 内最近的完整行顶部。
     *
     * 坐标映射：
     *   scrollY（LinearLayout 坐标）→ tvContent 内部 layoutY = scrollY - tvContent.top - tvContent.paddingTop
     *   → Layout.getLineForVertical(layoutY) 找到对应行
     *   → Layout.getLineTop(line) 得到行顶部偏移
     *   → 映射回 scrollY
     */
    private fun snapToLineTop(rawScrollY: Int): Int {
        val layout   = binding.tvContent.layout ?: return rawScrollY
        val tvTop    = binding.tvContent.top
        val tvPadTop = binding.tvContent.paddingTop
        val layoutY  = rawScrollY - tvTop - tvPadTop
        if (layoutY < 0 || layoutY > layout.height) return rawScrollY
        val line = layout.getLineForVertical(layoutY)
        return (layout.getLineTop(line) + tvTop + tvPadTop).coerceAtLeast(0)
    }

    /**
     * 向前翻页专用：返回第一个完整行的顶部（≥ rawScrollY），使新页头行不被截断。
     * 与 [snapToLineTop]（始终 ≤ rawScrollY）方向相反，前进/后退 snap 方向对称，
     * 保证「翻一页再翻回来」严格回到原位，不丢行。
     */
    private fun snapToFirstFullLine(rawScrollY: Int): Int {
        val layout   = binding.tvContent.layout ?: return rawScrollY
        val tvTop    = binding.tvContent.top
        val tvPadTop = binding.tvContent.paddingTop
        val layoutY  = rawScrollY - tvTop - tvPadTop
        if (layoutY <= 0) return 0
        if (layoutY >= layout.height) return rawScrollY
        val line     = layout.getLineForVertical(layoutY)
        val lineTop  = layout.getLineTop(line)
        // 若 rawScrollY 落在行中间，推进到下一行起始
        val targetLine = if (lineTop < layoutY && line + 1 < layout.lineCount) line + 1 else line
        return (layout.getLineTop(targetLine) + tvTop + tvPadTop).coerceAtLeast(0)
    }

    /**
     * 翻页模式专用滑入动画：当前页向左/右滑出，新页从反方向滑入。
     * 整个过程在 ScrollView 的 translationX 上操作，不影响实际内容位置。
     */
    /**
     * 翻页动画：水平滑动切页（自然不喧宾夺主）
     * 1. 截取当前可见区域为位图
     * 2. 跳转到目标 scrollY（新页已在背景渲染）
     * 3. 位图覆盖在顶层，向左/右平滑滑出，新页从背后显露
     */
    private fun slideAnimatePage(targetScrollY: Int, direction: Int) {
        if (isPageAnimating) return
        isPageAnimating = true

        val sv = binding.scrollView
        val overlay = binding.pageFlipOverlay

        // 截取当前可见内容
        val bmp = try {
            Bitmap.createBitmap(sv.width.coerceAtLeast(1), sv.height.coerceAtLeast(1),
                Bitmap.Config.RGB_565).also { sv.draw(Canvas(it)) }
        } catch (e: Exception) {
            sv.scrollTo(0, targetScrollY)
            isPageAnimating = false
            return
        }

        // 立即跳至新页（覆盖层遮住跳转闪烁）
        sv.scrollTo(0, targetScrollY)

        // 显示覆盖层，从原位平滑滑出
        overlay.frontBitmap = bmp
        overlay.translationX = 0f
        overlay.visibility = View.VISIBLE

        // direction > 0 = 向后翻（当前页左移消失）；direction < 0 = 向前翻（当前页右移消失）
        val targetX = -sv.width.toFloat() * direction
        overlay.animate()
            .translationX(targetX)
            .setDuration(200L)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .withEndAction {
                overlay.visibility = View.GONE
                overlay.translationX = 0f
                overlay.frontBitmap?.recycle()
                overlay.frontBitmap = null
                isPageAnimating = false
            }.start()
    }

    private fun saveScrollPosition() {
        val scrollY = binding.scrollView.scrollY
        val chapId  = chapterId
        lifecycleScope.launch(Dispatchers.IO) {
            (application as LocalWriterApp).database.chapterDao()
                .updateScrollPos(chapId, scrollY)
        }
    }

    // ─────────────────── 书签 ───────────────────

    /** 手动切换书签：当前页无书签则添加；有书签且当前在书签附近则删除 */
    private fun toggleBookmark() {
        val scrollY = binding.scrollView.scrollY
        val saved = getSharedPreferences(PREFS_READER, MODE_PRIVATE)
            .getInt("$KEY_BOOKMARK_PRE$chapterId", -1)
        if (saved > 0 && Math.abs(scrollY - saved) <= getPageHeight()) {
            // 已在书签页，点击删除书签
            getSharedPreferences(PREFS_READER, MODE_PRIVATE).edit()
                .remove("$KEY_BOOKMARK_PRE$chapterId").apply()
            updateBookmarkIndicatorByPosition()
            Toast.makeText(this, "已删除书签", Toast.LENGTH_SHORT).show()
        } else {
            // 不在书签页（或无书签），点击添加书签到当前位置
            getSharedPreferences(PREFS_READER, MODE_PRIVATE).edit()
                .putInt("$KEY_BOOKMARK_PRE$chapterId", scrollY).apply()
            updateBookmarkIndicatorByPosition()
            Toast.makeText(this, "已添加书签", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getPageHeight(): Int {
        return if (stablePageHeight > 0) stablePageHeight
        else (binding.scrollView.height - binding.scrollView.paddingTop - binding.scrollView.paddingBottom).coerceAtLeast(1)
    }

    /**
     * 书签指示器状态规则（兼顾阅读沉浸感与操作可发现性）：
     *
     * 沉浸模式（barsVisible=false）：
     *   - 当前页有书签 → 红色图标（提醒此处有书签，点击可删除）
     *   - 当前页无书签 → 隐藏（不干扰正文阅读）
     *
     * 控制栏可见（barsVisible=true）：
     *   - 当前页有书签 → 红色图标
     *   - 其余情况     → 浅灰图标（提示可点击添加书签）
     */
    private fun updateBookmarkIndicatorByPosition() {
        val saved = getSharedPreferences(PREFS_READER, MODE_PRIVATE)
            .getInt("$KEY_BOOKMARK_PRE$chapterId", -1)
        val scrollY = binding.scrollView.scrollY
        val isOnBookmarkPage = saved > 0 && Math.abs(scrollY - saved) <= getPageHeight()

        when {
            isOnBookmarkPage -> {
                // 在书签页：红色书签（沉浸/非沉浸都显示）
                binding.ivBookmarkIndicator.apply {
                    imageTintList = android.content.res.ColorStateList.valueOf(0xFFE53935.toInt())
                    visibility = View.VISIBLE
                }
            }
            barsVisible -> {
                // 控制栏可见时：浅灰图标（可添加书签）
                binding.ivBookmarkIndicator.apply {
                    imageTintList = android.content.res.ColorStateList.valueOf(0x99888888.toInt())
                    visibility = View.VISIBLE
                }
            }
            else -> {
                // 沉浸模式且不在书签页：完全隐藏，不干扰阅读
                binding.ivBookmarkIndicator.visibility = View.GONE
            }
        }
    }

    private fun updateBookmarkIndicator() {
        updateBookmarkIndicatorByPosition()
    }

    private fun saveBookmark() {
        // 保留此方法供 onPause 调用（无自动保存逻辑，此处仅作读取位置存档）
        // 真实书签已改为手动添加，无需在此自动保存
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

        val sheetBinding = LayoutTocBottomSheetBinding.inflate(layoutInflater)
        val sheet = BottomSheetDialog(this)
        sheet.setContentView(sheetBinding.root)
        // 透明背景使圆角生效
        sheet.window?.findViewById<android.view.View>(
            com.google.android.material.R.id.design_bottom_sheet
        )?.setBackgroundResource(android.R.color.transparent)

        // 设置书名
        lifecycleScope.launch {
            val db = (application as LocalWriterApp).database
            val book = withContext(Dispatchers.IO) { db.bookDao().findById(bookId) }
            sheetBinding.tvTocBookTitle.text = book?.title ?: ""
        }

        // 章节统计
        sheetBinding.tvTocStats.text = "共 ${allChapterIds.size} 章"

        // 书签 tab：跳转到当前章节书签位置
        sheetBinding.btnBookmarkTab.setOnClickListener {
            val scrollY = getSharedPreferences(PREFS_READER, MODE_PRIVATE)
                .getInt("$KEY_BOOKMARK_PRE$chapterId", -1)
            if (scrollY > 0) {
                sheet.dismiss()
                binding.scrollView.smoothScrollTo(0, scrollY)
                Toast.makeText(this, "已跳转到书签位置", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "当前章节暂无书签，可点击右上角书签图标添加", Toast.LENGTH_SHORT).show()
            }
        }

        // 笔记 tab：功能待开发
        sheetBinding.btnNotesTab.setOnClickListener {
            Toast.makeText(this, "笔记功能正在开发中", Toast.LENGTH_SHORT).show()
        }

        // 是否倒序
        var isReversed = false
        val displayIds    = allChapterIds.toMutableList()
        val displayTitles = chapterTitles.toMutableList()

        // 途径送给 adapter：点击切章
        val adapter = TocAdapter(displayTitles, currentIndex) { position ->
            val realIdx = if (isReversed) allChapterIds.size - 1 - position else position
            sheet.dismiss()
            if (realIdx != currentIndex) {
                saveScrollPosition()
                loadChapter(allChapterIds[realIdx])
                binding.scrollView.scrollTo(0, 0)
            }
        }
        sheetBinding.rvTocChapters.layoutManager = LinearLayoutManager(this)
        sheetBinding.rvTocChapters.adapter = adapter

        // 滚动到当前章节
        val scrollTo = if (isReversed) allChapterIds.size - 1 - currentIndex else currentIndex
        sheetBinding.rvTocChapters.scrollToPosition(scrollTo.coerceAtLeast(0))

        // 正序 / 倒序切换
        sheetBinding.tvTocSortOrder.setOnClickListener {
            isReversed = !isReversed
            sheetBinding.tvTocSortOrder.text = if (isReversed) "倒序" else "正序"
            displayIds.reverse()
            displayTitles.reverse()
            // 同步更新高亮位置：倒序时 activeIndex = totalSize-1-currentIndex
            adapter.activeIndex = if (isReversed) allChapterIds.size - 1 - currentIndex
                                  else currentIndex
            adapter.notifyDataSetChanged()
            val newScroll = if (isReversed) allChapterIds.size - 1 - currentIndex else currentIndex
            sheetBinding.rvTocChapters.scrollToPosition(newScroll.coerceAtLeast(0))
        }

        sheet.show()
    }

    /** 目录列表 Adapter */
    private inner class TocAdapter(
        private val titles: List<String>,
        var activeIndex: Int,
        private val onItemClick: (Int) -> Unit
    ) : RecyclerView.Adapter<TocAdapter.VH>() {

        inner class VH(val b: ItemTocChapterBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val b = ItemTocChapterBinding.inflate(layoutInflater, parent, false)
            return VH(b)
        }

        override fun getItemCount() = titles.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.b.tvTocChapterTitle.text = titles[position]
            val isActive = position == activeIndex
            val activeColor = MaterialColors.getColor(
                holder.itemView, com.google.android.material.R.attr.colorPrimary)
            holder.b.tvTocChapterTitle.setTextColor(
                if (isActive) activeColor else 0xFF1A1A1A.toInt())
            holder.b.tvTocChapterTitle.textSize = if (isActive) 15.5f else 15f
            // 当前章节圆点高亮（mutate 防止共享 ConstantState 导致所有圆点颜色相同）
            val dotDrawable = (holder.b.vChapterDot.background as? android.graphics.drawable.GradientDrawable)
                ?.mutate() as? android.graphics.drawable.GradientDrawable
            dotDrawable?.setColor(if (isActive) activeColor else 0xFFCCCCCC.toInt())
            holder.itemView.setOnClickListener { onItemClick(position) }
        }
    }

    private fun openEditor() {
        val intent = Intent(this, EditorActivity::class.java).apply {
            putExtra(EditorActivity.EXTRA_CHAPTER_ID, chapterId)
            putExtra(EditorActivity.EXTRA_BOOK_ID, bookId)
        }
        startActivity(intent)
    }
}
