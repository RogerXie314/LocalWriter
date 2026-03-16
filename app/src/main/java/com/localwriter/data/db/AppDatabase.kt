package com.localwriter.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.localwriter.data.db.dao.*
import com.localwriter.data.db.entity.*

/**
 * Room 数据库
 *
 * 版本升级规则（保证数据不丢失）：
 *  - 每次修改 entity 结构必须 version+1，并在下方 MIGRATIONS 列表中补充对应
 *    Migration 对象（只写 ALTER TABLE / CREATE TABLE 等）。
 *  - 禁止使用 fallbackToDestructiveMigration()，生产用户数据不可破坏性重建。
 *
 * 当前版本：1（初始版本）
 *
 * 示例（未来版本升级时仿照添加）：
 *   val MIGRATION_1_2 = object : Migration(1, 2) {
 *       override fun migrate(db: SupportSQLiteDatabase) {
 *           db.execSQL("ALTER TABLE books ADD COLUMN subtitle TEXT NOT NULL DEFAULT ''")
 *       }
 *   }
 */
@Database(
    entities = [User::class, Book::class, Volume::class, Chapter::class, UserSettings::class],
    version = 1,
    exportSchema = true   // 生产应用应导出 schema，便于 code review 和回滚比对
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun bookDao(): BookDao
    abstract fun volumeDao(): VolumeDao
    abstract fun chapterDao(): ChapterDao
    abstract fun userSettingsDao(): UserSettingsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * 所有版本迁移脚本按序列在此注册。
         * 每次 version 升级时把对应 MIGRATION_x_y 添加到这个数组中。
         */
        private val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            // MIGRATION_1_2,  // 未来版本在此追加
        )

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "localwriter.db"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    // 注意：不添加 fallbackToDestructiveMigration()
                    // 如果遗漏了迁移脚本，Room 会抛出异常而非静默删库。
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
