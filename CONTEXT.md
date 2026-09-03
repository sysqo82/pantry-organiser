# Pantry Organiser

A system for tracking and managing home food inventory using a visual grid and barcode scanning.

## Language

### Storage

**Pantry**:
The high-level container for all items. Current implementation focus is a single physical pantry, with multi-location support (e.g. Fridge, Freezer) reserved for future development.


**Dashboard Device (Tablet)**:
The dedicated tablet mounted in the pantry, serving as the curator and the only device with the Room database (Single Source of Truth). It consumes updates via SSE and removes all camera-related hardware code.
_Avoid_: Tablet, Primary Device

**Ingestion Device (Mobile)**:
Mobile phones used strictly for data-entry. Features continuous barcode scanning, batch processing, and dispatching payloads to the Dashboard.
_Avoid_: Phone, remote device, Secondary Device


**Shelf**:
A horizontal level within the Pantry, numbered 1 to 4.
_Avoid_: Row

**Zone**:
An arbitrary horizontal subdivision of a Shelf (Left, Mid, Right), used for digital organization in the app rather than physical dividers.
_Avoid_: Column, cell

### Items

**Pantry Item**:
A specific product record tracked at a specific Shelf/Zone location.
_Avoid_: Product, entry

**Product Quantity**:
Descriptive text (e.g. "1kg", "500ml") used only for display and item recognition, not for internal math.


**Staple**:
An item measured by its fill level rather than a numeric count (e.g., flour, sugar).
_Avoid_: Bulk, ingredient

**Unit**:
A single, countable instance of a product (e.g., one tin, one bottle).
_Avoid_: Discrete count

**Multipack**:
A single purchase that contains multiple Units (e.g., a "4-pack" of sweetcorn).

### Inventory

**Inventory**:
The total number of Units available for a Pantry Item across all physical packs.

**Sealed Count**:
The number of unopened Units in stock. For Units, this is the total consumable quantity. For Staples, this represents spare full packs.

**Active Pack**:
The specific pack currently being consumed. For Staples, it has a Fill Level.

**Opening**:
The automatic transition of a Unit from reserve stock to being available for immediate consumption when active stock reaches zero.

### Data & Sync

**Batch Payload**:
A collection of items scanned by an Ingestion Device in a single session. Dispatched to the cloud (PocketBase) to be consumed by the Dashboard.

**Sync Queue**:
The temporary state on the Dashboard representing items received from an Ingestion Device that are awaiting shelf assignment and quantity confirmation.

**Pantry ID**:
A unique identifier for a specific pantry household, used to route messages between Ingestion and Dashboard devices.



