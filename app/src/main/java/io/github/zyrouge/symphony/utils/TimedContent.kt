package io.github.zyrouge.symphony.utils

import java.time.Duration

data class TimedWord(val time: Long, val text: String)

data class TimedLine(val time: Long, val text: String, val words: List<TimedWord>)

data class TimedContent(val lines: List<TimedLine>) {
    val isSynced: Boolean get() = lines.firstOrNull()?.time != lines.lastOrNull()?.time
    val isWordSynced: Boolean get() = lines.any { it.words.isNotEmpty() }

    companion object {
        val lrcLineSeparatorRegex = Regex("""\n|\r|\r\n""")
        val lrcLineFilterRegex = Regex("""^\[\s*(\d+):(\d+)\.(\d+)?\s*](.*)""")
        val lrcWordFilterRegex = Regex("""<(\d+):(\d+)\.(\d+)>([^<]*)""")

        fun fromLyrics(content: String): TimedContent {
            var lastTime = 0L
            val lines = content.split(lrcLineSeparatorRegex).mapNotNull { x ->
                val match = lrcLineFilterRegex.matchEntire(x)
                if (match != null) {
                    val time = parseTime(match.groupValues[1], match.groupValues[2], match.groupValues[3])
                    val rawText = match.groupValues[4]
                    
                    val words = mutableListOf<TimedWord>()
                    var cleanText = ""
                    
                    val firstTagIndex = rawText.indexOf('<')
                    if (firstTagIndex > 0) {
                        val leadingText = rawText.substring(0, firstTagIndex)
                        words.add(TimedWord(time, leadingText))
                        cleanText += leadingText
                    } else if (firstTagIndex == -1) {
                        cleanText = rawText.trim()
                    }
                    
                    val wordMatches = lrcWordFilterRegex.findAll(rawText)
                    for (wordMatch in wordMatches) {
                        val wTime = parseTime(wordMatch.groupValues[1], wordMatch.groupValues[2], wordMatch.groupValues[3])
                        val wText = wordMatch.groupValues[4]
                        words.add(TimedWord(wTime, wText))
                        cleanText += wText
                    }
                    
                    lastTime = time
                    TimedLine(time, cleanText, words)
                } else {
                    val trimmed = x.trim()
                    if (trimmed.isNotEmpty()) TimedLine(lastTime, trimmed, emptyList()) else null
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