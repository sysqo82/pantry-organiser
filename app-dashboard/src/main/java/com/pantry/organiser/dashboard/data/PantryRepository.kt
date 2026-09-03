package com.pantry.organiser.dashboard.data

import com.pantry.organiser.core.model.PantryItem
import com.pantry.organiser.core.network.SyncService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PantryRepository @Inject constructor(
    private val pantryDao: PantryDao,
    private val syncService: SyncService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val allItems: Flow<List<PantryItem>> = pantryDao.getAllItems()

    private var observeJob: kotlinx.coroutines.Job? = null

    init {
        scope.launch {
            try {
                val remoteItems = syncService.fetchPantryItems()
                val remoteIds = remoteItems.map { it.id }.toSet()

                val localItems = pantryDao.getAllItemsOnce()

                localItems.forEach { local ->
                    val isGenericOrEmpty = local.name.isBlank() || 
                                          local.name == "Unknown Product" || 
                                          local.name == "Network Error" || 
                                          local.name == "Unnamed Item"
                    
                    val isGhostServerItem = !local.id.startsWith("local_") && !remoteIds.contains(local.id)
                    val isGenericGhost = isGenericOrEmpty && (!local.hasStock || local.barcode.isNullOrBlank())

                    // Prune local items that no longer exist on the server or generic local ghosts
                    if (isGhostServerItem || isGenericGhost) {
                        android.util.Log.i("PantryRepo", "Pruning ghost item: ${local.name} (${local.id})")
                        pantryDao.deleteItem(local)
                    } else if (local.id.startsWith("local_")) {
                        android.util.Log.d("PantryRepo", "Uploading missing local item to PB: ${local.name}")
                        val created = syncService.createPantryItem(local)
                        if (created != null && created.id != local.id) {
                            pantryDao.deleteItem(local)
                            pantryDao.insertItem(created)
                        }
                    }
                }

                if (remoteItems.isNotEmpty()) {
                    pantryDao.insertItems(remoteItems)
                }
            } catch (e: Exception) {
                android.util.Log.e("PantryRepo", "Failed reconciliation of pantry_items: ${e.message}")
            }
        }
    }

    fun startObservingRealtime() {
        if (observeJob?.isActive == true) return
        observeJob = scope.launch {
            syncService.observePantryItems().collect { remoteItem ->
                if (remoteItem.sealedCount < 0) {
                    pantryDao.deleteByIdOrBarcode(remoteItem.id, remoteItem.barcode)
                } else {
                    pantryDao.insertItem(remoteItem)
                }
            }
        }
    }

    fun stopObservingRealtime() {
        observeJob?.cancel()
        observeJob = null
    }

    suspend fun addItem(item: PantryItem) {
        if (!item.id.isEmpty() && !item.id.startsWith("local_")) {
            updateItem(item)
            return
        }

        val safeItem = if (item.id.isEmpty()) {
            item.copy(id = "local_" + java.util.UUID.randomUUID().toString())
        } else item

        pantryDao.insertItem(safeItem)
        scope.launch {
            val created = syncService.createPantryItem(safeItem)
            if (created != null && created.id != safeItem.id) {
                pantryDao.deleteItem(safeItem)
                pantryDao.insertItem(created)
            }
        }
    }

    suspend fun updateItem(item: PantryItem) {
        pantryDao.updateItem(item)
        scope.launch {
            val updated = syncService.updatePantryItem(item)
            if (updated != null && updated.id != item.id) {
                pantryDao.deleteItem(item)
                pantryDao.insertItem(updated)
            }
        }
    }

    suspend fun deleteItem(item: PantryItem) {
        pantryDao.deleteByIdOrBarcode(item.id, item.barcode)
        scope.launch {
            syncService.deletePantryItem(item.id)
        }
    }

    suspend fun getItemByBarcode(barcode: String): PantryItem? {
        return pantryDao.getItemByBarcode(barcode)
    }
}
