package com.pantry.organiser.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

enum class DragHandle {
    NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageCropperDialog(
    imagePath: String,
    onDismiss: () -> Unit,
    onImageCropped: (String) -> Unit
) {
    val context = LocalContext.current
    val bitmap = remember(imagePath) {
        BitmapFactory.decodeFile(imagePath)?.let { fixRotation(imagePath, it) }
    }

    if (bitmap == null) {
        onDismiss()
        return
    }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var cropRect by remember { mutableStateOf(Rect.Zero) }
    var imageDisplayRect by remember { mutableStateOf(Rect.Zero) }
    var activeHandle by remember { mutableStateOf(DragHandle.NONE) }

    LaunchedEffect(containerSize) {
        if (containerSize.width > 0 && containerSize.height > 0 && cropRect == Rect.Zero) {
            val imgAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
            val containerAspect = containerSize.width.toFloat() / containerSize.height.toFloat()
            val displayW: Float
            val displayH: Float
            if (imgAspect > containerAspect) {
                displayW = containerSize.width.toFloat()
                displayH = displayW / imgAspect
            } else {
                displayH = containerSize.height.toFloat()
                displayW = displayH * imgAspect
            }
            val left = (containerSize.width - displayW) / 2
            val top = (containerSize.height - displayH) / 2
            imageDisplayRect = Rect(left, top, left + displayW, top + displayH)
            val cropSize = min(displayW, displayH) * 0.8f
            cropRect = Rect(center = imageDisplayRect.center, radius = cropSize / 2)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Black,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Crop Photo", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White) }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Black)
                )
            },
            bottomBar = {
                // FIXED HEIGHT BOTTOM AREA to strictly clear navigation buttons
                Surface(
                    color = Color.Black.copy(alpha = 0.9f),
                    modifier = Modifier.fillMaxWidth().height(200.dp) // MASSIVE FIXED HEIGHT
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
                        contentAlignment = Alignment.TopCenter // Put button at the TOP of this massive area
                    ) {
                        Button(
                            onClick = {
                                val finalBitmap = performActualCrop(bitmap, cropRect, imageDisplayRect)
                                if (finalBitmap != null) {
                                    val file = File(context.cacheDir, "pantry_crop_${System.currentTimeMillis()}.jpg")
                                    FileOutputStream(file).use { out -> finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
                                    onImageCropped(file.absolutePath)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Yellow, contentColor = Color.Black),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(64.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("SAVE PHOTO", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .onGloballyPositioned { containerSize = it.size }
            ) {
                if (containerSize.width > 0 && cropRect != Rect.Zero) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { touch ->
                                        val handleSize = 40.dp.toPx()
                                        activeHandle = when {
                                            touch.x in (cropRect.left - handleSize)..(cropRect.left + handleSize) && touch.y in (cropRect.top - handleSize)..(cropRect.top + handleSize) -> DragHandle.TOP_LEFT
                                            touch.x in (cropRect.right - handleSize)..(cropRect.right + handleSize) && touch.y in (cropRect.top - handleSize)..(cropRect.top + handleSize) -> DragHandle.TOP_RIGHT
                                            touch.x in (cropRect.left - handleSize)..(cropRect.left + handleSize) && touch.y in (cropRect.bottom - handleSize)..(cropRect.bottom + handleSize) -> DragHandle.BOTTOM_LEFT
                                            touch.x in (cropRect.right - handleSize)..(cropRect.right + handleSize) && touch.y in (cropRect.bottom - handleSize)..(cropRect.bottom + handleSize) -> DragHandle.BOTTOM_RIGHT
                                            cropRect.contains(touch) -> DragHandle.CENTER
                                            else -> DragHandle.NONE
                                        }
                                    },
                                    onDragEnd = { activeHandle = DragHandle.NONE },
                                    onDragCancel = { activeHandle = DragHandle.NONE },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        if (activeHandle == DragHandle.NONE) return@detectDragGestures
                                        var newRect = cropRect
                                        when (activeHandle) {
                                            DragHandle.TOP_LEFT -> newRect = Rect(left = (cropRect.left + dragAmount.x).coerceIn(imageDisplayRect.left, cropRect.right - 50f), top = (cropRect.top + dragAmount.y).coerceIn(imageDisplayRect.top, cropRect.bottom - 50f), right = cropRect.right, bottom = cropRect.bottom)
                                            DragHandle.TOP_RIGHT -> newRect = Rect(left = cropRect.left, top = (cropRect.top + dragAmount.y).coerceIn(imageDisplayRect.top, cropRect.bottom - 50f), right = (cropRect.right + dragAmount.x).coerceIn(cropRect.left + 50f, imageDisplayRect.right), bottom = cropRect.bottom)
                                            DragHandle.BOTTOM_LEFT -> newRect = Rect(left = (cropRect.left + dragAmount.x).coerceIn(imageDisplayRect.left, cropRect.right - 50f), top = cropRect.top, right = cropRect.right, bottom = (cropRect.bottom + dragAmount.y).coerceIn(cropRect.top + 50f, imageDisplayRect.bottom))
                                            DragHandle.BOTTOM_RIGHT -> newRect = Rect(left = cropRect.left, top = cropRect.top, right = (cropRect.right + dragAmount.x).coerceIn(cropRect.left + 50f, imageDisplayRect.right), bottom = (cropRect.bottom + dragAmount.y).coerceIn(cropRect.top + 50f, imageDisplayRect.bottom))
                                            DragHandle.CENTER -> {
                                                val dx = dragAmount.x; val dy = dragAmount.y
                                                val clampedDx = if (cropRect.left + dx < imageDisplayRect.left) imageDisplayRect.left - cropRect.left else if (cropRect.right + dx > imageDisplayRect.right) imageDisplayRect.right - cropRect.right else dx
                                                val clampedDy = if (cropRect.top + dy < imageDisplayRect.top) imageDisplayRect.top - cropRect.top else if (cropRect.bottom + dy > imageDisplayRect.bottom) imageDisplayRect.bottom - cropRect.bottom else dy
                                                newRect = cropRect.translate(clampedDx, clampedDy)
                                            }
                                            DragHandle.NONE -> {}
                                        }
                                        cropRect = newRect
                                    }
                                )
                            }
                    ) {
                        drawImage(image = bitmap.asImageBitmap(), dstOffset = IntOffset(imageDisplayRect.left.toInt(), imageDisplayRect.top.toInt()), dstSize = IntSize(imageDisplayRect.width.toInt(), imageDisplayRect.height.toInt()))
                        clipPath(Path().apply { addRect(cropRect) }, clipOp = ClipOp.Difference) { drawRect(Color.Black.copy(alpha = 0.6f)) }
                        drawRect(color = Color.Yellow, topLeft = cropRect.topLeft, size = cropRect.size, style = Stroke(width = 2.dp.toPx()))
                        val gridColor = Color.Yellow.copy(alpha = 0.5f)
                        drawLine(gridColor, Offset(cropRect.left + cropRect.width/3, cropRect.top), Offset(cropRect.left + cropRect.width/3, cropRect.bottom), 1.dp.toPx())
                        drawLine(gridColor, Offset(cropRect.left + 2*cropRect.width/3, cropRect.top), Offset(cropRect.left + 2*cropRect.width/3, cropRect.bottom), 1.dp.toPx())
                        drawLine(gridColor, Offset(cropRect.left, cropRect.top + cropRect.height/3), Offset(cropRect.right, cropRect.top + cropRect.height/3), 1.dp.toPx())
                        drawLine(gridColor, Offset(cropRect.left, cropRect.top + 2*cropRect.height/3), Offset(cropRect.right, cropRect.top + 2*cropRect.height/3), 1.dp.toPx())
                        val hLen = 24.dp.toPx(); val hThick = 4.dp.toPx()
                        drawLine(Color.Yellow, cropRect.topLeft, cropRect.topLeft + Offset(hLen, 0f), hThick)
                        drawLine(Color.Yellow, cropRect.topLeft, cropRect.topLeft + Offset(0f, hLen), hThick)
                        drawLine(Color.Yellow, cropRect.topRight, cropRect.topRight + Offset(-hLen, 0f), hThick)
                        drawLine(Color.Yellow, cropRect.topRight, cropRect.topRight + Offset(0f, hLen), hThick)
                        drawLine(Color.Yellow, cropRect.bottomLeft, cropRect.bottomLeft + Offset(hLen, 0f), hThick)
                        drawLine(Color.Yellow, cropRect.bottomLeft, cropRect.bottomLeft + Offset(0f, -hLen), hThick)
                        drawLine(Color.Yellow, cropRect.bottomRight, cropRect.bottomRight + Offset(-hLen, 0f), hThick)
                        drawLine(Color.Yellow, cropRect.bottomRight, cropRect.bottomRight + Offset(0f, -hLen), hThick)
                    }
                }
            }
        }
    }
}

