package com.localwriter.data.repository

import com.localwriter.data.db.dao.UserDao
import com.localwriter.data.db.dao.UserSettingsDao
import com.localwriter.data.db.entity.User
import com.localwriter.data.db.entity.UserSettings
import com.localwriter.utils.SecurityUtils

class AuthRepository(
    private val userDao: UserDao,
    private val settingsDao: UserSettingsDao
) {

    /** 检查是否已有注册用户（本地单用户模式）*/
    suspend fun hasUser(): Boolean = userDao.count() > 0

    /**
     * 注册新用户
     * @return 成功返回用户ID，失败（用户名已存在）返回 -1
     */
    suspend fun register(username: String, password: String): Long {
        if (userDao.findByUsername(username) != null) return -1L
        val salt = SecurityUtils.generateSalt()
        val hash = SecurityUtils.hashPassword(password, salt)
        val user = User(username = username, passwordHash = hash, salt = salt)
        val userId = userDao.insert(user)
        // 同时创建默认设置
        settingsDao.insert(UserSettings(userId = userId))
        return userId
    }

    /**
     * 密码登录
     * @return 成功返回 User，失败返回 null
     */
    suspend fun loginWithPassword(username: String, password: String): User? {
        val user = userDao.findByUsername(username) ?: return null
        return if (SecurityUtils.verifyPassword(password, user.salt, user.passwordHash)) user else null
    }

    /**
     * 手势登录
     * @param patternInput 节点序号字符串，如 "0-1-2-5-8"
     */
    suspend fun loginWithGesture(userId: Long, patternInput: String): User? {
        val user = userDao.findById(userId) ?: return null
        val storedPattern = user.gesturePattern ?: return null
        // gesturePattern 存的是哈希值
        return if (SecurityUtils.verifyGesture(patternInput, user.salt, storedPattern)) user else null
    }

    /** 设置手势密码 */
    suspend fun setGesturePattern(userId: Long, pattern: String) {
        val user = userDao.findById(userId) ?: return
        val hashed = SecurityUtils.hashGesture(pattern, user.salt)
        userDao.update(user.copy(gesturePattern = hashed, preferredLockType = "GESTURE"))
    }

    /** 启用生物识别 */
    suspend fun enableBiometric(userId: Long, enabled: Boolean) {
        val user = userDao.findById(userId) ?: return
        userDao.update(user.copy(
            biometricEnabled = enabled,
            preferredLockType = if (enabled) "BIOMETRIC" else user.preferredLockType
        ))
    }

    /** 修改密码 */
    suspend fun changePassword(userId: Long, oldPwd: String, newPwd: String): Boolean {
        val user = userDao.findById(userId) ?: return false
        if (!SecurityUtils.verifyPassword(oldPwd, user.salt, user.passwordHash)) return false
        val newSalt = SecurityUtils.generateSalt()
        val newHash = SecurityUtils.hashPassword(newPwd, newSalt)
        userDao.update(user.copy(passwordHash = newHash, salt = newSalt))
        return true
    }

    suspend fun getUserById(userId: Long): User? = userDao.findById(userId)

    /** 获取本地单用户模式下的第一个用户 */
    suspend fun getFirstUser(): User? = userDao.getFirst()
}
