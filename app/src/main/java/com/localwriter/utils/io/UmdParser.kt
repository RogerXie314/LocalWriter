package com.localwriter.utils.io

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.InflaterInputStream
import java.io.ByteArrayInputStream

/**
 * UMD 格式解析器
 * UMD (Universal Mobile Document) 是中国移动阅读平台使用的电子书格式
 *
 * UMD v2 文件结构：
 *  - 文件魔术字：0xDE 0xC8 0xFD 0xEF (小端 0xEFFDC8DE)
 *  - 随后是一系列的 Chunk：[2字节类型][4字节长度][数据]
 *  - 文本数据用 zlib 压缩，编码为 UTF-16LE
 *
 * Chunk 类型（十六进制）：
 *  0x0101 书名, 0x0102 作者, 0x0103 年份, 0x0104 月份, 0x0105 日
 *  0x0F00 章节偏移表, 0x0F01 章节标题, 0x0A00 zlib压缩文本块
 */
class UmdParser {

    data class UmdBook(
        val title: String,
        val author: String,
        val chapters: List<ChapterSplitter.SplitChapter>
    )

    fun parse(bytes: ByteArray): UmdBook {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // 验证魔术字
        val magic = buf.int
        if (magic != 0xEFFDC8DE.toInt()) {
            // 不是标准UMD，尝试作为UTF-16LE文本解析
            return fallbackParse(bytes)
        }

        var title = ""
        var author = ""
        val chapterTitles = mutableListOf<String>()
        val textBlocks = mutableListOf<String>()

        while (buf.remaining() >= 6) {
            val chunkType = buf.short.toInt() and 0xFFFF
            val chunkLen = buf.int
            if (chunkLen < 0 || chunkLen > buf.remaining()) break

            val data = ByteArray(chunkLen)
            buf.get(data)

            when (chunkType) {
                0x0101 -> title = String(data, Charsets.UTF_16LE).trimNull()
                0x0102 -> author = String(data, Charsets.UTF_16LE).trimNull()
                0x0F01 -> {
                    // 章节标题块：每个标题以 null 终止
                    var i = 0
                    while (i < data.size) {
                        val end = data.indexOf(0, i)
                        if (end < 0) break
                        if (end > i) {
                            chapterTitles.add(String(data, i, end - i, Charsets.UTF_16LE))
                        }
                        i = end + 2  // UTF-16LE 的 null 是 2字节
                    }
                }
                0x0A00 -> {
                    // zlib 压缩文本块
                    try {
                        val decompressed = InflaterInputStream(ByteArrayInputStream(data)).readBytes()
                        textBlocks.add(String(decompressed, Charsets.UTF_16LE).trimNull())
                    } catch (e: Exception) {
                        textBlocks.add(String(data, Charsets.UTF_16LE).trimNull())
                    }
                }
            }
        }

        // 合并所有文本块
        val fullText = textBlocks.joinToString("\n")

        // 如果有章节标题列表，按标题分割
        val chapters = if (chapterTitles.isNotEmpty()) {
            matchChaptersToText(fullText, chapterTitles)
        } else {
            ChapterSplitter.split(fullText, title)
        }

        return UmdBook(title, author, chapters)
    }

    private fun matchChaptersToText(
        fullText: String,
        chapterTitles: List<String>
    ): List<ChapterSplitter.SplitChapter> {
        val result = mutableListOf<ChapterSplitter.SplitChapter>()
        val indices = chapterTitles.mapNotNull { title ->
            val idx = fullText.indexOf(title)
            if (idx >= 0) Pair(title, idx) else null
        }.sortedBy { it.second }

        for (i in indices.indices) {
            val (chTitle, start) = indices[i]
            val end = if (i + 1 < indices.size) indices[i + 1].second else fullText.length
            val content = fullText.substring(start + chTitle.length, end).trim()
            result.add(ChapterSplitter.SplitChapter(chTitle, content))
        }
        return result.ifEmpty { ChapterSplitter.split(fullText, "正文") }
    }

    private fun fallbackParse(bytes: ByteArray): UmdBook {
        val text = EncodingDetector.readText(bytes)
        return UmdBook(
            title = "",
            author = "",
            chapters = ChapterSplitter.split(text, "正文")
        )
    }

    private fun ByteArray.indexOf(value: Byte, startIndex: Int = 0): Int {
        for (i in startIndex until size) {
            if (this[i] == value) return i
        }
        return -1
    }

    private fun String.trimNull(): String = trimEnd('\u0000')
}
