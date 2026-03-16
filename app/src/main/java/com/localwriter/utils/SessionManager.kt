package com.localwriter.utils

import android.content.Context

/**
 * 会话管理器：保存当前登录的用户ID
 * 使用 EncryptedSharedPreferences 加密持久化
 */
object SessionManager {

    private const val KEY_USER_ID = "session_user_id"
    private const val KEY_IS_LOCKED = "session_locked"
    private const val NO_USER = -1L

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
            .apply()
    }

    /** 锁定（退后台时调用，下次进入需重新验证）*/
    fun lock(context: Context) {
        SecurityUtils.getEncryptedPrefs(context).edit()
            .putBoolean(KEY_IS_LOCKED, true)
            .apply()
    }

    fun unlock(context: Context) {
        SecurityUtils.getEncryptedPrefs(context).edit()
            .putBoolean(KEY_IS_LOCKED, false)
            .apply()
    }

    fun isLocked(context: Context): Boolean {
        return SecurityUtils.getEncryptedPrefs(context)
            .getBoolean(KEY_IS_LOCKED, false)
    }
}
