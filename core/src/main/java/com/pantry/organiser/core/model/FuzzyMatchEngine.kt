package com.pantry.organiser.core.model

import java.util.Locale

data class FuzzyMatchResult(
    val matchedItem: PantryItem,
    val score: Double,
    val matchedKeywords: List<String>
)

object FuzzyMatchEngine {

    private val RETAIL_BRANDS = setOf(
        "tesco", "sainsbury", "sainsburys", "sainsbury's", "aldi", "lidl", "asda",
        "morrisons", "waitrose", "marks & spencer", "m&s", "co-op", "coop", "heinz",
        "lurpak", "kellogg", "kelloggs", "kellogg's", "napolina", "anchor", "flora",
        "tate & lyle", "tate and lyle", "silver spoon", "birdseye", "mccain",
        "warburtons", "hovis", "kingsmill", "mcvitie", "mcvities", "mcvitie's",
        "cadbury", "nestle", "nestlé", "uncle ben", "ben's original", "ambrosia",
        "batchelors", "bisto", "oxo", "paxo", "colman's", "colmans", "hellmann's",
        "hellmanns", "knorr", "maggi", "schwartz", "mccormick", "finest", "essential",
        "value", "everyday", "smart price", "savers", "by sainsbury's", "tesco finest"
    )

    private val CONTAINER_AND_QUANTITY_WORDS = setOf(
        "pack", "package", "multipack", "single", "double", "triple", "family", "value",
        "giant", "mini", "small", "medium", "large", "extra", "super", "tin", "tins",
        "can", "cans", "jar", "jars", "bottle", "bottles", "pot", "pots", "tub", "tubs",
        "pouch", "pouches", "bag", "bags", "box", "boxes", "carton", "cartons", "pk",
        "pcs", "pieces", "gram", "grams", "grm", "kilo", "kilos", "kilogram", "kilograms",
        "litre", "litres", "liter", "liters", "millilitre", "millilitres"
    )

    private val STOP_WORDS = setOf(
        "and", "with", "in", "of", "for", "the", "a", "an", "by", "to", "from", "on",
        "at", "brand", "product", "item", "style", "type", "kind", "original", "classic",
        "fresh", "natural", "great", "quality", "best", "super", "premium"
    )

    // Groups of mutually exclusive qualifiers. If one item has term A and another has term B in the same group, score = 0.
    private val QUALIFIER_GROUPS = listOf(
        setOf("salted", "unsalted"),
        setOf("granulated", "caster", "icing", "demerara", "muscovado", "brown"),
        setOf("chopped", "plum", "cherry", "passata", "puree", "paste"),
        setOf("whole", "skimmed", "semi-skimmed", "semi skimmed"),
        setOf("kidney", "baked", "cannellini", "butter", "black", "haricot", "pinto")
    )

    fun findAllMatches(
        scannedProductName: String,
        scannedBrand: String? = null,
        scannedBarcode: String? = null,
        pantryItems: List<PantryItem>,
        minThreshold: Double = 0.55
    ): List<FuzzyMatchResult> {
        if (scannedProductName.isBlank() || pantryItems.isEmpty()) return emptyList()

        val scannedTokens = extractCoreTokens(scannedProductName, scannedBrand)
        if (scannedTokens.isEmpty()) return emptyList()

        val results = mutableListOf<FuzzyMatchResult>()
        val trimmedBarcode = scannedBarcode?.trim()

        for (item in pantryItems) {
            // Ignore if exact barcode matches (exact matches are handled separately)
            if (!trimmedBarcode.isNullOrBlank() && item.barcode?.trim().equals(trimmedBarcode, ignoreCase = true)) {
                continue
            }

            val itemTokens = extractCoreTokens(item.name, item.brand)
            if (itemTokens.isEmpty()) continue

            if (hasQualifierConflict(scannedTokens, itemTokens)) continue

            val (score, matchingTokens) = calculateSimilarity(scannedTokens, itemTokens)
            if (score >= minThreshold) {
                results.add(
                    FuzzyMatchResult(
                        matchedItem = item,
                        score = score,
                        matchedKeywords = matchingTokens
                    )
                )
            }
        }

        return results.sortedByDescending { it.score }
    }

