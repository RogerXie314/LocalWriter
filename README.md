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
- **Android SDK** API 26–36

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

## 📋 更新日志

### v1.6.8（2026-03）

- f5401fb refactor(reader): replace runtime snap with pre-pagination (pageBreaks table)

---

### v1.6.7（2026-03）

- d326a92 fix: forward+back page drift and whole-book page counter
- 2bad59a fix: 修正更新日志乱码，release 脚本强制 UTF-8 输出

---

### v1.6.6（2026-03）

- 修复：阅读底部无半行截断，整页翻页，底部额外留一行空白
- 修复：沉浸底部信息栏右侧改为页码（当前页/总页数），逐页计数
- 修复：目录"回收站"菜单白字不可见问题

---

### v1.6.5（2026-03）

- 修复：沉浸模式页眉页脚零遮挡，去除 elevation 阴影
- 修复：展示控制面板时双重遮挡问题（工具栏与沉浸栏不再同时可见）
- chore: release 脚本加入本地编译验证步骤

---

### v1.6.4（2026-03）

- 修复：GitHub Actions CI 构建失败，发布页长期停在旧版本的问题
- 修复：去掉 buildToolsVersion 硬编码（36.1.0），CI 环境与本地保持兼容

---

### v1.6.3（2026-03）

- 修复：沉浸模式顶部信息栏改为显示书名，消除与正文章节标题的重复
- 新增：沉浸模式左上角返回按钮，无需呼出工具栏即可退出阅读器
- 修复：底部进度改为全书百分比，短章节时不再出现每翻一页跳 10% 的情况
- 优化：翻页保留 10% 内容重叠，阅读不失位（参考番茄/起点交互规范）
- 优化：沉浸模式 padding 缩减，信息栏紧贴正文无多余空白

---

### v1.6.2（2026-03）

- 修复：导入超大 TXT 文件时报「Row too big to fit into CursorWindow」保存失败的问题
- 修复：单章内容过长时自动拆分（每章上限 25,000 字），彻底避免 SQLite 行溢出
- 修复：「从不自动锁定」时，切换后台再打开出现短暂白色方框闪烁的问题
- 修复：双栏布局右侧面板黑屏，补充背景色与空状态提示

---

### v1.6.1（2026-03）

- 优化：自动化发布流程（release.ps1 + GitHub Actions 打包发布 APK）
- 修复：发布脚本编码问题
- 修复：阅读器控制面板颜色跟随阅读皮肤
- 修复：SettingsActivity 缺失 View import 导致编译错误

---

### v1.6.0（2026-03）

**阅读器**

- 修复点击中间呼出/隐藏控制条时，文字内容跳动的问题：改为 `paddingTop + scrollY` 联动补偿方案（AppBar 覆盖在 ScrollView 上方而非推挤，切换时 scrollY 同步等差补偿，视觉内容位置完全不变）
- 底部控制栏改为覆盖式，不再压缩正文显示区域
- 沉浸模式优化：顶部显示小字章节标题覆盖层，paddingTop/Bottom 留出与系统栏等量 + 额外呼吸空间，参考主流阅读 App 体验
- 翻页动画重构：去掉 Camera/Matrix 3D 翻转（原动画结束时有黑帧闪烁），改为 `translationX` 水平滑动（200ms DecelerateInterpolator），过渡自然无闪
- 底部控制面板颜色跟随阅读皮肤：背景、导航图标、标签文字均与当前阅读背景色联动（白纸/米黄/暖灰/豆绿/夜间），不再固定使用 Material 主题表面色
- 修复大体积 TXT 导入后闪退：SQLite 批量 insert 超过 999 变量上限 → 改为 `chunked(75)` 分批写入（13字段×75=975 < 999）

**手势密码**

- 修复设置手势密码时操作完毕跳回书架：根本原因是 `Fragment` 被添加到 `Dialog` 窗口，与 `supportFragmentManager` 管理的 Activity 窗口层级不同，改为 `layoutInflater.inflate` 直接嵌入 Dialog，所有手势逻辑内联实现，彻底摆脱 FragmentManager

**书架**

- 书架封面改为默认 3 列（原来 2 列），更多书一screen显示
- 新增列数切换按钮（工具栏网格图标）：循环切换 2/3/4 列，选择记忆持久化
- 卡片高度自动跟随列数适配（2列=220dp，3列=155dp，4列=120dp）
- 修复 3/4 列模式下卡片内容重叠：4列隐藏作者名/字数/续读按钮，3列隐藏续读按钮，2列全显示；修复 `tvWordCount` margin 和 `btnContinueRead` 锚点

