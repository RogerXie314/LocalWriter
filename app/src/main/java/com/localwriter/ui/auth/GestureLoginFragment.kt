package com.localwriter.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.localwriter.databinding.FragmentGestureLoginBinding

/**
 * 手势密码 Fragment
 * isSetup=true：设置手势密码（注册时使用）
 * isSetup=false：验证手势密码（登录时使用）
 *
 * 使用 [GesturePatternView] 显示 3×3 九宫格，通过手指滑动记录路径。
 * - 至少连接 4 个节点才有效
 */
class GestureLoginFragment : Fragment() {

    private var _binding: FragmentGestureLoginBinding? = null
    private val binding get() = _binding!!

    private var userId: Long = 0
    private var isSetup: Boolean = false

    /** 设置模式下存储第一次绘制的图案，用于二次确认 */
    private var firstPattern: String? = null

    interface GestureCallback {
        fun onGestureComplete(pattern: String)
    }

    var callback: GestureCallback? = null
    /** 点击「密码登录」时通知父界面切换 */
    var switchToPasswordCallback: (() -> Unit)? = null

    companion object {
        private const val ARG_USER_ID = "userId"
        private const val ARG_IS_SETUP = "isSetup"
        const val MIN_NODES = 4

        fun newInstance(userId: Long, isSetup: Boolean) = GestureLoginFragment().apply {
            arguments = Bundle().apply {
                putLong(ARG_USER_ID, userId)
                putBoolean(ARG_IS_SETUP, isSetup)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userId = arguments?.getLong(ARG_USER_ID) ?: 0
        isSetup = arguments?.getBoolean(ARG_IS_SETUP) ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGestureLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateHint()

        // 连接九宫格手势回调
        binding.gestureLockView.listener = object : GesturePatternView.OnPatternListener {
            override fun onPatternStarted() {
                // 开始绘制：清空上一次的提示
            }

            override fun onPatternComplete(pattern: List<Int>) {
                val patternStr = pattern.joinToString(separator = "-")
                onPatternComplete(patternStr)
            }

            override fun onPatternTooShort() {
                binding.tvGestureHint.text = "节点不足 $MIN_NODES 个，请重试"
            }
        }

        binding.tvSwitchToPassword.setOnClickListener {
            switchToPasswordCallback?.invoke()
        }
        // 设置模式（注册）时不需要切换按钮
        if (isSetup) {
            binding.tvSwitchToPassword.visibility = View.GONE
        }
    }

    private fun updateHint() {
        binding.tvGestureHint.text = when {
            isSetup && firstPattern == null -> "请绘制手势密码（至少连接4个点）"
            isSetup && firstPattern != null -> "请再次绘制确认"
            else                            -> "请绘制手势密码解锁"
        }
    }

    private fun onPatternComplete(pattern: String) {
        val nodeCount = pattern.split("-").size
        if (nodeCount < MIN_NODES) {
            binding.gestureLockView.showError()
            binding.tvGestureHint.text = "节点不足 $MIN_NODES 个，请重试"
            return
        }

        if (isSetup) {
            if (firstPattern == null) {
                // 第一次：记录，请求二次确认
                firstPattern = pattern
                binding.gestureLockView.showSuccess()
                postUpdateHint()
            } else {
                // 第二次：校验一致性
                if (firstPattern == pattern) {
                    binding.gestureLockView.showSuccess()
                    callback?.onGestureComplete(pattern)
                } else {
                    firstPattern = null
                    binding.gestureLockView.showError()
                    binding.tvGestureHint.text = "两次不一致，请重新绘制"
                    Toast.makeText(context, "两次手势不一致，请重试", Toast.LENGTH_SHORT).show()
                    postUpdateHint(delayMs = 1200)
                }
            }
        } else {
            // 验证模式：直接通知外层
            callback?.onGestureComplete(pattern)
        }
    }

    /** 延迟刷新提示文字（等待动画结束）*/
    private fun postUpdateHint(delayMs: Long = 800) {
        binding.root.postDelayed({ updateHint() }, delayMs)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
