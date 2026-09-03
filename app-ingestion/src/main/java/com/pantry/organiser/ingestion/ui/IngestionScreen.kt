package com.pantry.organiser.ingestion.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.pantry.organiser.ingestion.FeedbackEffect
import com.pantry.organiser.ingestion.IngestionMode
import com.pantry.organiser.ingestion.IngestionViewModel

@Composable
fun IngestionScreen(
    viewModel: IngestionViewModel
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    var flashColor by remember { mutableStateOf(Color.Transparent) }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                viewModel.startRealtimeSync()
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                viewModel.stopRealtimeSync()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopRealtimeSync()
        }
    }
    
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            flashColor = when (effect) {
                FeedbackEffect.Success -> Color.Green.copy(alpha = 0.3f)
                FeedbackEffect.Unknown -> Color.Red.copy(alpha = 0.3f)
                FeedbackEffect.Duplicate -> Color.Transparent
            }
            kotlinx.coroutines.delay(300)
            flashColor = Color.Transparent
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            if (uiState.mode == IngestionMode.HOME) {
                Column(horizontalAlignment = Alignment.End) {
                    ExtendedFloatingActionButton(
                        onClick = { 
                            if (!hasCameraPermission) launcher.launch(Manifest.permission.CAMERA)
                            else viewModel.setMode(IngestionMode.CHECK) 
                        },
                        icon = { Icon(Icons.Default.Search, contentDescription = null) },
                        text = { Text("Check") },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    ExtendedFloatingActionButton(
                        onClick = { 
                            if (!hasCameraPermission) launcher.launch(Manifest.permission.CAMERA)
                            else viewModel.setMode(IngestionMode.INSERT) 
                        },
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        text = { Text("Insert") }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            // HOME VIEW: Grid + List
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(
                    "Pantry Ingestion",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Top: 4x3 Grid (Taking 25% space)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.25f)
                ) {
                    PantryShelfGrid(
                        selectedCell = null,
                        onCellClick = { _, _ -> },
                        pantryItems = uiState.items
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Current Inventory", style = MaterialTheme.typography.titleMedium)
                
                LazyColumn(modifier = Modifier.weight(1f)) {
                    if (uiState.items.isEmpty()) {
                        item {
                            Text("No items found", modifier = Modifier.padding(16.dp))
                        }
                    }
                    items(uiState.items, key = { it.id }) { item ->
                        ListItem(
                            headlineContent = { Text(item.name, fontWeight = FontWeight.Bold) },
                            supportingContent = { Text("${item.brand ?: ""} · Shelf ${item.shelfNumber}-${com.pantry.organiser.core.model.PantryConstants.getZoneLabel(item.zoneIndex)}") },
                            trailingContent = { Text("Stock: ${item.totalDisplayCount} ${item.getDisplayUnitLabel()}") }
                        )
                    }
                }
            }

            // SCANNER OVERLAY
            if (uiState.mode != IngestionMode.HOME) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    if (hasCameraPermission) {
                        AndroidView(
                            factory = { ctx ->
                                PreviewView(ctx).apply {
                                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                    viewModel.startScanner(lifecycleOwner, this.surfaceProvider)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Box(modifier = Modifier.fillMaxSize().background(flashColor))

                    // Scanner UI (Back button, Send button, Scanned list)
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { viewModel.setMode(IngestionMode.HOME) }) {
                                Text("Back", color = Color.White)
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                if (uiState.mode == IngestionMode.INSERT) "Continuous Scan" else "Single Scan",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        if (uiState.mode == IngestionMode.INSERT) {
                            Surface(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                                color = Color.Black.copy(alpha = 0.5f),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                LazyColumn(modifier = Modifier.padding(8.dp), reverseLayout = true) {
                                    items(uiState.scannedItems) { item ->
                                        Text("Scanned: ${item.barcode}", color = Color.White)
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Button(
                                onClick = { viewModel.sendToPantry() },
                                modifier = Modifier.fillMaxWidth().height(64.dp),
                                enabled = uiState.scannedItems.isNotEmpty() && !uiState.isSending
                            ) {
                                if (uiState.isSending) CircularProgressIndicator(color = Color.White)
                                else {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Send to Pantry (${uiState.scannedItems.size})")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
