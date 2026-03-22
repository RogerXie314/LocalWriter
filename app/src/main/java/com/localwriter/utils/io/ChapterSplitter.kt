package com.localwriter.utils.io

/**
 * 章节自动分割器（成熟阅读APP方案）
 *
 * 核心流程（抄自番茄/掌阅/QReader）：
 *  1. 预扫描(pre-scan)：统计每种正则在全文匹配次数 → 选出"主导模式"
 *  2. 仅用主导模式切分，避免多模式互相干扰导致误切
 *  3. 主导模式不存在（< 2 次）→ 按字数等分兜底
 *  4. 后处理：合并极短章、拆分超长章、去重同名章节
 */
object ChapterSplitter {

    // ─── 候选章节模式（互斥选一）─────────────────────────────────────────
    private val CHAPTER_CANDIDATES = listOf(
        Regex("""^第[零一二三四五六七八九十百千万\d]+[章节回集篇话幕].{0,40}$"""),
        Regex("""^[Cc]hapter\s+\d+.{0,40}$"""),
        Regex("""^正文\s+\S.{0,55}$"""),
        Regex("""^\d{1,4}[.、．]\s*\S.{0,35}$"""),
        Regex("""^(楔子|番外|序章|终章|尾声|后记|后传|序言|前言|引子).{0,25}$"""),
    )

    // ─── 卷模式（始终全量检测）────────────────────────────────────────────
    private val VOLUME_PATTERNS = listOf(
        Regex("""^第[零一二三四五六七八九十百千万\d]+[卷部册].{0,40}$"""),
        Regex("""^[（(][上中下前后][）)]\s*.{0,35}$"""),
        Regex("""^(上+部|中+部|下+部|卷[一二三四五六七八九十]+).{0,35}$"""),
        Regex("""^Volume\s+\d+.{0,40}$"""),
        Regex("""^Book\s+\d+.{0,40}$"""),
    )

    // 兜底按字数切分的默认段长
    private const val SIZE_SPLIT_CHARS  = 3_000
    // 合并阈值：正文 < 此长度则并入上一章
    private const val MERGE_MIN_CHARS   = 80
    // 单章上限（SQLite CursorWindow）
    private const val MAX_CHAPTER_CHARS = 25_000

    data class SplitChapter(
        val title: String,
        val content: String,
        val volumeTitle: String? = null
    )

    // ═══════════════════════════════════════════════════════════════
    //  公开入口
    // ═══════════════════════════════════════════════════════════════

    fun split(text: String, bookTitle: String = "正文"): List<SplitChapter> {
        val lines = text.lines()

        // ① 预扫描 → 主导模式
        val dominant = findDominantPattern(lines)

        // ② 按模式切分
        val raw = if (dominant != null) {
            doSplit(lines, dominant)
        } else {
            doSplit(lines, null)   // 用全量模式（含特殊词）尝试一次
        }

        // ③ 不够 2 章 → 按字数等分兜底
        val chapters = if (raw.size >= 2) raw else sizeBasedSplit(text, bookTitle)

        // ④ 后处理流水线
        return chapters
            .let { mergeShort(it, bookTitle) }
            .let { splitOversized(it) }
            .let { deduplicateTitles(it) }
    }

    // ═══════════════════════════════════════════════════════════════
    //  预扫描：选出出现次数最多的模式
    // ═══════════════════════════════════════════════════════════════

    private fun findDominantPattern(lines: List<String>): Regex? {
        var best: Regex? = null
        var bestCount = 0
        for (pattern in CHAPTER_CANDIDATES) {
            val indices = lines.indices.filter { isHeadingLine(lines[it].trim(), pattern) }
            val count = indices.size
            if (count > bestCount && isSpacingReasonable(indices)) {
                bestCount = count; best = pattern
            }
        }
        return if (bestCount >= 2) best else null
    }

    /** 判断匹配行之间的间距是否合理（中位数 ≥ 5 行），过密则说明是段落内列表而非章节标题 */
    private fun isSpacingReasonable(indices: List<Int>): Boolean {
        if (indices.size < 2) return true
        val spacings = (1 until indices.size).map { indices[it] - indices[it - 1] }
        val sorted = spacings.sorted()
        val median = sorted[sorted.size / 2]
        return median >= 5
    }

    // ═══════════════════════════════════════════════════════════════
    //  核心切分（dominant = null 时退回全量模式）
    // ═══════════════════════════════════════════════════════════════

