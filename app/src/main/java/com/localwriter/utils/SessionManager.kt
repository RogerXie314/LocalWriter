package com.localwriter.utils

import android.content.Context

/**
 * 会话管理器：保存当前登录的用户ID
 * 使用 EncryptedSharedPreferences 加密持久化
 *
 * 宽限期逻辑：
 *  - 退出前台时调用 lock()，记录锁定时间戳
 *  - isLocked() 如果距离上次锁定 < GRACE_PERIOD_MS，返回 false（不需要重新验证）
 *  - 超过宽限期或首次锁定后返回 true
 */
object SessionManager {

    private const val KEY_USER_ID = "session_user_id"
    private const val KEY_IS_LOCKED = "session_locked"
    private const val KEY_LOCK_TIMESTAMP = "session_lock_timestamp"
    /** 锁定超时分钟数，-1 = 从不自动锁定，0 = 立即锁定 */
    private const val KEY_LOCK_TIMEOUT_MINUTES = "lock_timeout_minutes"
    /** 无密码模式：开启后启动无需任何验证 */
    private const val KEY_NO_PASSWORD_MODE = "no_password_mode"
    private const val NO_USER = -1L

    /** 默认 5 分钟，用于首次安装时 */
    private const val DEFAULT_LOCK_TIMEOUT_MINUTES = 5

    // ─── 超时设置 ─────────────────────────────────────────────────────────

    /**
     * 设置锁定超时分钟数
     * @param minutes -1=从不自动锁定, 0=立即锁定, 正整数=N分钟后锁定
     */
    fun setLockTimeout(context: Context, minutes: Int) {
        SecurityUtils.getEncryptedPrefs(context).edit()
            .putInt(KEY_LOCK_TIMEOUT_MINUTES, minutes)
            .apply()
    }

    /** 读取当前设置的锁定超时分钟数 */
    fun getLockTimeout(context: Context): Int {
        return SecurityUtils.getEncryptedPrefs(context)
            .getInt(KEY_LOCK_TIMEOUT_MINUTES, DEFAULT_LOCK_TIMEOUT_MINUTES)
    }

    // ─── 无密码模式 ──────────────────────────────────────────────────────────

    /** 设置是否启用无密码直接使用模式 */
    fun setNoPasswordMode(context: Context, enabled: Boolean) {
        SecurityUtils.getEncryptedPrefs(context).edit()
            .putBoolean(KEY_NO_PASSWORD_MODE, enabled)
            .apply()
    }

    /** 是否处于无密码模式（启动无需验证） */
    fun isNoPasswordMode(context: Context): Boolean {
        return SecurityUtils.getEncryptedPrefs(context)
            .getBoolean(KEY_NO_PASSWORD_MODE, false)
    }

    fun saveUserId(context: Context, userId: Long) {
        SecurityUtils.getEncryptedPrefs(context).edit()
            .putLong(KEY_USER_ID, userId)
            .apply()
    }

    fun getUserId(context: Context): Long {
        return SecurityUtils.getEncryptedPrefs(context)
            .getLong(KEY_USER_ID, NO_USER)
    }

    fun isLoggedIn(context: Context): Boolean = getUserId(context) != NO_USER

    fun logout(context: Context) {
        SecurityUtils.getEncryptedPrefs(context).edit()
            .putLong(KEY_USER_ID, NO_USER)
            .putBoolean(KEY_IS_LOCKED, false)
            .putLong(KEY_LOCK_TIMESTAMP, 0L)
            .apply()
    }

    /** 锁定（退后台时调用），记录时间戳供宽限期判断 */
    fun lock(context: Context) {
        SecurityUtils.getEncryptedPrefs(context).edit()
            .putBoolean(KEY_IS_LOCKED, true)
            .putLong(KEY_LOCK_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    fun unlock(context: Context) {
        SecurityUtils.getEncryptedPrefs(context).edit()
            .putBoolean(KEY_IS_LOCKED, false)
            .putLong(KEY_LOCK_TIMESTAMP, 0L)
            .apply()
    }

    /**
     * 是否需要重新验证。
     * 根据用户设置的锁定超时判断：
     *  -1 = 从不自动锁定（但手动锁定仍有效）
     *   0 = 立即锁定（切换后台即需验证）
     *   N = N 分钟宽限期
     */
    fun isLocked(context: Context): Boolean {
        // 无密码模式：永不锁定
        if (isNoPasswordMode(context)) {
            unlock(context)
            return false
        }
        val prefs = SecurityUtils.getEncryptedPrefs(context)
        val locked = prefs.getBoolean(KEY_IS_LOCKED, false)
        if (!locked) return false

        val timeoutMinutes = getLockTimeout(context)

        // 从不自动锁定（-1）：宽限期无限，自动解锁
        if (timeoutMinutes < 0) {
            unlock(context)
            return false
        }

        // 立即锁定（0）：锁定时间戳一设就需要验证
        if (timeoutMinutes == 0) return true

        val lockTimestamp = prefs.getLong(KEY_LOCK_TIMESTAMP, 0L)
        val elapsed = System.currentTimeMillis() - lockTimestamp
        val gracePeriodMs = timeoutMinutes * 60 * 1000L
        return if (elapsed < gracePeriodMs) {
            // 在宽限期内，自动解锁
            unlock(context)
            false
        } else {
            true
        }
    }
}
