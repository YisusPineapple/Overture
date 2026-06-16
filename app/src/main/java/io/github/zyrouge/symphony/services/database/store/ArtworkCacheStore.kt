package io.github.zyrouge.symphony.services.database.store

import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.services.database.adapters.FileTreeDatabaseAdapter
import java.io.File

class ArtworkCacheStore(val symphony: Symphony) {
    private val adapter = FileTreeDatabaseAdapter(
        File(symphony.applicationContext.dataDir, "covers")
    )

    fun get(key: String) = adapter.get(key)
    fun all() = adapter.list()
    fun clear() = adapter.clear()
}