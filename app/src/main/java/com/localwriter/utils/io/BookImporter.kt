package com.localwriter.utils.io

import android.content.Context
import android.net.Uri
import nl.siegmann.epublib.domain.Book as EpubBook
import nl.siegmann.epublib.epub.EpubReader
import org.jsoup.Jsoup
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * 书籍导入结果
 */
data class ImportResult(
    val title: String,
    val author: String = "",
    val description: String = "",
    val chapters: List<ChapterSplitter.SplitChapter>
)

/**
 * 多格式书籍导入器
 * 支持：TXT, EPUB, DOC, PDF(提取文本), CHM(提取HTML)
 */
object BookImporter {

    enum class Format { TXT, EPUB, DOC, DOCX, PDF, CHM, UMD, UNKNOWN }

    fun detectFormat(filename: String): Format {
        return when (filename.lowercase().substringAfterLast('.')) {
            "txt" -> Format.TXT
            "epub" -> Format.EPUB
            "doc" -> Format.DOC
            "docx" -> Format.DOCX
            "pdf" -> Format.PDF
            "chm" -> Format.CHM
            "umd" -> Format.UMD
            else -> Format.UNKNOWN
        }
    }

    /**
     * 统一导入入口
     * @param context Android Context
     * @param uri 文件 URI
     * @param filename 文件名（用于判断格式）
     * @param userCharset 用户手动指定的编码（null表示自动检测）
     */
    fun import(
        context: Context,
        uri: Uri,
        filename: String,
        userCharset: String? = null
    ): ImportResult {
        val format = detectFormat(filename)
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("无法打开文件: $filename")
        return stream.use { input ->
            when (format) {
                Format.TXT  -> importTxt(input, filename, userCharset)
                Format.EPUB -> importEpub(input, filename)
                Format.DOC  -> importDoc(input, filename)
                Format.DOCX -> importDocx(input, filename)
                Format.PDF  -> importPdf(input, filename)
                Format.CHM  -> importChm(input, filename)
                Format.UMD  -> importUmd(input, filename)
                else        -> importTxt(input, filename, userCharset) // 未知格式尝试TXT
            }
        }
    }

    // ---------------------------------- TXT ----------------------------------

    private fun importTxt(
        stream: InputStream,
        filename: String,
        userCharset: String?
    ): ImportResult {
        val bytes = stream.readBytes()
        val text = if (userCharset != null) {
            String(bytes, charset(userCharset)).trimBom()
        } else {
            EncodingDetector.readText(bytes)
        }
        val title = filename.substringBeforeLast('.')
        val chapters = ChapterSplitter.split(text, title)
        return ImportResult(title = title, chapters = chapters)
    }

    private fun String.trimBom(): String =
        if (isNotEmpty() && this[0] == '\uFEFF') substring(1) else this

    // --------------------------------- EPUB ---------------------------------

    private fun importEpub(stream: InputStream, filename: String): ImportResult {
        val reader = EpubReader()
        val epub: EpubBook = reader.readEpub(stream)

        val title = epub.title ?: filename.substringBeforeLast('.')
        val author = epub.metadata?.authors?.firstOrNull()?.let {
            "${it.firstname} ${it.lastname}".trim()
        } ?: ""
        val desc = epub.metadata?.descriptions?.firstOrNull() ?: ""

        val chapters = mutableListOf<ChapterSplitter.SplitChapter>()

        // 按 TOC 顺序提取章节
        val tableOfContents = epub.tableOfContents?.tocReferences
        if (!tableOfContents.isNullOrEmpty()) {
            for (ref in tableOfContents) {
                val chTitle = ref.title ?: "章节"
                val content = extractHtmlText(ref.resource?.data ?: ByteArray(0))
                if (content.isNotBlank()) {
                    chapters.add(ChapterSplitter.SplitChapter(chTitle, content))
                }
                // 二级 TOC
                ref.children?.forEach { child ->
                    val subTitle = child.title ?: chTitle
                    val subContent = extractHtmlText(child.resource?.data ?: ByteArray(0))
                    if (subContent.isNotBlank()) {
                        chapters.add(ChapterSplitter.SplitChapter(subTitle, subContent))
                    }
                }
            }
        } else {
            // 无 TOC，按脊柱顺序提取
            epub.spine?.spineReferences?.forEach { spineRef ->
                val resource = spineRef.resource ?: return@forEach
                val content = extractHtmlText(resource.data ?: ByteArray(0))
                if (content.isNotBlank()) {
                    chapters.add(ChapterSplitter.SplitChapter(
                        chapters.size.plus(1).toString() + ". 章节", content
                    ))
                }
            }
        }

        // 如果未能提取到章节，尝试把所有文本合并后分割
        if (chapters.isEmpty()) {
            val allText = StringBuilder()
            epub.spine?.spineReferences?.forEach {
                val text = extractHtmlText(it.resource?.data ?: ByteArray(0))
                allText.append(text).append("\n")
            }
            return ImportResult(title, author, desc, ChapterSplitter.split(allText.toString(), title))
        }

        return ImportResult(title, author, desc, chapters)
    }

    private fun extractHtmlText(data: ByteArray): String {
        if (data.isEmpty()) return ""
        return try {
            val html = EncodingDetector.readText(data)
            val doc = Jsoup.parse(html)
            doc.body()?.text() ?: ""
        } catch (e: Exception) {
            String(data, Charsets.UTF_8)
        }
    }

    // --------------------------------- DOC ----------------------------------
    // DOC 是二进制格式，无法不依赖 POI/OpenOffice 解析；此处尝试提取可读文本（近似）

