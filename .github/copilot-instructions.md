# LocalWriter — Copilot 指令

## 发布流程

当用户说"发布"、"打包发布"、"出新版本"、"release"时，**直接执行**以下脚本，无需逐步询问：

```powershell
# 在项目根目录执行（默认 patch，末位 +1）
.\scripts\release.ps1

# 或指定升级类型
.\scripts\release.ps1 minor   # 次版本号 +1
.\scripts\release.ps1 major   # 主版本号 +1
.\scripts\release.ps1 1.9.0   # 直接指定版本
```

脚本会自动完成：
1. 检查工作区是否干净（如有未提交文件先提醒用户）
2. 从 git tag 读取当前版本，计算新版本
3. 从 `git log` 生成 CHANGELOG 条目并写入 `CHANGELOG.md`
4. `git commit` → 创建 annotated tag → `git push`
5. 推送 tag 触发 `.github/workflows/release.yml`，由 GitHub Actions 打包 APK 并自动发布到 Releases 页

## 版本号规则

- `versionName` 和 `versionCode` 均从 git 自动读取（见 `app/build.gradle`）
- 在 tag 上打包 → 版本干净（如 `1.7.0`）
- tag 之后有新 commit → 版本带后缀（如 `1.7.0-3-SNAPSHOT`）
- **不要**手动修改 `build.gradle` 中的版本号

## 本地构建

```powershell
# 仅 debug 包（不发布）
cd d:\Github\LocalWriter
cmd.exe /c "gradlew.bat assembleDebug --no-daemon > temp\build.txt 2>&1"
```

## 项目概览

- 架构：MVVM + Room + ViewBinding + LiveData
- compileSdk / targetSdk：35（android-35 必须安装到 `C:\Android\Sdk`）
- KSP：1.9.22-1.0.17，Room：2.6.1
- 构建遇到 Room/KSP 缓存问题时，先运行 `gradlew clean` 再重建
