package com.localwriter.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.localwriter.BuildConfig
import com.localwriter.LocalWriterApp
import com.localwriter.R
import com.localwriter.data.db.entity.UserSettings
import com.localwriter.databinding.ActivitySettingsBinding
import com.localwriter.ui.auth.AuthActivity
import com.localwriter.ui.auth.GestureLoginFragment
import com.localwriter.utils.SessionManager
import com.localwriter.utils.ThemeManager
import kotlinx.coroutines.launch

/**
 * 设置界面
 *
 * 功能分组：
 * ① 阅读/编辑外观
 *    - 背景风格：纯色(预设多款纸纹) / 自定义图片
 *    - 字号：10-28sp 滑杆
 *    - 字体：系统字体列表
 *    - 行间距 / 字间距
 *    - 文字颜色
 * ② 编辑习惯
 *    - 自动保存间隔
 *    - 是否显示字数统计
 * ③ 安全
 *    - 修改密码
 *    - 设置/取消手势密码
 *    - 生物识别开关
 * ④ 数据
 *    - 导入书籍
 *    - 导出书籍
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val viewModel: SettingsViewModel by viewModels {
        SettingsViewModel.Factory(
            (application as LocalWriterApp).settingsRepository,
            (application as LocalWriterApp).authRepository
        )
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { saveBackgroundImage(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "设置"

        val userId = SessionManager.getUserId(this)
        viewModel.load(userId)

        setupThemeSection()
        setupFontSizeSeekBar()
        setupFontFamilyChips()
        setupBackgroundPresets()
        setupColorPicker()
        setupAutoSave()
        setupSecuritySection()
        setupLockTimeoutSpinner()
        setupAboutSection()
        observeSettings()
    }

    /** 切换主题皮肤：显示圆形色块，当前主题加勾选标记 */
    private fun setupThemeSection() {
        val container = binding.llThemeRow
        container.removeAllViews()
        val currentIndex = ThemeManager.getSavedIndex(this)
        val dp = resources.displayMetrics.density
        val size = (44 * dp).toInt()
        val margin = (8 * dp).toInt()

        ThemeManager.themes.forEachIndexed { index, theme ->
            val btn = android.widget.ImageButton(this).apply {
                val params = android.widget.LinearLayout.LayoutParams(size, size)
                params.marginEnd = margin
                layoutParams = params
                setBackgroundResource(android.R.drawable.btn_default_small)
                background = android.graphics.drawable.GradientDrawable().also { d ->
                    d.shape = android.graphics.drawable.GradientDrawable.OVAL
                    d.setColor(theme.colorSample)
                    if (index == currentIndex) {
                        d.setStroke((3 * dp).toInt(), android.graphics.Color.WHITE)
                    }
                }
                contentDescription = theme.nameZh
                tooltipText = theme.nameZh
                setOnClickListener {
                    if (index != ThemeManager.getSavedIndex(this@SettingsActivity)) {
                        ThemeManager.saveTheme(this@SettingsActivity, index)
                        toast("已切换主题【${theme.nameZh}】")
                        // 重启应用以全面生效
                        val intent = android.content.Intent(this@SettingsActivity,
                            com.localwriter.ui.main.MainActivity::class.java).apply {
                            flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK or
                                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(intent)
                    }
                }
            }
            container.addView(btn)
        }
    }

    private fun setupFontSizeSeekBar() {
        binding.seekBarFontSize.min = 10
        binding.seekBarFontSize.max = 28
        binding.seekBarFontSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.tvFontSizeValue.text = "${progress}sp"
                    viewModel.updateFontSize(progress)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun setupFontFamilyChips() {
        val fonts = listOf(
            "sans-serif" to "默认",
            "serif" to "衬线",
            "monospace" to "等宽",
            "sans-serif-condensed" to "紧凑"
        )
        binding.chipGroupFonts.removeAllViews()
        fonts.forEach { (fontName, label) ->
            val chip = com.google.android.material.chip.Chip(this).apply {
                text = label
                isCheckable = true
                tag = fontName
                setOnClickListener { viewModel.updateFontFamily(fontName) }
            }
            binding.chipGroupFonts.addView(chip)
        }
    }

    private fun setupBackgroundPresets() {
        // 预设背景色方案
        val presets = listOf(
            "仿古纸"   to 0xFFF5E6D3.toInt(),
            "护眼绿"   to 0xFFCCE8CF.toInt(),
            "夜间黑"   to 0xFF1A1A2E.toInt(),
            "天空蓝"   to 0xFFE8F4FD.toInt(),
            "薰衣草"   to 0xFFF3E5F5.toInt(),
            "纯白"     to 0xFFFFFFFF.toInt()
        )
        binding.chipGroupBg.removeAllViews()
        presets.forEach { (name, color) ->
            val chip = com.google.android.material.chip.Chip(this).apply {
                text = name
                isCheckable = true
                setChipBackgroundColorResource(android.R.color.transparent)
                setOnClickListener {
                    viewModel.updateBackground(color = color)
                    updatePreview(color)
                }
            }
            binding.chipGroupBg.addView(chip)
        }

        // 自定义图片背景
        binding.btnPickBgImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
    }

    private fun setupColorPicker() {
        binding.btnTextColor.setOnClickListener {
            showColorPickerDialog()
        }
    }

    /** 内嵌颜色选择器（不依赖第三方库）*/
    private fun showColorPickerDialog() {
        val currentColor = viewModel.settings.value?.textColor ?: 0xFF333333.toInt()
        val presets = listOf(
            "深黑" to 0xFF1A1A1A.toInt(),
            "深灰" to 0xFF333333.toInt(),
            "灰色" to 0xFF666666.toInt(),
            "淡灰" to 0xFF999999.toInt(),
            "茶色" to 0xFF5D4037.toInt(),
            "深蓝" to 0xFF1A237E.toInt(),
            "深绿" to 0xFF1B5E20.toInt(),
            "白色" to 0xFFFFFFFF.toInt()
        )
        val names = presets.map { it.first }.toTypedArray()
        val colors = presets.map { it.second }
        val currentIdx = colors.indexOfFirst { it == currentColor }.coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("选择正文字色")
            .setSingleChoiceItems(names, currentIdx) { dialog, which ->
                val color = colors[which]
                viewModel.updateTextColor(color)
                binding.tvPreviewText.setTextColor(color)
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun setupAutoSave() {
        binding.switchShowWordCount.setOnCheckedChangeListener { _, checked ->
            viewModel.updateShowWordCount(checked)
        }
        binding.seekBarAutoSave.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val seconds = progress * 10  // 0,10,20,...,300 秒
                    binding.tvAutoSaveValue.text = if (seconds == 0) "不自动保存" else "${seconds}秒"
                    viewModel.updateAutoSave(seconds)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun setupSecuritySection() {
        binding.btnChangePassword.setOnClickListener { showChangePasswordDialog() }
        binding.btnSetGesture.setOnClickListener    { showSetGestureDialog() }
        binding.switchBiometric.setOnCheckedChangeListener { _, checked ->
            viewModel.enableBiometric(checked)
        }
        // 无密码模式开关
        binding.switchNoPassword.isChecked = SessionManager.isNoPasswordMode(this)
        binding.switchNoPassword.setOnCheckedChangeListener { _, checked ->
            SessionManager.setNoPasswordMode(this, checked)
            val msg = if (checked) "已开启无密码模式，下次启动无需验证"
                      else "已关闭无密码模式，请确保已设置密码"
            toast(msg)
        }
    }

    /** 设置后台自动锁定超时 Spinner */
    private fun setupLockTimeoutSpinner() {
        // 选项：显示文字 + 对应分钟数（-1=从不）
        val options = listOf(
            "立即锁定" to 0,
            "1 分钟" to 1,
            "5 分钟" to 5,
            "15 分钟" to 15,
            "30 分钟" to 30,
            "1 小时" to 60,
            "从不自动锁定" to -1
        )
        val labels = options.map { it.first }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerLockTimeout.adapter = adapter

        // 选中当前已保存的超时
        val currentMinutes = SessionManager.getLockTimeout(this)
        val currentIdx = options.indexOfFirst { it.second == currentMinutes }.let { if (it < 0) 2 else it }
        binding.spinnerLockTimeout.setSelection(currentIdx, false)

        binding.spinnerLockTimeout.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, pos: Int, id: Long) {
                SessionManager.setLockTimeout(this@SettingsActivity, options[pos].second)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    /** 修改密码对话框 */
    private fun showChangePasswordDialog() {
        val ctx = this
        val dp8 = (8 * resources.displayMetrics.density).toInt()

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp8 * 3, dp8, dp8 * 3, dp8)
        }
        val etOld = EditText(ctx).apply {
            hint = "原密码"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val etNew = EditText(ctx).apply {
            hint = "新密码（至少6位）"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val etConfirm = EditText(ctx).apply {
            hint = "确认新密码"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val spacer = { android.view.View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp8)
        }}
        container.addView(etOld)
        container.addView(spacer())
        container.addView(etNew)
        container.addView(spacer())
        container.addView(etConfirm)

        AlertDialog.Builder(ctx)
            .setTitle("修改密码")
            .setView(container)
            .setPositiveButton("确定") { _, _ ->
                val old     = etOld.text.toString()
                val newPwd  = etNew.text.toString()
                val confirm = etConfirm.text.toString()
                when {
                    old.isEmpty()          -> toast("请输入原密码")
                    newPwd.length < 6      -> toast("新密码至少6位")
                    newPwd != confirm      -> toast("两次输入不一致")
                    else -> {
                        val userId = SessionManager.getUserId(this)
                        lifecycleScope.launch {
                            val repo = (application as LocalWriterApp).authRepository
                            val ok = repo.changePassword(userId, old, newPwd)
                            if (ok) toast("密码修改成功")
                            else   toast("原密码错误")
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 设置手势密码 */
    private fun showSetGestureDialog() {
        val userId = SessionManager.getUserId(this)
        if (userId == -1L) { toast("请先登录"); return }

        // 先创建容器和对话框，show() 之后再加 Fragment（否则 container 不在 window 层级里）
        val containerId = android.view.View.generateViewId()
        val container = android.widget.FrameLayout(this).also { it.id = containerId }

        val dialog = AlertDialog.Builder(this)
            .setTitle("设置手势密码")
            .setView(container)
            .setNegativeButton("取消", null)
            .create()

        // 必须先 show，container 才会被加入 window 层级
        dialog.show()

        val fragment = GestureLoginFragment.newInstance(userId, isSetup = true)
        fragment.callback = object : GestureLoginFragment.GestureCallback {
            override fun onGestureComplete(pattern: String) {
                lifecycleScope.launch {
                    val repo = (application as LocalWriterApp).authRepository
                    repo.setGesturePattern(userId, pattern)
                    dialog.dismiss()
                    toast("手势密码已设置")
                }
            }
        }

        supportFragmentManager.beginTransaction()
            .replace(containerId, fragment)
            .commitAllowingStateLoss()
    }

    private fun observeSettings() {
        viewModel.settings.observe(this) { settings ->
            settings ?: return@observe
            binding.seekBarFontSize.progress = settings.fontSize
            binding.tvFontSizeValue.text = "${settings.fontSize}sp"
            binding.switchShowWordCount.isChecked = settings.showWordCount
            val autoSaveSteps = settings.autoSaveInterval / 10
            binding.seekBarAutoSave.progress = autoSaveSteps
            binding.tvAutoSaveValue.text = if (settings.autoSaveInterval == 0) "不自动保存"
                else "${settings.autoSaveInterval}秒"
            // 高亮当前字体 Chip
            for (i in 0 until binding.chipGroupFonts.childCount) {
                val chip = binding.chipGroupFonts.getChildAt(i) as? com.google.android.material.chip.Chip
                chip?.isChecked = chip?.tag == settings.fontFamily
            }
            updatePreview(settings.backgroundColor)
        }
    }

    private fun setupAboutSection() {
        // 版本号从 BuildConfig 读取，与打包版本保持一致
        binding.tvAboutVersion.text = "版本 ${BuildConfig.VERSION_NAME}"

        binding.llAboutRepo.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.AUTHOR_GITHUB))
            startActivity(intent)
        }
    }

    private fun saveBackgroundImage(uri: Uri) {
        // 复制图片到应用私有目录
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return
            val file = java.io.File(filesDir, "bg_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { output -> inputStream.copyTo(output) }
            viewModel.updateBackground(imagePath = file.absolutePath)
            Toast.makeText(this, "背景图片已设置", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "设置失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePreview(backgroundColor: Int) {
        binding.cardPreview.setCardBackgroundColor(backgroundColor)
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onResume() {
        super.onResume()
        if (SessionManager.isLoggedIn(this) && SessionManager.isLocked(this)) {
            startActivity(Intent(this, AuthActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
            finish()
            return
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
