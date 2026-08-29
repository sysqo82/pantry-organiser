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
    val isReadOnly: Boolean = false, // Device-level capability lock
    val isVisualSearchVisible: Boolean = false,
    val visualSearchSelectedItem: PantryItem? = null
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
                val saneItems = performDataSanityCheck(items)
                _uiState.update { 
                    it.copy(
                        items = saneItems,
                        filteredItems = filterItems(saneItems, it.selectedShelf),
                        editingItem = it.editingItem?.let { current -> 
                            saneItems.find { item -> item.id == current.id } ?: current 
                        }
                    ) 
                }
            }
        }
    }

    /**
     * One-time or per-observer check to fix inconsistent data states:
     * 1. Re-classify items based on current engine rules (e.g. spray -> discrete).
     * 2. Auto-open a sealed pack if the active pack is empty but sealed packs exist.
     */
    private fun performDataSanityCheck(items: List<PantryItem>): List<PantryItem> {
        return items.map { item ->
            var updated = item
            
            // 1. Re-classification check
            val correctType = PantryItem.determineTrackingType(item.name, quantity = item.packageQuantity)
            if (item.trackingType != correctType) {
                android.util.Log.d("PantryVM", "Sanity Fix: Re-classifying ${item.name} to $correctType")
                updated = updated.copy(trackingType = correctType)
                // If switching to Discrete, ensure sealedCount is at least 1 if we had any active stock
                if (correctType == TrackingType.DISCRETE_COUNT && updated.sealedCount == 0 && item.activeFill != FillLevel.EMPTY) {
                    updated = updated.copy(sealedCount = 1)
                }
            }

            // 2. Multipack Detection check
            val correctUnitsPerPack = PantryItem.inferUnitsPerPack(item.name, item.packageQuantity)
            if (updated.unitsPerPack != correctUnitsPerPack) {
                android.util.Log.d("PantryVM", "Sanity Fix: Correcting unitsPerPack for ${item.name} to $correctUnitsPerPack")
                updated = updated.copy(
                    unitsPerPack = correctUnitsPerPack,
                    // If it was a legacy single unit but it's actually a multipack, upgrade the count
                    sealedCount = if (updated.sealedCount <= 1) correctUnitsPerPack else updated.sealedCount
                )
            }

            // 3. Active Pack Auto-Roll (Fix for "1 Portion - Empty")
            if (updated.trackingType == TrackingType.BULK_LEVEL && 
                updated.activeFill == FillLevel.EMPTY && 
                updated.sealedCount > 0) {
                android.util.Log.d("PantryVM", "Sanity Fix: Opening sealed pack for ${item.name}")
                updated = updated.copy(
                    sealedCount = updated.sealedCount - 1,
                    activeFill = FillLevel.FULL,
                    updatedAt = System.currentTimeMillis()
                )
            }

            if (updated !== item) {
                viewModelScope.launch { repository.updateItem(updated) }
            }
            updated
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
        android.util.Log.d("PantryVM", "[DEBUG-SCAN] Device role set: isReadOnly=$readOnly")
        _uiState.update { it.copy(isReadOnly = readOnly) }
    }

    fun showVisualSearch() {
        _uiState.update { it.copy(isVisualSearchVisible = true) }
    }

    fun hideVisualSearch() {
        _uiState.update { it.copy(isVisualSearchVisible = false, visualSearchSelectedItem = null) }
    }

    fun selectVisualSearchItem(item: PantryItem) {
        _uiState.update { it.copy(visualSearchSelectedItem = item) }
    }

    fun clearVisualSearchItem() {
        _uiState.update { it.copy(visualSearchSelectedItem = null) }
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
        isProcessingScan = false // UNLOCK
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
        android.util.Log.d("PantryVM", "[DEBUG-SCAN] Processing barcode: '$trimmedBarcode'")
        
        viewModelScope.launch {
            try {
                // 1. Local Lookup with variants
                val codes = mutableSetOf(trimmedBarcode)
                // Normalize 12/13/14 digit barcodes (UPC/EAN/GTIN)
                if (trimmedBarcode.length == 12) codes.add("0$trimmedBarcode")
                if (trimmedBarcode.length == 13 && trimmedBarcode.startsWith("0")) codes.add(trimmedBarcode.substring(1))
                if (trimmedBarcode.length == 13) codes.add("0$trimmedBarcode")
                if (trimmedBarcode.length == 14 && trimmedBarcode.startsWith("0")) codes.add(trimmedBarcode.substring(1))
                
                android.util.Log.d("PantryVM", "[DEBUG-SCAN] Checking local DB for codes: $codes")

                var existingItem: PantryItem? = null
                for (code in codes) {
                    existingItem = repository.getItemByBarcode(code)
                    if (existingItem != null) break
                }



                if (existingItem != null) {
                    android.util.Log.d("PantryVM", "[DEBUG-SCAN] Local match found: ${existingItem.name}")
                        if (existingItem.apiImageUrl == null || existingItem.unitsPerPack <= 1) {
                        // Do background discovery/auto-fix without blocking the scan result
                        viewModelScope.launch {
                            val product = offRepository.getProduct(trimmedBarcode)
                            if (product != null) {
                                var updated = existingItem
                                if (existingItem.apiImageUrl == null && product.imageUrl != null) updated = updated.copy(apiImageUrl = product.imageUrl)
                                if (existingItem.unitsPerPack <= 1) {
                                    val inferred = product.inferUnitsPerPack()
                                    if (inferred > 1) {
                                        updated = updated.copy(
                                            unitsPerPack = inferred,
                                            sealedCount = if (updated.sealedCount <= 1) inferred else updated.sealedCount
                                        )
                                    }
                                }
                                if (updated != existingItem) repository.updateItem(updated)
                            }
                        }
                    }

                    handleExistingItemScan(existingItem)
                    // handleExistingItemScan resets isProcessingScan internally
                    return@launch

                }

                // 2. Consume Mode Fail-Fast
                if (_uiState.value.scannerMode == ScannerMode.CONSUME) {
                    android.util.Log.d("PantryVM", "[DEBUG-SCAN] Item not found in Consume mode")
                    _uiState.update { it.copy(error = "Item not in organiser") }
                    isProcessingScan = false 
                    kotlinx.coroutines.delay(2000)
                    _uiState.update { it.copy(error = null) }
                    return@launch
                }

                // 3. API Fetch
                android.util.Log.d("PantryVM", "[DEBUG-SCAN] Starting API fetch for $trimmedBarcode")
                _uiState.update { it.copy(isLoading = true, error = null) }
                val product = offRepository.getProduct(trimmedBarcode)
                
                if (product != null) {
                    android.util.Log.d("PantryVM", "[DEBUG-SCAN] Product found: ${product.displayProductName}")
                    if (_uiState.value.isReadOnly) {
                        _uiState.update { it.copy(isLoading = false, scannedProduct = product, error = "Item not found in pantry") }
                        // remains true to show the details overlay until user dismisses
                        return@launch
                    }
                    
                    var trackingType = PantryItem.determineTrackingType(
                        product.displayProductName ?: "",
                        product.categoriesTags,
                        product.weight
                    )

                    val unitsPerPack = product.inferUnitsPerPack()
                    if (unitsPerPack > 1) trackingType = TrackingType.DISCRETE_COUNT

                    val tempItem = PantryItem(
                        id = "", 
                        name = product.displayProductName ?: "Unknown Item",
                        brand = product.brands ?: "",
                        packageQuantity = product.weight ?: "",
                        shelfNumber = 1,
                        zoneIndex = 1,
                        apiImageUrl = product.imageUrl,
                        barcode = trimmedBarcode,
                        trackingType = trackingType,
                        unitsPerPack = unitsPerPack,
                        // Fix: Initialize discrete items with at least 1 pack (unitsPerPack units)
                        sealedCount = if (trackingType == TrackingType.DISCRETE_COUNT) unitsPerPack else 0,
                        activeFill = if (trackingType == TrackingType.BULK_LEVEL) FillLevel.FULL else FillLevel.EMPTY
                    )
                    _uiState.update { it.copy(isLoading = false, pendingNewItem = tempItem) }
                    // Flag stays true to block other scans while choosing shelf
                } else {
                    android.util.Log.d("PantryVM", "[DEBUG-SCAN] Product not found on OFF")
                    _uiState.update { it.copy(isLoading = false, error = "Item Not Found (Unknown Product)") }
                    isProcessingScan = false
                    kotlinx.coroutines.delay(4000)
                    _uiState.update { it.copy(error = null) }
                }



            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("PantryVM", "[DEBUG-SCAN] Error during scan", e)
                _uiState.update { it.copy(isLoading = false, error = "Scan failed") }
            } finally {
                // IMPORTANT: Only reset if we ARE NOT showing a pending UI overlay
                // The pending UI methods (assignShelf, updateLevel, etc) will reset it when they finish.
                val state = _uiState.value
                if (state.pendingNewItem == null && state.pendingConsumeItem == null && state.scannedProduct == null) {
                    isProcessingScan = false
                    android.util.Log.d("PantryVM", "[DEBUG-SCAN] Scanner unlocked in finally")
                } else {
                    android.util.Log.d("PantryVM", "[DEBUG-SCAN] Scanner remains locked for pending UI")
                }
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
                // isProcessingScan stays true to block other scans while picker is open
            } else {
                consumePortion(consumeItem)
                _uiState.update { it.copy(userNotification = "Consumed 1 unit of ${consumeItem.name}", recognizedItem = consumeItem) }
                kotlinx.coroutines.delay(800)
                _uiState.update { it.copy(recognizedItem = null) }
                isProcessingScan = false // UNLOCK after quick consume
            }

        } else {
            // Add mode: Increment stock.
            // Note: Data sanity check handles type/multipack re-evaluation on emission.
            addSealedUnit(item)
            val label = getCellLabel((4 - item.shelfNumber).coerceIn(0, 3), (item.zoneIndex - 1).coerceIn(0, 2))
            _uiState.update { it.copy(userNotification = "Added 1 pack to $label", recognizedItem = item) }
            kotlinx.coroutines.delay(800)
            _uiState.update { it.copy(recognizedItem = null) }
            isProcessingScan = false // UNLOCK after quick add
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
            isProcessingScan = false // UNLOCK for next item
        }
    }

    fun cancelPendingItem() {
        _uiState.update { it.copy(
            pendingNewItem = null, 
            scannedProduct = null,
            isScannerVisible = false // Dismiss the scanner overlay
        ) }
        isProcessingScan = false // UNLOCK
    }




    fun updateItemFillLevel(item: PantryItem, level: FillLevel) {
        if (_uiState.value.isReadOnly) return
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
                _uiState.update { it.copy(userNotification = "Updated ${item.name} to ${level.label}") }
            }
        }
    }

    fun updatePendingConsumeLevel(level: FillLevel) {
        val item = _uiState.value.pendingConsumeItem ?: return
        updateItemFillLevel(item, level)
        _uiState.update { it.copy(pendingConsumeItem = null, isScannerVisible = false) }
        isProcessingScan = false // UNLOCK
    }


    fun updatePendingConsumeUnits(units: Int) {
        val item = _uiState.value.pendingConsumeItem ?: return
        consumeUnits(item, units)
        // consumeUnits also handles the UI state and scanner close, but needs to unlock
        isProcessingScan = false // UNLOCK
    }


    fun cancelPendingConsume() {
        _uiState.update { it.copy(pendingConsumeItem = null) }
        isProcessingScan = false // UNLOCK
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
}
