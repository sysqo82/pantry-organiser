# Barcode Collision Resolution

In cases where a single barcode matches multiple entries in the database (e.g., the same product stored in different zones), the system resolves the conflict using **Location Priority**:
1.  Prioritize the item assigned to the shelf and zone currently being viewed in the UI.
2.  If no viewed items match, or if multiple items match within the same context, present a picker to the user to disambiguate.

This ensures that "Take / Consume" actions default to the items the user is most likely interacting with based on their current digital "focus."
