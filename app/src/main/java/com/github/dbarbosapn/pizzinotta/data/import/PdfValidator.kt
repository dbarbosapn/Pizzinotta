package com.github.dbarbosapn.pizzinotta.data.import

import java.io.File

interface PdfValidator {
    suspend fun pageCount(file: File): Int
}
