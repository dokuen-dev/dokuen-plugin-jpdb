package io.github.dokuendev.dokuen.plugins.dictionary.jpdb

import io.github.dokuendev.dokuenreader.dictionary.BLOCK_TYPE_LIST_ITEM
import io.github.dokuendev.dokuenreader.dictionary.DictionaryResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class JpdbUnitTests {

    @Test
    fun testParseTextSuccess() = runBlocking {
        val mockResponse =
            """{"tokens": [[0,0,3]], "vocabulary": [[10001, 20002, "食べる", "たべる", ["to eat", "to consume"], ["v1", "transitive verb"], 150]]}"""
        var capturedConnection: FakeHttpURLConnection? = null

        val client = ApiClient("mock_api_key", "https://jpdb.io/api/v1") { url ->
            FakeHttpURLConnection(url, 200, mockResponse).also { capturedConnection = it }
        }

        val result = client.parseText("食べる")

        assertNotNull(capturedConnection)
        assertEquals(mockResponse, result)
        assertEquals("POST", capturedConnection?.requestMethod)
        assertEquals("Bearer mock_api_key", capturedConnection?.getRequestProperty("Authorization"))
        assertEquals("application/json", capturedConnection?.getRequestProperty("Content-Type"))

        val sentBody = capturedConnection?.outputStream?.toString("UTF-8")
        assertTrue(sentBody != null && sentBody.contains("食べる"))
        assertTrue(sentBody != null && sentBody.contains("position_length_encoding"))
    }

    @Test
    fun testAddCardSuccess() = runBlocking {
        val mockResponse = """{"status": "success"}"""
        var capturedConnection: FakeHttpURLConnection? = null

        val client = ApiClient("mock_api_key", "https://jpdb.io/api/v1") { url ->
            FakeHttpURLConnection(url, 200, mockResponse).also { capturedConnection = it }
        }

        val result = client.addCard(42, 10001, 20002)

        assertNotNull(capturedConnection)
        assertEquals(mockResponse, result)
        assertEquals("POST", capturedConnection?.requestMethod)
        assertEquals("Bearer mock_api_key", capturedConnection?.getRequestProperty("Authorization"))
        assertEquals("application/json", capturedConnection?.getRequestProperty("Content-Type"))

        val sentBody = capturedConnection?.outputStream?.toString("UTF-8")
        assertTrue(sentBody != null && sentBody.contains("42"))
        assertTrue(sentBody != null && sentBody.contains("10001"))
    }

    @Test
    fun testResultFormatter() {
        val mockResponse = """
        {
          "tokens": [
            [0, 0, 3]
          ],
          "vocabulary": [
            [
              10001,
              20002,
              "食べる",
              "たべる",
              ["to eat", "to consume"],
              ["v1", "transitive verb"],
              150
            ]
          ]
        }
        """.trimIndent()

        val formattedResult: DictionaryResult = ResultFormatter.format(mockResponse, 0, 1)

        assertNotNull(formattedResult.entries)
        assertEquals(1, formattedResult.entries.size)

        val entry = formattedResult.entries[0]
        assertEquals("食べる", entry.headword)

        val bodyText = entry.body.text
        val expectedText =
            "[v1, transitive verb]  [Freq: 150]\nto eat\nto consume\n[Add to JPDB Deck]"
        assertEquals(expectedText, bodyText)

        // Check styled spans
        val styledSpans = entry.body.styledSpans
        assertNotNull(styledSpans)
        assertEquals(2, styledSpans.size)

        // POS tag styled span
        val posSpan = styledSpans.find { it.startIndex == 0 }
        assertNotNull("Should find POS tag styled span starting at 0", posSpan)
        assertEquals(21, posSpan?.endIndex) // "[v1, transitive verb]".length is 21
        assertEquals(0xFF805AD5.toInt(), posSpan?.style?.foregroundColor)

        // Link styled span
        val linkSpan = styledSpans.find { it.style.linkUrl != null }
        assertNotNull("Should find link styled span", linkSpan)
        assertEquals("action:add_card?vid=10001&sid=20002", linkSpan?.style?.linkUrl)
        assertTrue(linkSpan?.style?.bold == true)
        assertEquals(0xFF1976D2.toInt(), linkSpan?.style?.foregroundColor)

        // Check block spans
        val blockSpans = entry.body.blockSpans
        assertNotNull(blockSpans)
        assertEquals(2, blockSpans.size)

        val meaning1Block = blockSpans[0]
        assertEquals(35, meaning1Block.startIndex)
        assertEquals(42, meaning1Block.endIndex)
        assertEquals(BLOCK_TYPE_LIST_ITEM, meaning1Block.blockType)
        assertEquals(1, meaning1Block.indentLevel)
        assertEquals("1.", meaning1Block.listMarker)

        val meaning2Block = blockSpans[1]
        assertEquals(42, meaning2Block.startIndex)
        assertEquals(53, meaning2Block.endIndex)
        assertEquals(BLOCK_TYPE_LIST_ITEM, meaning2Block.blockType)
        assertEquals(1, meaning2Block.indentLevel)
        assertEquals("2.", meaning2Block.listMarker)
    }
}