    private fun importDoc(stream: InputStream, filename: String): ImportResult {
        return try {
            // DOC 文件为复合文档格式，直接提取 UTF-16 可读字符作为近似文本
            val bytes = stream.readBytes()
            val text = extractReadableText(bytes)
            val title = filename.substringBeforeLast('.')
            ImportResult(title = title, chapters = ChapterSplitter.split(text, title))
        } catch (e: Exception) {
            ImportResult(
                title = filename.substringBeforeLast('.'),
                chapters = listOf(ChapterSplitter.SplitChapter("导入内容", "无法解析 DOC 文件（建议转为 DOCX 或 TXT 格式）：${e.message}"))
            )
        }
    }

    /** 从二进制数据中提取连续可读的 ASCII/中文字符（用于 DOC 近似解析） */
    private fun extractReadableText(bytes: ByteArray): String {
        val sb = StringBuilder()
        var i = 0
        while (i < bytes.size - 1) {
            // 尝试 UTF-16 LE 双字节读取
            val lo = bytes[i].toInt() and 0xFF
            val hi = bytes[i + 1].toInt() and 0xFF
            val ch = (hi shl 8) or lo
            if (ch in 0x4E00..0x9FFF || ch in 0x0020..0x007E) {
                sb.append(ch.toChar())
                i += 2
                continue
            }
            // 单字节可打印字符
            if (lo in 0x20..0x7E || lo == 0x0A || lo == 0x0D) {
                sb.append(lo.toChar())
            }
            i++
        }
        return sb.toString().replace("\r\n", "\n").replace("\r", "\n")
    }

    private fun importDocx(stream: InputStream, filename: String): ImportResult {
        return try {
            // DOCX = ZIP，解析 word/document.xml 提取文本
            val zip = ZipInputStream(stream)
            var xmlContent: String? = null
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    xmlContent = zip.readBytes().toString(Charsets.UTF_8)
                    break
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
            zip.close()

            val text = if (xmlContent != null) {
                // 用 jsoup 解析 XML，按 <w:p> 段落提取 <w:t> 文本
                val doc = Jsoup.parse(xmlContent, "", org.jsoup.parser.Parser.xmlParser())
                val sb = StringBuilder()
                var lastWasPara = false
                // 检测段落分隔
                doc.getElementsByTag("w:p").forEach { para ->
                    val paraText = para.getElementsByTag("w:t").joinToString("") { it.text() }
                    if (paraText.isNotBlank()) {
                        sb.append(paraText).append("\n")
                        lastWasPara = false
                    } else {
                        if (!lastWasPara) sb.append("\n")
                        lastWasPara = true
                    }
                }
                sb.toString()
            } else {
                ""
            }
            val title = filename.substringBeforeLast('.')
            ImportResult(title = title, chapters = ChapterSplitter.split(text, title))
        } catch (e: Exception) {
            ImportResult(
                title = filename.substringBeforeLast('.'),
                chapters = listOf(ChapterSplitter.SplitChapter("导入内容", "无法解析 DOCX 文件：${e.message}"))
            )
        }
    }

    // --------------------------------- PDF ----------------------------------

    private fun importPdf(stream: InputStream, filename: String): ImportResult {
        return try {
            val reader = com.itextpdf.text.pdf.PdfReader(stream)
            val text = StringBuilder()
            val strategy = com.itextpdf.text.pdf.parser.SimpleTextExtractionStrategy()
            for (i in 1..reader.numberOfPages) {
                text.append(
                    com.itextpdf.text.pdf.parser.PdfTextExtractor.getTextFromPage(reader, i, strategy)
                )
                text.append("\n")
            }
            reader.close()
            val title = filename.substringBeforeLast('.')
            ImportResult(title = title, chapters = ChapterSplitter.split(text.toString(), title))
        } catch (e: Exception) {
            ImportResult(
                title = filename.substringBeforeLast('.'),
                chapters = listOf(ChapterSplitter.SplitChapter("导入内容", "无法解析 PDF 文件：${e.message}"))
            )
        }
    }

    // --------------------------------- CHM ----------------------------------
    // CHM 是微软帮助格式，实际是压缩的HTML集合
    // Android 无原生库，使用简单的 HTML 提取方式处理解压后内容

    private fun importChm(stream: InputStream, filename: String): ImportResult {
        // 尝试以UTF-8/GBK读取，然后用 Jsoup 提取文本
        val bytes = stream.readBytes()
        return try {
            val text = EncodingDetector.readText(bytes)
            // 判断是否为HTML内容
            val cleaned = if (text.contains("<html", ignoreCase = true)) {
                Jsoup.parse(text).body()?.text() ?: text
            } else text
            val title = filename.substringBeforeLast('.')
            ImportResult(title = title, chapters = ChapterSplitter.split(cleaned, title))
        } catch (e: Exception) {
            ImportResult(
                title = filename.substringBeforeLast('.'),
                chapters = listOf(ChapterSplitter.SplitChapter("导入内容", "CHM 解析需要预先解压：${e.message}"))
            )
        }
    }

    // --------------------------------- UMD ----------------------------------
    // UMD 是中国移动读书格式，小端序二进制格式
    // 以下实现基础 UMD 解析（UMD v2 规范）

    private fun importUmd(stream: InputStream, filename: String): ImportResult {
        val title = filename.substringBeforeLast('.')
        return try {
            val bytes = stream.readBytes()
            val parser = UmdParser()
            val result = parser.parse(bytes)
            ImportResult(
                title = result.title.ifEmpty { title },
                author = result.author,
                chapters = result.chapters
            )
        } catch (e: Exception) {
            ImportResult(
                title = title,
                chapters = listOf(ChapterSplitter.SplitChapter("导入内容", "UMD 解析失败：${e.message}"))
            )
        }
    }
}
