package io.github.dokuendev.dokuen.plugins.dictionary.jpdb

import io.github.dokuendev.dokuenreader.dictionary.BlockSpan
import io.github.dokuendev.dokuenreader.dictionary.DictionaryEntry
import io.github.dokuendev.dokuenreader.dictionary.DictionaryResult
import io.github.dokuendev.dokuenreader.dictionary.InlineStyle
import io.github.dokuendev.dokuenreader.dictionary.StyledSpan
import io.github.dokuendev.dokuenreader.dictionary.StyledText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object ResultFormatter {

    /**
     * Formats the JPDB JSON response into a [DictionaryResult].
     *
     * [selectionStart] is inclusive, [selectionEnd] is exclusive, the same convention as
     * [String.substring] / Android's text selection.
     */
    fun format(jsonResponse: String, selectionStart: Int, selectionEnd: Int): DictionaryResult {
        val root = try {
            Json.parseToJsonElement(jsonResponse).jsonObject
        } catch (_: Exception) {
            return DictionaryResult(emptyArray())
        }

        val tokensJson = root["tokens"]?.jsonArray ?: return DictionaryResult(emptyArray())
        val vocabularyJson = root["vocabulary"]?.jsonArray ?: return DictionaryResult(emptyArray())

        // Normalize in case start/end arrive swapped.
        val selStart = minOf(selectionStart, selectionEnd)
        val selEnd = maxOf(selectionStart, selectionEnd)

        // 1. Find all token indices whose character span overlaps the user's selection,
        // even if only partially (e.g. selection starts or ends mid-token).
        val matchedVocabIndices = mutableSetOf<Int>()
        for (tokenElement in tokensJson) {
            val tokenArray = tokenElement.jsonArray
            // token_fields order: ["vocabulary_index", "position", "length"]
            if (tokenArray.size < 3) continue

            val vocabIndex = tokenArray[0].jsonPrimitive.content.toIntOrNull() ?: continue
            val position = tokenArray[1].jsonPrimitive.content.toIntOrNull() ?: continue
            val length = tokenArray[2].jsonPrimitive.content.toIntOrNull() ?: continue
            if (length <= 0) continue

            val tokenEndExclusive = position + length // one past the token's last character

            val overlaps = if (selStart == selEnd) {
                // Zero-width caret (no drag width, e.g. some taps report start == end):
                // treat it as "does this offset fall inside the token".
                position <= selStart && selStart < tokenEndExclusive
            } else {
                // Standard half-open range overlap: [position, tokenEndExclusive) vs [selStart, selEnd)
                position < selEnd && selStart < tokenEndExclusive
            }

            if (overlaps) {
                matchedVocabIndices.add(vocabIndex)
            }
        }

        // If the user tapped on whitespace or punctuation not recognized by JPDB
        if (matchedVocabIndices.isEmpty()) {
            return DictionaryResult(emptyArray())
        }

        // 2. Format only the matching vocabulary items
        val entries = matchedVocabIndices.mapNotNull { index ->
            val element = vocabularyJson.getOrNull(index) ?: return@mapNotNull null
            val vocabArray = element.jsonArray

            // vocabulary_fields order: ["vid", "sid", "spelling", "reading", "meanings", "part_of_speech", "frequency_rank"]
            if (vocabArray.size < 6) return@mapNotNull null

            val vid = vocabArray[0].jsonPrimitive.content.toLongOrNull() ?: return@mapNotNull null
            val sid = vocabArray[1].jsonPrimitive.content.toLongOrNull() ?: return@mapNotNull null
            val spelling = vocabArray[2].jsonPrimitive.content
            val reading = vocabArray[3].jsonPrimitive.content

            val meanings = vocabArray[4].jsonArray.map { it.jsonPrimitive.content }
            val posList = vocabArray[5].jsonArray.map { it.jsonPrimitive.content }
            val frequency = if (vocabArray.size > 6) vocabArray[6].jsonPrimitive.content.toIntOrNull() else null

            val bodyBuilder = StringBuilder()
            val styledSpans = mutableListOf<StyledSpan>()
            val blockSpans = mutableListOf<BlockSpan>()

            val posStart = bodyBuilder.length
            if (posList.isNotEmpty()) {
                bodyBuilder.append("[")
                bodyBuilder.append(posList.joinToString(", "))
                bodyBuilder.append("]")
                val posEnd = bodyBuilder.length
                bodyBuilder.append(" ")
                styledSpans.add(
                    StyledSpan(
                        startIndex = posStart,
                        endIndex = posEnd,
                        style = InlineStyle(foregroundColor = 0xFF805AD5.toInt())
                    )
                )
            }

            if (frequency != null) {
                bodyBuilder.append(" [Freq: ").append(frequency).append("]")
            }
            bodyBuilder.append("\n")

            meanings.forEachIndexed { index, meaning ->
                val itemStart = bodyBuilder.length
                bodyBuilder.append(meaning).append("\n")
                val itemEnd = bodyBuilder.length
                blockSpans.add(
                    BlockSpan(
                        startIndex = itemStart,
                        endIndex = itemEnd,
                        blockType = 1, // BLOCK_TYPE_LIST_ITEM
                        indentLevel = 1,
                        listMarker = "${index + 1}."
                    )
                )
            }

            val linkStart = bodyBuilder.length
            val linkText = "[Add to JPDB Deck]"
            bodyBuilder.append(linkText)
            val linkEnd = bodyBuilder.length
            styledSpans.add(
                StyledSpan(
                    startIndex = linkStart,
                    endIndex = linkEnd,
                    style = InlineStyle(
                        bold = true,
                        foregroundColor = 0xFF1976D2.toInt(),
                        linkUrl = "action:add_card?vid=$vid&sid=$sid"
                    )
                )
            )

            DictionaryEntry(
                headword = spelling,
                pronunciation = RubyBuilder.build(spelling, reading),
                body = StyledText(
                    text = bodyBuilder.toString(),
                    blockSpans = blockSpans.toTypedArray(),
                    styledSpans = styledSpans.toTypedArray()
                )
            )
        }

        return DictionaryResult(entries.toTypedArray())
    }
}