    fun findBestMatch(
        scannedProductName: String,
        scannedBrand: String? = null,
        scannedBarcode: String? = null,
        pantryItems: List<PantryItem>,
        minThreshold: Double = 0.55
    ): FuzzyMatchResult? {
        return findAllMatches(scannedProductName, scannedBrand, scannedBarcode, pantryItems, minThreshold).firstOrNull()
    }

    fun extractCoreTokens(name: String, brand: String? = null): Set<String> {
        var text = name.lowercase(Locale.ROOT)

        // Remove explicit brand if passed
        if (!brand.isNullOrBlank()) {
            val lowerBrand = brand.lowercase(Locale.ROOT)
            text = text.replace(lowerBrand, "")
            text = text.replace(lowerBrand.replace("'s", ""), "")
        }

        // Remove quantity expressions like 1kg, 500g, 400ml, 4x100g, pack of 4
        text = text.replace("""\b\d+(\.\d+)?\s*(g|kg|ml|l|cl|oz|lb|pk|grm)\b""".toRegex(), " ")
        text = text.replace("""\b\d+\s*[x×*]\s*\d+\w*\b""".toRegex(), " ")
        text = text.replace("""\bpack of \d+\b""".toRegex(), " ")

        // Remove non-alphanumeric chars
        text = text.replace("[^a-z0-9\\s]".toRegex(), " ")

        val rawTokens = text.split("\\s+".toRegex()).filter { it.isNotBlank() }

        val tokens = mutableSetOf<String>()
        for (token in rawTokens) {
            if (token in STOP_WORDS) continue
            if (token in RETAIL_BRANDS) continue
            if (token in CONTAINER_AND_QUANTITY_WORDS) continue
            if (token.length < 2 && !token.all { it.isDigit() }) continue
            tokens.add(token)
        }

        return tokens
    }

    private fun hasQualifierConflict(tokens1: Set<String>, tokens2: Set<String>): Boolean {
        for (group in QUALIFIER_GROUPS) {
            val inGroup1 = tokens1.filter { it in group }
            val inGroup2 = tokens2.filter { it in group }
            if (inGroup1.isNotEmpty() && inGroup2.isNotEmpty() && inGroup1.intersect(inGroup2.toSet()).isEmpty()) {
                return true
            }
        }
        return false
    }

    private fun calculateSimilarity(
        tokens1: Set<String>,
        tokens2: Set<String>
    ): Pair<Double, List<String>> {
        val matchingTokens = mutableListOf<String>()

        for (t1 in tokens1) {
            for (t2 in tokens2) {
                if (t1 == t2 || isFuzzyTokenMatch(t1, t2)) {
                    matchingTokens.add(t1)
                    break
                }
            }
        }

        if (matchingTokens.isEmpty()) return Pair(0.0, emptyList())

        val matchCount = matchingTokens.size.toDouble()
        val minSize = minOf(tokens1.size, tokens2.size).toDouble()
        val unionSize = tokens1.union(tokens2).size.toDouble()

        val overlapScore = matchCount / minSize
        val jaccardScore = matchCount / unionSize

        // Weighted combination favoring overlap score when core keywords match
        val combinedScore = (0.65 * overlapScore) + (0.35 * jaccardScore)

        return Pair(combinedScore.coerceIn(0.0, 1.0), matchingTokens)
    }

    private fun isFuzzyTokenMatch(t1: String, t2: String): Boolean {
        if (kotlin.math.abs(t1.length - t2.length) > 2) return false
        if (t1.length < 4 || t2.length < 4) return t1 == t2

        val distance = levenshteinDistance(t1, t2)
        return distance <= 1
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }
}
