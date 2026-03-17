package com.localwriter.ui.editor

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.localwriter.LocalWriterApp
import com.localwriter.R
import com.localwriter.databinding.ActivityEditorBinding
import com.localwriter.ui.auth.AuthActivity
import com.localwriter.ui.settings.SettingsActivity
import com.localwriter.utils.SessionManager
import com.localwriter.utils.ThemeManager
import com.localwriter.utils.UndoRedoHelper

/**
 * 写作编辑器界面
 *
 * 功能：
 * - 全屏沉浸式编辑
 * - 顶部工具栏：章节标题 / 字数统计 / 保存状态
 * - 实时字数统计
 * - 自动保存（默认30秒）
 * - 手动保存（Ctrl+S 或菜单）
 * - 退出时保存光标位置
 * - 支持自定义背景色、字体、字号（从设置读取）
 * - 快捷插入：标点、常用句式（底部悬浮工具栏）
 */
class EditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditorBinding
    private val viewModel: EditorViewModel by viewModels {
        EditorViewModel.Factory(
            (application as LocalWriterApp).chapterRepository,
            (application as LocalWriterApp).settingsRepository
        )
    }

    private var chapterId: Long = 0
    private var bookId: Long = 0
    private var isTextChangedByUser = false
    private lateinit var undoRedoHelper: UndoRedoHelper

    companion object {
        const val EXTRA_CHAPTER_ID = "chapter_id"
        const val EXTRA_BOOK_ID = "book_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        chapterId = intent.getLongExtra(EXTRA_CHAPTER_ID, 0)
        bookId    = intent.getLongExtra(EXTRA_BOOK_ID, 0)

        if (chapterId == 0L) { finish(); return }

        val userId = SessionManager.getUserId(this)
        viewModel.loadChapter(chapterId, bookId, userId)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupEditor()
        setupQuickInputBar()
        observeData()

        // 使用 OnBackPressedDispatcher 替代已废弃的 onBackPressed()（Android 13+）
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                saveAndExit()
            }
        })
    }

    private fun setupEditor() {
        undoRedoHelper = UndoRedoHelper(binding.etContent)
        binding.etContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!isTextChangedByUser) return
                val content = s?.toString() ?: ""
                val interval = viewModel.settings.value?.autoSaveInterval ?: 30
                viewModel.onContentChanged(content, chapterId, interval)
            }
        })
    }

    /** 底部快捷输入工具栏 */
    private fun setupQuickInputBar() {
        // 常用标点快捷插入
        val punctuations = listOf("，", "。", "？", "！", "……", "—", "「」", "『』", "【】")
        binding.chipGroupPunctuation.removeAllViews()
        punctuations.forEach { p ->
            val chip = com.google.android.material.chip.Chip(this).apply {
                text = p
                isClickable = true
                setOnClickListener { insertText(p) }
            }
            binding.chipGroupPunctuation.addView(chip)
        }

        // 常用句式
        binding.btnQuickPhrase.setOnClickListener {
            showQuickPhraseDialog()
        }

        // 保存并返回
        binding.btnEditorBack.setOnClickListener { saveAndExit() }

        // 撤销/重做
        binding.btnUndo.setOnClickListener { undoRedoHelper.undo() }
        binding.btnRedo.setOnClickListener { undoRedoHelper.redo() }
    }

    private fun observeData() {
        viewModel.chapter.observe(this) { chapter ->
            if (chapter != null) {
                supportActionBar?.title = chapter.title
                isTextChangedByUser = false
                binding.etContent.setText(chapter.content)
                // 恢复光标位置
                if (chapter.lastCursorPos > 0 && chapter.lastCursorPos <= chapter.content.length) {
                    binding.etContent.setSelection(chapter.lastCursorPos)
                }
                isTextChangedByUser = true
            }
        }

        viewModel.wordCount.observe(this) { count ->
            binding.tvWordCount.text = when {
                count >= 10000 -> "%.1f万字".format(count / 10000.0)
                else -> "${count}字"
            }
        }

        viewModel.saveStatus.observe(this) { status ->
            binding.tvSaveStatus.text = when (status) {
                is EditorViewModel.SaveStatus.Idle   -> ""
                is EditorViewModel.SaveStatus.Saving -> "保存中…"
                is EditorViewModel.SaveStatus.Saved  -> "已保存"
                is EditorViewModel.SaveStatus.Error  -> "保存失败"
            }
        }

        viewModel.settings.observe(this) { settings ->
            settings ?: return@observe
            applyEditorStyle(settings)
        }
    }

    private fun applyEditorStyle(settings: com.localwriter.data.db.entity.UserSettings) {
        // 背景色
        binding.scrollContent.setBackgroundColor(settings.backgroundColor)
        // 字色
        binding.etContent.setTextColor(settings.textColor)
        // 字号
        binding.etContent.textSize = settings.fontSize.toFloat()
        // 字体
        val typeface = try {
            Typeface.create(settings.fontFamily, Typeface.NORMAL)
        } catch (e: Exception) {
            Typeface.DEFAULT
        }
        binding.etContent.typeface = typeface
        // 行高
        binding.etContent.setLineSpacing(0f, settings.lineSpacing)
        // 字间距
        binding.etContent.letterSpacing = settings.letterSpacing
        // 内边距
        val dp = resources.displayMetrics.density
        binding.etContent.setPadding(
            (settings.contentPaddingHorizontal * dp).toInt(),
            (settings.contentPaddingVertical * dp).toInt(),
            (settings.contentPaddingHorizontal * dp).toInt(),
            (settings.contentPaddingVertical * dp).toInt()
        )
        // 背景图（如有）
        if (settings.backgroundType == "IMAGE" && !settings.backgroundImagePath.isNullOrBlank()) {
            val bitmap = android.graphics.BitmapFactory.decodeFile(settings.backgroundImagePath)
            if (bitmap != null) {
                binding.scrollContent.background =
                    android.graphics.drawable.BitmapDrawable(resources, bitmap)
            }
        }
    }

    private fun insertText(text: String) {
        val start = binding.etContent.selectionStart.coerceAtLeast(0)
        binding.etContent.editableText.insert(start, text)
    }

    private fun showQuickPhraseDialog() {
        val phrases = arrayOf(
            "话说……", "且说……", "却说……",
            "只见……", "不料……", "哪知……",
            "此时……", "正当……", "忽然……"
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("常用句式")
            .setItems(phrases) { _, which -> insertText(phrases[which]) }
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (SessionManager.isLoggedIn(this) && SessionManager.isLocked(this)) {
            // 先尝试保存未保存的内容，再跳转认证，应对迟延自动保存错过的场景
            val content = binding.etContent.text?.toString() ?: ""
            if (chapterId > 0 && content.isNotEmpty()) {
                lifecycleScope.launch {
                    try { viewModel.saveContentSync(chapterId, content) } catch (_: Exception) {}
                    startActivity(Intent(this@EditorActivity, AuthActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    })
                    finish()
                }
            } else {
                startActivity(Intent(this, AuthActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                })
                finish()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_editor, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                saveAndExit()
                true
            }
            R.id.action_save -> {
                viewModel.saveContent(chapterId, binding.etContent.text.toString())
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun saveAndExit() {
        val content = binding.etContent.text.toString()
        val cursorPos = binding.etContent.selectionStart
        // 同步等待保存完成后再退出，防止协程未结束就销毁导致数据丢失
        lifecycleScope.launch {
            try {
                viewModel.saveContentSync(chapterId, content)
                viewModel.saveLastCursor(chapterId, cursorPos)
            } finally {
                finish()
            }
        }
    }
}
