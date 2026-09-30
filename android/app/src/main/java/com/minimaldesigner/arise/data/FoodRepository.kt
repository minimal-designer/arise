package com.minimaldesigner.arise.data

import android.content.Context
import com.minimaldesigner.arise.core.FoodJson
import com.minimaldesigner.arise.core.FoodLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * What the Food screen knows. [log] is the last good copy and survives failed syncs,
 * so the app keeps working off the tailnet. [error] is the latest sync's failure, if any.
 */
data class FoodState(
    val log: FoodLog? = null,
    val syncedAt: Long? = null,
    val syncing: Boolean = false,
    val error: String? = null,
    val configured: Boolean = false,
)

/**
 * Keeps a copy of arise-food's /v1/food in the app's files, as the raw body plus a
 * small meta file (ETag, then sync time), and refreshes it on request.
 */
class FoodRepository(context: Context) {
    private val body = File(context.filesDir, "food.json")
    private val meta = File(context.filesDir, "food.meta")
    private val lock = Mutex()
    private var lastAttempt = 0L

    private val _state = MutableStateFlow(FoodState())
    val state: StateFlow<FoodState> = _state.asStateFlow()

    /** Reads the saved copy. Call once at start-up, before the first [sync]. */
    suspend fun load() = withContext(Dispatchers.IO) {
        val log = runCatching { if (body.exists()) FoodJson.parse(body.readText()) else null }.getOrNull()
        val syncedAt = runCatching { meta.readLines().getOrNull(1)?.toLongOrNull() }.getOrNull()
        _state.update { it.copy(log = log ?: it.log, syncedAt = if (log != null) syncedAt else it.syncedAt) }
    }

    /**
     * Refreshes from the server. Unless [force], does nothing if the last try was under
     * [STALE_MS] ago. A blank token only marks Food as not set up.
     */
    suspend fun sync(settings: Settings, force: Boolean) {
        val configured = settings.token.isNotBlank() && settings.nasUrl.isNotBlank()
        _state.update { it.copy(configured = configured, error = if (configured) it.error else null) }
        if (!configured) return
        if (!force && System.currentTimeMillis() - lastAttempt < STALE_MS) return
        if (!lock.tryLock()) return
        try {
            lastAttempt = System.currentTimeMillis()
            _state.update { it.copy(syncing = true) }
            val etag = if (_state.value.log != null) readEtag() else null
            when (val r = FoodApi.fetch(settings.nasUrl, settings.token, etag)) {
                is FoodFetch.Fresh -> {
                    val log = try {
                        FoodJson.parse(r.body)
                    } catch (e: Exception) {
                        _state.update { it.copy(syncing = false, error = "The server sent food data the app couldn't read.") }
                        return
                    }
                    val now = System.currentTimeMillis()
                    save(r.body, r.etag, now)
                    _state.update { it.copy(log = log, syncedAt = now, syncing = false, error = null) }
                }
                FoodFetch.NotModified -> {
                    val now = System.currentTimeMillis()
                    save(null, readEtag(), now)
                    _state.update { it.copy(syncedAt = now, syncing = false, error = null) }
                }
                is FoodFetch.Failed -> _state.update { it.copy(syncing = false, error = r.message) }
            }
        } finally {
            lock.unlock()
        }
    }

    private fun readEtag(): String? = runCatching { meta.readLines().firstOrNull()?.takeIf { it.isNotBlank() } }.getOrNull()

    /** Writes via temp files so a crash mid-write never leaves a half copy. */
    private suspend fun save(newBody: String?, etag: String?, syncedAt: Long) = withContext(Dispatchers.IO) {
        if (newBody != null) write(body, newBody)
        write(meta, "${etag.orEmpty()}\n$syncedAt\n")
    }

    private fun write(f: File, text: String) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(f)) {
            f.writeText(text)
            tmp.delete()
        }
    }

    companion object {
        /** How old the copy can get before opening Home or Food refreshes it. */
        const val STALE_MS = 10 * 60 * 1000L
    }
}
