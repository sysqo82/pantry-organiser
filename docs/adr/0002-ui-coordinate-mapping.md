# UI Coordinate Mapping

To ensure the visual grid in the app matches the user's physical experience of looking at a pantry, we map the UI rows (which are 0-indexed from the top) to Shelf numbers as follows:
- **UI Row 0** (Top) = **Shelf 4** (Top shelf)
- **UI Row 3** (Bottom) = **Shelf 1** (Bottom shelf)

This inverse mapping (Row `y` maps to Shelf `4 - y`) ensures that adding an item to the "top" of the digital grid correctly places it on the physical top shelf of the pantry.
