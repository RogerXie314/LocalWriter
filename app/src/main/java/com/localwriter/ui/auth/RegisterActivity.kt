package com.localwriter.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.localwriter.LocalWriterApp
import com.localwriter.databinding.ActivityRegisterBinding
import com.localwriter.ui.main.MainActivity
import com.localwriter.utils.SessionManager
import com.localwriter.utils.ThemeManager
import kotlinx.coroutines.launch

/**
 * 注册界面
 * 注册步骤：
 * 1. 填写用户名 + 密码（确认密码）
 * 2. 可选：设置手势密码
 * 3. 可选：启用生物识别
 */
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val viewModel: AuthViewModel by viewModels {
        AuthViewModel.Factory((application as LocalWriterApp).authRepository)
    }

    private var newUserId: Long = -1L
    private var currentStep = STEP_BASIC
    private var gestureSaved = false

    companion object {
        private const val STEP_BASIC = 1
        private const val STEP_GESTURE = 2
        private const val STEP_BIOMETRIC = 3
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupStepper()
        setupListeners()
        setupObservers()
    }

    private fun setupStepper() {
        showStep(STEP_BASIC)
    }

    private fun showStep(step: Int) {
        currentStep = step
        binding.stepBasic.visibility = if (step == STEP_BASIC) View.VISIBLE else View.GONE
        binding.stepGesture.visibility = if (step == STEP_GESTURE) View.VISIBLE else View.GONE
        binding.stepBiometric.visibility = if (step == STEP_BIOMETRIC) View.VISIBLE else View.GONE

        when (step) {
            STEP_BASIC -> binding.tvStepTitle.text = "创建账号（第1步/3步）"
            STEP_GESTURE -> binding.tvStepTitle.text = "设置手势密码（可选，第2步/3步）"
            STEP_BIOMETRIC -> binding.tvStepTitle.text = "生物识别（可选，第3步/3步）"
        }
    }

    private fun setupListeners() {
        // 第一步：基本注册
        binding.btnRegister.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString()
            val confirm = binding.etConfirmPassword.text.toString()

            when {
                username.isEmpty() -> Toast.makeText(this, "请输入用户名", Toast.LENGTH_SHORT).show()
                username.length < 2 -> Toast.makeText(this, "用户名至少 2 个字符", Toast.LENGTH_SHORT).show()
                password.length < 6 -> Toast.makeText(this, "密码至少 6 位", Toast.LENGTH_SHORT).show()
                password != confirm -> Toast.makeText(this, "两次密码不一致", Toast.LENGTH_SHORT).show()
                else -> viewModel.register(username, password)
            }
        }

        // 跳过登录：无密码模式，使用中文默认昵称
        binding.btnSkipAuth.setOnClickListener {
            lifecycleScope.launch {
                val authRepo = (application as LocalWriterApp).authRepository
                val username = "我的空间"
                val password = java.util.UUID.randomUUID().toString()
                val userId = authRepo.register(username, password)
                if (userId > 0) {
                    SessionManager.saveUserId(this@RegisterActivity, userId)
                    SessionManager.setNoPasswordMode(this@RegisterActivity, true)
                    SessionManager.unlock(this@RegisterActivity)
                    goToMain()
                } else {
                    Toast.makeText(this@RegisterActivity, "初始化失败，请重试", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 第二步：手势设置（跳过/确认）
        binding.btnSkipGesture.setOnClickListener { showStep(STEP_BIOMETRIC) }
        binding.btnConfirmGesture.setOnClickListener {
            if (!gestureSaved) {
                Toast.makeText(this, "请先绘制手势密码", Toast.LENGTH_SHORT).show()
            } else {
                showStep(STEP_BIOMETRIC)
            }
        }

        // 第三步：生物识别（跳过/完成）
        binding.btnSkipBiometric.setOnClickListener { goToMain() }
        binding.btnEnableBiometric.setOnClickListener {
            // 开启生物识别后直接进入主界面
            goToMain()
        }
    }

    private fun setupObservers() {
        viewModel.state.observe(this) { state ->
            when (state) {
                is AuthViewModel.AuthState.Success -> {
                    newUserId = state.userId
                    SessionManager.saveUserId(this, newUserId)
                    // 注册成功，进入手势设置步骤
                    showStep(STEP_GESTURE)
                    // 加载手势设置 Fragment，并绑定回调以自动保存 pattern
                    val gestureFragment = GestureLoginFragment.newInstance(newUserId, isSetup = true)
                    gestureFragment.callback = object : GestureLoginFragment.GestureCallback {
                        override fun onGestureComplete(pattern: String) {
                            lifecycleScope.launch {
                                val authRepo = (application as LocalWriterApp).authRepository
                                authRepo.setGesturePattern(newUserId, pattern)
                                gestureSaved = true
                                Toast.makeText(this@RegisterActivity, "手势密码已保存", Toast.LENGTH_SHORT).show()
                                // 自动进入下一步
                                showStep(STEP_BIOMETRIC)
                            }
                        }
                    }
                    supportFragmentManager.beginTransaction()
                        .replace(binding.flGestureHolder.id, gestureFragment)
                        .commit()
                }
                is AuthViewModel.AuthState.Error -> {
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }
    }

    private fun goToMain() {
        SessionManager.unlock(this)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
