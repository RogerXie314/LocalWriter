package com.localwriter.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.localwriter.LocalWriterApp
import com.localwriter.databinding.ActivityAuthBinding
import com.localwriter.ui.main.MainActivity
import com.localwriter.utils.SessionManager

/**
 * 认证界面
 * 支持：密码登录 / 手势登录 / 面容ID/指纹登录
 * 根据用户设置的 preferredLockType 自动展示对应模块
 */
class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private val viewModel: AuthViewModel by viewModels {
        AuthViewModel.Factory((application as LocalWriterApp).authRepository)
    }

    /** 缓存当前已加载用户 ID，用于切换到手势登录时传参 */
    private var currentUserId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupObservers()
        setupListeners()
        viewModel.init()
    }

    private fun setupObservers() {
        viewModel.state.observe(this) { state ->
            when (state) {
                is AuthViewModel.AuthState.Loading -> showLoading(true)

                is AuthViewModel.AuthState.NoUser -> {
                    showLoading(false)
                    // 没有注册用户，跳转注册
                    startActivity(Intent(this, RegisterActivity::class.java))
                    finish()
                }

                is AuthViewModel.AuthState.NeedAuth -> {
                    showLoading(false)
                    val user = state.user
                    currentUserId = user.id   // 保存 userId 供手势切换使用
                    binding.tvUsername.text = "欢迎回来，${user.username}"

                    when (user.preferredLockType) {
                        "GESTURE" -> showGestureLogin(user.id)
                        "BIOMETRIC" -> {
                            if (isBiometricAvailable()) {
                                showBiometricPrompt()
                            } else {
                                showPasswordLogin()
                            }
                        }
                        else -> showPasswordLogin()
                    }
                }

                is AuthViewModel.AuthState.Success -> {
                    SessionManager.saveUserId(this, state.userId)
                    SessionManager.unlock(this)
                    goToMain()
                }

                is AuthViewModel.AuthState.Error -> {
                    showLoading(false)
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString()
            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "请填写用户名和密码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.loginWithPassword(username, password)
        }

        binding.tvSwitchToPassword.setOnClickListener {
            showPasswordLogin()
        }

        binding.tvSwitchToGesture.setOnClickListener {
            // 切换到手势登录，复用 showGestureLogin 以正确加载 Fragment
            if (currentUserId != -1L) {
                showGestureLogin(currentUserId)
            } else {
                Toast.makeText(this, "请先用密码登录", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnBiometric.setOnClickListener {
            showBiometricPrompt()
        }
    }

    private fun showPasswordLogin() {
        binding.clPassword.visibility = View.VISIBLE
        binding.flGestureContainer.visibility = View.GONE
        binding.btnBiometric.visibility = View.GONE
        // 已在密码登录，隐藏"密码登录"入口；显示"手势登录"入口
        binding.tvSwitchToPassword.visibility = View.GONE
        binding.tvSwitchToGesture.visibility = View.VISIBLE
    }

    private fun showGestureLogin(userId: Long) {
        binding.clPassword.visibility = View.GONE
        binding.flGestureContainer.visibility = View.VISIBLE
        binding.btnBiometric.visibility = View.GONE
        // 已在手势登录，隐藏"手势登录"入口；显示"密码登录"入口
        binding.tvSwitchToGesture.visibility = View.GONE
        binding.tvSwitchToPassword.visibility = View.VISIBLE

        // 加载手势输入 Fragment
        val fragment = GestureLoginFragment.newInstance(userId, isSetup = false)
        supportFragmentManager.beginTransaction()
            .replace(binding.flGestureContainer.id, fragment)
            .commit()
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                // 生物识别成功，获取当前用户ID（从 SessionManager 或 DB 首个用户）
                val userId = SessionManager.getUserId(this@AuthActivity).let {
                    if (it == -1L) 1L else it
                }
                SessionManager.saveUserId(this@AuthActivity, userId)
                SessionManager.unlock(this@AuthActivity)
                goToMain()
            }
            override fun onAuthenticationError(code: Int, errString: CharSequence) {
                Toast.makeText(this@AuthActivity, "生物识别失败：$errString", Toast.LENGTH_SHORT).show()
                showPasswordLogin()
            }
            override fun onAuthenticationFailed() {
                Toast.makeText(this@AuthActivity, "识别失败，请重试", Toast.LENGTH_SHORT).show()
            }
        }

        val prompt = BiometricPrompt(this, executor, callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("生物识别解锁")
            .setSubtitle("使用面容 ID 或指纹解锁 LocalWriter")
            .setNegativeButtonText("使用密码")
            .build()
        prompt.authenticate(info)
    }

    private fun isBiometricAvailable(): Boolean {
        val manager = BiometricManager.from(this)
        return manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
