package io.github.zyrouge.symphony.utils

import java.time.Duration

data class TimedWord(val time: Long, val text: String)

data class TimedLine(val time: Long, val text: String, val words: List<TimedWord>)

data class TimedContent(val lines: List<TimedLine>) {
    // Fixed: comparing first vs last time fails for single-line content and for
    // files where two lines accidentally share a timestamp. Any time > 0 means
    // the file carries real sync data.
    val isSynced: Boolean get() = lines.any { it.time > 0L }
    val isWordSynced: Boolean get() = lines.any { it.words.isNotEmpty() }

    companion object {
        val lrcLineSeparatorRegex = Regex("""\n|\r|\r\n""")
        val lrcLineFilterRegex = Regex("""^\[\s*(\d+):(\d+)\.(\d+)?\s*](.*)""")
        val lrcWordFilterRegex = Regex("""<(\d+):(\d+)\.(\d+)>([^<]*)""")

        // Filters out full-line metadata headers: [ar:…], [ti:…], [by:…], etc.
        val lrcMetadataRegex = Regex(
            """^\[(ar|ti|al|by|offset|length|re|ve):.*]""",
            RegexOption.IGNORE_CASE
        )

        // Matches lines whose entire text content (after the timestamp) is a
        // structural section tag, e.g. "[Chorus]", "[Verse 2]", "[Bridge]".
        // These carry no lyric text and should be dropped.
        val lrcSectionLineRegex = Regex(
            """^\[(?:verse|chorus|bridge|intro|outro|pre-?chorus|hook|interlude|breakdown|drop)\b[^\]]*]$""",
            RegexOption.IGNORE_CASE
        )

        // Strips inline section prefixes that appear at the start of a line's
        // text content, e.g. "v1:", "Chorus:", "ch:", "Verse 2:".
        // These are common in LRC files exported from certain taggers (MusicBee,
        // fre:ac) and should be removed before displaying the lyric text.
        val lrcSectionPrefixRegex = Regex(
            """^(?:v\d+|verse\s*\d*|ch(?:orus)?|bridge|intro|outro|pre-?chorus|hook|interlude|breakdown|drop)\s*:?\s+""",
            RegexOption.IGNORE_CASE
        )

        fun fromLyrics(content: String): TimedContent {
            var lastTime = 0L
            val lines = content.split(lrcLineSeparatorRegex).mapNotNull { x ->
                val trimmed = x.trim()
                if (trimmed.isEmpty() || lrcMetadataRegex.matches(trimmed)) {
                    return@mapNotNull null
                }

                val match = lrcLineFilterRegex.matchEntire(trimmed)
                if (match != null) {
                    val time = parseTime(match.groupValues[1], match.groupValues[2], match.groupValues[3])
                    val rawText = match.groupValues[4]

                    // Step 1: drop lines whose entire text is a section tag like "[Chorus]"
                    val rawTrimmed = rawText.trim()
                    if (lrcSectionLineRegex.matches(rawTrimmed)) {
                        lastTime = time
                        // Keep as a blank timing marker (used for instrumental detection)
                        return@mapNotNull TimedLine(time, "", emptyList())
                    }

                    // Step 2: strip inline section prefixes like "v1:", "Chorus: "
                    val strippedText = lrcSectionPrefixRegex.replaceFirst(rawTrimmed, "")

                    val words = mutableListOf<TimedWord>()
                    var cleanText = ""

                    val firstTagIndex = strippedText.indexOf('<')
                    if (firstTagIndex > 0) {
                        val leadingText = strippedText.substring(0, firstTagIndex)
                        words.add(TimedWord(time, leadingText))
                        cleanText += leadingText
                    } else if (firstTagIndex == -1) {
                        cleanText = strippedText
                    }

                    val wordMatches = lrcWordFilterRegex.findAll(strippedText)
                    for (wordMatch in wordMatches) {
                        val wTime = parseTime(wordMatch.groupValues[1], wordMatch.groupValues[2], wordMatch.groupValues[3])
                        val wText = wordMatch.groupValues[4]
                        words.add(TimedWord(wTime, wText))
                        cleanText += wText
                    }

                    lastTime = time
                    TimedLine(time, cleanText, words)
                } else {
                    // Unsynced line: also strip section prefixes
                    val cleanText = lrcSectionPrefixRegex.replaceFirst(trimmed, "").trim()
                    if (cleanText.isEmpty()) return@mapNotNull null
                    TimedLine(lastTime, cleanText, emptyList())
                }
            }
            return TimedContent(lines)
        }

        private fun parseTime(m: String, s: String, ms: String?): Long {
            val msPadded = (ms ?: "0").padEnd(3, '0').substring(0, 3)
            return Duration.ofMinutes(m.toLong())
                .plusSeconds(s.toLong())
                .plusMillis(msPadded.toLong())
                .toMillis()
        }
    }
}