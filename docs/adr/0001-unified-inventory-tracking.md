# Unified Inventory Tracking

For items tracked by count (Units), we use the `sealed_count` field in the database to represent the total number of consumable units available, rather than splitting inventory between "sealed" and "active" fields. This decision simplifies synchronization with the PocketBase server and ensures the UI reliably displays the total items remaining, while still allowing the app to calculate and display the number of physical "Packs" based on the product's units-per-pack metadata.
