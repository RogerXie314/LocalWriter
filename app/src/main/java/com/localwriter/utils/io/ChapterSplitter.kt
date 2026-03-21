package com.localwriter.utils.io

/**
 * 章节自动分割器
 * 针对导入的平铺文本，按主流小说章节标题正则自动拆分章节
 * 同时识别卷标题，自动分卷
 */
object ChapterSplitter {

    /** 主流章节标题正则（覆盖绝大多数网文命名习惯）*/
    private val CHAPTER_PATTERNS = listOf(
        Regex("""^第[零一二三四五六七八九十百千万\d]+[章节回集篇话幕][\s\S]{0,50}$"""),
        Regex("""^[Cc]hapter\s+\d+[\s\S]{0,50}$"""),
        Regex("""^\d{1,4}[.、．]\s*[\S][\s\S]{0,40}$"""),
        Regex("""^(楔子|番外|序章|终章|尾声|后记|后传|序言|前言|引子)[\s\S]{0,30}$"""),
    )

    /** 卷标题正则（第X卷 / 第X部 / 上中下部 / 卷一等）*/
    private val VOLUME_PATTERNS = listOf(
        Regex("""^第[零一二三四五六七八九十百千万\d]+[卷部册][\s\S]{0,50}$"""),
        Regex("""^[（(][上中下前后][）)]\s*[\s\S]{0,40}$"""),
        Regex("""^(上+部|中+部|下+部|卷[一二三四五六七八九十]+)[\s\S]{0,40}$"""),
        Regex("""^Volume\s+\d+[\s\S]{0,50}$"""),
        Regex("""^Book\s+\d+[\s\S]{0,50}$"""),
    )

    data class SplitChapter(
        val title: String,
        val content: String,
        /** 所属卷标题；null 表示使用默认卷或维持上一卷 */
        val volumeTitle: String? = null
    )

    /**
     * 将文本按章节/卷标题分割
     * @param text 完整小说文本
     * @param bookTitle 书名（用于无法分割时的单章模式）
     */
    fun split(text: String, bookTitle: String = "正文"): List<SplitChapter> {
        val lines = text.lines()
        val result = mutableListOf<SplitChapter>()

        var currentTitle       = ""
        var currentVolumeTitle: String? = null
        val currentContent     = StringBuilder()
        var foundChapter       = false
        val prefaceContent     = StringBuilder()
        var pendingVolumeTitle: String? = null  // 遇到卷标题后暂存，等下一章用

        for (line in lines) {
            val trimmed = line.trim()

            when {
                // ─── 卷标题 ───────────────────────────────────────
                isVolumeTitle(trimmed) -> {
                    // 先保存当前章节
                    if (foundChapter) {
                        val content = currentContent.toString().trim()
                        if (currentTitle.isNotEmpty() || content.isNotEmpty()) {
                            result.add(SplitChapter(
                                currentTitle.ifEmpty { "章节 ${result.size}" },
                                content,
                                currentVolumeTitle
                            ))
                        }
                        currentTitle = ""
                        currentContent.clear()
                    }
                    pendingVolumeTitle = trimmed
                }

                // ─── 章节标题 ─────────────────────────────────────
                isChapterTitle(trimmed) -> {
                    if (!foundChapter) {
                        val preface = prefaceContent.toString().trim()
                        if (preface.isNotEmpty()) {
                            result.add(SplitChapter("前言", preface, null))
                        }
                        foundChapter = true
                    } else {
                        val content = currentContent.toString().trim()
                        if (currentTitle.isNotEmpty() || content.isNotEmpty()) {
                            result.add(SplitChapter(
                                currentTitle.ifEmpty { "章节 ${result.size}" },
                                content,
                                currentVolumeTitle
                            ))
                        }
                    }
                    // 切换卷
                    if (pendingVolumeTitle != null) {
                        currentVolumeTitle = pendingVolumeTitle
                        pendingVolumeTitle = null
                    }
                    currentTitle = trimmed
                    currentContent.clear()
                }

                // ─── 正文 ─────────────────────────────────────────
                else -> {
                    if (foundChapter) {
                        if (trimmed.isNotEmpty()) {
                            currentContent.append(trimmed).append("\n")
                        } else if (currentContent.isNotEmpty() && !currentContent.endsWith("\n\n")) {
                            // 最多保留一个空行，避免多个连续空行造成大段空白
                            currentContent.append("\n")
                        }
                    } else {
                        if (trimmed.isNotEmpty()) {
                            prefaceContent.append(trimmed).append("\n")
                        } else if (prefaceContent.isNotEmpty() && !prefaceContent.endsWith("\n\n")) {
                            prefaceContent.append("\n")
                        }
                    }
                }
            }
        }

        // 保存最后一章
        val lastContent = currentContent.toString().trim()
        if (currentTitle.isNotEmpty() || lastContent.isNotEmpty()) {
            result.add(SplitChapter(
                currentTitle.ifEmpty { "章节 ${result.size + 1}" },
                lastContent,
                currentVolumeTitle
            ))
        }

        // 如果一个章节都没找到，整本作为一章
        if (result.isEmpty()) {
            result.add(SplitChapter(bookTitle, text.trim(), null))
        }

        // ── 后处理：合并内容过短（< 80字）的章节到上一章 ─────────────────
        // 避免错误地把编号列表行（如"1. 走进大厅"）当作章节标题
        val cleaned = mutableListOf<SplitChapter>()
        for (ch in result) {
            val len = ch.content.trim().length
            if (len < 80 && cleaned.isNotEmpty()) {
                // 将此章的标题和内容并入上一章末尾
                val prev = cleaned.last()
                val appended = buildString {
                    append(prev.content.trimEnd())
                    if (ch.title.isNotEmpty()) append("\n${ch.title}")
                    if (ch.content.trim().isNotEmpty()) append("\n${ch.content.trim()}")
                }
                cleaned[cleaned.size - 1] = prev.copy(content = appended)
            } else {
                cleaned.add(ch)
            }
        }

        return cleaned.ifEmpty { listOf(SplitChapter(bookTitle, text.trim(), null)) }
    }

    private fun isChapterTitle(line: String): Boolean {
        if (line.isBlank() || line.length > 60) return false
        return CHAPTER_PATTERNS.any { it.matches(line) }
    }

    private fun isVolumeTitle(line: String): Boolean {
        if (line.isBlank() || line.length > 60) return false
        return VOLUME_PATTERNS.any { it.matches(line) }
    }
}