private fun performActualCrop(bitmap: Bitmap, cropRect: Rect, imageDisplayRect: Rect): Bitmap? {
    val scaleX = bitmap.width.toFloat() / imageDisplayRect.width
    val scaleY = bitmap.height.toFloat() / imageDisplayRect.height
    val leftInImage = (cropRect.left - imageDisplayRect.left) * scaleX
    val topInImage = (cropRect.top - imageDisplayRect.top) * scaleY
    val widthInImage = cropRect.width * scaleX
    val heightInImage = cropRect.height * scaleY
    return try { Bitmap.createBitmap(bitmap, leftInImage.toInt().coerceIn(0, bitmap.width - 1), topInImage.toInt().coerceIn(0, bitmap.height - 1), widthInImage.toInt().coerceAtMost(bitmap.width - leftInImage.toInt()), heightInImage.toInt().coerceAtMost(bitmap.height - topInImage.toInt())) } catch (_: Exception) { null }
}

private fun fixRotation(path: String, bitmap: Bitmap): Bitmap {
    val exif = androidx.exifinterface.media.ExifInterface(path)
    val orientation = exif.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, androidx.exifinterface.media.ExifInterface.ORIENTATION_UNDEFINED)
    val matrix = Matrix()
    when (orientation) {
        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
    }
    return if (orientation != androidx.exifinterface.media.ExifInterface.ORIENTATION_UNDEFINED) { Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true) } else { bitmap }
}
