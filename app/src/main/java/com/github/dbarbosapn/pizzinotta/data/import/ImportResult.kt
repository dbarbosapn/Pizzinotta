package com.github.dbarbosapn.pizzinotta.data.import

import java.util.UUID

sealed interface ImportResult {
    val filename: String

    data class Imported(
        val songId: UUID,
        override val filename: String,
    ) : ImportResult

    data class Duplicate(
        val songId: UUID,
        override val filename: String,
    ) : ImportResult

    data class Failure(
        override val filename: String,
        val reason: Reason,
        val message: String,
    ) : ImportResult {
        enum class Reason { CORRUPT, PASSWORD_PROTECTED, ZERO_PAGES, IO_ERROR }
    }
}

internal fun displayTitleFromFilename(filename: String): String {
    val base = filename.substringBeforeLast('.')
    return base.ifBlank { "Untitled" }
}
