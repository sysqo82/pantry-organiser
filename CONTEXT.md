# Pantry Organiser

A system for tracking and managing home food inventory using a visual grid and barcode scanning.

## Language

### Storage

**Pantry**:
The high-level container for all items. Current implementation focus is a single physical pantry, with multi-location support (e.g. Fridge, Freezer) reserved for future development.


**Primary Device**:
The dedicated tablet mounted in the pantry, serving as the only device with write permissions and the primary source of truth.
_Avoid_: Tablet

**Secondary Device**:
Mobile phones used in a read-only mode for inventory lookups.
_Avoid_: Phone, remote device


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

### Inventory State

**Low Stock**:
A state where an item's remaining quantity triggers a restock reminder.
- For **Staples**: Active pack is at "Low" Fill Level AND there are zero sealed units in reserve.
- For **Units**: Total consumable units is less than or equal to one full "Pack" size (e.g., 1 tin left of a 3-pack).

**Shopping List**:
A collection of items marked for replenishment. Items are added **automatically** when they reach a Low Stock state or are deleted (consumed to zero).

**Barcode Multi-Match**:
A scenario where one barcode resolves to multiple pantry items (e.g. same product in two locations). Resolved via **Location Priority** (favoring the currently viewed shelf/zone).



