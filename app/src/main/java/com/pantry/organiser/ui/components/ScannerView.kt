package com.pantry.organiser.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pantry.organiser.data.OffProduct
import com.pantry.organiser.data.PantryItem
import com.pantry.organiser.data.TrackingType
import com.pantry.organiser.data.FillLevel
import com.pantry.organiser.ui.ScannerMode
import kotlinx.coroutines.delay
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import android.util.Size
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import android.util.Log

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.drawWithCache
import android.app.Activity
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun ScannerView(
    scannedProduct: OffProduct?,
    isLoading: Boolean,
    error: String?,
    recognizedItem: PantryItem?,
    pendingNewItem: PantryItem?,
    pendingConsumeItem: PantryItem?,
    scannerMode: ScannerMode,
    onBarcodeScanned: (String) -> Unit,
    onManualEntry: () -> Unit,
    onAssignShelf: (Int, Int) -> Unit,
    onCancelPending: () -> Unit,
    onUpdateConsumeLevel: (FillLevel) -> Unit,
    onUpdateConsumeUnits: (Int) -> Unit,
    onCancelConsume: () -> Unit,

    onSave: (String, String, String, Int, Int, String?, String?, TrackingType) -> Unit,
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    var isScreenLightEnabled by remember { mutableStateOf(false) }

    // Screen Light Brightness Logic
    DisposableEffect(isScreenLightEnabled) {
        if (isScreenLightEnabled) {
            val activity = context as? Activity
            val window = activity?.window
            val params = window?.attributes
            val originalBrightness = params?.screenBrightness ?: -1f
            
            params?.screenBrightness = 1.0f // Force Max Brightness
            window?.attributes = params
            
            onDispose {
                val resetParams = window?.attributes
                resetParams?.screenBrightness = originalBrightness
                window?.attributes = resetParams
            }
        } else {
            onDispose {}
        }
    }

    // Handle system back button to close scanner
    BackHandler(onBack = onDismiss)

    // 30-Second Inactivity Timer
    var timerKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(timerKey, recognizedItem, pendingNewItem, pendingConsumeItem, error, isLoading, scannedProduct) {
        delay(30000)
        onDismiss()
    }

    // Success Haptic on recognition
    LaunchedEffect(recognizedItem) {
        if (recognizedItem != null) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // LAYER 0: BASE LAYER (Camera) - Persistent static background
        CameraPreview(
            isTablet = isTablet,
            onBarcodeScanned = { barcode ->
                if (pendingNewItem == null && scannedProduct == null && pendingConsumeItem == null) {
                    onBarcodeScanned(barcode)
                    timerKey++
                }
            }
        )

        // Activity detector to reset timer on any touch that isn't handled by children
        Box(
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures { timerKey++ }
            }
        )


        // LAYER 0.5: SCREEN LIGHT OVERLAY
        AnimatedVisibility(
            visible = isScreenLightEnabled,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = 0.99f) // Required for BlendMode.Clear to work
                    .drawWithCache {
                        onDrawWithContent {
                            // Stark white background to reflect light
                            drawRect(Color.White)
                            
                            // Cut out a viewfinder hole so we can still see the camera
                            val holeWidth = size.width * 0.85f
                            val holeHeight = size.height * 0.35f
                            val topOffset = (size.height - holeHeight) / 2f
                            val leftOffset = (size.width - holeWidth) / 2f
                            
                            drawRoundRect(
                                color = Color.Transparent,
                                topLeft = Offset(leftOffset, topOffset),
                                size = androidx.compose.ui.geometry.Size(holeWidth, holeHeight),
                                cornerRadius = CornerRadius(24.dp.toPx()),
                                blendMode = androidx.compose.ui.graphics.BlendMode.Clear
                            )
                            
                            // Visual guide for the "Sweet Spot"
                            drawRoundRect(
                                color = Color.Yellow.copy(alpha = 0.5f),
                                topLeft = Offset(leftOffset, topOffset),
                                size = androidx.compose.ui.geometry.Size(holeWidth, holeHeight),
                                cornerRadius = CornerRadius(24.dp.toPx()),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
            )
        }

        // LAYER 1: UI OVERLAYS & CONTROLS
        // We coordinate guidance and bottom bar to avoid overlap
        val showGuidance = isTablet && pendingNewItem == null && scannedProduct == null && pendingConsumeItem == null

        // Standard Overlay Animations
        val overlayEnter = fadeIn(animationSpec = tween(150))
        val overlayExit = fadeOut(animationSpec = tween(150))

        // LAYER 1.1: RECOGNITION FLASH
        AnimatedVisibility(
            visible = recognizedItem != null,
            enter = fadeIn(tween(50)),
            exit = fadeOut(tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Green.copy(alpha = 0.3f))
            )
        }

        // LAYER 1.2: ACTIVE OVERLAYS (Place Item / Form)
        Box(
            modifier = Modifier.fillMaxSize().clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            // ... (pendingConsumeItem, scannedProduct, pendingNewItem AnimatedVisibility blocks remain same)
            // Overlay for Staples or Multipack Consumption
            AnimatedVisibility(
                visible = pendingConsumeItem != null,
                enter = overlayEnter,
                exit = overlayExit
            ) {
                pendingConsumeItem?.let { item ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.widthIn(max = 500.dp)
                        ) {
                            Surface(
                                color = Color.White.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(88.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    ProductThumbnail(
                                        imageUrl = item.imageUrl,
                                        apiImageUrl = item.apiImageUrl,
                                        localImageUri = item.localImageUri,
                                        itemName = item.name,
                                        updatedAt = item.updatedAt,
                                        thumbnailSize = 64.dp
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Column {
                                        Text(item.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
                                        if (item.unitsPerPack > 1) {
                                            val totalUnits = if (item.sealedCount == 1) item.unitsPerPack else item.sealedCount
                                            Text("$totalUnits total units remaining", color = Color.White.copy(alpha = 0.7f))
                                        } else {
                                            Text("Current: ${item.activeFill.label}", color = Color.White.copy(alpha = 0.7f))
                                        }

                                    }
                                }
                            }

                            if (item.unitsPerPack > 1) {
                                Text("How many taken?", color = Color.Yellow, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                
                                val totalAvailable = if (item.sealedCount == 1) item.unitsPerPack else item.sealedCount
                                // Key on item.id ONLY. Do not reset when totalAvailable flickers (e.g. background discovery)
                                var unitsTaken by remember(item.id) { mutableIntStateOf(1) }
                                
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        // Minus Button
                                        Button(
                                            onClick = { 
                                                if (unitsTaken > 1) { 
                                                    unitsTaken--
                                                    timerKey++ 
                                                } 
                                            },
                                            modifier = Modifier.size(80.dp),
                                            shape = CircleShape,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color.White.copy(alpha = 0.2f),
                                                contentColor = Color.White
                                            ),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Icon(Icons.Default.Remove, "Less", modifier = Modifier.size(40.dp))
                                        }
                                        
                                        Surface(
                                            color = Color.White.copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(24.dp),
                                            modifier = Modifier.padding(horizontal = 24.dp).widthIn(min = 120.dp)
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                                                Text(
                                                    text = unitsTaken.toString(),
                                                    style = MaterialTheme.typography.displayLarge,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Black
                                                )
                                                Text(
                                                    text = "of $totalAvailable",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = Color.White.copy(alpha = 0.6f)
                                                )
                                            }
                                        }

                                        // Plus Button
                                        Button(
                                            onClick = { 
                                                if (unitsTaken < totalAvailable) { 
                                                    unitsTaken++
                                                    timerKey++ 
                                                } 
                                            },
                                            modifier = Modifier.size(80.dp),
                                            shape = CircleShape,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color.White.copy(alpha = 0.2f),
                                                contentColor = Color.White
                                            ),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Icon(Icons.Default.Add, "More", modifier = Modifier.size(40.dp))
                                        }
                                    }



                                    
                                    Spacer(Modifier.height(32.dp))
                                    
                                    Button(
                                        onClick = { 
                                            android.util.Log.d("ScannerView", "Confirming removal of $unitsTaken units")
                                            onUpdateConsumeUnits(unitsTaken) 
                                        },
                                        modifier = Modifier.fillMaxWidth().height(72.dp),
                                        shape = RoundedCornerShape(20.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Yellow, contentColor = Color.Black)
                                    ) {
                                        Text("Confirm Removal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else {


                                Text("How much is left?", color = Color.Yellow, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

                                var selectedLevel by remember(item) { mutableStateOf(item.activeFill) }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FillLevel.entries.forEach { level ->
                                        FilterChip(
                                            selected = selectedLevel == level,
                                            onClick = { selectedLevel = level; timerKey++ },
                                            label = { Text(level.label) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                selectedLabelColor = Color.White,
                                                labelColor = Color.White.copy(alpha = 0.7f)
                                            )
                                        )
                                    }
                                }

                                Button(
                                    onClick = { onUpdateConsumeLevel(selectedLevel) },
                                    modifier = Modifier.fillMaxWidth().height(56.dp)
                                ) {
                                    Text("Update Stock")
                                }
                            }

                            TextButton(onClick = onCancelConsume, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }


            // Overlay for Manual Entry Form
            AnimatedVisibility(
                visible = scannedProduct != null,
                enter = overlayEnter,
                exit = overlayExit
            ) {
                scannedProduct?.let { product ->
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp)
                                .navigationBarsPadding(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text("Add Product Details", style = MaterialTheme.typography.headlineSmall)
                            
                            var name by remember { mutableStateOf(product.displayProductName ?: "") }
                            var brand by remember { mutableStateOf(product.brands ?: "") }
                            var packageQuantity by remember { mutableStateOf(product.weight ?: "") }
                            var trackingType by remember { mutableStateOf(TrackingType.DISCRETE_COUNT) }
                            var selectedRow by remember { mutableIntStateOf(0) }
                            var selectedCol by remember { mutableIntStateOf(1) }

                            OutlinedTextField(value = name, onValueChange = { name = it; timerKey++ }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = brand, onValueChange = { brand = it; timerKey++ }, label = { Text("Brand") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = packageQuantity, onValueChange = { packageQuantity = it; timerKey++ }, label = { Text("Weight / Quantity") }, modifier = Modifier.fillMaxWidth())
                            
                            Text("Tracking Mode", style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = trackingType == TrackingType.DISCRETE_COUNT, onClick = { trackingType = TrackingType.DISCRETE_COUNT; timerKey++ }, label = { Text("Discrete") })
                                FilterChip(selected = trackingType == TrackingType.BULK_LEVEL, onClick = { trackingType = TrackingType.BULK_LEVEL; timerKey++ }, label = { Text("Bulk/Staple") })
                            }

                            Text("Placement", style = MaterialTheme.typography.titleMedium)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp)
                                    .padding(vertical = 8.dp)
                            ) {
                                PantryShelfGrid(
                                    selectedCell = selectedRow to selectedCol,
                                    onCellClick = { r, c -> selectedRow = r; selectedCol = c; timerKey++ },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Button(
                                onClick = { onSave(name, brand, packageQuantity, selectedRow, selectedCol, product.imageUrl, null, trackingType) },
                                modifier = Modifier.fillMaxWidth().height(56.dp)
                            ) {
                                Text("Save to Pantry")
                            }
                        }
                    }
                }
            }

            // Overlay for Location Assignment
            AnimatedVisibility(
                visible = pendingNewItem != null,
                enter = overlayEnter,
                exit = overlayExit
            ) {
                pendingNewItem?.let { item ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.widthIn(max = 500.dp)
                        ) {
                            Surface(
                                color = Color.White.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(88.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    ProductThumbnail(
                                        imageUrl = item.imageUrl,
                                        apiImageUrl = item.apiImageUrl,
                                        localImageUri = item.localImageUri,
                                        itemName = item.name,
                                        updatedAt = item.updatedAt,
                                        thumbnailSize = 64.dp
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Column {
                                        Text(item.name, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(item.brand ?: "", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                            if (item.unitsPerPack > 1) {
                                                Spacer(Modifier.width(8.dp))
                                                Surface(
                                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "${item.unitsPerPack}-PACK",
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Black,
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Text(
                                "Tap a shelf to place this item",
                                color = Color.Yellow,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1.1f)
                                    .padding(vertical = 4.dp)
                            ) {
                                PantryShelfGrid(
                                    selectedCell = null,
                                    onCellClick = { r, c -> 
                                        onAssignShelf(r, c)
                                        timerKey++
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            TextButton(
                                onClick = onCancelPending,
                                colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }
        }

        // LAYER 1.3: PERSISTENT UI CONTROLS & GUIDANCE
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Guidance Overlay (Now managed here to avoid overlap)
            if (showGuidance) {
                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text(
                        text = "Hold item ~30cm from camera for best focus",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Bottom Bar
            if (pendingNewItem == null && scannedProduct == null && pendingConsumeItem == null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (scannerMode == ScannerMode.RESTOCK) "Continuous Scanning Active" else "Scan to Take / Consume",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (scannerMode == ScannerMode.RESTOCK) Color.White else MaterialTheme.colorScheme.tertiary
                    )
                    
                    if (isLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
                    }
                    
                    if (error != null) {
                        Text(error, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }

                    if (scannerMode == ScannerMode.RESTOCK && !isLoading) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onManualEntry,
                            shape = CircleShape,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
                        ) {
                            Text("Manual Entry")
                        }
                    }
                }
            }
        }
        
        // Close button (Always present, top layer)
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.3f), CircleShape)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }

        // Screen Light Toggle (Top Left)
        IconButton(
            onClick = { isScreenLightEnabled = !isScreenLightEnabled },
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp)
                .background(
                    if (isScreenLightEnabled) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.3f), 
                    CircleShape
                )
        ) {
            Icon(
                imageVector = if (isScreenLightEnabled) Icons.Filled.Lightbulb else Icons.Outlined.Lightbulb,
                contentDescription = "Screen Light",
                tint = if (isScreenLightEnabled) Color.White else Color.White
            )
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
fun CameraPreview(
    isTablet: Boolean,
    onBarcodeScanned: (String) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnBarcodeScanned by rememberUpdatedState(onBarcodeScanned)
    
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember { 
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_13,
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_8,
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_UPC_A,
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_UPC_E
            )
            .build()
        BarcodeScanning.getClient(options)
    }

    var lastScannedBarcode by remember { mutableStateOf<String?>(null) }
    var lastScanTime by remember { mutableLongStateOf(0L) }
    
    // STAGE 4: Stability Filter State
    // We keep track of the barcode seen in the *previous* frame.
    // We only confirm the scan if we see the same valid barcode twice in a row.
    var unconfirmedBarcode by remember { mutableStateOf<String?>(null) }
    
    val cameraProviderRef = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val cameraControlRef = remember { mutableStateOf<CameraControl?>(null) }
    val isDisposed = remember { mutableStateOf(false) }

    // Periodic Focus / Metering Refresh
    if (!isDisposed.value) {
        LaunchedEffect(Unit) {
            while (true) {
                cameraControlRef.value?.let { control ->
                    val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)
                    val point = factory.createPoint(0.5f, 0.5f)
                    val action = FocusMeteringAction.Builder(point)
                        .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    control.startFocusAndMetering(action)
                }
                delay(4000)
            }
        }
    }

    // Wrap the CameraX binding logic strictly inside a remember(lifecycleOwner) block
    // to prevent recomposition from rebounding the camera.
    val bindCamera = remember(lifecycleOwner) {
        { ctx: android.content.Context, previewView: PreviewView ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                if (isDisposed.value) return@addListener
                
                try {
                    val provider = cameraProviderFuture.get()
                    cameraProviderRef.value = provider
                    
                    val preview = Preview.Builder()
                        .setResolutionSelector(
                            ResolutionSelector.Builder()
                                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                                .build()
                        )
                        .build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setResolutionSelector(
                            ResolutionSelector.Builder()
                                .setResolutionStrategy(
                                    ResolutionStrategy(
                                        Size(1280, 720),
                                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                    )
                                )
                                .build()
                        )
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(cameraExecutor) { imageProxy ->
                                processImageProxy(
                                    barcodeScanner, 
                                    imageProxy, 
                                    onBarcodeScanned = { barcode ->
                                        val currentTime = System.currentTimeMillis()
                                        
                                        // STAGE 4: Stability Check (Double-Pass)
                                        // 1. If it's a "fresh" scan, hold it for confirmation
                                        if (barcode != unconfirmedBarcode) {
                                            unconfirmedBarcode = barcode
                                            return@processImageProxy
                                        }
                                        
                                        // 2. If it's the SAME barcode as the previous frame, we trust it.
                                        // We still apply the 3-second debounce to prevent multiple triggers.
                                        if (barcode != lastScannedBarcode || currentTime - lastScanTime > 3000) {
                                            lastScannedBarcode = barcode
                                            lastScanTime = currentTime
                                            unconfirmedBarcode = null // Reset for next item
                                            currentOnBarcodeScanned(barcode)
                                        }
                                    }
                                )
                            }
                        }

                    provider.unbindAll()
                    
                    val cameraSelector = if (isTablet) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }

                    val camera = provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                    cameraControlRef.value = camera.cameraControl
                    
                    // STAGE 1: Exposure Compensation for difficult labels
                    // We bump exposure by +1 index to brighten up dark labels (like soy sauce)
                    // provided the hardware supports it.
                    val exposureState = camera.cameraInfo.exposureState
                    if (exposureState.isExposureCompensationSupported) {
                        val range = exposureState.exposureCompensationRange
                        val targetIndex = (exposureState.exposureCompensationIndex + 3).coerceIn(range.lower, range.upper)
                        camera.cameraControl.setExposureCompensationIndex(targetIndex)
                        Log.d("ScannerView", "Exposure compensated: $targetIndex (Range: $range)")
                    }

                    camera.cameraControl.setLinearZoom(0.2f)
                } catch (e: Exception) {
                    Log.e("CameraPreview", "Use case binding failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            isDisposed.value = true
            lifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
                cameraProviderRef.value?.let { provider ->
                    launch(Dispatchers.Main) {
                        provider.unbindAll()
                    }
                }
                cameraExecutor.shutdown()
                barcodeScanner.close()
            }
        }
    }

    if (isDisposed.value) return

    Box(modifier = Modifier.fillMaxSize()) {
        // AndroidView(factory = { PreviewView(ctx) }) placed in static layer
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    clipToOutline = true
                    bindCamera(ctx, this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun processImageProxy(
    barcodeScanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    imageProxy: ImageProxy,
    onBarcodeScanned: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        
        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    val rawValue = barcode.rawValue ?: continue
                    
                    // STAGE 4: Mathematical Checksum + Stability Filter
                    if (isValidBarcodeChecksum(rawValue)) {
                        onBarcodeScanned(rawValue)
                    } else {
                        Log.d("Scanner", "Ignoring corrupted barcode (failed checksum): $rawValue")
                    }
                }
            }
            .addOnFailureListener {
                Log.e("Scanner", "Barcode scanning failed", it)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}

/**
 * Validates EAN-13 and UPC checksums to prevent misreads from low-quality front cameras.
 */
private fun isValidBarcodeChecksum(barcode: String): Boolean {
    val digits = barcode.filter { it.isDigit() }
    if (digits.length !in listOf(8, 12, 13)) return false
    
    return try {
        val checkDigit = digits.last().digitToInt()
        val payload = digits.dropLast(1).reversed()
        
        var sum = 0
        for ((index, char) in payload.withIndex()) {
            val digit = char.digitToInt()
            // Weighted sum: odd positions (from right) multiplied by 3, even by 1
            sum += if (index % 2 == 0) digit * 3 else digit
        }
        
        val calculatedCheck = (10 - (sum % 10)) % 10
        checkDigit == calculatedCheck
    } catch (_: Exception) {
        false
    }
}
