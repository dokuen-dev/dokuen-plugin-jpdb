package io.github.dokuendev.dokuen.plugins.dictionary.jpdb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.net.HttpURLConnection
import java.net.URL

class ApiClient(
    private val apiKey: String,
    private val baseUrl: String = "https://jpdb.io/api/v1",
    private val openConnection: (URL) -> HttpURLConnection = { url -> url.openConnection() as HttpURLConnection }
) {
    suspend fun parseText(text: String): String {
        val payloadString = buildJsonObject {
            put("text", text)
            put("position_length_encoding", "utf16")
            putJsonArray("token_fields") {
                add("vocabulary_index")
                add("position")
                add("length")
            }
            putJsonArray("vocabulary_fields") {
                add("vid")
                add("sid")
                add("spelling")
                add("reading")
                add("meanings")
                add("part_of_speech")
                add("frequency_rank")
            }
        }.toString()

        return post("/parse", payloadString)
    }

    suspend fun addCard(targetDeck: Int, vid: Long, sid: Long): String {
        val payloadString = buildJsonObject {
            put("id", targetDeck)
            putJsonArray("vocabulary") {
                add(buildJsonArray {
                    add(vid)
                    add(sid)
                })
            }
        }.toString()

        return post("/deck/add-vocabulary", payloadString)
    }

    private suspend fun post(path: String, body: String): String = withContext(Dispatchers.IO) {
        val url = URL("$baseUrl$path")
        val connection = openConnection(url)
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.doOutput = true
            connection.doInput = true
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")

            connection.outputStream.use { os ->
                os.write(body.toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                val errorResponse = try {
                    connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                } catch (_: Exception) {
                    null
                }
                throw java.io.IOException("HTTP error $responseCode: ${errorResponse ?: connection.responseMessage}")
            }
        } finally {
            connection.disconnect()
        }
    }
}
