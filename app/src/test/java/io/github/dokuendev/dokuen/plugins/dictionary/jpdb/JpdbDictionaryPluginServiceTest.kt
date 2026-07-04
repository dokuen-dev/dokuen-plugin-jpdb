package io.github.dokuendev.dokuen.plugins.dictionary.jpdb

import android.os.Bundle
import io.github.dokuendev.dokuenreader.dictionary.CustomActionResult
import io.github.dokuendev.dokuenreader.dictionary.DictionaryErrorCode
import io.github.dokuendev.dokuenreader.dictionary.DictionaryException
import io.github.dokuendev.dokuenreader.plugin.core.PluginHostConfigKeys
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class JpdbDictionaryPluginServiceTest {

    @Test
    fun testCapabilities() {
        val service = JpdbDictionaryPluginService()
        val caps = service.capabilities
        assertTrue(caps.getBoolean("handles_segmentation"))
        assertFalse(caps.getBoolean("requires_dictionary_form"))
        assertTrue(caps.getBoolean("requires_internet"))
        val sourceLangs = caps.getStringArray("supported_source_languages")
        assertNotNull(sourceLangs)
        assertEquals(1, sourceLangs?.size)
        assertEquals("ja", sourceLangs?.get(0))
        val targetLangs = caps.getStringArray("supported_target_languages")
        assertNotNull(targetLangs)
        assertEquals(1, targetLangs?.size)
        assertEquals("en", targetLangs?.get(0))
    }

    @Test
    fun testConfigSchema() {
        val service = JpdbDictionaryPluginService()
        val schema = service.configSchema
        assertEquals(2, schema.size)
        assertEquals("api_key", schema[0].key)
        assertEquals("deck_id", schema[1].key)
        assertTrue(schema[0].isRequired)
        assertTrue(schema[1].isRequired)
    }

    @Test
    fun testInitializeSuccessWithIntDeckId() = runBlocking {
        val service = JpdbDictionaryPluginService()
        val config = Bundle().apply {
            putString("api_key", "test_api_key")
            putInt("deck_id", 42)
            putString(PluginHostConfigKeys.UI_THEME, "dark")
        }
        val result = service.onInitialize(config)
        assertTrue(result.success)

        val deckIdField = JpdbDictionaryPluginService::class.java.getDeclaredField("deckId")
        deckIdField.isAccessible = true
        assertEquals(42, deckIdField.get(service))
    }

    @Test
    fun testInitializeFailureMissingApiKey() = runBlocking {
        val service = JpdbDictionaryPluginService()
        val config = Bundle().apply {
            putInt("deck_id", 42)
        }
        val result = service.onInitialize(config)
        assertFalse(result.success)
        assertTrue(result.errorMessage?.contains("API Key") == true)
    }

    @Test
    fun testInitializeFailureMissingDeckId() = runBlocking {
        val service = JpdbDictionaryPluginService()
        val config = Bundle().apply {
            putString("api_key", "test_api_key")
        }
        val result = service.onInitialize(config)
        assertFalse(result.success)
        assertTrue(result.errorMessage?.contains("Deck ID") == true)
    }

    @Test
    fun testLookupUninitialized() = runBlocking {
        val service = JpdbDictionaryPluginService()
        try {
            service.onLookup("食べる", 0, 3)
            fail("Should throw DictionaryException when uninitialized")
        } catch (e: DictionaryException) {
            assertEquals(DictionaryErrorCode.SERVICE_DISABLED, e.errorCode)
        }
    }

    @Test
    fun testLookupSuccess() = runBlocking {
        val service = JpdbDictionaryPluginService()
        val config = Bundle().apply {
            putString("api_key", "mock_key")
            putInt("deck_id", 42)
        }
        val initResult = service.onInitialize(config)
        assertTrue(initResult.success)

        // Mock ApiClient using reflection
        val mockResponse =
            """{"tokens": [[0,0,3]], "vocabulary": [[10001, 20002, "食べる", "たべる", ["to eat"], ["v1"], 150]]}"""
        val mockClient = ApiClient("mock_key", "https://jpdb.io/api/v1") { url ->
            FakeHttpURLConnection(url, 200, mockResponse)
        }
        val clientField = JpdbDictionaryPluginService::class.java.getDeclaredField("apiClient")
        clientField.isAccessible = true
        clientField.set(service, mockClient)

        val result = service.onLookup("食べる", 0, 3)
        assertEquals(1, result.entries.size)
        assertEquals("食べる", result.entries[0].headword)
    }

    @Test
    fun testLookupNetworkError() = runBlocking {
        val service = JpdbDictionaryPluginService()
        val config = Bundle().apply {
            putString("api_key", "mock_key")
            putInt("deck_id", 42)
        }
        service.onInitialize(config)

        val mockClient = ApiClient("mock_key", "https://jpdb.io/api/v1") { url ->
            FakeHttpURLConnection(url, 500, "Internal Server Error")
        }
        val clientField = JpdbDictionaryPluginService::class.java.getDeclaredField("apiClient")
        clientField.isAccessible = true
        clientField.set(service, mockClient)

        try {
            service.onLookup("食べる", 0, 3)
            fail("Should throw DictionaryException on network error")
        } catch (e: DictionaryException) {
            assertEquals(DictionaryErrorCode.NETWORK_ERROR, e.errorCode)
        }
    }

    @Test
    fun testLookupAuthenticationError() = runBlocking {
        val service = JpdbDictionaryPluginService()
        val config = Bundle().apply {
            putString("api_key", "mock_key")
            putInt("deck_id", 42)
        }
        service.onInitialize(config)

        val mockClient = ApiClient("mock_key", "https://jpdb.io/api/v1") { url ->
            FakeHttpURLConnection(url, 401, "Unauthorized")
        }
        val clientField = JpdbDictionaryPluginService::class.java.getDeclaredField("apiClient")
        clientField.isAccessible = true
        clientField.set(service, mockClient)

        try {
            service.onLookup("食べる", 0, 3)
            fail("Should throw DictionaryException on auth error")
        } catch (e: DictionaryException) {
            assertEquals(DictionaryErrorCode.AUTHENTICATION_ERROR, e.errorCode)
        }
    }

    @Test
    fun testExecuteCustomActionSuccess() = runBlocking {
        val service = JpdbDictionaryPluginService()
        val config = Bundle().apply {
            putString("api_key", "mock_key")
            putInt("deck_id", 42)
        }
        service.onInitialize(config)

        var capturedUrl: String? = null
        val mockClient = ApiClient("mock_key", "https://jpdb.io/api/v1") { url ->
            capturedUrl = url.toString()
            FakeHttpURLConnection(url, 200, """{"status": "success"}""")
        }
        val clientField = JpdbDictionaryPluginService::class.java.getDeclaredField("apiClient")
        clientField.isAccessible = true
        clientField.set(service, mockClient)

        val actionResult = service.onExecuteCustomAction("add_card?vid=10001&sid=20002")
        assertTrue(actionResult is CustomActionResult.SuccessMessage)
        assertEquals("Added word to JPDB deck!", (actionResult as CustomActionResult.SuccessMessage).message)
        assertNotNull(capturedUrl)
        assertTrue(capturedUrl?.contains("/deck/add-vocabulary") == true)
    }
}
