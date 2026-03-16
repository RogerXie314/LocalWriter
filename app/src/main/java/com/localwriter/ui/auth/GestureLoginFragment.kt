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
 * 手势宫格逻辑：
 * - 3x3 宫格，节点编号 0-8（从左到右，从上到下）
 * - 至少连接 4 个节点才有效
 * - 绘制后自动延迟清除
 */
class GestureLoginFragment : Fragment() {

    private var _binding: FragmentGestureLoginBinding? = null
    private val binding get() = _binding!!

    private var userId: Long = 0
    private var isSetup: Boolean = false

    // 手势宫格视图（实际使用第三方 GestureLockView 或自定义 View）
    // 在真实实现中，这里使用 com.github.ihsanbal.GestureLockView 或自定义
    private var firstPattern: String? = null  // 用于二次确认

    interface GestureCallback {
        fun onGestureComplete(pattern: String)
    }

    var callback: GestureCallback? = null

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

        binding.tvGestureHint.text = when {
            isSetup && firstPattern == null -> "请绘制手势密码（至少连接4个点）"
            isSetup && firstPattern != null -> "请再次绘制确认"
            else                            -> "请绘制手势密码解锁"
        }

        // GestureView 回调（示例：与第三方库集成）
        // gestureLockView.setOnPatternListener(...)
        // 在真实代码中连接手势回调，这里用按钮模拟流程说明
        binding.btnGestureDemo.setOnClickListener {
            // 在实际实现中，这里是手势宫格的完成回调
            // 收到节点序列后转为字符串，如 "0-3-6-7-8"
            onPatternComplete("0-1-2-5-8")
        }

        binding.tvSwitchToPassword.setOnClickListener {
            parentFragmentManager.popBackStack()
            (activity as? AuthActivity)?.let {
                // 切换回密码登录
            }
        }
    }

    private fun onPatternComplete(pattern: String) {
        val nodes = pattern.split("-").size
        if (nodes < MIN_NODES) {
            Toast.makeText(context, "节点不足 $MIN_NODES 个，请重试", Toast.LENGTH_SHORT).show()
            return
        }

        if (isSetup) {
            if (firstPattern == null) {
                firstPattern = pattern
                binding.tvGestureHint.text = "请再次绘制确认"
            } else {
                if (firstPattern == pattern) {
                    callback?.onGestureComplete(pattern)
                    Toast.makeText(context, "手势密码设置成功", Toast.LENGTH_SHORT).show()
                } else {
                    firstPattern = null
                    binding.tvGestureHint.text = "两次不一致，请重新绘制"
                    Toast.makeText(context, "两次手势不一致，请重试", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            // 验证模式：通知 ViewModel
            (activity as? AuthActivity)?.let {
                (it as? AuthActivity)
                // ViewModel 中调用 loginWithGesture
            }
            callback?.onGestureComplete(pattern)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
