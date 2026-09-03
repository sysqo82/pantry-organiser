package com.pantry.organiser.core.model

object PantryConstants {
    const val POCKETBASE_URL = "https://pantry.lockpc.co.uk"
    const val TOTAL_SHELVES = 4
    const val ZONES_PER_SHELF = 3

    /**
     * Maps a 1-indexed shelf number (1 to 4) to a 0-indexed UI row (0 to 3).
     * Shelf 4 (Top) -> Row 0
     * Shelf 1 (Bottom) -> Row 3
     */
    fun shelfToRow(shelfNumber: Int): Int = (TOTAL_SHELVES - shelfNumber).coerceIn(0, TOTAL_SHELVES - 1)

    /**
     * Maps a 0-indexed UI row (0 to 3) to a 1-indexed shelf number (1 to 4).
     */
    fun rowToShelf(row: Int): Int = (TOTAL_SHELVES - row).coerceIn(1, TOTAL_SHELVES)

    /**
     * Maps a 1-indexed zone index (1 to 3) to a 0-indexed UI column (0 to 2).
     */
    fun zoneToCol(zoneIndex: Int): Int = (zoneIndex - 1).coerceIn(0, ZONES_PER_SHELF - 1)

    /**
     * Maps a 0-indexed UI column (0 to 2) to a 1-indexed zone index (1 to 3).
     */
    fun colToZone(col: Int): Int = (col + 1).coerceIn(1, ZONES_PER_SHELF)
    
    fun getShelfName(shelfNumber: Int): String = when (shelfNumber) {
        4 -> "Top Shelf 4"
        3 -> "Middle Shelf 3"
        2 -> "Lower Shelf 2"
        1 -> "Bottom Shelf 1"
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
