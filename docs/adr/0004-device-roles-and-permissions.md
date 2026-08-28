# Device Roles and Permissions

To maintain data integrity and simplify the system architecture, we enforce strict roles based on the device type:

- **Primary Device (Tablet)**: The wall-mounted tablet is the only device allowed to perform "Write" operations (Add, Consume, Edit, Delete). It is the definitive source of truth for the physical inventory.
- **Secondary Devices (Mobile)**: All other devices (phones) operate in a strictly **Read-Only** mode. They can view the grid and item details but cannot modify the database.

This eliminates the need for complex conflict resolution logic (like CRDTs or manual merging) because only one physical location manages the data.
