# Low Stock Calculation

We define a standardized logic for determining when an item needs restocking to provide consistent visual feedback across the app.

- **For Staples (Fill Level tracking)**: An item is "Low Stock" if the active pack's `FillLevel` is `LOW` and the `sealed_count` (reserve stock) is exactly `0`.
- **For Units (Count tracking)**: An item is "Low Stock" if the total consumable units (all items across all packs) is less than or equal to the defined `units_per_pack`.

This rule prioritizes reserve stock: an item is never considered "low" if there is at least one unopened pack available.

Low Stock items are highlighted in the UI using a distinct **Amber/Orange background tint or border**, and can be filtered via a dedicated "Restock List" toggle at the top of the pantry view.

