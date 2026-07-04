package io.github.dokuendev.dokuen.plugins.dictionary.jpdb

import android.os.Bundle
import android.util.Log
import io.github.dokuendev.dokuenreader.dictionary.CustomActionResult
import io.github.dokuendev.dokuenreader.dictionary.DictionaryErrorCode
import io.github.dokuendev.dokuenreader.dictionary.DictionaryException
import io.github.dokuendev.dokuenreader.dictionary.DictionaryPluginService
import io.github.dokuendev.dokuenreader.dictionary.DictionaryResult
import io.github.dokuendev.dokuenreader.plugin.core.ConfigField
import io.github.dokuendev.dokuenreader.plugin.core.ConfigFieldType
import io.github.dokuendev.dokuenreader.plugin.core.InitResult
import io.github.dokuendev.dokuenreader.plugin.core.InitResultFactory
import io.github.dokuendev.dokuenreader.plugin.core.PluginCapabilityKeys
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class JpdbDictionaryPluginService : DictionaryPluginService() {

    companion object {
        private const val TAG = "JpdbDictionaryPlugin"
    }

    override val capabilities = Bundle().apply {
        putBoolean(PluginCapabilityKeys.HANDLES_SEGMENTATION, true)
        putBoolean(PluginCapabilityKeys.REQUIRES_DICTIONARY_FORM, false)
        putBoolean(PluginCapabilityKeys.REQUIRES_INTERNET, true)
        putStringArray(PluginCapabilityKeys.SUPPORTED_SOURCE_LANGUAGES, arrayOf("ja"))
        putStringArray(PluginCapabilityKeys.SUPPORTED_TARGET_LANGUAGES, arrayOf("en"))
    }

    override val configSchema: List<ConfigField> = listOf(
        ConfigField(
            key = "api_key",
            displayName = "API Key",
            description = "Your JPDB API Key",
            type = ConfigFieldType.STRING,
            defaultValue = null,
            isRequired = true
        ),
        ConfigField(
            key = "deck_id",
            displayName = "Deck ID",
            description = "The deck ID to add cards to",
            type = ConfigFieldType.INT,
            defaultValue = null,
            isRequired = true
        )
    )

    private var apiClient: ApiClient? = null
    private var deckId: Int = -1

    override suspend fun onInitialize(config: Bundle?): InitResult {
        Log.d(TAG, "Initializing JPDB Plugin")
        if (config == null) {
            return InitResultFactory.failure("Configuration is missing")
        }
        val apiKey = config.getString("api_key")
        if (apiKey.isNullOrBlank()) {
            return InitResultFactory.failure("API Key is required")
        }

        this.deckId = config.getInt("deck_id")
        if (this.deckId <= 0) {
            return InitResultFactory.failure("Valid Deck ID is required")
        }

        this.apiClient = ApiClient(apiKey)

        Log.d(TAG, "JPDB Plugin initialized successfully for deck $deckId")
        return InitResultFactory.success()
    }

    override suspend fun onLookup(
        contextText: String,
        cursorStartIndex: Int,
        cursorEndIndex: Int
    ): DictionaryResult {
        Log.d(TAG, "========== NEW LOOKUP ==========")
        Log.d(TAG, "Indices -> Start: $cursorStartIndex | End: $cursorEndIndex")
        Log.d(TAG, "Full Context Text length: ${contextText.length}")
        Log.d(TAG, "Full Context Text: [$contextText]") // Brackets help spot leading/trailing whitespace

        // Detailed Index Logging
        if (cursorStartIndex in contextText.indices) {
            val charAtCursor = contextText[cursorStartIndex]
            val windowStart = maxOf(0, cursorStartIndex - 5)
            val windowEnd = minOf(contextText.length, cursorStartIndex + 5)
            val window = contextText.substring(windowStart, windowEnd)

            Log.d(TAG, "Character at cursor: '$charAtCursor'")
            Log.d(TAG, "Text window (-5 to +5): [$window]")
        } else {
            Log.w(
                TAG,
                "WARNING: cursorStartIndex ($cursorStartIndex) is outside the bounds of contextText (0..${contextText.length - 1})"
            )
        }

        val client = apiClient ?: throw DictionaryException(
            DictionaryErrorCode.SERVICE_DISABLED,
            "Plugin not initialized"
        )

        // Basic bounds checking
        if (cursorStartIndex < 0 || cursorStartIndex > contextText.length) {
            Log.d(TAG, "Lookup aborted: Cursor out of bounds")
            return DictionaryResult(emptyArray())
        }

        if (contextText.isBlank()) {
            Log.d(TAG, "Lookup aborted: Context text is blank")
            return DictionaryResult(emptyArray())
        }

        try {
            Log.d(TAG, "Sending text to JPDB API for parsing...")
            val jsonResponse = client.parseText(contextText)

            Log.d(TAG, "Raw JPDB Response: $jsonResponse")

            val result = ResultFormatter.format(jsonResponse, cursorStartIndex, cursorEndIndex)

            if (result.entries.isEmpty()) {
                Log.w(
                    TAG,
                    "JPDB parsed successfully, but found NO matching vocabulary overlapping range [$cursorStartIndex, $cursorEndIndex]."
                )
                Log.w(
                    TAG,
                    "If this is a valid word, check if JPDB's returned token bounds are misaligned with the text string we sent them."
                )
            } else {
                Log.d(TAG, "Lookup successful! Formatted ${result.entries.size} dictionary entries.")
            }

            return result

        } catch (e: Exception) {
            Log.e(TAG, "Exception during JPDB lookup", e)
            if (e is java.io.IOException) {
                val msg = e.message ?: ""
                val sanitizedMsg = sanitizeErrorMessage(msg)

                if (msg.contains("401") || msg.contains("403") || msg.contains("unauthorized")) {
                    throw DictionaryException(
                        DictionaryErrorCode.AUTHENTICATION_ERROR,
                        "Authentication failed: $sanitizedMsg"
                    )
                }
                throw DictionaryException(
                    DictionaryErrorCode.NETWORK_ERROR,
                    sanitizedMsg
                )
            }
            throw DictionaryException(
                DictionaryErrorCode.INTERNAL_ERROR,
                "Internal error during lookup: ${e.message}"
            )
        }
    }

    override suspend fun onExecuteCustomAction(actionPayload: String): CustomActionResult {
        Log.d(TAG, "Executing custom action: $actionPayload")
        val client = apiClient ?: throw DictionaryException(
            DictionaryErrorCode.SERVICE_DISABLED,
            "Plugin not initialized"
        )

        // Action format: add_card?vid=X&sid=Y
        val params = actionPayload.substringAfter("?").split("&").associate {
            val parts = it.split("=")
            parts.getOrNull(0) to parts.getOrNull(1)
        }

        val vid = params["vid"]?.toLongOrNull()
        val sid = params["sid"]?.toLongOrNull()

        if (vid == null || sid == null) {
            Log.e(TAG, "Failed to execute custom action: Invalid or missing parameters.")
            throw DictionaryException(
                DictionaryErrorCode.INVALID_ARGUMENT,
                "Invalid parameters: vid and sid are required"
            )
        }

        try {
            Log.d(TAG, "Attempting to add card (vid: $vid, sid: $sid) to deck $deckId...")
            client.addCard(deckId, vid, sid)
            Log.d(TAG, "Card successfully added to JPDB!")
            return CustomActionResult.SuccessMessage("Added word to JPDB deck!")
        } catch (e: Exception) {
            Log.e(TAG, "Exception during addCard", e)
            if (e is java.io.IOException) {
                val msg = e.message ?: ""
                val sanitizedMsg = sanitizeErrorMessage(msg)

                if (msg.contains("401") || msg.contains("403") || msg.contains("unauthorized")) {
                    throw DictionaryException(
                        DictionaryErrorCode.AUTHENTICATION_ERROR,
                        "Authentication failed: $sanitizedMsg"
                    )
                }
                throw DictionaryException(
                    DictionaryErrorCode.NETWORK_ERROR,
                    sanitizedMsg
                )
            }
            throw DictionaryException(
                DictionaryErrorCode.INTERNAL_ERROR,
                "Failed to execute action: ${e.message}"
            )
        }
    }

    private fun sanitizeErrorMessage(rawMessage: String): String {
        if (!rawMessage.contains("{") || !rawMessage.contains("}")) return rawMessage

        return try {
            val jsonString = rawMessage.substring(rawMessage.indexOf("{"))
            val jsonObject = Json.parseToJsonElement(jsonString).jsonObject
            jsonObject["error_message"]?.jsonPrimitive?.content ?: rawMessage
        } catch (_: Exception) {
            rawMessage
        }
    }
}
