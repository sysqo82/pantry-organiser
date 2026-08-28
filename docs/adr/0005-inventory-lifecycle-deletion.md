# Inventory Lifecycle Deletion

Pantry Items are automatically **deleted from the database** as soon as their total consumable quantity reaches zero.
- For **Units**: Deleted when the total unit count hit 0.
- For **Staples**: Deleted when the active pack is "Empty" and there are 0 sealed packs remaining.

We do not maintain "Empty Placeholder" records in the pantry grid. This keeps the database size minimal and the UI focused on physical presence. Future restocking relies on scanning the barcode of the new product rather than reviving an old record.
