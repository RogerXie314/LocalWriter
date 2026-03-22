<#
.SYNOPSIS  LocalWriter release script
.PARAMETER Bump  patch | minor | major | x.y.z
#>
param([string]$Bump = "patch")

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
# 强制控制台和 git log 均以 UTF-8 输出，避免中文提交信息写入 CHANGELOG 时乱码
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding            = [System.Text.Encoding]::UTF8
$env:LANG                  = "en_US.UTF-8"

Push-Location "$PSScriptRoot\.."
try {

    # 1. Working tree must be clean
    $dirty = git status --porcelain 2>&1
    if ($dirty) {
        Write-Error "Working tree is dirty. Commit or stash first.`n$dirty"
    }

    # 2. Get current latest tag
    $prevTag = (git describe --tags --abbrev=0 --match "v[0-9]*" 2>$null) -replace "`n", ""
    if (-not $prevTag) { $prevTag = "v0.0.0" }
    $prevVer = $prevTag -replace '^v', ''
    $parts   = $prevVer -split '\.'
    $major   = [int]$parts[0]
    $minor   = [int]$parts[1]
    $patch   = [int]$parts[2]

    # 3. Compute new version
    if ($Bump -match '^\d+\.\d+\.\d+$') {
        $newVer = $Bump
    } elseif ($Bump -eq "major") {
        $newVer = "$($major + 1).0.0"
    } elseif ($Bump -eq "minor") {
        $newVer = "$major.$($minor + 1).0"
    } else {
        $newVer = "$major.$minor.$($patch + 1)"
    }
    $newTag = "v$newVer"

    Write-Host ""
    Write-Host "$prevTag -> $newTag" -ForegroundColor Cyan

    # 4. Collect git log for changelog
    $logLines = @(git log "$prevTag..HEAD" --oneline --no-merges 2>$null)
    if (-not $logLines -or $logLines.Count -eq 0) {
        $logLines = @("维护与改进")
    }
    $nl = [System.Environment]::NewLine
    $bulletLines = ($logLines | ForEach-Object {
        # 去掉 7 位 hash 前缀
        $msg = $_ -replace '^[0-9a-f]{7}\s+', ''
        # 去掉英文 conventional commit 前缀，如 fix(reader): / feat: 等
        $msg = $msg -replace '^(fix|feat|refactor|chore|docs|style|test|perf|ci|build)\([^)]*\):\s*', ''
        $msg = $msg -replace '^(fix|feat|refactor|chore|docs|style|test|perf|ci|build):\s*', ''
        "- $msg"
    }) -join $nl
    $date = Get-Date -Format "yyyy-MM-dd"
    $changeBlock = "## [$newVer] - $date$nl$nl$bulletLines$nl"

    # 5. Write CHANGELOG.md
    $clPath = "CHANGELOG.md"
    if (Test-Path $clPath) {
        $existing = Get-Content $clPath -Raw -Encoding UTF8
        $idx = $existing.IndexOf('## [')
        if ($idx -ge 0) {
            $newContent = $existing.Substring(0, $idx) + $changeBlock + $nl + $existing.Substring($idx)
        } else {
            $newContent = $changeBlock + $nl + $existing
        }
        [System.IO.File]::WriteAllText((Resolve-Path $clPath), $newContent, [System.Text.Encoding]::UTF8)
    } else {
        [System.IO.File]::WriteAllText((Join-Path (Get-Location) $clPath), "# Changelog$nl$nl$changeBlock", [System.Text.Encoding]::UTF8)
    }
    Write-Host "CHANGELOG.md updated" -ForegroundColor Green

    # 6. Update README.md changelog section
    $readmePath = "README.md"
    if (Test-Path $readmePath) {
        $readme = Get-Content $readmePath -Raw -Encoding UTF8
        $summaryLines = @(git log "$prevTag..HEAD" --oneline --no-merges 2>$null | Select-Object -First 8)
        if (-not $summaryLines -or $summaryLines.Count -eq 0) {
            $summaryLines = @("维护与改进")
        }
        $summaryBullets = ($summaryLines | ForEach-Object {
            $msg = $_ -replace '^[0-9a-f]{7}\s+', ''
            $msg = $msg -replace '^(fix|feat|refactor|chore|docs|style|test|perf|ci|build)\([^)]*\):\s*', ''
            $msg = $msg -replace '^(fix|feat|refactor|chore|docs|style|test|perf|ci|build):\s*', ''
            "- $msg"
        }) -join $nl
        $readmeBlock = "### v$newVer（$( Get-Date -Format 'yyyy-MM' )）$nl$nl$summaryBullets$nl$nl---$nl$nl"
        $ridx = $readme.IndexOf('### v')
        if ($ridx -ge 0) {
            $newReadme = $readme.Substring(0, $ridx) + $readmeBlock + $readme.Substring($ridx)
            [System.IO.File]::WriteAllText((Resolve-Path $readmePath), $newReadme, [System.Text.Encoding]::UTF8)
            Write-Host "README.md updated" -ForegroundColor Green
        } else {
            Write-Host "README.md: could not find changelog anchor, skipping" -ForegroundColor Yellow
        }
    }

    # 7. Commit + Tag (先打 tag，让本地编译能拿到正确版本号)
    git add CHANGELOG.md README.md
    git commit -m "chore: release $newTag"
    git tag -a $newTag -m "Release $newTag"
    Write-Host "Tag $newTag created" -ForegroundColor Green

    # 8. 本地编译验证（tag 已存在，版本号与发布版一致）
    Write-Host "Building debug APK locally (version: $newVer)..." -ForegroundColor Cyan
    $buildLog = "temp\build_release.txt"
    New-Item -ItemType Directory -Force -Path "temp" | Out-Null
    $proc = Start-Process -FilePath "cmd.exe" -ArgumentList "/c gradlew.bat assembleDebug --no-daemon > $buildLog 2>&1" -Wait -PassThru -NoNewWindow
    if ($proc.ExitCode -ne 0) {
        Write-Host (Get-Content $buildLog -Tail 20 -Raw)
        # 回滚：删除 tag 和 commit
        git tag -d $newTag | Out-Null
        git reset --hard HEAD~1 | Out-Null
        Write-Error "Local build FAILED. Tag $newTag and release commit have been rolled back."
    }
    Write-Host "Local build OK -> app\build\outputs\apk\debug\" -ForegroundColor Green

    # 9. Push
    Write-Host "Pushing to origin..." -ForegroundColor Cyan
    git push origin HEAD
    git push origin $newTag

    $url = "https://github.com/RogerXie314/LocalWriter/releases/tag/$newTag"
    Write-Host ""
    Write-Host "Done! GitHub Actions is building and publishing:" -ForegroundColor Green
    Write-Host "  $url" -ForegroundColor Blue
    Write-Host ""

} finally {
    Pop-Location
}
