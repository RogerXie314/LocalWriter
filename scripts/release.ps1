<#
.SYNOPSIS
    LocalWriter 发布脚本
.DESCRIPTION
    自动完成：版本 tag 升级 → CHANGELOG 生成 → 推送远程。
    推送 tag 后，GitHub Actions 自动打包 APK 并在 Releases 页面发布。
.PARAMETER Bump
    patch（默认）| minor | major | 指定版本号（如 1.8.0）
.EXAMPLE
    .\scripts\release.ps1           # patch: 1.6.0 → 1.6.1
    .\scripts\release.ps1 minor     # minor: 1.6.0 → 1.7.0
    .\scripts\release.ps1 major     # major: 1.6.0 → 2.0.0
    .\scripts\release.ps1 1.9.0     # 直接指定版本
#>
param(
    [string]$Bump = "patch"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Push-Location "$PSScriptRoot\.."
try {

    # ── 1. 工作区必须干净 ───────────────────────────────────────────────────
    $dirty = git status --porcelain 2>&1
    if ($dirty) {
        Write-Error "工作区有未提交的修改，请先 commit 或 stash。`n$dirty"
    }

    # ── 2. 获取当前最新 tag ─────────────────────────────────────────────────
    $prevTag = (git describe --tags --abbrev=0 --match "v[0-9]*" 2>$null) -replace "`n", ""
    if (-not $prevTag) { $prevTag = "v0.0.0" }
    $prevVer = $prevTag -replace '^v', ''
    $parts   = $prevVer -split '\.'
    $major   = [int]$parts[0]
    $minor   = [int]$parts[1]
    $patch   = [int]$parts[2]

    # ── 3. 计算新版本 ───────────────────────────────────────────────────────
    if ($Bump -match '^\d+\.\d+\.\d+$') {
        $newVer = $Bump
    } elseif ($Bump -eq "major") {
        $newVer = "$($major + 1).0.0"
    } elseif ($Bump -eq "minor") {
        $newVer = "$major.$($minor + 1).0"
    } else {
        # patch（默认）
        $newVer = "$major.$minor.$($patch + 1)"
    }
    $newTag = "v$newVer"

    Write-Host ""
    Write-Host "版本: $prevTag → $newTag" -ForegroundColor Cyan

    # ── 4. 收集 git log 作为 changelog 内容 ─────────────────────────────────
    $logLines = git log "$prevTag..HEAD" --oneline --no-merges 2>$null
    if (-not $logLines) { $logLines = @("- 日常维护与优化") }
    $bulletLines = ($logLines | ForEach-Object { "- $_" }) -join "`n"
    $date = Get-Date -Format "yyyy-MM-dd"

    $changeBlock = "## [$newVer] - $date`n`n$bulletLines`n"

    # ── 5. 写入 CHANGELOG.md ────────────────────────────────────────────────
    $clPath = "CHANGELOG.md"
    if (Test-Path $clPath) {
        $existing = Get-Content $clPath -Raw -Encoding UTF8
        # 把新块插到第一个 "## [" 条目之前（保留文件头）
        if ($existing -match '## \[') {
            $newContent = $existing -replace '(## \[)', "$changeBlock`n`$1"
        } else {
            $newContent = $changeBlock + "`n" + $existing
        }
        $newContent | Set-Content $clPath -Encoding UTF8 -NoNewline
    } else {
        "# Changelog`n`n$changeBlock" | Set-Content $clPath -Encoding UTF8 -NoNewline
    }
    Write-Host "CHANGELOG.md 已更新" -ForegroundColor Green

    # ── 6. Commit → Tag → Push ──────────────────────────────────────────────
    git add CHANGELOG.md
    git commit -m "chore: release $newTag"

    git tag -a $newTag -m "Release $newTag"
    Write-Host "Tag $newTag 已创建" -ForegroundColor Green

    Write-Host "推送到 origin ..." -ForegroundColor Cyan
    git push origin HEAD
    git push origin $newTag

    Write-Host ""
    Write-Host "完成！GitHub Actions 正在打包并发布：" -ForegroundColor Green
    Write-Host "  https://github.com/RogerXie314/LocalWriter/releases/tag/$newTag" -ForegroundColor Blue
    Write-Host ""

} finally {
    Pop-Location
}
