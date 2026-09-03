# Comprehensive Repository Guide (Agent-Use Only)

This guide is designed for AI agents to understand the architecture, domain, and synchronization mechanics of the Pantry Organiser system.

## 1. System Architecture

The project is a multi-module Android system using **Compose**, **Hilt**, and **Ktor**.

- **`:app-dashboard` (Tablet)**: The Single Source of Truth (SSOT).
  - Maintains the **Room Database** (`PantryDatabase`).
  - Consumes scan payloads from PocketBase via SSE.
  - Handles "Enrichment" (resolving barcodes to product names/images via OpenFoodFacts if not already provided).
  - Only device role allowed to perform destructive or additive writes to the physical inventory.
- **`:app-ingestion` (Mobile)**: The data-entry tool.
  - Uses **CameraX** and **ML Kit** for continuous barcode scanning.
  - Aggregates scans into a `BatchPayload`.
  - Dispatches payloads to PocketBase; does not maintain a local inventory database.
- **`:core`**: Shared logic.
  - **Models**: `PantryItem`, `BatchPayload`, `TrackingType` (Discrete vs. Staple).
  - **Network**: `PocketBaseSyncService` handles the Ktor client and SSE stream.
  - **Constants**: `PantryConstants` handles coordinate mapping (Shelf 1-4 to UI Rows 0-3).

## 2. Domain & Data Models

### Inventory Concepts
- **Unit**: Countable items (e.g., tins). Tracked via `sealedCount`.
- **Staple**: Measured by fill level (e.g., flour). Tracked via `activeFill` (Empty to Full).
- **Shelf/Zone**: 4 Shelves, 3 Zones (Left, Mid, Right) per shelf.
- **Coordinate Mapping**: 
  - `Shelf 4` (Top) -> `Row 0`
  - `Shelf 1` (Bottom) -> `Row 3`
  - `Zone 1` (Left) -> `Col 0`

### Database (Dashboard only)
- `pantry_items`: Physical inventory.
- `sync_queue`: Temporary storage for items scanned by Ingestion but not yet assigned to a shelf by the Dashboard.

## 3. Synchronization Flow (PocketBase)

1. **Ingestion Scan**: `ContinuousScanner` detects barcode -> Stability Filter -> Emit to `IngestionViewModel`.
2. **Payload Dispatch**: `IngestionViewModel` collects items -> `PocketBaseSyncService.dispatchBatch`.
3. **Realtime Observation**: `SyncQueueRepository` (Dashboard) calls `observeBatches`.
4. **SSE Stream**: `PocketBaseSyncService` reads line-by-line from `/api/realtime`.
   - Re-subscribes to the `batch_payloads` collection upon receiving a `clientId`.
   - Parses `RealtimeEvent` and emits `BatchPayload`.
5. **Dashboard Processing**: `SyncQueueRepository` receives payload -> Enriches via OpenFoodFacts if needed -> Inserts into `sync_queue` table.
   - **Enrichment Logic**: `OpenFoodFactsRepository` handles UPC-A/EAN-13 normalization (trying with and without leading zero in parallel) to maximize hit rate.
6. **Manual Assignment**: User clicks a pending item in Dashboard -> `EnrichmentOverlay` -> User selects Shelf/Zone -> Item moved to `pantry_items` table.

## 4. Key Components & Paths

- **Shared Constants**: [PantryConstants.kt](file:///D:/D%20backup/My%20Documents/projects/pantry-organiser/core/src/main/java/com/pantry/organiser/core/model/PantryConstants.kt)
- **SSE Sync Implementation**: [PocketBaseSyncService.kt](file:///D:/D%20backup/My%20Documents/projects/pantry-organiser/core/src/main/java/com/pantry/organiser/core/network/PocketBaseSyncService.kt)
- **Scanner Logic**: [ContinuousScanner.kt](file:///D:/D%20backup/My%20Documents/projects/pantry-organiser/app-ingestion/src/main/java/com/pantry/organiser/ingestion/scanner/ContinuousScanner.kt)
- **Grid UI Mapping**: [PantryShelfGrid.kt](file:///D:/D%20backup/My%20Documents/projects/pantry-organiser/app-dashboard/src/main/java/com/pantry/organiser/dashboard/ui/PantryShelfGrid.kt)

## 5. Maintenance Constraints

- **Tests**: ALWAYS run `./gradlew test` after changes.
- **Barcode Validation**: Always use `PantryConstants.isValidBarcodeChecksum` for new scanner/data entry features.
- **Lifecycle**: Camera resources must be strictly lifecycle-bound (already handled in `ContinuousScanner`).
- **UI Insets**: Refer to `AGENTS.md` for proper `statusBarsPadding` and `navigationBarsPadding` usage.

## 6. Known Potential Issues
- **SSE Instability**: The Ktor stream reading is manual; check `PocketBaseSyncService` for reconnection logic if sync fails.
- **Duplication**: Some data classes and DAOs exist in both `:app` and `:app-dashboard`. Prioritize `:app-dashboard` as it is the active SSOT module.
