package com.minimaldesigner.arise.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** What one GET /v1/food came back with. */
sealed interface FoodFetch {
    data class Fresh(val body: String, val etag: String?) : FoodFetch
    data object NotModified : FoodFetch
    data class Failed(val message: String) : FoodFetch
}

/** Talks to a self-hosted arise-food server (github.com/minimal-designer/arise-food). */
object FoodApi {
    private class Bad(message: String) : Exception(message)

    /** Opens an authorised GET to [path], or throws [Bad] with a line for the user. */
    private fun open(baseUrl: String, token: String, path: String): HttpURLConnection {
        if (token.isBlank()) throw Bad("Add the token first.")
        val url = try {
            URL(baseUrl.trim().trimEnd('/') + path)
        } catch (e: Exception) {
            throw Bad("That address isn't a valid URL.")
        }
        return (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 15000
            setRequestProperty("Authorization", "Bearer ${token.trim()}")
        }
    }

    private fun unreachable(c: HttpURLConnection, e: Exception) =
        "Couldn't reach ${c.url.host}. Is the server up, and reachable from this phone? (${e.javaClass.simpleName})"

    private fun status(code: Int) = when (code) {
        401 -> "The server rejected the token."
        else -> "The server answered $code."
    }

    /** One line for the Settings screen: "OK · 19 days · synced 79ba094" or what went wrong. */
    suspend fun check(baseUrl: String, token: String): String = withContext(Dispatchers.IO) {
        val c = try {
            open(baseUrl, token, "/health")
        } catch (e: Bad) {
            return@withContext e.message!!
        }
        try {
            if (c.responseCode != 200) return@withContext status(c.responseCode)
            val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val sha = j.optString("sha").take(7)
            val days = j.optInt("days")
            val err = j.optString("error").takeIf { it.isNotEmpty() && it != "null" }
            if (err != null) "Reached the server, but its last pull failed: $err"
            else "OK · $days days · synced $sha"
        } catch (e: Exception) {
            unreachable(c, e)
        } finally {
            c.disconnect()
        }
    }

    /** The whole log. With [etag] from the last copy, an unchanged log costs a 304. */
    suspend fun fetch(baseUrl: String, token: String, etag: String?): FoodFetch = withContext(Dispatchers.IO) {
        val c = try {
            open(baseUrl, token, "/v1/food")
        } catch (e: Bad) {
            return@withContext FoodFetch.Failed(e.message!!)
        }
        try {
            if (etag != null) c.setRequestProperty("If-None-Match", etag)
            when (val code = c.responseCode) {
                200 -> FoodFetch.Fresh(c.inputStream.bufferedReader().use { it.readText() }, c.getHeaderField("ETag"))
                304 -> FoodFetch.NotModified
                else -> FoodFetch.Failed(status(code))
            }
        } catch (e: Exception) {
            FoodFetch.Failed(unreachable(c, e))
        } finally {
            c.disconnect()
        }
    }
}
