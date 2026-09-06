package com.pantry.organiser.dashboard.data

import android.util.Log
import com.pantry.organiser.core.model.PantryItem
import com.pantry.organiser.core.network.SyncService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PantryRepository @Inject constructor(
    private val pantryDao: PantryDao,
    private val syncService: SyncService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val allItems: Flow<List<PantryItem>> = pantryDao.getAllItems()

    private var observeJob: Job? = null

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
                        Log.i("PantryRepo", "Pruning ghost item: ${local.name} (${local.id})")
                        pantryDao.deleteItem(local)
                    } else if (local.id.startsWith("local_")) {
                        Log.d("PantryRepo", "Uploading missing local item to PB: ${local.name}")
                        val created = syncService.createPantryItem(local)
                        if (created != null && created.id != local.id) {
                            pantryDao.deleteItem(local)
                            pantryDao.insertItem(created)
                        }
                    }
                }

                if (remoteItems.isNotEmpty()) {
                    // Consolidate duplicate assigned items sharing the same barcode
                    val assignedDupes = remoteItems.filter { it.isAssigned && !it.barcode.isNullOrBlank() }
                        .groupBy { it.barcode!! }
                        .filter { it.value.size > 1 }

                    assignedDupes.forEach { (barcode, dupes) ->
                        val primary = dupes.maxByOrNull { it.totalDisplayCount } ?: dupes.first()
                        val extras = dupes.filter { it.id != primary.id }

                        var consolidatedSealed = primary.sealedCount
                        var consolidatedActiveCount = primary.activeCount

                        extras.forEach { extra ->
                            consolidatedSealed += extra.sealedCount
                            if (primary.unitsPerPack > 1) {
                                consolidatedActiveCount += extra.activeCount
                            }
                            Log.i("PantryRepo", "Consolidating duplicate assigned item for barcode $barcode: ${extra.id}")
                            pantryDao.deleteItem(extra)
                            syncService.deletePantryItem(extra.id)
                        }

                        val updatedPrimary = primary.copy(
                            sealedCount = consolidatedSealed,
                            activeCount = consolidatedActiveCount,
                            updatedAt = System.currentTimeMillis()
                        )
                        pantryDao.insertItem(updatedPrimary)
                        syncService.updatePantryItem(updatedPrimary)
                    }

                    pantryDao.insertItems(remoteItems)
                }
            } catch (e: Exception) {
                Log.e("PantryRepo", "Failed reconciliation of pantry_items: ${e.message}")
            }
        }
    }

    fun startObservingRealtime() {
        if (observeJob?.isActive == true) return
        observeJob = scope.launch {
            syncService.observePantryItems().collect { remoteItem ->
                if (remoteItem.sealedCount < 0) {
                    pantryDao.deleteItem(remoteItem)
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
            item.copy(id = "local_" + UUID.randomUUID().toString())
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
        pantryDao.deleteItem(item)
        scope.launch {
            syncService.deletePantryItem(item.id)
        }
    }

    suspend fun getItemByBarcode(barcode: String): PantryItem? {
        return pantryDao.getItemByBarcode(barcode)
    }
}
