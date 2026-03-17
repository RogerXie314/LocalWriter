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
    private const val NO_USER = -1L

    /** 宽限期：5 分钟内切换应用不需要重新验证 */
    private const val GRACE_PERIOD_MS = 5 * 60 * 1000L

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
     * 在宽限期内（5 分钟）切换回前台不需要重新验证。
     */
    fun isLocked(context: Context): Boolean {
        val prefs = SecurityUtils.getEncryptedPrefs(context)
        val locked = prefs.getBoolean(KEY_IS_LOCKED, false)
        if (!locked) return false

        val lockTimestamp = prefs.getLong(KEY_LOCK_TIMESTAMP, 0L)
        val elapsed = System.currentTimeMillis() - lockTimestamp
        return if (elapsed < GRACE_PERIOD_MS) {
            // 在宽限期内，自动解锁
            unlock(context)
            false
        } else {
            true
        }
    }
}
