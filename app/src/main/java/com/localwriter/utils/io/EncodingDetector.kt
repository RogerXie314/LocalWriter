package com.localwriter.utils.io

import org.mozilla.universalchardet.UniversalDetector
import java.io.InputStream
import java.nio.charset.Charset

/**
 * 编码检测与读取工具
 * 核心功能：解决 TXT 等文件的乱码问题
 * 使用 juniversalchardet 库（Mozilla 的 nsUniversalDetector 移植）
 */
object EncodingDetector {

    /**
     * 自动检测字节数组的编码
     * @return 检测到的 Charset，检测失败时返回 GBK（中文小说最常见编码）
     */
    fun detect(bytes: ByteArray): Charset {
        val detector = UniversalDetector(null)
        val bufSize = minOf(bytes.size, 4096)
        detector.handleData(bytes, 0, bufSize)
        detector.dataEnd()
        val detected = detector.detectedCharset
        detector.reset()

        return when {
            detected != null -> {
                try {
                    Charset.forName(detected)
                } catch (e: Exception) {
                    Charsets.UTF_8
                }
            }
            // 未检测到时的智能回退
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
                Charsets.UTF_8  // BOM
            else -> Charset.forName("GBK")  // 中文文件默认
        }
    }

    fun detect(stream: InputStream): Charset {
        val bytes = stream.readBytes()
        return detect(bytes)
    }

    /**
     * 读取文本，自动处理编码
     * 同时处理 UTF-8 BOM
     */
    fun readText(bytes: ByteArray): String {
        val charset = detect(bytes)
        var text = String(bytes, charset)
        // 去除 BOM
        if (text.isNotEmpty() && text[0] == '\uFEFF') {
            text = text.substring(1)
        }
        return text
    }

    fun readText(stream: InputStream): String = readText(stream.readBytes())

    /**
     * 常见编码列表（供用户手动选择时展示）
     */
    val COMMON_CHARSETS = listOf(
        "UTF-8", "GBK", "GB2312", "GB18030", "BIG5",
        "UTF-16", "UTF-16LE", "UTF-16BE", "ISO-8859-1"
    )
}
