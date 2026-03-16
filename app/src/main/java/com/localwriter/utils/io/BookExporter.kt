package com.localwriter.utils.io

import android.content.Context
import android.net.Uri
import com.localwriter.data.db.entity.Book
import com.localwriter.data.db.entity.Chapter
import com.itextpdf.text.*
import com.itextpdf.text.pdf.PdfWriter
import com.itextpdf.text.pdf.BaseFont
import nl.siegmann.epublib.domain.*
import nl.siegmann.epublib.epub.EpubWriter
import nl.siegmann.epublib.service.MediatypeService
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 多格式书籍导出器
 * 支持：TXT, EPUB, PDF, DOC(DOCX)
 */
object BookExporter {

    enum class ExportFormat { TXT, EPUB, PDF, DOCX }

    data class ExportBook(
        val book: Book,
        val chapters: List<Chapter>
    )

    /**
     * 导出到指定 URI（通过 SAF 文件选择器获取的 URI）
     */
    fun export(context: Context, data: ExportBook, format: ExportFormat, uri: Uri) {
        val stream = context.contentResolver.openOutputStream(uri)
            ?: throw IllegalStateException("无法写入文件")
        stream.use { out ->
            when (format) {
                ExportFormat.TXT  -> exportTxt(data, out)
                ExportFormat.EPUB -> exportEpub(data, out)
                ExportFormat.PDF  -> exportPdf(data, out)
                ExportFormat.DOCX -> exportDocx(data, out)
            }
        }
    }

    // ---------------------------------- TXT ----------------------------------

    private fun exportTxt(data: ExportBook, out: OutputStream) {
        val sb = StringBuilder()
        sb.appendLine(data.book.title)
        sb.appendLine("作者：${data.book.author}")
        sb.appendLine()
        sb.appendLine(data.book.description)
        sb.appendLine()
        sb.appendLine("=".repeat(40))
        sb.appendLine()

        data.chapters.forEach { ch ->
            sb.appendLine(ch.title)
            sb.appendLine()
            // 正文段落缩进（每段加两个全角空格）
            ch.content.lines().forEach { line ->
                if (line.isNotBlank()) sb.appendLine("　　$line") else sb.appendLine()
            }
            sb.appendLine()
        }
        // UTF-8 with BOM（兼容 Windows 记事本）
        out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        out.write(sb.toString().toByteArray(Charsets.UTF_8))
    }

    // --------------------------------- EPUB ---------------------------------

    private fun exportEpub(data: ExportBook, out: OutputStream) {
        val epubBook = Book()

        // 元数据
        val metadata = epubBook.metadata
        metadata.addTitle(data.book.title)
        if (data.book.author.isNotBlank()) {
            metadata.addAuthor(Author(data.book.author))
        }
        if (data.book.description.isNotBlank()) {
            metadata.addDescription(data.book.description)
        }

        // CSS 样式
        val css = """
            body { font-family: serif; line-height: 1.8; margin: 1em 1.5em; }
            h1 { font-size: 1.5em; text-align: center; margin: 1em 0; }
            p { text-indent: 2em; margin: 0.3em 0; }
        """.trimIndent()
        val styleResource = Resource(css.toByteArray(Charsets.UTF_8), "style.css")
        epubBook.addResource(styleResource)

        // 封面页
        val coverHtml = buildHtml(
            "封面",
            "<h1>${data.book.title}</h1><p>作者 ${data.book.author}</p>" +
            "<p>${data.book.description}</p>",
            "cover"
        )
        val coverRes = Resource(coverHtml.toByteArray(Charsets.UTF_8), "cover.xhtml")
        coverRes.mediaType = MediatypeService.XHTML
        epubBook.spine.addSpineReference(SpineReference(coverRes))
        epubBook.tableOfContents.addTOCReference(TOCReference("封面", coverRes))

        // 章节
        data.chapters.forEach { chapter ->
            val contentHtml = buildHtml(
                chapter.title,
                "<h1>${chapter.title}</h1>\n" +
                chapter.content.lines().joinToString("\n") { line ->
                    if (line.isBlank()) "<p>&nbsp;</p>" else "<p>$line</p>"
                },
                "ch_${chapter.id}"
            )
            val filename = "ch_${chapter.id}.xhtml"
            val res = Resource(contentHtml.toByteArray(Charsets.UTF_8), filename)
            res.mediaType = MediatypeService.XHTML
            epubBook.spine.addSpineReference(SpineReference(res))
            epubBook.tableOfContents.addTOCReference(
                TOCReference(chapter.title, res)
            )
        }

        EpubWriter().write(epubBook, out)
    }

