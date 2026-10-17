package com.lucent.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CancellationException

internal object AndroidDocumentImporter {
    fun parse(context: Context, uri: Uri): Result<ParsedDocument> = try {
        Result.success(parseDocument(context, uri))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }

    private fun parseDocument(context: Context, uri: Uri): ParsedDocument {
        val fileName = queryDisplayName(context, uri) ?: "未命名文档"
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (!MarkdownDocumentParser.isSupported(fileName)) {
            error("仅支持导入 TXT 与 Word (DOC、DOCX) 格式文档")
        }

        val bytes = context.contentResolver.openInputStream(uri)?.use { DocumentInputLimits.read(it, DocumentInputLimits.MAX_INPUT_BYTES) }
            ?: error("无法读取该文件数据")

        val title = fileName.substringBeforeLast(".").ifBlank { "导入文档" }
        val markdown = when (ext) {
            "docx" -> MarkdownDocumentParser.parseDocxToMarkdown(bytes)
            "doc" -> MarkdownDocumentParser.parseDocToMarkdown(bytes)
            "txt" -> MarkdownDocumentParser.parseTxtToMarkdown(bytes)
            else -> error("暂不支持该格式")
        }

        return ParsedDocument(
            title = title,
            markdown = markdown
        )
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) return cursor.getString(idx)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

}
