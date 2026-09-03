package com.pantry.organiser.ingestion

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantry.organiser.core.model.BatchPayload
import com.pantry.organiser.core.model.PantryItem
import com.pantry.organiser.core.model.ScannedItem
import com.pantry.organiser.core.network.OpenFoodFactsRepository
import com.pantry.organiser.core.network.SyncService
import com.pantry.organiser.ingestion.scanner.ContinuousScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class IngestionMode {
    HOME, CHECK, INSERT
}

data class IngestionUiState(
    val mode: IngestionMode = IngestionMode.HOME,
    val scannedItems: List<ScannedItem> = emptyList(),
    val createdPantryItems: List<PantryItem> = emptyList(),
    val items: List<PantryItem> = emptyList(), // Placeholder for fetching current inventory
    val isSending: Boolean = false,
    val pantryId: String = "default-pantry"
)

@HiltViewModel
class IngestionViewModel @Inject constructor(
    private val scanner: ContinuousScanner,
    private val feedbackController: FeedbackController,
    private val syncService: SyncService,
    private val offRepository: OpenFoodFactsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(IngestionUiState())
    val uiState: StateFlow<IngestionUiState> = _uiState.asStateFlow()

    val effects = feedbackController.effects

    private var realtimeSyncJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            scanner.barcodes.collect { barcode ->
                handleBarcode(barcode)
            }
        }
    }

    fun startRealtimeSync() {
        if (realtimeSyncJob?.isActive == true) return
        
        android.util.Log.d("IngestionVM", "Starting realtime sync")
        
        // Fetch initial stock inventory
        viewModelScope.launch {
            try {
                val currentPantryItems = syncService.fetchPantryItems(_uiState.value.pantryId)
                val assignedItems = currentPantryItems.filter { it.isAssigned }
                _uiState.update { it.copy(items = assignedItems) }
            } catch (e: Exception) {
                android.util.Log.e("IngestionVM", "Failed to fetch pantry items: ${e.message}")
            }
        }

        // Observe realtime stock inventory updates
        realtimeSyncJob = viewModelScope.launch {
            syncService.observePantryItems(_uiState.value.pantryId).collect { newItem ->
                _uiState.update { state ->
                    val updatedList = state.items.toMutableList()
                    val existingIndex = updatedList.indexOfFirst { it.id == newItem.id || (it.barcode != null && it.barcode == newItem.barcode) }

                    if (newItem.isAssigned) {
                        if (existingIndex >= 0) {
                            updatedList[existingIndex] = newItem
                        } else {
                            updatedList.add(newItem)
                        }
                    } else {
                        if (existingIndex >= 0) {
                            updatedList.removeAt(existingIndex)
                        }
                    }
                    state.copy(items = updatedList)
                }
            }
        }
    }

    fun stopRealtimeSync() {
        android.util.Log.d("IngestionVM", "Stopping realtime sync (screen off / app stopped)")
        realtimeSyncJob?.cancel()
        realtimeSyncJob = null
    }

    private fun handleBarcode(barcode: String) {
        if (_uiState.value.mode == IngestionMode.CHECK) {
            // Single shot logic: show item and exit
            setMode(IngestionMode.HOME)
            feedbackController.signalSuccess()
        } else if (_uiState.value.mode == IngestionMode.INSERT) {
            val alreadyScanned = _uiState.value.scannedItems.any { it.barcode == barcode }
            if (alreadyScanned) {
                feedbackController.signalDuplicate()
            } else {
                viewModelScope.launch {
                    // Signal success immediately for haptic feedback
                    feedbackController.signalSuccess()
                    
                    // Add placeholder first to show something in the UI
                    val placeholder = ScannedItem(
                        barcode = barcode,
                        productName = "Enriching...",
                        brand = "",
                        imageUrl = "",
                        quantity = ""
                    )
                    _uiState.update { state ->
                        state.copy(scannedItems = state.scannedItems + placeholder)
                    }
                    
                    var productName: String
                    var brand: String
                    var imageUrl: String
                    var quantity: String

                    try {
                        val offProduct = offRepository.getProduct(barcode)
                        if (offProduct != null) {
                            productName = offProduct.displayProductName ?: "Unknown Product"
                            brand = offProduct.displayBrands ?: ""
                            imageUrl = offProduct.imageUrl ?: ""
                            quantity = offProduct.weight ?: ""
                        } else {
                            productName = "Unknown Product"
                            brand = ""
                            imageUrl = ""
                            quantity = ""
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("IngestionVM", "Failed to enrich barcode $barcode", e)
                        productName = "Network Error"
                        brand = ""
                        imageUrl = ""
                        quantity = ""
                    }

                    _uiState.update { state ->
                        state.copy(scannedItems = state.scannedItems.map { 
                            if (it.barcode == barcode) {
                                it.copy(
                                    productName = productName,
                                    brand = brand,
                                    imageUrl = imageUrl,
                                    quantity = quantity
                                )
                            } else it
                        })
                    }

                    // Create pantry_item record in PocketBase DB as unassigned
                    val inferredUnits = PantryItem.inferUnitsPerPack(productName, quantity)
                    val determinedType = PantryItem.determineTrackingType(productName, quantity = quantity, unitsPerPack = inferredUnits)

                    val newPantryItem = PantryItem(
                        id = "", // Empty ID so PocketBase generates record ID
                        name = productName,
                        barcode = barcode,
                        brand = brand,
                        packageQuantity = quantity,
                        imageUrl = imageUrl,
                        apiImageUrl = imageUrl,
                        shelfNumber = 1,
                        zoneIndex = 1,
                        trackingType = determinedType,
                        sealedCount = 1,
                        unitsPerPack = inferredUnits,
                        activeCount = inferredUnits,
                        isAssigned = false, // Unassigned
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )

                    val created = try {
                        syncService.createPantryItem(newPantryItem)
                    } catch (e: Exception) {
                        Log.e("IngestionVM", "Failed to create unassigned pantry item on PB: ${e.message}")
                        null
                    }

                    if (created != null && created.id.isNotBlank() && !created.id.startsWith("local_")) {
                        Log.d("IngestionVM", "Successfully created pantry item on PB with ID: ${created.id}")
                        _uiState.update { state ->
                            state.copy(createdPantryItems = state.createdPantryItems + created)
                        }
                    } else {
                        Log.e("IngestionVM", "Failed to create pantry item on PB for barcode: $barcode")
                    }
                }
            }
        }
    }

    fun setMode(mode: IngestionMode) {
        _uiState.update { it.copy(mode = mode) }
    }

    fun sendToPantry() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            val itemIds = _uiState.value.createdPantryItems
                .map { it.id }
                .filter { it.isNotBlank() && !it.startsWith("local_") }

            Log.d("IngestionVM", "Sending batch_payload with ${itemIds.size} itemIds: $itemIds")
            val payload = BatchPayload(
                pantryId = _uiState.value.pantryId,
                itemIds = itemIds,
                items = emptyList()
            )
            try {
                syncService.dispatchBatch(payload)
                _uiState.update { it.copy(scannedItems = emptyList(), createdPantryItems = emptyList(), isSending = false, mode = IngestionMode.HOME) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSending = false) }
            }
        }
    }

    fun startScanner(lifecycleOwner: androidx.lifecycle.LifecycleOwner, surfaceProvider: androidx.camera.core.Preview.SurfaceProvider) {
        scanner.start(lifecycleOwner, surfaceProvider)
    }
}
