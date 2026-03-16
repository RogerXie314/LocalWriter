package com.localwriter.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 用户个性化设置
 * 包含：阅读/编辑界面外观、字体、背景等
 */
@Entity(
    tableName = "user_settings",
    foreignKeys = [ForeignKey(
        entity = User::class,
        parentColumns = ["id"],
        childColumns = ["userId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("userId", unique = true)]
)
data class UserSettings(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long,

    // ---------- 字体相关 ----------
    /** 字体大小 sp，默认 16 */
    val fontSize: Int = 16,
    /** 字体名，如 "serif" / "sans-serif" / "monospace" 或系统字体路径 */
    val fontFamily: String = "sans-serif",
    /** 行高倍数，默认 1.8 */
    val lineSpacing: Float = 1.8f,
    /** 字间距 em，默认 0.05 */
    val letterSpacing: Float = 0.05f,

    // ---------- 背景/主题 ----------
    /**
     * 背景类型: COLOR（纯色）/ IMAGE（图片）/ PRESET（预设纸纹）
     */
    val backgroundType: String = "COLOR",
    /** 背景颜色 ARGB 整数 */
    val backgroundColor: Int = 0xFFF5E6D3.toInt(),
    /** 背景图片本地路径（backgroundType=IMAGE 时有效）*/
    val backgroundImagePath: String? = null,
    /** 预设背景名称: PAPER / BAMBOO / NIGHT / MINT */
    val backgroundPreset: String = "PAPER",
    /** 正文字色 */
    val textColor: Int = 0xFF333333.toInt(),

    // ---------- 布局相关 ----------
    /** 左侧导航栏宽度比例 0.0~0.5，默认 0.28 */
    val navPanelWidthRatio: Float = 0.28f,
    /** 是否显示左侧导航栏 */
    val navPanelVisible: Boolean = true,
    /** 内容区左右边距 dp */
    val contentPaddingHorizontal: Int = 16,
    val contentPaddingVertical: Int = 20,

    // ---------- 编辑器 ----------
    /** 自动保存间隔（秒），0表示不自动保存 */
    val autoSaveInterval: Int = 30,
    /** 是否显示字数统计 */
    val showWordCount: Boolean = true,

    val updatedAt: Long = System.currentTimeMillis()
)
