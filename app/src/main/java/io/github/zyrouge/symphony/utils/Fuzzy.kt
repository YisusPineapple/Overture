package io.github.zyrouge.symphony.utils

import kotlin.math.max

class FuzzySearchComparator(val input: String) {
    fun compareString(value: String) = Fuzzy.compare(input, value)

    fun compareCollection(values: Collection<String>): Int? {
        if (values.isEmpty()) {
            return null
        }
        var score = 0
        values.forEach {
            score = max(score, compareString(it))
        }
        return score
    }
}

data class FuzzySearchOption<T>(
    val match: FuzzySearchComparator.(T) -> Int?,
    val weight: Int = 1,
)

data class FuzzyResultEntity<T>(
    val score: Int,
    val entity: T,
)

class FuzzySearcher<T>(val options: List<FuzzySearchOption<T>>) {
    fun search(
        terms: String,
        entities: List<T>,
        maxLength: Int = -1,
    ): List<FuzzyResultEntity<T>> {
        val results = entities
            .map { compare(terms, it) }
            .filter { it.score > 0 } // Overture: Only keep actual matches to speed up rendering
            .sortedByDescending { it.score }
        return when {
            maxLength > -1 -> results.subListNonStrict(maxLength)
            else -> results
        }
    }

    private fun compare(terms: String, entity: T): FuzzyResultEntity<T> {
        var score = 0
        val comparator = FuzzySearchComparator(terms)
        options.forEach { option ->
            option.match.invoke(comparator, entity)?.let {
                score = max(score, it * option.weight)
            }
        }
        return FuzzyResultEntity(score, entity)
    }
}

object Fuzzy {
    // Overture: Replaced heavy FuzzyWuzzy Levenshtein distance with a blazing fast word-matching algorithm.
    // This prevents the UI from freezing on devices with slow eMMC storage during searches.
    fun compare(input: String, against: String): Int {
        val normInput = normalizeTerms(input)
        val normAgainst = normalizeTerms(against)
        
        if (normInput.isEmpty() || normAgainst.isEmpty()) return 0
        if (normAgainst == normInput) return 100
        if (normAgainst.contains(normInput)) return 90
        
        val inputWords = normInput.split(" ")
        val againstWords = normAgainst.split(" ")
        var matches = 0
        
        for (word in inputWords) {
            if (againstWords.any { it.startsWith(word) }) matches++
        }
        
        if (matches == inputWords.size && inputWords.isNotEmpty()) return 80
        
        return 0 
    }

    private val symbolsRegex = Regex("""[~${'$'}&+,:;=?@#|'"<>.^*()\[\]%!\-_/\\]+""")
    private val whitespaceRegex = Regex("""\s+""")
    private fun normalizeTerms(terms: String) = terms.lowercase()
        .replace(symbolsRegex, "")
        .replace(whitespaceRegex, " ")
        .trim()
}