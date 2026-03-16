package com.localwriter.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * 密码安全工具
 * - 使用 SHA-256 + 随机盐值 哈希密码
 * - 使用 EncryptedSharedPreferences 存储会话 Token
 */
object SecurityUtils {

    /** 生成随机盐值（16字节，Base64编码）*/
    fun generateSalt(): String {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return Base64.getEncoder().encodeToString(salt)
    }

    /** SHA-256 哈希密码（附加 PEPPER 防彩虹表）*/
    fun hashPassword(password: String, salt: String): String {
        val pepper = "LW_PEPPER_2024_LOCAL"
        val input = "$salt$password$pepper"
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(hash)
    }

    /** 验证密码 */
    fun verifyPassword(input: String, salt: String, storedHash: String): Boolean {
        return hashPassword(input, salt) == storedHash
    }

    /** 手势密码 Hash（与密码逻辑相同）*/
    fun hashGesture(pattern: String, salt: String): String = hashPassword(pattern, salt)

    fun verifyGesture(input: String, salt: String, storedHash: String): Boolean =
        verifyPassword(input, salt, storedHash)

    /** 获取加密 SharedPreferences（单例，避免重复构建 MasterKey 开销）*/
    @Volatile private var encryptedPrefsInstance: SharedPreferences? = null

    fun getEncryptedPrefs(context: Context): SharedPreferences {
        return encryptedPrefsInstance ?: synchronized(this) {
            encryptedPrefsInstance ?: run {
                val masterKey = MasterKey.Builder(context.applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context.applicationContext,
                    "lw_secure_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                ).also { encryptedPrefsInstance = it }
            }
        }
    }
}
