package com.localwriter.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 章节实体
 * 章节管理逻辑：
 *  - 章节归属于某卷（volumeId），卷归属于书
 *  - 章节状态：DRAFT（草稿）/ PUBLISHED（已发布）/ DELETED（回收站）
 *  - 支持字数统计
 *  - 支持拖拽排序（sortOrder）
 *  - 删除章节先移入回收站，30天后自动清理
 *  - 内容存储为纯文本（富文本用Markdown/HTML视未来扩展）
 */
@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = Volume::class,
            parentColumns = ["id"],
            childColumns = ["volumeId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("volumeId"), Index("bookId")]
)
data class Chapter(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: Long,
    val volumeId: Long,
    val title: String,
    /** 正文内容（纯文本，换行符保留）*/
    val content: String = "",
    /** 章节状态: DRAFT / PUBLISHED / DELETED */
    val status: String = "DRAFT",
    val wordCount: Int = 0,
    /** 上次编辑光标位置，下次打开时恢复 */
    val lastCursorPos: Int = 0,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** 进入回收站时间 */
    val deletedAt: Long? = null
)
