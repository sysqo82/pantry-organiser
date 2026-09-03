package com.pantry.organiser.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantry.organiser.core.model.FillLevel
import com.pantry.organiser.core.model.PantryConstants
import com.pantry.organiser.core.model.PantryItem
import com.pantry.organiser.core.model.TrackingType
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
    val isSaving: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val syncQueueRepository: SyncQueueRepository,
    private val pantryRepository: PantryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                syncQueueRepository.getPendingItems(),
                pantryRepository.allItems
            ) { pending, items ->
                _uiState.update { it.copy(pendingItems = pending, pantryItems = items) }

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
            val existingItem = if (item.itemId.isNotBlank()) {
                pantryRepository.allItems.firstOrNull()?.find { it.id == item.itemId }
                    ?: pantryRepository.getItemByBarcode(item.barcode)
            } else {
                pantryRepository.getItemByBarcode(item.barcode)
            }
            _uiState.update { it.copy(activeOverlay = OverlayContext.SyncQueueEnrichment(item, existingItem)) }
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
        fillLevel: FillLevel
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            val inferredUnits = PantryConstants.inferUnitsPerPack(syncItem.productName, syncItem.quantity)
            val determinedType = PantryItem.determineTrackingType(
                name = syncItem.productName ?: "Unknown Product",
                quantity = syncItem.quantity,
                unitsPerPack = inferredUnits
            )

            val itemToSave = if (existingItem != null && existingItem.isAssigned) {
                val updatedType = determinedType
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

                val targetId = existingItem?.id?.takeIf { it.isNotBlank() }
                    ?: syncItem.itemId.takeIf { it.isNotBlank() }
                    ?: ("local_" + UUID.randomUUID().toString())

                val effectiveName = existingItem?.name?.takeIf { 
                    it.isNotBlank() && it != "Unknown Product" && it != "Unnamed Item" && it != "Network Error" && it != "Enriching..." 
                } ?: syncItem.productName ?: "Unknown Product"

                val effectiveBrand = existingItem?.brand?.takeIf { it.isNotBlank() } ?: syncItem.brand
                val effectiveImageUrl = existingItem?.imageUrl?.takeIf { it.isNotBlank() } ?: syncItem.imageUrl
                val effectiveApiImageUrl = existingItem?.apiImageUrl?.takeIf { it.isNotBlank() } ?: syncItem.imageUrl
                val effectiveQuantity = existingItem?.packageQuantity?.takeIf { it.isNotBlank() } ?: syncItem.quantity

                PantryItem(
                    id = targetId,
                    name = effectiveName,
                    barcode = syncItem.barcode.ifBlank { existingItem?.barcode },
                    brand = effectiveBrand,
                    packageQuantity = effectiveQuantity,
                    imageUrl = effectiveImageUrl,
                    apiImageUrl = effectiveApiImageUrl,
                    shelfNumber = shelf,
                    zoneIndex = zone,
                    trackingType = determinedType,
                    sealedCount = initialSealed,
                    unitsPerPack = inferredUnits,
                    activeCount = inferredUnits,
                    activeFill = fillLevel,
                    isAssigned = true, // Assigned!
                    createdAt = existingItem?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            }

            if (existingItem != null) {
                pantryRepository.updateItem(itemToSave)
            } else {
                pantryRepository.addItem(itemToSave)
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
                    pantryRepository.deleteItem(item)
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
                        pantryRepository.deleteItem(item)
                        updateOverlayIfShowing(null)
                    }
                } else {
                    val prevFill = item.activeFill.prev()
                    if (prevFill == FillLevel.EMPTY && item.sealedCount > 0) {
                        val updated = item.copy(
                            sealedCount = item.sealedCount - 1,
                            activeFill = FillLevel.FULL,
                            updatedAt = System.currentTimeMillis()
                        )
                        pantryRepository.updateItem(updated)
                        updateOverlayIfShowing(updated)
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
                    pantryRepository.deleteItem(item)
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
            val updated = if (fillLevel == FillLevel.EMPTY && item.sealedCount > 0) {
                item.copy(
                    sealedCount = item.sealedCount - 1,
                    activeFill = FillLevel.FULL,
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                item.copy(activeFill = fillLevel, updatedAt = System.currentTimeMillis())
            }
            pantryRepository.updateItem(updated)
            updateOverlayIfShowing(updated)
        }
    }

    fun editItem(item: PantryItem) {
        _uiState.update { it.copy(activeOverlay = OverlayContext.ItemEdit(item)) }
    }

    fun saveEditedItem(updatedItem: PantryItem) {
        viewModelScope.launch {
            pantryRepository.updateItem(updatedItem)
            _uiState.update { it.copy(activeOverlay = OverlayContext.ItemDetail(updatedItem)) }
        }
    }

    fun deleteItem(item: PantryItem) {
        viewModelScope.launch {
            pantryRepository.deleteItem(item)
            _uiState.update { it.copy(activeOverlay = null) }
        }
    }

    fun dismissOverlay() {
        _uiState.update { it.copy(activeOverlay = null) }
    }
}
