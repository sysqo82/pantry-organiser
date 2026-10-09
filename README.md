# Pantry Organiser

Know exactly what's in your pantry – and where it is – without opening every tin.

Pantry Organiser turns a tablet mounted on the pantry door into a live map of your shelves. Unpack the shopping, scan the barcodes with your phone, and everything appears on the tablet in the right spot. When something runs low, it goes on the shopping list automatically.

## How it works

There are two apps that work together:

### 📱 The phone app – for putting things away
- **Scan as you unpack** – keep the camera running and scan item after item.
- **Product details are filled in for you** – name, size and picture come from Open Food Facts. If a product isn't recognised, snap a photo of it instead.
- **Send the batch** to the pantry tablet when you're done.
- **Shopping list widget** – keep the shopping list on your home screen so it's there when you're at the shops.

### 📟 The tablet – for seeing what you've got
- **A map of your shelves** – four shelves, each split into left, middle and right, showing what lives where.
- **Place new items** – newly scanned items wait in a queue until you pick a spot for them and confirm how many there are.
- **Track what's left** – count things like tins and bottles one by one; for things like flour or sugar, just mark how full the open pack is.
- **Spot what's running low** – low items are highlighted in amber, and a "restock" filter shows only those.
- **Automatic shopping list** – when something runs low or runs out, it's added to the list for you.

## Made for the pantry door

The tablet wakes up when the door opens, dims when it's left idle, and switches to a darker night mode in the evening – so it's always ready, but never glaring at you.

## How it's built

- **Language:** Kotlin, with screens built in **Jetpack Compose**.
- **Barcode scanning:** **CameraX** with **Google ML Kit**.
- **Product details:** looked up from **Open Food Facts** using **Ktor**. Pictures are loaded with **Coil**.
- **Sending scans from phone to tablet:** the phone uploads each batch to a **PocketBase** server, and the tablet receives it straight away through a live stream.
- **Where the pantry lives:** the tablet is the one place the pantry is stored, in a **Room** database. Phones only send scans.
- **Shopping list widget:** built with **Glance** and kept up to date with **WorkManager**.
- **Door detection:** the tablet uses its light sensor and motion sensors to tell when the door is open.
- **Setup:** **Hilt** wires the app's parts together.

### Project layout

```
app-dashboard/   the tablet app
app-ingestion/   the phone app
core/            code shared by both apps
app/             the original single-app prototype
docs/adr/        notes on key design decisions
CONTEXT.md       a glossary of the terms used in the project
```

## Running it yourself

1. Open the project in **Android Studio** and let Gradle sync.
2. Set up a **PocketBase** server for the two apps to talk through.
3. Install **app-dashboard** on the tablet and **app-ingestion** on your phone.
4. Link both to the same pantry.

Tests can be run with `./gradlew test`.
