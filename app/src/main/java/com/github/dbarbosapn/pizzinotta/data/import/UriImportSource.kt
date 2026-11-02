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
            val index = cursor.getColumnIndex("display_name")
            if (index >= 0 && cursor.moveToFirst()) {
                val name = cursor.getString(index)
                if (!name.isNullOrEmpty()) return name
            }
        }
        return uri.lastPathSegment?.substringBeforeLast('.')?.takeIf { it.isNotEmpty() }
            ?: "document.pdf"
    }
}
