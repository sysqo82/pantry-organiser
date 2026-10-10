package com.pantry.organiser.ingestion

import android.content.Context
import android.util.Log
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantry.organiser.core.model.BatchPayload
import com.pantry.organiser.core.model.PantryItem
import com.pantry.organiser.core.model.ScannedItem
import com.pantry.organiser.core.model.TrackingType
import com.pantry.organiser.core.network.OpenFoodFactsRepository
import com.pantry.organiser.core.network.SyncService
import com.pantry.organiser.ingestion.scanner.ContinuousScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class IngestionMode {
    HOME, CHECK, INSERT
}

data class CheckedNotFoundItem(
    val barcode: String,
    val productName: String,
    val brand: String? = null,
    val imageUrl: String? = null
)

data class IngestionUiState(
    val mode: IngestionMode = IngestionMode.HOME,
    val scannedItems: List<ScannedItem> = emptyList(),
    val createdPantryItems: List<PantryItem> = emptyList(),
    val items: List<PantryItem> = emptyList(), // Placeholder for fetching current inventory
    val allPantryItems: List<PantryItem> = emptyList(),
    val isSending: Boolean = false,
    val pantryId: String = "default-pantry",
    val scannedCheckItem: PantryItem? = null,
    val checkedNotFound: CheckedNotFoundItem? = null
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

    private var realtimeSyncJob: Job? = null

    init {
        viewModelScope.launch {
            scanner.barcodes.collect { barcode ->
                handleBarcode(barcode)
            }
        }
    }

    fun startRealtimeSync() {
        if (realtimeSyncJob?.isActive == true) return
        
        Log.d("IngestionVM", "Starting realtime sync")
        
        // Fetch initial stock inventory
        viewModelScope.launch {
            try {
                val currentPantryItems = syncService.fetchPantryItems(_uiState.value.pantryId)
                val assignedItems = currentPantryItems.filter { it.isAssigned && it.hasStock && it.sealedCount >= 0 }.sortPantryItems()
                _uiState.update { it.copy(items = assignedItems, allPantryItems = currentPantryItems) }
            } catch (e: Exception) {
                Log.e("IngestionVM", "Failed to fetch pantry items: ${e.message}")
            }
        }

        // Observe realtime stock inventory updates
        realtimeSyncJob = viewModelScope.launch {
            syncService.observePantryItems(_uiState.value.pantryId).collect { newItem ->
                _uiState.update { state ->
                    val allList = state.allPantryItems.toMutableList()
                    val existingIndex = allList.indexOfFirst { it.id == newItem.id }

                    if (existingIndex >= 0) {
                        allList[existingIndex] = newItem
                    } else {
                        allList.add(newItem)
                    }

                    val assignedItems = allList.filter { it.isAssigned && it.hasStock && it.sealedCount >= 0 }.sortPantryItems()
                    state.copy(items = assignedItems, allPantryItems = allList)
                }
            }
        }
    }

    fun stopRealtimeSync() {
        Log.d("IngestionVM", "Stopping realtime sync (screen off / app stopped)")
        realtimeSyncJob?.cancel()
        realtimeSyncJob = null
    }

    fun uploadCustomItemPhoto(context: Context, item: PantryItem, imageBytes: ByteArray) {
        viewModelScope.launch {
            try {
                val imagesDir = File(context.filesDir, "product_images").apply { mkdirs() }
                val imageFile = File(imagesDir, "${item.id.ifBlank { System.currentTimeMillis().toString() }}.jpg")
                imageFile.writeBytes(imageBytes)

                val updatedLocal = item.copy(
                    localImageUri = imageFile.absolutePath,
                    updatedAt = System.currentTimeMillis()
                )

                // Update local list state instantly
                _uiState.update { state ->
                    state.copy(items = state.items.map { if (it.id == item.id) updatedLocal else it })
                }

                // Upload image file to PocketBase
                val uploadedItem = syncService.uploadPantryItemImage(
                    itemId = item.id,
                    imageBytes = imageBytes,
                    filename = "${item.id}.jpg"
                )

                if (uploadedItem != null) {
                    Log.d("IngestionVM", "Custom photo successfully uploaded to PocketBase for ${item.name}: ${uploadedItem.localImageUrl}")
                    val finalItem = uploadedItem.copy(
                        localImageUri = imageFile.absolutePath
                    )
                    syncService.updatePantryItem(finalItem)
                    _uiState.update { state ->
                        state.copy(items = state.items.map { if (it.id == item.id) finalItem else it })
                    }
                }
            } catch (e: Exception) {
                Log.e("IngestionVM", "Failed to upload custom item photo: ${e.message}", e)
            }
        }
    }

    private fun handleBarcode(barcode: String) {
        if (_uiState.value.mode == IngestionMode.CHECK) {
            viewModelScope.launch {
                val trimmedBarcode = barcode.trim()
                val barcodeVariants = mutableSetOf(trimmedBarcode)
                if (trimmedBarcode.length == 12) barcodeVariants.add("0$trimmedBarcode")
                if (trimmedBarcode.length == 13 && trimmedBarcode.startsWith("0")) barcodeVariants.add(trimmedBarcode.substring(1))
                if (trimmedBarcode.length == 13) barcodeVariants.add("0$trimmedBarcode")
                if (trimmedBarcode.length == 14 && trimmedBarcode.startsWith("0")) barcodeVariants.add(trimmedBarcode.substring(1))

                // Search in local state first (allPantryItems + items)
                val candidateItems = (_uiState.value.allPantryItems + _uiState.value.items).distinctBy { it.id }
                var matched = candidateItems.find { item ->
                    val itemBc = item.barcode?.trim()
                    itemBc != null && barcodeVariants.any { it.equals(itemBc, ignoreCase = true) }
                }

                // If not found locally, fetch fresh from PocketBase
                if (matched == null) {
                    val freshItems = try {
                        syncService.fetchPantryItems(_uiState.value.pantryId)
                    } catch (e: Exception) {
                        emptyList()
                    }
                    matched = freshItems.find { item ->
                        val itemBc = item.barcode?.trim()
                        itemBc != null && barcodeVariants.any { it.equals(itemBc, ignoreCase = true) }
                    }
                    if (matched != null) {
                        _uiState.update { state ->
                            val updatedAll = (state.allPantryItems + freshItems).distinctBy { it.id }
                            val assignedItems = updatedAll.filter { it.isAssigned && it.hasStock && it.sealedCount >= 0 }.sortPantryItems()
                            state.copy(items = assignedItems, allPantryItems = updatedAll)
                        }
                    }
                }

                if (matched != null) {
                    feedbackController.signalSuccess()
                    _uiState.update { state ->
                        state.copy(
                            mode = IngestionMode.HOME,
                            scannedCheckItem = matched,
                            checkedNotFound = null
                        )
                    }
                } else {
                    feedbackController.signalUnknown()
                    val offProduct = try { offRepository.getProduct(barcode) } catch (e: Exception) { null }
                    val name = offProduct?.displayProductName?.takeIf { it.isNotBlank() } ?: "Unknown Product"
                    val brand = offProduct?.displayBrands
                    val imageUrl = offProduct?.imageUrl

                    val notFoundInfo = CheckedNotFoundItem(
                        barcode = barcode,
                        productName = name,
                        brand = brand,
                        imageUrl = imageUrl
                    )
                    _uiState.update { state ->
                        state.copy(
                            mode = IngestionMode.HOME,
                            scannedCheckItem = null,
                            checkedNotFound = notFoundInfo
                        )
                    }
                }
            }
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
                        Log.e("IngestionVM", "Failed to enrich barcode $barcode", e)
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
            val currentScanned = _uiState.value.scannedItems
            val createdItemIds = mutableListOf<String>()

            // Create unassigned pantry_item records on PocketBase ONLY when user clicks "Send to Pantry"
            currentScanned.forEach { item ->
                val inferredUnits = PantryItem.inferUnitsPerPack(item.productName, item.quantity)
                val determinedType = PantryItem.determineTrackingType(item.productName, quantity = item.quantity, unitsPerPack = inferredUnits)
                val initialSealed = if (determinedType == TrackingType.DISCRETE_COUNT) {
                    if (inferredUnits > 1) 0 else 1
                } else 0

                val newPantryItem = PantryItem(
                    id = "",
                    name = item.productName.ifBlank { "Unknown Product" },
                    barcode = item.barcode,
                    brand = item.brand,
                    packageQuantity = item.quantity,
                    imageUrl = item.imageUrl,
                    apiImageUrl = item.imageUrl,
                    shelfNumber = 1,
                    zoneIndex = 1,
                    trackingType = determinedType,
                    sealedCount = initialSealed,
                    unitsPerPack = inferredUnits,
                    activeCount = inferredUnits,
                    isAssigned = false, // Unassigned
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                try {
                    val created = syncService.createPantryItem(newPantryItem)
                    if (created != null && created.id.isNotBlank() && !created.id.startsWith("local_")) {
                        Log.d("IngestionVM", "Successfully created pantry item on PB for ${item.barcode}: ${created.id}")
                        createdItemIds.add(created.id)
                    }
                } catch (e: Exception) {
                    Log.e("IngestionVM", "Failed to create pantry item on PB for ${item.barcode}: ${e.message}")
                }
            }

            Log.d("IngestionVM", "Sending batch_payload with ${createdItemIds.size} itemIds: $createdItemIds")
            val payload = BatchPayload(
                pantryId = _uiState.value.pantryId,
                itemIds = createdItemIds,
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

    fun clearCheckedNotFound() {
        _uiState.update { it.copy(checkedNotFound = null) }
    }

    fun startScanner(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        scanner.start(lifecycleOwner, surfaceProvider)
    }

    private fun List<PantryItem>.sortPantryItems(): List<PantryItem> {
        return this.sortedWith(
            compareBy<PantryItem> { it.shelfNumber }
                .thenBy { it.zoneIndex }
                .thenBy { it.name.lowercase() }
        )
    }
}
