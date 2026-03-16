package com.localwriter.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 书籍实体
 * 书籍管理逻辑：
 *  - 每本书属于某个用户
 *  - 状态分：连载中(ONGOING) / 已完结(FINISHED) / 暂停(PAUSED)
 *  - 支持分类/标签（逗号分隔字符串）
 *  - 封面存储为本地文件绝对路径
 */
@Entity(
    tableName = "books",
    foreignKeys = [ForeignKey(
        entity = User::class,
        parentColumns = ["id"],
        childColumns = ["userId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("userId")]
)
data class Book(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long,
    val title: String,
    val author: String = "",
    val description: String = "",
    /** 封面本地路径（可为空使用默认封面）*/
    val coverPath: String? = null,
    /** 书籍状态: ONGOING / FINISHED / PAUSED */
    val status: String = "ONGOING",
    /** 分类标签，逗号分隔，如 "玄幻,仙侠" */
    val tags: String = "",
    /** 当前正在阅读/编辑的章节ID（-1表示未开始）*/
    val lastChapterId: Long = -1L,
    /** 字数统计 */
    val wordCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** 排序权重，数字小的在前（支持手动拖拽排序）*/
    val sortOrder: Int = 0
)
