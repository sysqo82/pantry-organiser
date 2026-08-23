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
import androidx.compose.material.icons.filled.Close
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
    onCancelConsume: () -> Unit,
    onSave: (String, String, String, Int, Int, String?, String?, TrackingType) -> Unit,
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val haptic = LocalHapticFeedback.current

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
            .pointerInput(Unit) {
                detectTapGestures { timerKey++ } 
            }
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
            // Overlay for Staples Consumption
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
                                        localImageUri = item.localImageUri,
                                        itemName = item.name,
                                        updatedAt = item.updatedAt,
                                        thumbnailSize = 64.dp
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Column {
                                        Text(item.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
                                        Text("Current: ${item.activeFill.label}", color = Color.White.copy(alpha = 0.7f))
                                    }
                                }
                            }

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
                                        localImageUri = item.localImageUri,
                                        itemName = item.name,
                                        updatedAt = item.updatedAt,
                                        thumbnailSize = 64.dp
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Column {
                                        Text(item.name, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                                        Text(item.brand ?: "", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
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
            .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_ALL_FORMATS)
            .build()
        BarcodeScanning.getClient(options)
    }

    var lastScannedBarcode by remember { mutableStateOf<String?>(null) }
    var lastScanTime by remember { mutableLongStateOf(0L) }
    
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
                                        if (barcode != lastScannedBarcode || currentTime - lastScanTime > 3000) {
                                            lastScannedBarcode = barcode
                                            lastScanTime = currentTime
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
                    barcode.rawValue?.let { 
                        onBarcodeScanned(it) 
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
