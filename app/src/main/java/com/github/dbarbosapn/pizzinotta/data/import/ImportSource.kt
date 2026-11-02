package com.github.dbarbosapn.pizzinotta.data.import

import java.io.InputStream

interface ImportSource {
    val displayName: String
    fun openStream(): InputStream?
}
