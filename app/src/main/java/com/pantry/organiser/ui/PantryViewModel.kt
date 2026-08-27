package com.pantry.organiser.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantry.organiser.data.*
import com.pantry.organiser.ui.components.getCellLabel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class ScannerMode { RESTOCK, CONSUME }

data class PantryUiState(
    val items: List<PantryItem> = emptyList(),
    val filteredItems: List<PantryItem> = emptyList(),
    val selectedShelf: Pair<Int, Int>? = null, // (row, col) 0-indexed for UI
    val highlightedItemId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val scannedProduct: OffProduct? = null,
    val editingItem: PantryItem? = null,
    val isScannerVisible: Boolean = false,
    val recognizedItem: PantryItem? = null,
    val userNotification: String? = null,
    val scannerMode: ScannerMode = ScannerMode.RESTOCK,
    val photoCaptureItem: PantryItem? = null,
    val pendingNewItem: PantryItem? = null,
    val pendingConsumeItem: PantryItem? = null,
    val isReadOnly: Boolean = false // Device-level capability lock
)

class PantryViewModel(
    private val repository: PantryRepository,
    private val offRepository: OpenFoodFactsRepository = OpenFoodFactsRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(PantryUiState())
    val uiState: StateFlow<PantryUiState> = _uiState.asStateFlow()

    // Synchronous flag to prevent race conditions during scan processing
    private var isProcessingScan = false

    init {
        viewModelScope.launch {
            repository.allItems.collect { items ->
                _uiState.update { 
                    it.copy(
                        items = items,
                        filteredItems = filterItems(items, it.selectedShelf),
                        editingItem = it.editingItem?.let { current -> 
                            items.find { item -> item.id == current.id } ?: current 
                        }
                    ) 
                }
            }
        }
    }

    fun selectShelf(row: Int, col: Int) {
        _uiState.update { 
            val newSelection = if (it.selectedShelf?.first == row && it.selectedShelf?.second == col) null else row to col
            it.copy(
                selectedShelf = newSelection,
                filteredItems = filterItems(it.items, newSelection),
                highlightedItemId = null
            )
        }
    }

    fun setReadOnly(readOnly: Boolean) {
        _uiState.update { it.copy(isReadOnly = readOnly) }
    }

    fun selectItem(item: PantryItem) {
        _uiState.update { 
            val isAlreadyHighlighted = it.highlightedItemId == item.id
            // Map 1-indexed shelf/zone to 0-indexed row/col with strict clamping
            // Shelf 1 (S1) -> Row 3. Formula: (4 - shelfNumber)
            val shelfRow = (4 - item.shelfNumber).coerceIn(0, 3)
            val shelfCol = (item.zoneIndex - 1).coerceIn(0, 2)
            val newShelf = if (isAlreadyHighlighted) null else shelfRow to shelfCol
            it.copy(
                highlightedItemId = if (isAlreadyHighlighted) null else item.id,
                selectedShelf = newShelf,
                filteredItems = filterItems(it.items, newShelf),
                isScannerVisible = false // Ensure scanner closes when an item is selected
            )
        }
    }

    fun consumePortion(item: PantryItem) {
        if (_uiState.value.isReadOnly) return
        
        // Multipack logic: Show selection overlay instead of immediate consumption
        if (item.unitsPerPack > 1) {
            _uiState.update { it.copy(pendingConsumeItem = item) }
            return
        }

        viewModelScope.launch {
            if (item.trackingType == TrackingType.DISCRETE_COUNT) {
                // Discrete logic: Unify on sealedCount as total units
                if (item.sealedCount > 1) {
                    val updatedItem = item.copy(
                        sealedCount = item.sealedCount - 1,
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.updateItem(updatedItem)
                    _uiState.update { it.copy(userNotification = "Consumed 1 unit of ${item.name} (${updatedItem.sealedCount} left)") }
                } else {
                    repository.deleteItem(item)
                    _uiState.update { it.copy(userNotification = "Removed ${item.name} (Last unit consumed)") }
                }
            } else {
                // Staple/Bulk logic: remains same for now (fill levels)
                var updatedItem = item.copy(updatedAt = System.currentTimeMillis())
                
                if (item.activeFill == FillLevel.EMPTY) {
                    if (item.sealedCount > 0) {
                        updatedItem = updatedItem.copy(
                            sealedCount = item.sealedCount - 1,
                            activeFill = FillLevel.FULL
                        )
                        _uiState.update { it.copy(userNotification = "Opened a sealed pack of ${item.name}") }
                        repository.updateItem(updatedItem)
                    } else {
                        repository.deleteItem(item)
                        _uiState.update { it.copy(userNotification = "Removed ${item.name}") }
                    }
                } else {
                    val nextFill = item.activeFill.prev()
                    if (nextFill == FillLevel.EMPTY) {
                        if (item.sealedCount > 0) {
                            updatedItem = updatedItem.copy(
                                sealedCount = item.sealedCount - 1,
                                activeFill = FillLevel.FULL
                            )
                            _uiState.update { it.copy(userNotification = "Opened a sealed pack of ${item.name}") }
                            repository.updateItem(updatedItem)
                        } else {
                            repository.deleteItem(item)
                            _uiState.update { it.copy(userNotification = "Removed ${item.name}") }
                        }
                    } else {
                        updatedItem = updatedItem.copy(activeFill = nextFill)
                        repository.updateItem(updatedItem)
                    }
                }

            }
        }
    }

    fun consumeUnits(item: PantryItem, unitsToConsume: Int) {
        if (_uiState.value.isReadOnly) return
        viewModelScope.launch {
            // Auto-expand legacy 1-count multipack to full pack size before deduction
            val currentUnits = if (item.unitsPerPack > 1 && item.sealedCount == 1) {
                item.unitsPerPack
            } else {
                item.sealedCount
            }

            val remainingUnits = currentUnits - unitsToConsume
            
            android.util.Log.d("PantryVM", "Consuming $unitsToConsume of ${item.name}. Total: $currentUnits, Remaining: $remainingUnits")

            if (remainingUnits <= 0) {
                android.util.Log.d("PantryVM", "Item finished, deleting: ${item.name}")
                repository.deleteItem(item)
                _uiState.update { it.copy(
                    userNotification = "Consumed $unitsToConsume units of ${item.name}. All finished!",
                    pendingConsumeItem = null,
                    isScannerVisible = false
                ) }

            } else {
                val updatedItem = item.copy(
                    sealedCount = remainingUnits,
                    updatedAt = System.currentTimeMillis()
                )
                repository.updateItem(updatedItem)
                _uiState.update { it.copy(
                    userNotification = "Consumed $unitsToConsume units of ${item.name}. $remainingUnits left.",
                    pendingConsumeItem = null,
                    isScannerVisible = false
                ) }
            }
        }
    }





    fun addSealedUnit(item: PantryItem) {
        if (_uiState.value.isReadOnly) return
        viewModelScope.launch {
            if (item.trackingType == TrackingType.BULK_LEVEL && item.activeFill == FillLevel.EMPTY) {
                // If it was empty, the "new" unit becomes the active one
                repository.updateItem(item.copy(
                    activeFill = FillLevel.FULL,
                    updatedAt = System.currentTimeMillis()
                ))
            } else {
                // Otherwise increment the reserve stock
                val increment = if (item.trackingType == TrackingType.DISCRETE_COUNT) item.unitsPerPack else 1
                repository.updateItem(item.copy(
                    sealedCount = item.sealedCount + increment,
                    updatedAt = System.currentTimeMillis()
                ))
            }
        }
    }




    fun removeSealedUnit(item: PantryItem) {
        if (_uiState.value.isReadOnly) return
        viewModelScope.launch {
            if (item.sealedCount > 0) {
                repository.updateItem(item.copy(
                    sealedCount = item.sealedCount - 1,
                    updatedAt = System.currentTimeMillis()
                ))
            }
        }
    }

    fun incrementActiveFill(item: PantryItem) {
        if (_uiState.value.isReadOnly) return
        viewModelScope.launch {
            val nextFill = item.activeFill.next()
            repository.updateItem(item.copy(
                activeFill = nextFill,
                updatedAt = System.currentTimeMillis()
            ))
        }
    }

    fun clearNotification() {
        _uiState.update { it.copy(userNotification = null) }
    }

    fun startEditItem(item: PantryItem) {
        _uiState.update { it.copy(editingItem = item) }
    }

    fun clearEditingItem() {
        _uiState.update { it.copy(editingItem = null) }
    }

    fun clearHighlight() {
        _uiState.update { 
            it.copy(
                highlightedItemId = null,
                selectedShelf = null,
                filteredItems = filterItems(it.items, null)
            )
        }
    }

    fun updateItem(item: PantryItem) {
        if (_uiState.value.isReadOnly) return
        viewModelScope.launch {
            repository.updateItem(item.copy(updatedAt = System.currentTimeMillis()))
            clearEditingItem()
        }
    }

    fun deleteItem(item: PantryItem) {
        if (_uiState.value.isReadOnly) return
        viewModelScope.launch {
            repository.deleteItem(item)
            clearEditingItem()
        }
    }

    fun restoreApiImage(item: PantryItem) {
        if (_uiState.value.isReadOnly) return
        viewModelScope.launch {
            // Clear both local path and remote custom URL to force fallback to apiImageUrl
            val updated = item.copy(localImageUri = null, imageUrl = null, updatedAt = System.currentTimeMillis())
            repository.updateItem(updated)
            clearEditingItem()
            _uiState.update { it.copy(userNotification = "Restored API image for ${item.name}") }
        }
    }

    fun updateLocalImageUri(item: PantryItem, uri: String) {
        viewModelScope.launch {
            repository.updateLocalImageUri(item, uri)
            _uiState.update { it.copy(photoCaptureItem = null) }
        }
    }

    fun startPhotoCapture(item: PantryItem) {
        _uiState.update { it.copy(photoCaptureItem = item) }
    }

    fun cancelPhotoCapture() {
        _uiState.update { it.copy(photoCaptureItem = null) }
    }

    fun showScanner(mode: ScannerMode = ScannerMode.RESTOCK) {
        _uiState.update { it.copy(isScannerVisible = true, scannerMode = mode) }
    }

    fun hideScanner() {
        _uiState.update { 
            it.copy(
                isScannerVisible = false, 
                scannedProduct = null, 
                recognizedItem = null,
                pendingNewItem = null,
                pendingConsumeItem = null,
                isLoading = false,
                error = null
            ) 
        }
    }

    fun scanBarcode(barcode: String) {
        val trimmedBarcode = barcode.trim()
        if (trimmedBarcode.isEmpty()) return

        // Synchronous check to block concurrent processing
        if (isProcessingScan || _uiState.value.isLoading || 
            _uiState.value.pendingNewItem != null || _uiState.value.pendingConsumeItem != null) {
            android.util.Log.d("PantryVM", "Scan ignored: Busy processing")
            return
        }
        
        isProcessingScan = true
        android.util.Log.d("PantryVM", "Processing barcode: '$trimmedBarcode'")
        
        viewModelScope.launch {
            try {
                // 1. Local Lookup with variants
                val codes = mutableSetOf(trimmedBarcode)
                if (trimmedBarcode.length == 12) codes.add("0$trimmedBarcode")
                else if (trimmedBarcode.length == 13 && trimmedBarcode.startsWith("0")) codes.add(trimmedBarcode.substring(1))

                var existingItem: PantryItem? = null
                for (code in codes) {
                    existingItem = repository.getItemByBarcode(code)
                    if (existingItem != null) break
                }

                if (existingItem != null) {
                    android.util.Log.d("PantryVM", "Local match found for: ${existingItem.name}")
                    
                    // Re-discover missing data or upgrade to multipack
                    if (existingItem.apiImageUrl == null || existingItem.unitsPerPack <= 1) {
                        viewModelScope.launch {
                            val product = offRepository.getProduct(trimmedBarcode)
                            if (product != null) {
                                var updated = existingItem
                                if (existingItem.apiImageUrl == null && product.imageUrl != null) {
                                    updated = updated.copy(apiImageUrl = product.imageUrl)
                                }
                                if (existingItem.unitsPerPack <= 1) {
                                    val inferred = product.inferUnitsPerPack()
                                    if (inferred > 1) {
                                        updated = updated.copy(
                                            unitsPerPack = inferred,
                                            activeCount = if (updated.activeCount <= 1) inferred else updated.activeCount
                                        )
                                        android.util.Log.d("PantryVM", "Discovered multipack for existing item: ${existingItem.name} ($inferred-pack)")
                                    }
                                }
                                if (updated != existingItem) {
                                    repository.updateItem(updated)
                                }
                            }
                        }
                    }
                    
                    handleExistingItemScan(existingItem)

                    isProcessingScan = false
                    return@launch
                }

                // 2. Consume Mode Fail-Fast
                if (_uiState.value.scannerMode == ScannerMode.CONSUME) {
                    _uiState.update { it.copy(error = "Item not in organiser") }
                    isProcessingScan = false // UNLOCK IMMEDIATELY
                    kotlinx.coroutines.delay(2000)
                    _uiState.update { it.copy(error = null) }
                    return@launch
                }

                // 3. API Fetch
                _uiState.update { it.copy(isLoading = true, error = null) }
                val product = offRepository.getProduct(trimmedBarcode)
                isProcessingScan = false // UNLOCK IMMEDIATELY AFTER API RESPONSE
                
                if (product != null) {
                    var trackingType = determineTrackingType(product)
                    val unitsPerPack = product.inferUnitsPerPack()
                    
                    // Force discrete for multipacks
                    if (unitsPerPack > 1) {
                        trackingType = TrackingType.DISCRETE_COUNT
                    }

                    val tempItem = PantryItem(
                        id = "", // Empty ID for new local items
                        name = product.displayProductName ?: "Unknown Item",
                        brand = product.brands ?: "",
                        packageQuantity = product.weight ?: "",
                        shelfNumber = 1,
                        zoneIndex = 1,
                        imageUrl = null, // No custom image yet
                        apiImageUrl = product.imageUrl, // Capture official API image
                        barcode = trimmedBarcode,
                        trackingType = trackingType,
                        unitsPerPack = unitsPerPack,
                        activeCount = 1, // Not used for logic anymore
                        activeFill = FillLevel.FULL,
                        sealedCount = if (trackingType == TrackingType.DISCRETE_COUNT) unitsPerPack else 0
                    )
                    _uiState.update { it.copy(isLoading = false, pendingNewItem = tempItem) }
                } else {


                    _uiState.update { it.copy(isLoading = false, error = "Product not found") }
                    kotlinx.coroutines.delay(2000)
                    _uiState.update { it.copy(error = null) }
                }
            } catch (e: Exception) {
                android.util.Log.e("PantryVM", "Error during scan", e)
                _uiState.update { it.copy(isLoading = false, error = "Scan failed") }
                isProcessingScan = false
                kotlinx.coroutines.delay(2000)
                _uiState.update { it.copy(error = null) }
            }
        }
    }

    private suspend fun handleExistingItemScan(item: PantryItem) {
        if (_uiState.value.isReadOnly) {
            // Read-Only mode just highlights the item (Identity Lookup)
            selectItem(item)
            val label = getCellLabel((4 - item.shelfNumber).coerceIn(0, 3), (item.zoneIndex - 1).coerceIn(0, 2))
            _uiState.update { it.copy(userNotification = "Found: ${item.name} at $label", recognizedItem = item) }
            kotlinx.coroutines.delay(800)
            _uiState.update { it.copy(recognizedItem = null) }
            return
        }

        if (_uiState.value.scannerMode == ScannerMode.CONSUME) {
            // Apply heuristic before setting pendingConsumeItem to ensure UI knows it's a multipack
            var consumeItem = item
            val inferred = PantryItem.inferUnitsPerPack(item.name, item.packageQuantity)
            if (inferred > 1 && item.unitsPerPack <= 1) {
                // If it was tracked as 1 item but is clearly a multipack, upgrade it
                consumeItem = item.copy(
                    unitsPerPack = inferred,
                    sealedCount = if (item.sealedCount == 1) inferred else item.sealedCount
                )
                android.util.Log.d("PantryVM", "Consume-time upgrade for ${item.name}: $inferred-pack")
            }

            if (consumeItem.trackingType == TrackingType.BULK_LEVEL || consumeItem.unitsPerPack > 1) {
                _uiState.update { it.copy(pendingConsumeItem = consumeItem) }
            } else {
                consumePortion(consumeItem)
                _uiState.update { it.copy(userNotification = "Consumed 1 unit of ${consumeItem.name}", recognizedItem = consumeItem) }
                kotlinx.coroutines.delay(800)
                _uiState.update { it.copy(recognizedItem = null) }
            }
        } else {
            // Re-evaluate tracking type and multipack status on scan
            var updatedItem = item
            val nameLower = item.name.lowercase()
            val stapleKeywords = listOf("flour", "sugar", "rice", "pasta", "cereal", "oats", "lentils", "oil", "salt", "syrup", "honey")
            val discreteKeywords = listOf("sauce", "vinegar", "ketchup", "mayonnaise")
            
            var needsRepoUpdate = false

            if (item.trackingType == TrackingType.DISCRETE_COUNT && 
                stapleKeywords.any { nameLower.contains(it) } &&
                discreteKeywords.none { nameLower.contains(it) }
            ) {
                updatedItem = updatedItem.copy(trackingType = TrackingType.BULK_LEVEL)
                needsRepoUpdate = true
                android.util.Log.d("PantryVM", "Auto-upgraded ${item.name} to Staple mode")
            }

            // Re-evaluate multipack if current is 1
            if (updatedItem.unitsPerPack <= 1) {
                val inferredUnits = PantryItem.inferUnitsPerPack(updatedItem.name, updatedItem.packageQuantity)
                if (inferredUnits > 1) {
                    updatedItem = updatedItem.copy(
                        unitsPerPack = inferredUnits,
                        trackingType = TrackingType.DISCRETE_COUNT, // Force discrete for multipacks
                        sealedCount = if (updatedItem.sealedCount <= 1) inferredUnits else updatedItem.sealedCount
                    )
                    needsRepoUpdate = true
                    android.util.Log.d("PantryVM", "Auto-upgraded ${item.name} to ${inferredUnits}-pack Discrete")
                }
            }


            if (needsRepoUpdate) {
                repository.updateItem(updatedItem)
            }

            addSealedUnit(updatedItem)
            if (updatedItem.sealedCount >= 0) {
                val label = getCellLabel((4 - updatedItem.shelfNumber).coerceIn(0, 3), (updatedItem.zoneIndex - 1).coerceIn(0, 2))
                _uiState.update { it.copy(userNotification = "Added 1 pack to $label", recognizedItem = updatedItem) }
            }
            kotlinx.coroutines.delay(800)
            _uiState.update { it.copy(recognizedItem = null) }
        }

    }

    fun assignPendingItemShelf(row: Int, col: Int) {
        if (_uiState.value.isReadOnly) return
        val pending = _uiState.value.pendingNewItem ?: return
        viewModelScope.launch {
            // Map Row 3 (Bottom) to Shelf 1. Standard mapping: (4 - row)
            val shelfNumber = (4 - row).coerceIn(1, 4)
            val zoneIndex = (col + 1).coerceIn(1, 3)
            val newItem = pending.copy(shelfNumber = shelfNumber, zoneIndex = zoneIndex)
            android.util.Log.d("PantryVM", "Assigning shelf: row=$row -> shelf=$shelfNumber")
            repository.addItem(newItem)
            _uiState.update { it.copy(pendingNewItem = null, userNotification = "Saved to shelf ${shelfNumber}") }
        }
    }

    fun cancelPendingItem() {
        _uiState.update { it.copy(pendingNewItem = null) }
    }

    fun updatePendingConsumeLevel(level: FillLevel) {
        val item = _uiState.value.pendingConsumeItem ?: return
        viewModelScope.launch {
            if (level == FillLevel.EMPTY) {
                if (item.sealedCount > 0) {
                    repository.updateItem(item.copy(
                        sealedCount = item.sealedCount - 1,
                        activeFill = FillLevel.FULL,
                        updatedAt = System.currentTimeMillis()
                    ))
                    _uiState.update { it.copy(userNotification = "Opened a sealed pack of ${item.name}") }
                } else {
                    repository.deleteItem(item)
                    _uiState.update { it.copy(userNotification = "Removed ${item.name}") }
                }
            } else {
                repository.updateItem(item.copy(activeFill = level, updatedAt = System.currentTimeMillis()))
                _uiState.update { it.copy(userNotification = "Updated ${item.name} to $level") }
            }
            _uiState.update { it.copy(pendingConsumeItem = null, isScannerVisible = false) }
        }
    }



    fun updatePendingConsumeUnits(units: Int) {
        val item = _uiState.value.pendingConsumeItem ?: return
        consumeUnits(item, units)
    }


    fun cancelPendingConsume() {
        _uiState.update { it.copy(pendingConsumeItem = null) }
    }

    fun startManualEntry() {
        _uiState.update { it.copy(scannedProduct = OffProduct(), error = null) }
    }

    fun saveScannedItem(
        name: String, 
        brand: String, 
        packageQuantity: String, 
        row: Int, // 0-indexed
        col: Int, // 0-indexed
        imageUrl: String?, 
        barcode: String?,
        trackingType: TrackingType
    ) {
        if (_uiState.value.isReadOnly) return
        val trimmedBarcode = barcode?.trim()?.takeIf { it.isNotEmpty() }
        
        viewModelScope.launch {
            // Map UI row/col to 1-indexed shelf/zone with strict clamping
            val shelfNumber = (4 - row).coerceIn(1, 4)
            val zoneIndex = (col + 1).coerceIn(1, 3)

            val existingItem = _uiState.value.items.find { 
                it.shelfNumber == shelfNumber && it.zoneIndex == zoneIndex && (
                    (trimmedBarcode != null && it.barcode == trimmedBarcode) || 
                    (it.name.equals(name, ignoreCase = true) && it.brand.equals(brand, ignoreCase = true))
                )
            }

            if (existingItem != null) {
                // If it exists, update stock and ensure barcode is saved if it was missing
                var updatedItem = existingItem
                if (existingItem.barcode == null && trimmedBarcode != null) {
                    updatedItem = updatedItem.copy(barcode = trimmedBarcode)
                }
                // Also update tracking type if the heuristic now says it's a staple
                if (existingItem.trackingType == TrackingType.DISCRETE_COUNT && trackingType == TrackingType.BULK_LEVEL) {
                    updatedItem = updatedItem.copy(trackingType = TrackingType.BULK_LEVEL)
                }
                
                addSealedUnit(updatedItem)
                selectItem(updatedItem)
            } else {
                val isOffUrl = imageUrl?.contains("openfoodfacts.org") == true
                val inferredUnits = PantryItem.inferUnitsPerPack(name, packageQuantity)
                val newItem = PantryItem(
                    id = "", // Will be assigned a temp ID in repository
                    name = name,
                    brand = brand,
                    packageQuantity = packageQuantity,
                    shelfNumber = shelfNumber.coerceIn(1, 4),
                    zoneIndex = zoneIndex.coerceIn(1, 3),
                    imageUrl = if (isOffUrl) null else imageUrl,
                    apiImageUrl = if (isOffUrl) imageUrl else null,
                    barcode = trimmedBarcode,
                    trackingType = trackingType,
                    unitsPerPack = inferredUnits,
                    activeCount = 1,
                    activeFill = FillLevel.FULL,
                    sealedCount = if (trackingType == TrackingType.DISCRETE_COUNT) inferredUnits else 0
                )
                repository.addItem(newItem)
            }

            _uiState.update { it.copy(scannedProduct = null, isScannerVisible = false) }
        }
    }

    fun clearScannedProduct() {
        _uiState.update { it.copy(scannedProduct = null) }
    }

    private fun filterItems(items: List<PantryItem>, selection: Pair<Int, Int>?): List<PantryItem> {
        return if (selection == null) items else {
            // Standard mapping: Row 0 -> Shelf 4, Row 3 -> Shelf 1
            val targetShelf = (4 - selection.first).coerceIn(1, 4)
            val targetZone = (selection.second + 1).coerceIn(1, 3)
            items.filter { it.shelfNumber.coerceIn(1, 4) == targetShelf && it.zoneIndex.coerceIn(1, 3) == targetZone }
        }
    }

    internal fun determineTrackingType(product: OffProduct): TrackingType {
        val categories = product.categoriesTags
        val quantity = product.weight
        val name = product.displayProductName ?: ""

        val strictBulkKeywords = listOf(
            "flour", "sugar", "rice", "pasta", "cooking oil", "olive oil", 
            "vegetable oil", "sunflower oil", "cereal", "oats", "lentils", 
            "baking powder", "salt"
        )
        
        // Removed "pack" as it's too aggressive for items like "Flour Pack"
        val forceDiscreteKeywords = listOf(
            "sauce", "soy sauce", "ketchup", "mayonnaise", "mustard", 
            "vinegar", "dressing", "can", "tin", "jar", "bottle"
        )

        val categoriesCombined = categories?.joinToString(" ")?.lowercase() ?: ""
        val quantityLower = quantity?.lowercase() ?: ""
        val nameLower = name.lowercase()

        // 1. Strict Bulk Keywords check (Highest Priority)
        // These are staples typically managed by fill level (dry goods and oils).
        if (strictBulkKeywords.any { categoriesCombined.contains(it) || nameLower.contains(it) }) {
            return TrackingType.BULK_LEVEL
        }

        // 2. Force Discrete check
        // If it's explicitly a sauce, condiment, or mentions a container (bottle/jar), it's discrete.
        if (forceDiscreteKeywords.any { 
            categoriesCombined.contains(it) || quantityLower.contains(it) || nameLower.contains(it) 
        }) {
            return TrackingType.DISCRETE_COUNT
        }

        // 3. Size/Unit Heuristics Fallback
        if (quantityLower.isNotEmpty()) {
            // Check for large quantities (>= 1kg or >= 1L)
            val hasLargeUnit = quantityLower.contains("kg") || 
                             quantityLower.contains(" l ") || 
                             quantityLower.contains("liter") ||
                             quantityLower.contains("litre")
            
            if (hasLargeUnit) return TrackingType.BULK_LEVEL
            
            // Try to extract number for grams/ml
            val regex = """(\d+)\s*(g|ml)""".toRegex()
            val match = regex.find(quantityLower)
            if (match != null) {
                val value = match.groupValues[1].toIntOrNull() ?: 0
                if (value >= 1000) return TrackingType.BULK_LEVEL
            }
        }

        // 4. Safe Default
        return TrackingType.DISCRETE_COUNT
    }
}
