# Changelog

## [1.6.2] - 2026-03-22

- 修复：导入超大 TXT 文件时报「Row too big to fit into CursorWindow」保存失败的问题
- 修复：单章内容过长时自动拆分，每章上限 25,000 字，彻底避免 SQLite 行溢出
- 修复：设置「从不自动锁定」时，切换后台再打开出现短暂白色方框闪烁的问题
- 修复：双栏布局右侧面板黑屏，补充背景色与空状态提示

## [1.6.1] - 2026-03-22

- fix: release script encoding
- chore: build infra, release pipeline and bug fixes
- fix: reader control panel color matches reading skin
- fix: add missing View import in SettingsActivity
- fix: batch insert crash, gesture dialog, delete confirm, shelf overlap
- feat: natural reader margins + smooth slide page flip
- fix: resolve JVM declaration clash in BookListAdapter

## [1.6.0] - 2026-03-16

- fix: reader control panel color matches reading skin
- fix: add missing View import in SettingsActivity
- fix: batch insert crash, gesture dialog, delete confirm, shelf overlap
- feat: natural reader margins + smooth slide page flip
- fix: resolve JVM declaration clash in BookListAdapter

## [1.5.0] - 2026-03-08

- feat: v1.5.0  reader 手势与界面完善

## [1.4.0] - 2026-02-20

- 详见 Git 历史

## [1.3.0] - 2026-02-06

- 详见 Git 历史

## [1.1.0] - 2026-01-15

- 初始功能版本
