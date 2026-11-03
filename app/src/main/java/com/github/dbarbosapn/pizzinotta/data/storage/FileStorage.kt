package com.github.dbarbosapn.pizzinotta.data.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

interface FileStorage {
    suspend fun remove(path: String): Boolean
}

class PdfFileStorage : FileStorage {
    override suspend fun remove(path: String): Boolean =
        withContext(Dispatchers.IO) {
            File(path).delete()
        }
}
