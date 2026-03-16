# LocalWriter · 本地写作助手

> 纯本地化 Android 写作助手 App，无任何网络请求，数据完全离线存储。

---

## 项目进度

### ✅ 已完成

#### 1. 项目基础结构
- [x] 创建 Android 项目目录结构（MVVM + Repository 架构）
- [x] `settings.gradle` — 模块注册
- [x] `build.gradle`（根） — Kotlin / KSP 插件版本管理
- [x] `app/build.gradle` — 所有依赖声明（Room / Biometric / POI / iText / epublib / juniversalchardet 等）
- [x] `gradle.properties` — Xmx2048m / AndroidX / Jetifier / 并行编译
- [x] `gradle/wrapper/gradle-wrapper.properties` — Gradle 8.4
- [x] `gradlew` / `gradlew.bat` — Gradle Wrapper 启动脚本
- [x] `local.properties` — SDK 路径占位（需替换为本机路径）
- [x] `app/proguard-rules.pro` — ProGuard 规则（Room / epublib / iText / POI / juniversalchardet）

---

#### 2. 数据库层（Room + SQLite）
- [x] `User.kt` — 用户实体（用户名 / 密码哈希 / 盐 / 手势哈希 / 生物识别开关 / 锁类型）
- [x] `Book.kt` — 书籍实体（关联用户 / 状态 ONGOING·FINISHED·PAUSED / 标签 / 字数 / 排序）
- [x] `Volume.kt` — 卷实体（书→卷层级 / sortOrder 支持拖拽排序）
- [x] `Chapter.kt` — 章节实体（状态 DRAFT·PUBLISHED·DELETED / 软删除 deletedAt / 光标位置 / 字数）
- [x] `UserSettings.kt` — 用户个性化设置（字号 / 字体 / 行高 / 字间距 / 背景 / 自动保存间隔）
- [x] `UserDao.kt` — 用户增删查改
- [x] `BookDao.kt` — 书籍增删查改 + 按用户查询
- [x] `VolumeDao.kt` — 卷增删查改 + sortOrder 更新
- [x] `ChapterDao.kt` — 章节增删查改 + ChapterPreview（不含正文的轻量数据类）+ 全文搜索 + 字数聚合
- [x] `UserSettingsDao.kt` — 设置增删查改
- [x] `AppDatabase.kt` — Room 单例入口，含5个 DAO

---

#### 3. 安全与会话
- [x] `SecurityUtils.kt` — SHA-256 + 随机16字节盐 + PEPPER 密码哈希；`EncryptedSharedPreferences`（AES256-GCM）获取；手势验证
- [x] `SessionManager.kt` — 登录状态持久化（加密存储）；退后台自动锁定；lock / unlock / isLocked

---

#### 4. 数据仓库层
- [x] `AuthRepository.kt` — 注册 / 密码登录 / 手势登录 / 修改密码 / 生物识别开关管理
- [x] `BookRepository.kt` — 书籍 + 卷的 CRUD，创建书时自动生成「第一卷」
- [x] `ChapterRepository.kt` — 章节 CRUD / 软删除 / 恢复 / 30天自动清理 / 字数统计（CJK + 英文分词）/ 字数同步回书籍
- [x] `SettingsRepository.kt` — 读写用户个性化设置
- [x] `LocalWriterApp.kt` — Application 类，lazy 单例 Repository

---

#### 5. 认证模块（密码 / 手势 / 面容 ID）
- [x] `AuthActivity.kt` — 登录入口；根据 preferredLockType 自动切换三种验证方式；退后台触发自动锁定
- [x] `AuthViewModel.kt` — 密码 / 手势登录逻辑，生物识别触发
- [x] `GestureLoginFragment.kt` — 手势绘制 Fragment（isSetup 模式 / 验证模式，至少4个节点）
- [x] `RegisterActivity.kt` — 三步注册引导（基本信息 → 手势设置 → 生物识别开关）

---

