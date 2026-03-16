<div align="center">

# 📖 LocalWriter

**纯本地化 Android 写作助手 · 无网络 · 数据完全离线**

[![Platform](https://img.shields.io/badge/Platform-Android-brightgreen?logo=android)](https://developer.android.com)
[![Min SDK](https://img.shields.io/badge/Min_SDK-26_(Android_8.0)-blue)](https://developer.android.com/about/versions/oreo)
[![Language](https://img.shields.io/badge/Language-Kotlin-orange?logo=kotlin)](https://kotlinlang.org)
[![Architecture](https://img.shields.io/badge/Architecture-MVVM-purple)](https://developer.android.com/topic/architecture)
[![License](https://img.shields.io/badge/License-MIT-lightgrey)](LICENSE)

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
| Min / Target SDK | 26 / 34 |

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

## 👤 作者

**Roger_xie**

> *"写作是对抗遗忘的最好方式。"*
