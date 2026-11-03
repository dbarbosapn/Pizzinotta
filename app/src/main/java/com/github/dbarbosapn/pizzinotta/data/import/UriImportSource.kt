package com.github.dbarbosapn.pizzinotta.data.import

import android.content.ContentResolver
import android.net.Uri
import java.io.InputStream

class UriImportSource(
    private val uri: Uri,
    private val resolver: ContentResolver,
) : ImportSource {
    override val displayName: String by lazy { resolveName() }

    override fun openStream(): InputStream? = resolver.openInputStream(uri)

    private fun resolveName(): String {
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                for (column in NAME_COLUMNS) {
                    val index = cursor.getColumnIndex(column)
                    if (index >= 0) {
                        cursor.getString(index)?.let { raw ->
                            basenameOf(raw)?.takeIf { it.isNotEmpty() }?.let { return it }
                        }
                    }
                }
            }
        }
        uri.lastPathSegment?.let { segment ->
            val base = basenameOf(decode(segment))
            if (!base.isNullOrEmpty() && base.indexOf(':') < 0) return base
        }
        return "document.pdf"
    }

    private companion object {
        private val NAME_COLUMNS = listOf("_display_name", "display_name", "filename")
    }

    private fun decode(segment: String): String =
        try {
            java.net.URLDecoder.decode(segment, "UTF-8")
        } catch (_: IllegalArgumentException) {
            segment
        }

    private fun basenameOf(path: String?): String? {
        if (path.isNullOrEmpty()) return null
        val trimmed = path.trim().trimEnd('/', '\\')
        if (trimmed.isEmpty()) return null
        val separator =
            trimmed.lastIndexOf('/').coerceAtLeast(trimmed.lastIndexOf('\\'))
        return if (separator >= 0) trimmed.substring(separator + 1) else trimmed
    }
}
