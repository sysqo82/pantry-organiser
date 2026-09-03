# Bug Fix: Infinite Sanity Fix Loop

The app enters an infinite loop of "Sanity Fix" updates when adding certain items (like flour). This is caused by a race condition between `PantryViewModel`'s data sanity check and `PantryRepository`'s realtime sync with PocketBase.

## Problem Analysis

1.  **Race Condition**: `PantryViewModel` observes `allItems`. When it sees an item that needs re-classification (e.g., "flour" should be `BULK_LEVEL` but is `DISCRETE_COUNT`), it updates the repository.
2.  **Circular Overwrite**: The repository updates the local DB (triggering a new emission) and then syncs to PocketBase. PocketBase sends a realtime event back. If the server record still has the old type (or defaults to `DISCRETE_COUNT`), `mergeAndInsert` overwrites the local fix.
3.  **ViewModel Loop**: The ViewModel sees the reverted type and triggers the fix again.

## Proposed Changes

### 1. Centralize Tracking Type Logic
Move `determineTrackingType` from `PantryViewModel` to `PantryItem` companion object to make it accessible to both the UI and the Data layer.

#### [MODIFY] [PantryItem.kt](file:///D:/D%20backup/My%20Documents/projects/pantry-organiser/app/src/main/java/com/pantry/organiser/data/PantryItem.kt)
- Move `determineTrackingType` logic here.
- Clean up the logic to use simple strings (name, categories, quantity) instead of requiring an `OffProduct` object, making it more portable.

### 2. Sanitize at the Source (Data Layer)
Update the mapping logic to ensure data is sane as soon as it arrives from the server.

#### [MODIFY] [PocketBaseModels.kt](file:///D:/D%20backup/My%20Documents/projects/pantry-organiser/app/src/main/java/com/pantry/organiser/data/PocketBaseModels.kt)
- Update `toLocal()` to apply `determineTrackingType` to incoming server records. This prevents the "overwriting" of local fixes by server data that might be missing the correct type.

### 3. Simplify ViewModel Sanity Check
Clean up `PantryViewModel` to use the centralized logic and reduce the risk of loops.

#### [MODIFY] [PantryViewModel.kt](file:///D:/D%20backup/My%20Documents/projects/pantry-organiser/app/src/main/java/com/pantry/organiser/ui/PantryViewModel.kt)
- Use `PantryItem.determineTrackingType`.
- Fix `determineTrackingTypeForName` to correctly pass the name.
- Add a safety check to `performDataSanityCheck` to avoid triggering updates if an update is already likely in flight or if the change is redundant.

## Verification Plan

### Automated Tests
- Run `PantryViewModelTest` to ensure classification still works.
- Add a test case for "flour" specifically.

### Manual Verification
- Add "Plain flour" via scanner or manual entry and verify no infinite loop occurs in Logcat.
- Verify that tracking type is correctly set to `BULK_LEVEL` for flour.
