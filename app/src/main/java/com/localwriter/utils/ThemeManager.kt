package com.localwriter.utils

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.localwriter.R

/**
 * 应用主题管理器
 * 支持 5 种颜色主题，保存至 SharedPreferences。
 * 在每个 Activity 的 setContentView() 之前调用 [applyTheme]。
 */
object ThemeManager {

    private const val PREF_FILE  = "localwriter_prefs"
    private const val PREF_THEME = "app_theme_index"

    data class ThemeOption(
        val nameZh: String,
        val styleRes: Int,
        val colorSample: Int  // 用于 UI 预览色块（ARGB Int）
    )

    val themes = listOf(
        ThemeOption("棕色经典", R.style.Theme_LocalWriter,       0xFF5D4037.toInt()),
        ThemeOption("深蓝文墨", R.style.Theme_LocalWriter_Blue,  0xFF1565C0.toInt()),
        ThemeOption("翠竹青绿", R.style.Theme_LocalWriter_Green, 0xFF2E7D32.toInt()),
        ThemeOption("深夜暗黑", R.style.Theme_LocalWriter_Dark,  0xFF212121.toInt()),
        ThemeOption("玫瑰紫红", R.style.Theme_LocalWriter_Rose,  0xFFAD1457.toInt()),
    )

    fun getSavedIndex(context: Context): Int =
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
               .getInt(PREF_THEME, 0)

    fun saveTheme(context: Context, index: Int) {
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
               .edit().putInt(PREF_THEME, index).apply()
    }

    /**
     * 在 setContentView() 之前调用，以使 ?attr/colorPrimary 正确解析。
     */
    fun applyTheme(activity: AppCompatActivity) {
        val index = getSavedIndex(activity)
        activity.setTheme(themes[index].styleRes)
    }
}