#### 6. 书籍管理模块
- [x] `BookListFragment.kt` — 书籍列表；长按 PopupMenu（重命名 / 编辑 / 删除 / 导出 / 修改状态）；点击进入章节
- [x] `BookListAdapter.kt` — RecyclerView ListAdapter；显示书名 / 作者 / 字数 / 状态标签
- [x] `BookViewModel.kt` — 书籍列表 LiveData / 增删改查

---

#### 7. 章节管理模块（书 → 卷 → 章三级结构）
- [x] `ChapterListFragment.kt` — 按卷分组的章节列表；添加卷 / 添加章节 / 重命名 / 软删除 / 回收站入口 / 全文搜索
- [x] `ChapterSectionAdapter.kt` — 支持 TYPE_VOLUME_HEADER + TYPE_CHAPTER 双 ViewHolder 的扁平化 Adapter
- [x] `ChapterViewModel.kt` — 章节与卷的业务逻辑，软删除/恢复
- [x] 软删除（回收站）：章节标记 `DELETED` + `deletedAt`，30 天后自动清理

---

#### 8. 写作编辑器模块
- [x] `EditorActivity.kt` — 全屏沉浸式编辑；底部快捷标点栏；常用句式；退出保存光标位置；动态应用背景/字体/字号
- [x] `EditorViewModel.kt` — 内容变更计时自动保存（coroutine delay）；wordCount LiveData
- [x] `UndoRedoHelper.kt` — 基于 TextWatcher 双栈，支持最多 200 步撤销/重做

---

#### 9. 导入 / 导出模块（多格式 + 乱码解决）
- [x] `EncodingDetector.kt` — 基于 juniversalchardet（Mozilla）自动检测编码；处理 UTF-8 BOM；未检测时回退 GBK（中文小说常见编码）
- [x] `ChapterSplitter.kt` — 正则自动按章节标题分割导入文本（覆盖「第X章」「Chapter X」「楔子」等5种格式）
- [x] `BookImporter.kt` — 统一导入入口，支持 **TXT / EPUB / DOC / DOCX / PDF / CHM / UMD**
- [x] `UmdParser.kt` — UMD v2 二进制格式自实现解析器（魔术字验证 → Chunk 解析 → zlib 解压 → UTF-16LE 解码）
- [x] `BookExporter.kt` — 统一导出入口，支持 **TXT（带BOM）/ EPUB / PDF（内嵌STSong中文字体）/ DOCX**

---

#### 10. 设置模块
- [x] `SettingsActivity.kt` — 字号滑杆 / 字体 Chip / 背景预设 Chip / 自定义图片背景 / 自定义字色 / 自动保存间隔 / 安全设置入口
- [x] `SettingsViewModel.kt` — 设置增量更新，`update{...}` 高阶函数模式

---

#### 11. 主界面布局
- [x] `MainActivity.kt` — DrawerLayout 主界面；退后台触发 SessionManager.lock()
- [x] 手机：抽屉式左侧导航（书籍列表）+ 右侧内容区
- [x] 平板（≥600dp）：双栏常驻布局（`layout-w600dp/activity_main.xml`）

---

#### 12. 资源文件
- [x] **布局**：activity_auth / activity_register / activity_main / activity_editor / activity_settings / fragment_book_list / fragment_chapter_list / fragment_gesture_login / item_book / item_chapter / item_volume_section / dialog_edit_title
- [x] **菜单**：menu_main / menu_editor / menu_chapter_list / menu_book_item / menu_chapter_item / menu_volume_item
- [x] **矢量图标**：ic_add / ic_arrow_back / ic_lock / ic_menu / ic_more_vert / ic_quick_text / ic_redo / ic_save / ic_search / ic_settings / ic_undo
- [x] **背景 Drawable**：bg_tag_ongoing / bg_tag_finished / bg_tag_paused / bg_tag_draft / bg_word_count / bg_gesture_grid
- [x] **启动图标**：ic_launcher_foreground / ic_launcher_background（自适应图标，mipmap-anydpi-v26 / v33）
- [x] **主题**：values/themes.xml（日间）/ values-night/themes.xml（夜间，DayNight 自动切换）
- [x] **颜色**：values/colors.xml / values-night/colors.xml
- [x] **字符串**：values/strings.xml
- [x] **尺寸**：values/dimens.xml
- [x] **FileProvider**：xml/file_paths.xml
- [x] **清单**：AndroidManifest.xml（含 FileProvider / 所有 Activity / 无网络权限）

