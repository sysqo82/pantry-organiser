package com.pantry.organiser.dashboard.data

import android.content.Context
import android.util.Log
import com.pantry.organiser.core.model.PantryItem
import com.pantry.organiser.core.model.PastItem
import com.pantry.organiser.core.model.toPastItem
import com.pantry.organiser.core.network.SyncService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PantryRepository @Inject constructor(
    private val pantryDao: PantryDao,
    private val pastItemDao: PastItemDao,
    private val syncService: SyncService,
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val allItems: Flow<List<PantryItem>> = pantryDao.getAllItems()
    val pastItems: Flow<List<PastItem>> = pastItemDao.getAllPastItems()

    private var observeJob: Job? = null

    init {
        scope.launch {
            try {
                val rawRemoteItems = syncService.fetchPantryItems()
                val flagFile = File(context.filesDir, "pantry_pb_shelves_migrated_v2.flag")
                val needsMigration = !flagFile.exists()

                val remoteItems = rawRemoteItems.map { item ->
                    var migrated = item
                    if (needsMigration && migrated.shelfNumber in 1..4) {
                        val newShelf = 5 - migrated.shelfNumber
                        Log.i("PantryRepo", "Migrating remote dashboard item shelf for ${migrated.name}: ${migrated.shelfNumber} -> $newShelf")
                        migrated = migrated.copy(shelfNumber = newShelf)
                        scope.launch {
                            syncService.updatePantryItem(migrated)
                        }
                    }
                    migrated
                }
                if (needsMigration) {
                    try { flagFile.createNewFile() } catch (_: Exception) {}
                }

                val remoteIds = remoteItems.map { it.id }.toSet()

                val localItems = pantryDao.getAllItemsOnce()

                localItems.forEach { local ->
                    val isGenericOrEmpty = local.name.isBlank() || 
                                          local.name == "Unknown Product" || 
                                          local.name == "Network Error" || 
                                          local.name == "Unnamed Item"
                    
                    val isGhostServerItem = !local.id.startsWith("local_") && !remoteIds.contains(local.id)
                    val isGenericGhost = isGenericOrEmpty && (!local.hasStock || local.barcode.isNullOrBlank())

                    // Migrate zero-stock assigned items to past items, or prune ghost items
                    if (local.isAssigned && !local.hasStock) {
                        Log.i("PantryRepo", "Migrating zero-stock assigned item to past items: ${local.name} (${local.id})")
                        moveToPastItems(local)
                    } else if (isGhostServerItem || isGenericGhost) {
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

                    remoteItems.forEach { mergeAndInsert(it) }
                }

                val remotePastItems = try { syncService.fetchPastItems() } catch (e: Exception) { emptyList() }
                if (remotePastItems.isNotEmpty()) {
                    pastItemDao.insertPastItems(remotePastItems)
                }
            } catch (e: Exception) {
                Log.e("PantryRepo", "Failed reconciliation of pantry_items: ${e.message}")
            }
        }
    }

    private suspend fun mergeAndInsert(remoteItemBase: PantryItem) {
        val existing = pantryDao.getItemById(remoteItemBase.id)
        val trackingTypeToUse = existing?.trackingType ?: remoteItemBase.trackingType
        val remoteItem = remoteItemBase.copy(trackingType = trackingTypeToUse)

        if (existing != null) {
            val preservedUri = existing.localImageUri ?: remoteItem.localImageUri
            val isLocalNewer = existing.updatedAt >= remoteItem.updatedAt

            Log.d("PantryRepo", "mergeAndInsert: ${remoteItem.name} (${remoteItem.id}) -> localUpdated=${existing.updatedAt}, remoteUpdated=${remoteItem.updatedAt}, isLocalNewer=$isLocalNewer, trackingTypeToUse=$trackingTypeToUse")

            val effectiveFill = if (isLocalNewer) existing.activeFill else remoteItem.activeFill
            val effectiveSealed = if (isLocalNewer) existing.sealedCount else remoteItem.sealedCount
            val effectiveUnits = if (isLocalNewer) existing.unitsPerPack else remoteItem.unitsPerPack
            val effectiveActiveCount = if (isLocalNewer) existing.activeCount else remoteItem.activeCount
            val effectiveShelf = if (isLocalNewer) existing.shelfNumber else remoteItem.shelfNumber
            val effectiveZone = if (isLocalNewer) existing.zoneIndex else remoteItem.zoneIndex

            val merged = remoteItem.copy(
                localImageUri = preservedUri,
                trackingType = trackingTypeToUse,
                activeFill = effectiveFill,
                sealedCount = effectiveSealed,
                unitsPerPack = effectiveUnits,
                activeCount = effectiveActiveCount,
                shelfNumber = effectiveShelf,
                zoneIndex = effectiveZone,
                updatedAt = if (isLocalNewer) existing.updatedAt else remoteItem.updatedAt
            )
            pantryDao.insertItem(merged)
        } else {
            Log.d("PantryRepo", "mergeAndInsert: inserting new remote item ${remoteItem.name} (${remoteItem.id}) with trackingType=${remoteItem.trackingType}")
            pantryDao.insertItem(remoteItem)
        }
    }

    fun startObservingRealtime() {
        if (observeJob?.isActive == true) return
        observeJob = scope.launch {
            syncService.observePantryItems().collect { remoteItem ->
                if (remoteItem.sealedCount < 0) {
                    pantryDao.deleteItem(remoteItem)
                } else {
                    remoteItem.barcode?.takeIf { it.isNotBlank() }?.let { barcode ->
                        pantryDao.deleteLocalItemsByBarcode(barcode)
                    }
                    mergeAndInsert(remoteItem)
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
        safeItem.barcode?.let { removeFromPastItems(it) }
        scope.launch {
            val created = syncService.createPantryItem(safeItem)
            if (created != null && created.id != safeItem.id) {
                pantryDao.deleteItem(safeItem)
                safeItem.barcode?.takeIf { it.isNotBlank() }?.let { barcode ->
                    pantryDao.deleteLocalItemsByBarcode(barcode)
                }
                pantryDao.insertItem(created)
            }
        }
    }

    suspend fun updateItem(item: PantryItem) {
        Log.d("PantryRepo", "updateItem called for ${item.name} (${item.id}): trackingType=${item.trackingType}, updatedAt=${item.updatedAt}")
        pantryDao.updateItem(item)
        item.barcode?.let { removeFromPastItems(it) }
        scope.launch {
            val updated = syncService.updatePantryItem(item)
            if (updated != null) {
                Log.d("PantryRepo", "Successfully synced update for ${item.name} to server. Server returned trackingType=${updated.trackingType}")
            } else {
                Log.e("PantryRepo", "FAILED to sync update for ${item.name} to server!")
            }
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

    suspend fun moveToPastItems(item: PantryItem) {
        pantryDao.deleteItem(item)
        val pastItem = item.toPastItem().copy(
            isAssigned = false,
            updatedAt = System.currentTimeMillis()
        )
        pastItemDao.insertPastItem(pastItem)
        scope.launch {
            syncService.deletePantryItem(item.id)

            // Delete any existing duplicate past items on server before creating new past item
            item.barcode?.takeIf { it.isNotBlank() }?.let { bc ->
                try {
                    val remotePastItems = syncService.fetchPastItems()
                    remotePastItems.filter { it.barcode == bc }.forEach { existing ->
                        syncService.deletePastItem(existing.id)
                    }
                } catch (_: Exception) {}
            }

            val createdPast = syncService.createPastItem(pastItem)
            if (createdPast != null && createdPast.id != pastItem.id) {
                pastItemDao.deletePastItem(pastItem)
                pastItemDao.insertPastItem(createdPast)
            }
        }
    }

    suspend fun getItemByBarcode(barcode: String): PantryItem? {
        return pantryDao.getItemByBarcode(barcode)
    }

    suspend fun getPastItemByBarcode(barcode: String): PastItem? {
        if (barcode.isBlank()) return null
        return pastItemDao.getPastItemByBarcode(barcode)
    }

    suspend fun removeFromPastItems(barcode: String) {
        if (barcode.isBlank()) return
        val existingPastItem = pastItemDao.getPastItemByBarcode(barcode)
        pastItemDao.deletePastItemByBarcode(barcode)
        scope.launch {
            if (existingPastItem != null && existingPastItem.id.isNotBlank() && !existingPastItem.id.startsWith("local_")) {
                syncService.deletePastItem(existingPastItem.id)
            }
            try {
                val remotePastItems = syncService.fetchPastItems()
                remotePastItems.filter { it.barcode == barcode }.forEach { remote ->
                    if (remote.id.isNotBlank() && !remote.id.startsWith("local_")) {
                        syncService.deletePastItem(remote.id)
                    }
                }
            } catch (e: Exception) {
                Log.e("PantryRepo", "Failed cleaning remote past item for $barcode: ${e.message}")
            }
        }
    }
}
