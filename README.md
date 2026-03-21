<div align="center">

# 📖 LocalWriter

**纯本地化 Android 写作助手 · 无网络 · 数据完全离线**

[![Platform](https://img.shields.io/badge/Platform-Android-brightgreen?logo=android)](https://developer.android.com)
[![Min SDK](https://img.shields.io/badge/Min_SDK-26_(Android_8.0)-blue)](https://developer.android.com/about/versions/oreo)
[![Language](https://img.shields.io/badge/Language-Kotlin-orange?logo=kotlin)](https://kotlinlang.org)
[![Architecture](https://img.shields.io/badge/Architecture-MVVM-purple)](https://developer.android.com/topic/architecture)
[![License](https://img.shields.io/badge/License-MIT-lightgrey)](LICENSE)
[![Build Debug APK](https://github.com/RogerXie314/LocalWriter/actions/workflows/build.yml/badge.svg)](https://github.com/RogerXie314/LocalWriter/actions/workflows/build.yml)

</div>

---

## ✨ 功能亮点

| 模块 | 功能 |
|---|---|
| 🔐 **多重身份验证** | 密码 / 九宫格手势 / 面容指纹，退后台自动锁定 |
| 📚 **书籍管理** | 书→卷→章三级结构，拖拽排序，连载状态管理 |
| ✍️ **沉浸式编辑器** | 全屏写作，快捷标点栏，撤销重做（200步），自动保存 |
| 📥 **多格式导入** | TXT / EPUB / DOCX / PDF / UMD，自动识别中文编码（GBK/UTF-8/BOM） |
| 📤 **多格式导出** | TXT / EPUB / PDF（内嵌中文字体）/ DOCX |
| 📖 **阅读器** | 章节导航，字体大小持久化，沉浸模式，书签（滚动自动保存+红色标记） |
| 🔖 **书签系统** | 下拉自动保存阅读进度，书签可视化红色标记，一键跳回 |
| 🔍 **全文搜索** | 搜索章节标题及正文，结果支持直接阅读或编辑 |
| 🗑️ **回收站** | 软删除 + 30天自动清理，支持批量恢复/清空 |
| 🎨 **个性化** | 背景/字色/字体/字号/行距自定义，UserSettings 跨界面同步 |

---

## 📸 界面预览

> *(截图待补充，运行后可在 `app-debug.apk` 中体验)*

---

## 🏗️ 技术栈

| 类别 | 技术 / 库 |
|---|---|
| 语言 | Kotlin 1.9.22，JVM Target 17 |
| 架构 | MVVM + Repository Pattern，ViewBinding |
| 数据库 | Room 2.6.1（SQLite），注解处理器 KSP |
| UI | Material Design 3，DayNight 双主题，响应式双栏布局（平板） |
| 安全 | EncryptedSharedPreferences (AES256-GCM)，BiometricPrompt，SHA-256 + 随机盐 |
| 异步 | kotlinx.coroutines 1.7.3，LiveData |
| 导入导出 | epublib-core · Apache POI · iTextPDF · Jsoup · juniversalchardet |
| Min / Target SDK | 26 / 36 |

---

## 🚀 快速开始

### 环境要求

- **Android Studio** Hedgehog 或更高版本（含 JDK 17）
- **Android SDK** API 26–34

### 克隆 & 构建

```bash
git clone https://github.com/Roger-xie/LocalWriter.git
cd LocalWriter
```

修改 `local.properties` 中的 SDK 路径：

```properties
sdk.dir=C\:\\Users\\<YourName>\\AppData\\Local\\Android\\Sdk
```

构建 Debug APK：

```bash
# Windows
.\gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```

输出：`app/build/outputs/apk/debug/app-debug.apk`

### 安装到手机

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

或直接将 APK 传输到手机，开启「允许安装未知来源」后安装。

---

## 📂 项目结构

```
LocalWriter/
├── app/src/main/
│   ├── java/com/localwriter/
│   │   ├── data/
│   │   │   ├── db/          # Room 实体 + DAO (User/Book/Volume/Chapter/UserSettings)
│   │   │   └── repository/  # Repository 层 (Auth/Book/Chapter/Settings)
│   │   ├── ui/
│   │   │   ├── auth/        # 登录 · 注册 · 手势解锁
│   │   │   ├── books/       # 书架列表
│   │   │   ├── chapters/    # 章节管理 (卷/章三级)
│   │   │   ├── editor/      # 写作编辑器
│   │   │   ├── reader/      # 阅读器
│   │   │   ├── settings/    # 设置
│   │   │   └── main/        # DrawerLayout 主界面
│   │   └── utils/
│   │       ├── SecurityUtils.kt
│   │       ├── SessionManager.kt
│   │       ├── UndoRedoHelper.kt
│   │       └── io/          # BookImporter · BookExporter · ChapterSplitter · EncodingDetector
│   └── res/                 # 布局 · 菜单 · 图标 · 主题 · 字符串
├── IMPLEMENTATION_NOTES.md  # 详细实施记录
├── build.gradle
└── settings.gradle
```

---

## 🔒 隐私说明

LocalWriter **不申请任何网络权限**，所有数据（书籍、章节、用户信息）均存储在设备本地 SQLite 数据库中，从不上传至任何服务器。

---

## 📄 许可证

```
MIT License

Copyright (c) 2026 Roger_xie

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
```

---

## � 更新日志

### v1.1.0（2026-03）

**Bug 修复**

- 修复阅读器退出后重新打开无法恢复滚动位置（新增 `lastScrollPos` 字段，Room DB 迁移 v1→v2）
- 修复生物识别验证时 userId 硬编码为 1 导致多账号下认证失败
- 修复 `SUM()` 在无章节时返回 NULL 引发 NPE 崩溃
- 修复九宫格手势 `postDelayed` 未取消导致的内存泄漏与状态混乱
- 修复锁屏按钮点击后未跳转至认证界面
- 修复词数统计遗漏 CJK 扩展 B 区汉字
- 修复 IME 中文输入法连续输入时触发过多撤销记录（新增 400ms 合并阈值）
- 修复编辑器字体大小初始为 0 导致的短暂闪烁
- 修复手势锁设置界面旋转屏幕后第一次图案丢失

**UX 改进**

- 卷标题背景改用 `?attr/colorSurfaceVariant`，正确支持深色模式
- 启用阅读器目录底部弹层的「书签」与「笔记」标签页（笔记功能开发中提示）
- 修复认证界面初次加载时「切换方式」文字闪现
- 编辑器保存失败时增加 Toast 提示
- 修复设置页生物识别开关状态未从数据库读取
- 修复锁定超时下拉框监听器注册顺序错误导致设置被意外重置

**工程**

- 新增 GitHub Actions CI 工作流（`build.yml`），推送后自动构建 Debug APK
- 修正 `gradle-wrapper.properties` 中的 Gradle 发行包 URL

---

## �👤 作者

**Roger_xie**

> *"写作是对抗遗忘的最好方式。"*