---

## 技术栈

| 分类 | 技术/库 |
|---|---|
| 语言 | Kotlin 1.9.22，JVM Target 17 |
| 架构 | MVVM + Repository，ViewBinding |
| 数据库 | Room 2.6.1（SQLite），KSP |
| UI | Material Design 3（DayNight 主题） |
| 认证安全 | EncryptedSharedPreferences（AES256-GCM）/ BiometricPrompt / SHA-256+随机盐 |
| 编码检测 | juniversalchardet 2.4.0（Mozilla 移植） |
| EPUB | epublib-core 3.1 |
| PDF | iTextPDF 5.5.13.3（STSong 中文字体） |
| DOC/DOCX | Apache POI 5.2.5 |
| HTML 解析 | Jsoup 1.17.2 |
| 异步 | kotlinx.coroutines 1.7.3 |
| Min SDK | 26（Android 8.0）|
| Target SDK | 34（Android 14）|

---

## 如何构建 APK

### 前提条件
1. 安装 [Android Studio](https://developer.android.com/studio)（含 JDK 17 和 Android SDK）
2. 修改项目根目录 `local.properties`，将 `sdk.dir` 替换为本机 SDK 路径：
   ```
   sdk.dir=C\:\\Users\\<你的用户名>\\AppData\\Local\\Android\\Sdk
   ```

### 构建 Debug APK（可直接安装测试）
```powershell
cd d:\Development\Learn\LocalWriter
.\gradlew.bat assembleDebug
```
输出文件：`app\build\outputs\apk\debug\app-debug.apk`

### 用 Android Studio 打包
1. 打开 Android Studio → `Open` → 选择本项目文件夹
2. 等待 Gradle 同步完成
3. 菜单 → `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`

### 构建 Release 签名 APK（正式分发）
Android Studio → `Build` → `Generate Signed Bundle / APK` → 选 APK → 创建/选择 Keystore → Release 构建

### 手机安装
- 开启手机「设置 → 开发者选项 → 允许安装未知来源应用」
- USB 传输或通过文件传输工具发送 APK 到手机安装
- 最低需要 Android 8.0（API 26）

---

## 项目结构

```
LocalWriter/
├── app/
│   ├── build.gradle
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/localwriter/
│       │   ├── LocalWriterApp.kt
│       │   ├── data/
│       │   │   ├── db/
│       │   │   │   ├── AppDatabase.kt
│       │   │   │   ├── dao/          (5个 DAO)
│       │   │   │   └── entity/       (5个实体)
│       │   │   └── repository/       (4个 Repository)
│       │   ├── ui/
│       │   │   ├── auth/             (登录/注册/手势)
│       │   │   ├── books/            (书籍列表)
│       │   │   ├── chapters/         (章节管理)
│       │   │   ├── editor/           (写作编辑器)
│       │   │   ├── main/             (主界面)
│       │   │   └── settings/         (设置)
│       │   └── utils/
│       │       ├── SecurityUtils.kt
│       │       ├── SessionManager.kt
│       │       ├── UndoRedoHelper.kt
│       │       └── io/               (导入/导出/编码检测)
│       └── res/
│           ├── drawable/             (图标+背景)
│           ├── layout/               (13个布局)
│           ├── layout-w600dp/        (平板双栏布局)
│           ├── menu/                 (6个菜单)
│           ├── mipmap-anydpi-v26/    (启动图标)
│           ├── mipmap-anydpi-v33/    (启动图标 monochrome)
│           ├── values/               (strings/colors/themes/dimens)
│           ├── values-night/         (暗色主题)
│           └── xml/                  (file_paths)
├── gradle/wrapper/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── gradlew / gradlew.bat
└── local.properties
```
