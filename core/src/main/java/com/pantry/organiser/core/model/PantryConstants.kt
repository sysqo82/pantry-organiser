package com.pantry.organiser.core.model

import com.pantry.organiser.core.BuildConfig

object PantryConstants {
    val POCKETBASE_URL = BuildConfig.POCKETBASE_URL
    const val TOTAL_SHELVES = 5
    const val ZONES_PER_SHELF = 3

    /**
     * Maps a 1-indexed shelf number (1 to 5) to a 0-indexed UI row (0 to 4).
     * Shelf 1 (Top) -> Row 0
     * Shelf 5 (Bottom) -> Row 4
     */
    fun shelfToRow(shelfNumber: Int): Int = (shelfNumber - 1).coerceIn(0, TOTAL_SHELVES - 1)

    /**
     * Maps a 0-indexed UI row (0 to 4) to a 1-indexed shelf number (1 to 5).
     */
    fun rowToShelf(row: Int): Int = (row + 1).coerceIn(1, TOTAL_SHELVES)

    /**
     * Maps a 1-indexed zone index (1 to 3) to a 0-indexed UI column (0 to 2).
     */
    fun zoneToCol(zoneIndex: Int): Int = (zoneIndex - 1).coerceIn(0, ZONES_PER_SHELF - 1)

    /**
     * Maps a 0-indexed UI column (0 to 2) to a 1-indexed zone index (1 to 3).
     */
    fun colToZone(col: Int): Int = (col + 1).coerceIn(1, ZONES_PER_SHELF)
    
    fun getShelfName(shelfNumber: Int): String = when (shelfNumber) {
        1 -> "Top Shelf 1"
        2 -> "Shelf 2"
        3 -> "Shelf 3"
        4 -> "Shelf 4"
        5 -> "Bottom Shelf 5"
        else -> "Shelf $shelfNumber"
    }
    
    fun getZoneLabel(zoneIndex: Int): String = when (zoneIndex) {
        1 -> "L"
        2 -> "M"
        3 -> "R"
        else -> zoneIndex.toString()
    }

    /**
     * Validates EAN-13 and UPC checksums to prevent misreads.
     */
    fun isValidBarcodeChecksum(barcode: String): Boolean {
        val digits = barcode.filter { it.isDigit() }
        if (digits.length !in listOf(8, 12, 13)) return false

        return try {
            val checkDigit = digits.last().digitToInt()
            val payload = digits.dropLast(1).reversed()

            var sum = 0
            for ((index, char) in payload.withIndex()) {
                val digit = char.digitToInt()
                // Weighted sum: odd positions (from right) multiplied by 3, even by 1
                sum += if (index % 2 == 0) digit * 3 else digit
            }

            val calculatedCheck = (10 - (sum % 10)) % 10
            checkDigit == calculatedCheck
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Heuristic to extract bundle size (e.g. "4 x 100g", "3x80g", "4x 200ml", "4 Pack", "Pack of 6")
     */
    fun inferUnitsPerPack(name: String?, quantity: String?): Int {
        val searchString = "${name ?: ""} ${quantity ?: ""}".lowercase()

        // 1. Multiplier patterns: "4 x 100g", "3x80g", "4x 200ml", "3 * 100g"
        val multiplierRegex = """(\d+)\s*[x×*]\s*\d+""".toRegex()
        multiplierRegex.find(searchString)?.let {
            val val1 = it.groupValues[1].toIntOrNull() ?: 1
            if (val1 in 2..48) return val1
        }

        // 2. "Pack of X" or "X Pack"
        val packRegex = """pack of (\d+)|(\d+)\s*pack""".toRegex()
        packRegex.find(searchString)?.let {
            val val1 = it.groupValues[1].toIntOrNull() ?: it.groupValues[2].toIntOrNull() ?: 1
            if (val1 in 2..48) return val1
        }

        return 1
    }
}