    private fun buildHtml(title: String, body: String, id: String): String =
        """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="zh">
<head>
  <meta charset="utf-8"/>
  <title>$title</title>
  <link rel="stylesheet" href="style.css"/>
</head>
<body id="$id">
$body
</body>
</html>"""

    // --------------------------------- PDF ----------------------------------

    private fun exportPdf(data: ExportBook, out: OutputStream) {
        val document = Document(PageSize.A4, 60f, 60f, 80f, 80f)
        PdfWriter.getInstance(document, out)
        document.open()

        // 中文字体（使用内置或系统字体）
        val font = try {
            BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED)
        } catch (e: Exception) {
            BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.EMBEDDED)
        }
        val titleFont = Font(font, 22f, Font.BOLD)
        val chapterFont = Font(font, 16f, Font.BOLD)
        val bodyFont = Font(font, 12f, Font.NORMAL)
        val metaFont = Font(font, 11f, Font.ITALIC)

        // 书名
        document.add(Paragraph(data.book.title, titleFont).apply {
            alignment = Element.ALIGN_CENTER
            spacingAfter = 12f
        })
        if (data.book.author.isNotBlank()) {
            document.add(Paragraph("作者：${data.book.author}", metaFont).apply {
                alignment = Element.ALIGN_CENTER
                spacingAfter = 8f
            })
        }
        if (data.book.description.isNotBlank()) {
            document.add(Paragraph(data.book.description, metaFont).apply {
                spacingAfter = 20f
            })
        }
        document.add(Chunk.NEWLINE)

        // 章节内容
        data.chapters.forEach { chapter ->
            document.newPage()
            document.add(Paragraph(chapter.title, chapterFont).apply {
                alignment = Element.ALIGN_CENTER
                spacingAfter = 16f
            })
            chapter.content.lines().forEach { line ->
                if (line.isBlank()) {
                    document.add(Chunk.NEWLINE)
                } else {
                    document.add(Paragraph("　　$line", bodyFont).apply {
                        spacingAfter = 4f
                    })
                }
            }
        }
        document.close()
    }

    // --------------------------------- DOCX ---------------------------------
    // 手动生成 DOCX（Office Open XML = ZIP + XML），零额外依赖

    private fun exportDocx(data: ExportBook, out: OutputStream) {
        val zos = ZipOutputStream(out)

        // [Content_Types].xml
        zos.putNextEntry(ZipEntry("[Content_Types].xml"))
        zos.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml"
    ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>""".toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        // _rels/.rels
        zos.putNextEntry(ZipEntry("_rels/.rels"))
        zos.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1"
    Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
    Target="word/document.xml"/>
</Relationships>""".toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        // word/_rels/document.xml.rels
        zos.putNextEntry(ZipEntry("word/_rels/document.xml.rels"))
        zos.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
</Relationships>""".toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        // word/document.xml
        zos.putNextEntry(ZipEntry("word/document.xml"))
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:wpc="http://schemas.microsoft.com/office/word/2010/wordprocessingCanvas"
  xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:body>""")

        // 书名段落
        sb.append(buildDocxCenteredPara(xmlEscape(data.book.title), bold = true, fontSize = 44))
        if (data.book.author.isNotBlank()) {
            sb.append(buildDocxCenteredPara(xmlEscape("作者：${data.book.author}")))
        }
        if (data.book.description.isNotBlank()) {
            sb.append(buildDocxPara(xmlEscape(data.book.description)))
        }

        // 章节内容
        data.chapters.forEach { chapter ->
            sb.append(buildDocxCenteredPara(xmlEscape(chapter.title), bold = true, fontSize = 32))
            chapter.content.lines().forEach { line ->
                sb.append(buildDocxPara(if (line.isBlank()) "" else xmlEscape("　　$line")))
            }
        }

        sb.append("""<w:sectPr/>
</w:body>
</w:document>""")
        zos.write(sb.toString().toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        zos.finish()
    }

    private fun buildDocxPara(text: String): String =
        "<w:p><w:r><w:t xml:space=\"preserve\">$text</w:t></w:r></w:p>\n"

    private fun buildDocxCenteredPara(text: String, bold: Boolean = false, fontSize: Int = 0): String {
        val jc = "<w:jc w:val=\"center\"/>"
        val rPr = buildString {
            append("<w:rPr>")
            if (bold) append("<w:b/>")
            if (fontSize > 0) append("<w:sz w:val=\"$fontSize\"/><w:szCs w:val=\"$fontSize\"/>")
            append("</w:rPr>")
        }
        return "<w:p><w:pPr>$jc</w:pPr><w:r>$rPr<w:t>$text</w:t></w:r></w:p>\n"
    }

    private fun xmlEscape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
