You never NEVER EVER, commit to main, if the user is on main, you MUST always create a branch and commit to the branch!
You must NEVER commit and push changes even to a branch, unless the user tells you to do it
ALWAYS!!!!!!!! run the entire suite of tests after every code change, ALWAYS!!!!!
Never EVER touch anything out of the scope of fix, feature or refactor code
If I ask you to use a skill, you can find them in the `D:\D backup\My Documents\projects\skills` directory. Always refer to that path to read skill instructions.

## Agent skills

### Issue tracker

GitHub Issues. See `docs/agents/issue-tracker.md`.

### Triage labels

Canonical five-role vocabulary. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context layout. See `docs/agents/domain.md`.

\# UI \& Android Development Terminology Glossary



Whenever I describe UI elements, bugs, or layout requests using everyday terms, map them directly to the corresponding Android / Jetpack Compose technical implementations below:



\### 1. System Bars \& Screen Boundaries

\* \*\*"Notification area" / "Top bar with clock and battery":\*\*

&#x20; \* Technical term: \*\*Status Bar\*\* (`WindowInsets.statusBars`).

&#x20; \* Icon color/contrast: `WindowInsetsControllerCompat.isAppearanceLightStatusBars` (set to `true` for dark icons on light backgrounds, `false` for light icons on dark backgrounds).

&#x20; \* Padding: `Modifier.statusBarsPadding()`.



\* \*\*"Navigation buttons" / "Bottom bar (Back/Home/Recents)":\*\*

&#x20; \* Technical term: \*\*System Navigation Bar\*\* (`WindowInsets.navigationBars`).

&#x20; \* Icon color/contrast: `WindowInsetsControllerCompat.isAppearanceLightNavigationBars` (set to `true` for dark buttons).

&#x20; \* Padding: `Modifier.navigationBarsPadding()`.



\* \*\*"Don't go behind the bars" / "Keep inside the screen":\*\*

&#x20; \* Technical term: \*\*Safe Inset Enforcement\*\*.

&#x20; \* Use `Modifier.safeDrawingPadding()`, `Modifier.systemBarsPadding()`, or wrap the screen inside a `Scaffold`. Ensure components do not draw edge-to-edge behind system controls unless explicitly asked.



\* \*\*"When the keyboard opens":\*\*

&#x20; \* Technical term: \*\*IME (Input Method Editor) Insets\*\*.

&#x20; \* Handled via `Modifier.imePadding()` or `WindowInsets.ime`.



\---



\### 2. Layouts, Navigation \& Containers

\* \*\*"App title bar" / "Header":\*\*

&#x20; \* Technical term: `TopAppBar` or `CenterAlignedTopAppBar` placed inside `Scaffold(topBar = { ... })`.



\* \*\*"Sliding bottom menu" / "Half-screen popup from bottom":\*\*

&#x20; \* Technical term: `ModalBottomSheet` / `BottomSheetScaffold`. Must respect `WindowInsets.navigationBars` to prevent bottom clipping.



\* \*\*"The visual shelf grid" / "The 4x3 boxes":\*\*

&#x20; \* Technical term: Visual matrix layout constructed with `Column` + `Row` or `LazyVerticalGrid`.



\* \*\*"The list of items":\*\*

&#x20; \* Technical term: `LazyColumn` with keyed items (`items(items, key = { it.id })`).



\* \*\*"Item card / row":\*\*

&#x20; \* Technical term: `Card`, `ElevatedCard`, or `ListItem`.



\* \*\*"Floating plus button":\*\*

&#x20; \* Technical term: `FloatingActionButton` (FAB) anchored in `Scaffold(floatingActionButton = { ... })`.



\* \*\*"Popup box" / "Confirmation box":\*\*

&#x20; \* Technical term: `AlertDialog` or Compose `Dialog`.



\---



\### 3. Camera, Hardware \& Live Previews

\* \*\*"Camera window" / "Viewfinder":\*\*

&#x20; \* Technical term: CameraX `PreviewView` embedded inside an `AndroidView`.

&#x20; \* Lifecycle rule: Must bind to `ProcessCameraProvider` only when visible, and completely unbind/release lifecycle listeners when closed.



\* \*\*"Ghost camera" / "Extra camera showing in background":\*\*

&#x20; \* Technical issue: Duplicate or leaked `AndroidView(PreviewView)` instantiation that was not scoped strictly inside the active conditional composable or bottom sheet.



\* \*\*"Barcode reader":\*\*

&#x20; \* Technical term: Google ML Kit `BarcodeScanning` analyzer attached to CameraX `ImageAnalysis.Builder`.



\---



\### 4. Interactive Elements \& Data Controls

\* \*\*"Plus/Minus count buttons" / "Counter":\*\*

&#x20; \* Technical term: Stepper control (`IconButton` with `Icons.Default.Add` / `Icons.Default.Remove`).

\* \*\*"Clickable box / shelf cell":\*\*

&#x20; \* Technical term: `Box` / `Surface` with `Modifier.clickable { ... }` and active state styling (border highlight, container color change).

\* \*\*"Pantry item location badge" (e.g., S2-M):\*\*

&#x20; \* Technical term: `AssistChip`, `SuggestionChip`, or custom `Surface` badge with rounded corners.