    private fun doSplit(lines: List<String>, dominant: Regex?): List<SplitChapter> {
        val result = mutableListOf<SplitChapter>()
        var currentTitle       = ""
        var currentVolumeTitle: String? = null
        val currentContent     = StringBuilder()
        var foundChapter       = false
        val prefaceContent     = StringBuilder()
        var pendingVolumeTitle: String? = null

        fun flush(vol: String?) {
            val content = currentContent.toString().trim()
            if (currentTitle.isNotEmpty() || content.isNotEmpty()) {
                result.add(SplitChapter(currentTitle.ifEmpty { "章节 ${result.size + 1}" }, content, vol))
            }
            currentTitle = ""
            currentContent.clear()
        }

        for (line in lines) {
            val trimmed = line.trim()
            when {
                isVolumeTitle(trimmed) -> {
                    if (foundChapter) flush(currentVolumeTitle)
                    pendingVolumeTitle = trimmed
                }
                isChapterLine(trimmed, dominant) -> {
                    if (!foundChapter) {
                        val preface = prefaceContent.toString().trim()
                        if (preface.isNotEmpty()) result.add(SplitChapter("前言", preface, null))
                        foundChapter = true
                    } else {
                        flush(currentVolumeTitle)
                    }
                    if (pendingVolumeTitle != null) {
                        currentVolumeTitle = pendingVolumeTitle; pendingVolumeTitle = null
                    }
                    currentTitle = trimmed
                    currentContent.clear()
                }
                else -> {
                    val buf = if (foundChapter) currentContent else prefaceContent
                    if (trimmed.isNotEmpty()) {
                        buf.append(trimmed).append("\n")
                    } else if (buf.isNotEmpty() && !buf.endsWith("\n\n")) {
                        buf.append("\n")
                    }
                }
            }
        }
        // 保存最后一章
        flush(currentVolumeTitle)
        return result
    }

    // ═══════════════════════════════════════════════════════════════
    //  兜底：按字数等分（无章节标记时）
    // ═══════════════════════════════════════════════════════════════

    private fun sizeBasedSplit(text: String, bookTitle: String): List<SplitChapter> {
        val cleaned = text.trim()
        if (cleaned.length <= SIZE_SPLIT_CHARS) return listOf(SplitChapter(bookTitle, cleaned, null))
        val chunks = cleaned.chunked(SIZE_SPLIT_CHARS)
        return chunks.mapIndexed { i, chunk ->
            SplitChapter("第 ${i + 1} 节", chunk, null)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  后处理
    // ═══════════════════════════════════════════════════════════════

    /** 合并正文过短（< MERGE_MIN_CHARS）的章节到上一章末尾 */
    private fun mergeShort(chapters: List<SplitChapter>, bookTitle: String): List<SplitChapter> {
        val result = mutableListOf<SplitChapter>()
        for (ch in chapters) {
            if (ch.content.trim().length < MERGE_MIN_CHARS && result.isNotEmpty()) {
                val prev = result.last()
                val merged = buildString {
                    append(prev.content.trimEnd())
                    if (ch.title.isNotEmpty()) append("\n${ch.title}")
                    if (ch.content.trim().isNotEmpty()) append("\n${ch.content.trim()}")
                }
                result[result.size - 1] = prev.copy(content = merged)
            } else {
                result.add(ch)
            }
        }
        return result.ifEmpty { listOf(SplitChapter(bookTitle, chapters.joinToString("\n") { it.content }, null)) }
    }

    /** 拆分超长章节（> MAX_CHAPTER_CHARS） */
    private fun splitOversized(chapters: List<SplitChapter>): List<SplitChapter> {
        val result = mutableListOf<SplitChapter>()
        for (ch in chapters) {
            if (ch.content.length <= MAX_CHAPTER_CHARS) {
                result.add(ch)
            } else {
                val parts = ch.content.chunked(MAX_CHAPTER_CHARS)
                parts.forEachIndexed { idx, part ->
                    val title = if (parts.size == 1) ch.title else "${ch.title}（${idx + 1}/${parts.size}）"
                    result.add(ch.copy(title = title, content = part))
                }
            }
        }
        return result
    }

    /**
     * 同名章节去重（成熟APP核心处理）
     * 同一标题出现多次时，第1次保持原名，后续追加序号（2）（3）……
     */
    private fun deduplicateTitles(chapters: List<SplitChapter>): List<SplitChapter> {
        val freq    = chapters.groupingBy { it.title }.eachCount()
        val counter = mutableMapOf<String, Int>()
        return chapters.map { ch ->
            if ((freq[ch.title] ?: 1) > 1) {
                val n = (counter[ch.title] ?: 1)
                counter[ch.title] = n + 1
                ch.copy(title = if (n == 1) ch.title else "${ch.title}（$n）")
            } else ch
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  辅助判断
    // ═══════════════════════════════════════════════════════════════

    /** 是否为章节标题行（dominant == null 时用全量模式） */
    private fun isChapterLine(line: String, dominant: Regex?): Boolean {
        if (line.isBlank() || line.length > 60) return false
        return if (dominant != null) {
            dominant.matches(line)
        } else {
            CHAPTER_CANDIDATES.any { it.matches(line) }
        }
    }

    private fun isHeadingLine(line: String, pattern: Regex): Boolean {
        if (line.isBlank() || line.length > 60) return false
        return pattern.matches(line)
    }

    private fun isVolumeTitle(line: String): Boolean {
        if (line.isBlank() || line.length > 60) return false
        return VOLUME_PATTERNS.any { it.matches(line) }
    }
}