**删除书籍**

- 删除确认弹窗去掉输入书名验证，改为一步简单确认，降低误操作门槛（数据有回收站保护）

**APK 发布**

- 发布包名称去掉 `debug` 字样：`LocalWriter-v1.5.0.apk`（之前是 `LocalWriter-v1.4.0-debug.apk`）
- 版本号同步：`build.gradle` → `BuildConfig.VERSION_NAME` → 设置页"关于"→ APK文件名→ Release标题，全链路一处修改即同步

**章节识别**

- 修复连续章节标题之间内容为空时产生大量空章节的问题（< 80字的章节自动合并到上一章）
- 修复多个连续空行导致章节正文出现大段空白（限制最多保留一个空行分隔段落）

---

### v1.4.0（2026-03）

**界面 & 体验**

- 修复暗色模式下注册/首页颜色显示为紫色：将 `night_primary` 从紫色 `#BB86FC` 改为蓝色 `#5B9CF6`
- 修复暗色模式下注册界面输入文字不可见：`Auth` 夜间主题补充 `textColorPrimary` 等深色文字覆盖
- 登录/注册界面品牌区渐变从棕色改为深蓝（与整体深墨蓝主题一致）
- 注册页输入框直接绑定 `@color/on_surface`，无论日/夜模式均可见
- 主题色块名称由"棕色经典"更新为"深墨蓝"，色值与实际一致
- 阅读器界面优化：章节标题改为左对齐小字（15sp）；正文改为 17sp / 1.65x 行距；默认适中行距；每段首行自动缩进两个全角空格

**翻页动画重构**

- 替换原来的平移滑动为 3D 仿真翻页（Android `Camera` + `Matrix` + 旋转轴边缘阴影）
- 截取当前页快照 → 跳转新页 → 3D 旋转快照覆盖层动画，自然类书页效果

**安全 & 稳定**

- 修复设置页「设置手势密码」在 MIUI/HyperOS 等机型上仍跳回主界面：改用时间窗口（`suppressLockUntilMs`）替代易竞态的 `postDelayed` 布尔重置
- 导入文件大小上限提升至 100 MB，超限时给出友好提示；新增 `OutOfMemoryError` 捕获，避免大文件导入时闪退

---

### v1.3.0（2026-03）

**界面 & 体验**

- 书架重新设计：仿真彩色书皮封面，两列网格排列，书脊深色渐变 + 行间书架横板装饰
- 默认主题配色从棕色（#5D4037）升级为深墨蓝（#1E3A5F），金色点缀，更具文学气质
- 阅读器全屏适配打孔/刘海屏：`LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES` + 实时 insets 追踪，首尾行不再被遮挡
- 翻页不再出现「半行字」：翻页步长改用扣除安全 padding 后的真实视口高度
- 翻页动画优化：55% 宽度位移 + alpha 淡入淡出（0.25↔1.0），过渡更自然

**Bug 修复**

- 修复注册/登录界面米色背景上出现白色不可见文字（Auth 主题强制使用深色文字，禁用 forceDark 自动反色）
- 修复设置页「设置手势密码」对话框关闭后秒跳主页（`suppressLockCheck` 重置改为 `postDelayed(400ms)`，规避部分机型对话框触发 onResume 的竞态）

---

### v1.2.0（2026-03）

**Bug 修复**

- 修复左右滑动调用 `navigateChapter()` 而非 `navigatePage()`，导致直接跳章
- 修复阅读器章节标题颜色未跟随用户文字主题（两处均修正）
- 修复设置手势密码对话框不显示（`FrameLayout` 容器添加 `minimumHeight=360dp`）
- 修复注册流程确认手势后未自动跳转到生物识别步骤

**UX 改进**

- 新增翻页滑入动画（水平位移 160ms，加速→减速）
- 滚动阅读模式下侧边点击不再误触翻页
- 沉浸模式底部状态栏新增实时阅读进度百分比（`xx% · n/N 章`）

---

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

- 新增 GitHub Actions CI 工作流（`build.yml`），推送后自动构建 Debug APK 并创建 GitHub Release
- 修正 `gradle-wrapper.properties` 中的 Gradle 发行包 URL

---

## 👤 作者

**Roger_xie**

> *"写作是对抗遗忘的最好方式。"*
