package com.pantry.organiser.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantry.organiser.core.model.FillLevel
import com.pantry.organiser.core.model.PantryConstants
import com.pantry.organiser.core.model.PantryItem
import com.pantry.organiser.core.model.TrackingType
import com.pantry.organiser.core.model.toPantryItem
import com.pantry.organiser.dashboard.data.OpenFoodFactsProber
import com.pantry.organiser.dashboard.data.PantryRepository
import com.pantry.organiser.dashboard.data.SyncQueueItem
import com.pantry.organiser.dashboard.data.SyncQueueRepository
import com.pantry.organiser.dashboard.ui.OverlayContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class DashboardUiState(
    val pendingItems: List<SyncQueueItem> = emptyList(),
    val pantryItems: List<PantryItem> = emptyList(),
    val activeOverlay: OverlayContext? = null,
    val pantryId: String = "default-pantry",
    val isSaving: Boolean = false,
    val isProbing: Boolean = false,
    val probeMessage: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val syncQueueRepository: SyncQueueRepository,
    private val pantryRepository: PantryRepository,
    private val openFoodFactsProber: OpenFoodFactsProber
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                syncQueueRepository.getPendingItems(),
                pantryRepository.allItems
            ) { pending, items ->
                val sortedItems = items.sortedWith(
                    compareByDescending<PantryItem> { it.shelfNumber }
                        .thenBy { it.zoneIndex }
                        .thenBy { it.name }
                )
                _uiState.update { it.copy(pendingItems = pending, pantryItems = sortedItems) }

                if (pending.isEmpty() && _uiState.value.activeOverlay is OverlayContext.SyncQueueEnrichment) {
                    _uiState.update { it.copy(activeOverlay = null) }
                }
            }.collect()
        }
    }

    fun startRealtimeSync() {
        val pantryId = "default-pantry"
        syncQueueRepository.startObserving(pantryId)
        pantryRepository.startObservingRealtime()
    }

    fun stopRealtimeSync() {
        syncQueueRepository.stopObserving()
        pantryRepository.stopObservingRealtime()
    }

    fun processItem(item: SyncQueueItem) {
        viewModelScope.launch {
            val allItems = pantryRepository.allItems.firstOrNull() ?: emptyList()
            val assignedMatch = if (item.barcode.isNotBlank()) {
                allItems.find { it.barcode == item.barcode && it.isAssigned }
                    ?: pantryRepository.getItemByBarcode(item.barcode)?.takeIf { it.isAssigned }
            } else null

            val activeExistingItem = assignedMatch
                ?: (if (item.itemId.isNotBlank()) allItems.find { it.id == item.itemId && it.isAssigned } else null)

            if (activeExistingItem != null) {
                _uiState.update { 
                    it.copy(
                        activeOverlay = OverlayContext.SyncQueueEnrichment(
                            syncItem = item, 
                            existingItem = activeExistingItem,
                            isPastItem = false
                        )
                    ) 
                }
            } else {
                val pastItem = if (item.barcode.isNotBlank()) {
                    pantryRepository.getPastItemByBarcode(item.barcode)
                } else null

                if (pastItem != null) {
                    val pastAsPantry = pastItem.toPantryItem().copy(isAssigned = false, activeFill = FillLevel.FULL)
                    val suggestedRow = 4 - pastItem.shelfNumber
                    val suggestedCol = pastItem.zoneIndex - 1
                    _uiState.update {
                        it.copy(
                            activeOverlay = OverlayContext.SyncQueueEnrichment(
                                syncItem = item,
                                existingItem = pastAsPantry,
                                isPastItem = true,
                                suggestedShelf = suggestedRow to suggestedCol
                            )
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            activeOverlay = OverlayContext.SyncQueueEnrichment(
                                syncItem = item,
                                existingItem = null,
                                isPastItem = false
                            )
                        )
                    }
                }
            }
        }
    }

    fun selectItem(item: PantryItem) {
        _uiState.update { it.copy(activeOverlay = OverlayContext.ItemDetail(item)) }
    }

    fun saveEnrichedItem(
        syncItem: SyncQueueItem,
        existingItem: PantryItem?,
        shelf: Int,
        zone: Int,
        quantityToAdd: Int,
        fillLevel: FillLevel,
        isPastItem: Boolean = false
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            val inferredUnits = PantryConstants.inferUnitsPerPack(syncItem.productName, syncItem.quantity)
            val determinedType = PantryItem.determineTrackingType(
                name = syncItem.productName ?: "Unknown Product",
                quantity = syncItem.quantity,
                unitsPerPack = inferredUnits
            )

            val itemToSave = if (existingItem != null && existingItem.isAssigned && !isPastItem) {
                val updatedType = if (determinedType == TrackingType.DISCRETE_COUNT && inferredUnits > 1) TrackingType.DISCRETE_COUNT else existingItem.trackingType
                val updatedUnits = if (inferredUnits > 1) inferredUnits else existingItem.unitsPerPack

                val isGenericName = existingItem.name.isBlank() || 
                                    existingItem.name == "Unnamed Item" || 
                                    existingItem.name == "Unknown Product" || 
                                    existingItem.name == "Network Error" || 
                                    existingItem.name == "Enriching..."

                val effectiveName = if (isGenericName && !syncItem.productName.isNullOrBlank()) {
                    syncItem.productName
                } else existingItem.name.ifBlank { syncItem.productName ?: "Unknown Product" }

                val effectiveBrand = existingItem.brand?.takeIf { it.isNotBlank() } ?: syncItem.brand
                val effectiveImageUrl = existingItem.imageUrl?.takeIf { it.isNotBlank() } ?: syncItem.imageUrl
                val effectiveApiImageUrl = existingItem.apiImageUrl?.takeIf { it.isNotBlank() } ?: syncItem.imageUrl
                val effectiveQuantity = existingItem.packageQuantity?.takeIf { it.isNotBlank() } ?: syncItem.quantity

                if (updatedType == TrackingType.DISCRETE_COUNT) {
                    val addAmount = quantityToAdd
                    val newActive = if (existingItem.activeCount == 0) updatedUnits else existingItem.activeCount
                    existingItem.copy(
                        name = effectiveName,
                        brand = effectiveBrand,
                        imageUrl = effectiveImageUrl,
                        apiImageUrl = effectiveApiImageUrl,
                        packageQuantity = effectiveQuantity,
                        shelfNumber = shelf,
                        zoneIndex = zone,
                        trackingType = updatedType,
                        unitsPerPack = updatedUnits,
                        activeCount = newActive,
                        isAssigned = true, // Assigned!
                        sealedCount = existingItem.sealedCount + addAmount,
                        updatedAt = System.currentTimeMillis()
                    )
                } else {
                    existingItem.copy(
                        name = effectiveName,
                        brand = effectiveBrand,
                        imageUrl = effectiveImageUrl,
                        apiImageUrl = effectiveApiImageUrl,
                        packageQuantity = effectiveQuantity,
                        shelfNumber = shelf,
                        zoneIndex = zone,
                        trackingType = updatedType,
                        activeFill = if (existingItem.activeFill == FillLevel.EMPTY) fillLevel else existingItem.activeFill,
                        isAssigned = true, // Assigned!
                        sealedCount = existingItem.sealedCount + (if (existingItem.activeFill == FillLevel.EMPTY) maxOf(0, quantityToAdd - 1) else quantityToAdd),
                        updatedAt = System.currentTimeMillis()
                    )
                }
            } else {
                val initialSealed = if (determinedType == TrackingType.DISCRETE_COUNT) {
                    if (inferredUnits > 1) maxOf(0, quantityToAdd - 1) else quantityToAdd
                } else {
                    maxOf(0, quantityToAdd - 1)
                }

                val targetId = if (isPastItem) {
                    "local_" + UUID.randomUUID().toString()
                } else {
                    existingItem?.id?.takeIf { it.isNotBlank() && !it.startsWith("local_") && !it.startsWith("past_") }
                        ?: syncItem.itemId.takeIf { it.isNotBlank() && !it.startsWith("local_") }
                        ?: ("local_" + UUID.randomUUID().toString())
                }

                val effectiveName = existingItem?.name?.takeIf { 
                    it.isNotBlank() && it != "Unknown Product" && it != "Unnamed Item" && it != "Network Error" && it != "Enriching..." 
                } ?: syncItem.productName ?: "Unknown Product"

                val effectiveBrand = existingItem?.brand?.takeIf { it.isNotBlank() } ?: syncItem.brand
                val effectiveImageUrl = existingItem?.imageUrl?.takeIf { it.isNotBlank() } ?: syncItem.imageUrl
                val effectiveApiImageUrl = existingItem?.apiImageUrl?.takeIf { it.isNotBlank() } ?: syncItem.imageUrl
                val effectiveLocalImageUrl = existingItem?.localImageUrl
                val effectiveLocalImageUri = existingItem?.localImageUri
                val effectiveQuantity = existingItem?.packageQuantity?.takeIf { it.isNotBlank() } ?: syncItem.quantity

                PantryItem(
                    id = targetId,
                    name = effectiveName,
                    barcode = syncItem.barcode.ifBlank { existingItem?.barcode },
                    brand = effectiveBrand,
                    packageQuantity = effectiveQuantity,
                    imageUrl = effectiveImageUrl,
                    apiImageUrl = effectiveApiImageUrl,
                    localImageUrl = effectiveLocalImageUrl,
                    localImageUri = effectiveLocalImageUri,
                    shelfNumber = shelf,
                    zoneIndex = zone,
                    trackingType = determinedType,
                    sealedCount = initialSealed,
                    unitsPerPack = inferredUnits,
                    activeCount = inferredUnits,
                    activeFill = if (isPastItem) FillLevel.FULL else fillLevel,
                    isAssigned = true, // Assigned!
                    createdAt = existingItem?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            }

            if (existingItem != null && !isPastItem) {
                pantryRepository.updateItem(itemToSave)
            } else {
                pantryRepository.addItem(itemToSave)
            }

            if (existingItem != null && syncItem.itemId.isNotBlank() && syncItem.itemId != existingItem.id) {
                pantryRepository.deleteItem(PantryItem(id = syncItem.itemId, name = "", shelfNumber = 1, zoneIndex = 1))
            }

            syncQueueRepository.markAsProcessed(syncItem.id)

            _uiState.update { it.copy(isSaving = false, activeOverlay = null) }
        }
    }

    private fun updateOverlayIfShowing(updatedItem: PantryItem?) {
        _uiState.update { state ->
            val current = state.activeOverlay
            if (current is OverlayContext.ItemDetail) {
                if (updatedItem == null || current.item.id == updatedItem.id) {
                    state.copy(activeOverlay = updatedItem?.let { OverlayContext.ItemDetail(it) })
                } else {
                    state
                }
            } else {
                state
            }
        }
    }

    fun consumeItem(item: PantryItem, amount: Int = 1) {
        viewModelScope.launch {
            if (item.unitsPerPack > 1) {
                val totalUnits = (item.sealedCount * item.unitsPerPack) + item.activeCount
                val remainingUnits = totalUnits - amount
                if (remainingUnits <= 0) {
                    pantryRepository.moveToPastItems(item)
                    updateOverlayIfShowing(null)
                } else {
                    val newActiveCount = if (remainingUnits % item.unitsPerPack != 0) remainingUnits % item.unitsPerPack else item.unitsPerPack
                    val newSealedCount = (remainingUnits - newActiveCount) / item.unitsPerPack
                    val updated = item.copy(
                        sealedCount = newSealedCount,
                        activeCount = newActiveCount,
                        updatedAt = System.currentTimeMillis()
                    )
                    pantryRepository.updateItem(updated)
                    updateOverlayIfShowing(updated)
                }
            } else if (item.trackingType == TrackingType.BULK_LEVEL) {
                if (item.activeFill == FillLevel.EMPTY) {
                    if (item.sealedCount > 0) {
                        val updated = item.copy(
                            sealedCount = item.sealedCount - 1,
                            activeFill = FillLevel.FULL,
                            updatedAt = System.currentTimeMillis()
                        )
                        pantryRepository.updateItem(updated)
                        updateOverlayIfShowing(updated)
                    } else {
                        pantryRepository.moveToPastItems(item)
                        updateOverlayIfShowing(null)
                    }
                } else {
                    val prevFill = item.activeFill.prev()
                    if (prevFill == FillLevel.EMPTY) {
                        if (item.sealedCount > 0) {
                            val updated = item.copy(
                                sealedCount = item.sealedCount - 1,
                                activeFill = FillLevel.FULL,
                                updatedAt = System.currentTimeMillis()
                            )
                            pantryRepository.updateItem(updated)
                            updateOverlayIfShowing(updated)
                        } else {
                            pantryRepository.moveToPastItems(item)
                            updateOverlayIfShowing(null)
                        }
                    } else {
                        val updated = item.copy(
                            activeFill = prevFill,
                            updatedAt = System.currentTimeMillis()
                        )
                        pantryRepository.updateItem(updated)
                        updateOverlayIfShowing(updated)
                    }
                }
            } else if (item.trackingType == TrackingType.DISCRETE_COUNT) {
                val newCount = item.sealedCount - amount
                if (newCount <= 0) {
                    pantryRepository.moveToPastItems(item)
                    updateOverlayIfShowing(null)
                } else {
                    val updated = item.copy(sealedCount = newCount, updatedAt = System.currentTimeMillis())
                    pantryRepository.updateItem(updated)
                    updateOverlayIfShowing(updated)
                }
            }
        }
    }

    fun restockItem(item: PantryItem) {
        viewModelScope.launch {
            val updated = if (item.unitsPerPack > 1) {
                if (item.activeCount == 0) {
                    item.copy(activeCount = item.unitsPerPack, updatedAt = System.currentTimeMillis())
                } else {
                    item.copy(sealedCount = item.sealedCount + 1, updatedAt = System.currentTimeMillis())
                }
            } else if (item.trackingType == TrackingType.BULK_LEVEL) {
                if (item.activeFill == FillLevel.EMPTY) {
                    item.copy(activeFill = FillLevel.FULL, updatedAt = System.currentTimeMillis())
                } else {
                    item.copy(sealedCount = item.sealedCount + 1, updatedAt = System.currentTimeMillis())
                }
            } else {
                item.copy(sealedCount = item.sealedCount + 1, updatedAt = System.currentTimeMillis())
            }
            pantryRepository.updateItem(updated)
            updateOverlayIfShowing(updated)
        }
    }

    fun updateFillLevel(item: PantryItem, fillLevel: FillLevel) {
        viewModelScope.launch {
            if (fillLevel == FillLevel.EMPTY) {
                if (item.sealedCount > 0) {
                    val updated = item.copy(
                        sealedCount = item.sealedCount - 1,
                        activeFill = FillLevel.FULL,
                        updatedAt = System.currentTimeMillis()
                    )
                    pantryRepository.updateItem(updated)
                    updateOverlayIfShowing(updated)
                } else {
                    pantryRepository.moveToPastItems(item)
                    updateOverlayIfShowing(null)
                }
            } else {
                val updated = item.copy(activeFill = fillLevel, updatedAt = System.currentTimeMillis())
                pantryRepository.updateItem(updated)
                updateOverlayIfShowing(updated)
            }
        }
    }

    fun editItem(item: PantryItem) {
        _uiState.update { it.copy(activeOverlay = OverlayContext.ItemEdit(item)) }
    }

    fun saveEditedItem(updatedItem: PantryItem) {
        viewModelScope.launch {
            pantryRepository.updateItem(updatedItem)
            _uiState.update { it.copy(activeOverlay = null) }
        }
    }

    fun deleteItem(item: PantryItem) {
        viewModelScope.launch {
            pantryRepository.moveToPastItems(item)
            _uiState.update { it.copy(activeOverlay = null) }
        }
    }

    fun clearPendingItem(item: SyncQueueItem) {
        viewModelScope.launch {
            syncQueueRepository.clearPendingItem(item)
            _uiState.update { state ->
                state.copy(pendingItems = state.pendingItems.filter { it.id != item.id })
            }
        }
    }

    fun clearAllPendingItems() {
        viewModelScope.launch {
            syncQueueRepository.clearAllPendingItems()
            _uiState.update { it.copy(pendingItems = emptyList()) }
        }
    }

    fun dismissOverlay() {
        _uiState.update { it.copy(activeOverlay = null) }
    }

    fun probeOpenFoodFacts() {
        if (_uiState.value.isProbing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProbing = true) }
            val updatedCount = openFoodFactsProber.probeAndSync()
            val msg = if (updatedCount > 0) {
                "Probed Open Food Facts: $updatedCount item(s) updated."
            } else {
                "Probed Open Food Facts: All items are up to date."
            }
            _uiState.update { it.copy(isProbing = false, probeMessage = msg) }
        }
    }

    fun clearProbeMessage() {
        _uiState.update { it.copy(probeMessage = null) }
    }
}
