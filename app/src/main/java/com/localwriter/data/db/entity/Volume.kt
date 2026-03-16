package com.localwriter.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 卷（Volume）实体 —— 章节分卷管理
 * 章节管理逻辑：
 *  书 → 卷（可选）→ 章节
 *  - 卷是可选层级，可以只有默认卷"第一卷"
 *  - 卷支持拖拽排序
 */
@Entity(
    tableName = "volumes",
    foreignKeys = [ForeignKey(
        entity = Book::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("bookId")]
)
data class Volume(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: Long,
    val title: String = "第一卷",
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
