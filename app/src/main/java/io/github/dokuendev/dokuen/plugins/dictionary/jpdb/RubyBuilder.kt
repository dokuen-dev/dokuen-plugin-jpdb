package io.github.dokuendev.dokuen.plugins.dictionary.jpdb

import io.github.dokuendev.dokuenreader.dictionary.RubySpan
import java.util.regex.Pattern

object RubyBuilder {

    private fun isKanji(c: Char): Boolean {
        // Matches standard CJK Unified Ideographs and common Japanese iteration marks
        return c.code in 0x4E00..0x9FBF || c == '々' || c == '〆' || c == 'ヶ'
    }

    private fun toHiragana(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            if (c.code in 0x30A1..0x30F6) {
                sb.append((c.code - 0x60).toChar())
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    /**
     * Builds an array of native RubySpans mapped to the base word's indices.
     */
    fun build(word: String, reading: String): Array<RubySpan> {
        if (word.isBlank() || reading.isBlank() || word == reading) return emptyArray()

        val hWord = toHiragana(word)
        val hReading = toHiragana(reading)

        if (hWord == hReading) return emptyArray()

        // 1. Find continuous blocks of Kanji (e.g. "言い当てる" -> [0..0], [2..2])
        val blocks = mutableListOf<IntRange>()
        var i = 0
        while (i < word.length) {
            if (isKanji(word[i])) {
                val start = i
                while (i < word.length && isKanji(word[i])) {
                    i++
                }
                blocks.add(start until i)
            } else {
                i++
            }
        }

        if (blocks.isEmpty()) return emptyArray()

        var currentReading = reading
        var currentHReading = hReading

        val firstKanjiStart = blocks.first().first
        val lastKanjiEnd = blocks.last().last

        // 2. Strip matching prefix kana (e.g. "お" in "お茶")
        val prefix = hWord.substring(0, firstKanjiStart)
        if (prefix.isNotEmpty() && currentHReading.startsWith(prefix)) {
            currentReading = currentReading.substring(prefix.length)
            currentHReading = currentHReading.substring(prefix.length)
        }

        // 3. Strip matching suffix kana (e.g. "れる" in "垂れる")
        val suffix = hWord.substring(lastKanjiEnd + 1)
        if (suffix.isNotEmpty() && currentHReading.endsWith(suffix)) {
            currentReading = currentReading.substring(0, currentReading.length - suffix.length)
            currentHReading = currentHReading.substring(0, currentHReading.length - suffix.length)
        }

        // 4. Handle single kanji block
        if (blocks.size == 1) {
            return arrayOf(
                RubySpan(
                    startIndex = blocks[0].first,
                    endIndex = blocks[0].last + 1,
                    rubyText = currentReading
                )
            )
        }

        // 5. Handle multiple blocks using Regex for unambiguous interior kana splitting
        val regexBuilder = StringBuilder("^")
        val interiorKanas = mutableListOf<String>()

        for (bIndex in 0 until blocks.size - 1) {
            val interior = hWord.substring(blocks[bIndex].last + 1, blocks[bIndex + 1].first)
            interiorKanas.add(interior)
            regexBuilder.append("(.+?)") // Non-greedy match for the kanji reading
            regexBuilder.append(Pattern.quote(interior))
        }
        regexBuilder.append("(.+?)$")

        val pattern = Pattern.compile(regexBuilder.toString())
        val matcher = pattern.matcher(currentHReading)

        if (matcher.matches()) {
            val spans = mutableListOf<RubySpan>()
            var readingOffset = 0

            for (bIndex in blocks.indices) {
                val block = blocks[bIndex]
                val groupLength = matcher.group(bIndex + 1)?.length ?: 0

                val chunk = currentReading.substring(readingOffset, readingOffset + groupLength)
                if (chunk.isNotEmpty()) {
                    spans.add(
                        RubySpan(
                            startIndex = block.first,
                            endIndex = block.last + 1,
                            rubyText = chunk
                        )
                    )
                }

                readingOffset += groupLength
                if (bIndex < interiorKanas.size) {
                    readingOffset += interiorKanas[bIndex].length
                }
            }
            return spans.toTypedArray()
        }

        // 6. Fallback if regex fails (ambiguous reading with interior kana)
        return arrayOf(
            RubySpan(
                startIndex = blocks.first().first,
                endIndex = blocks.last().last + 1,
                rubyText = currentReading
            )
        )
    }
}
