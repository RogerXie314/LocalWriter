package com.localwriter.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 用户实体 —— 本地注册账号（仅限单设备）
 */
@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val username: String,
    /** SHA-256 加盐哈希后的密码 */
    val passwordHash: String,
    val salt: String,
    /** 手势密码："-"分隔的节点序号字符串 e.g. "0-1-2-5-8" */
    val gesturePattern: String? = null,
    /** 是否启用面容/指纹解锁 */
    val biometricEnabled: Boolean = false,
    /** 首选解锁方式：PASSWORD / GESTURE / BIOMETRIC */
    val preferredLockType: String = "PASSWORD",
    val createdAt: Long = System.currentTimeMillis()
)
