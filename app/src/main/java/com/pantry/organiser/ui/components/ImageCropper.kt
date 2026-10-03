package com.pantry.organiser.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sqrt

enum class DragHandle {
    NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, TOP, BOTTOM, LEFT, RIGHT, CENTER
}

private fun detectDragHandle(
    touch: Offset,
    cropRect: Rect,
    cornerTouchRadius: Float,
    edgeTouchMargin: Float
): DragHandle {
    fun distance(p1: Offset, p2: Offset): Float {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return sqrt(dx * dx + dy * dy)
    }

    if (distance(touch, cropRect.topLeft) <= cornerTouchRadius) return DragHandle.TOP_LEFT
    if (distance(touch, cropRect.topRight) <= cornerTouchRadius) return DragHandle.TOP_RIGHT
    if (distance(touch, cropRect.bottomLeft) <= cornerTouchRadius) return DragHandle.BOTTOM_LEFT
    if (distance(touch, cropRect.bottomRight) <= cornerTouchRadius) return DragHandle.BOTTOM_RIGHT

    if (touch.y in (cropRect.top - edgeTouchMargin)..(cropRect.top + edgeTouchMargin) &&
        touch.x in (cropRect.left - edgeTouchMargin)..(cropRect.right + edgeTouchMargin)
    ) {
        return DragHandle.TOP
    }
    if (touch.y in (cropRect.bottom - edgeTouchMargin)..(cropRect.bottom + edgeTouchMargin) &&
        touch.x in (cropRect.left - edgeTouchMargin)..(cropRect.right + edgeTouchMargin)
    ) {
        return DragHandle.BOTTOM
    }
    if (touch.x in (cropRect.left - edgeTouchMargin)..(cropRect.left + edgeTouchMargin) &&
        touch.y in (cropRect.top - edgeTouchMargin)..(cropRect.bottom + edgeTouchMargin)
    ) {
        return DragHandle.LEFT
    }
    if (touch.x in (cropRect.right - edgeTouchMargin)..(cropRect.right + edgeTouchMargin) &&
        touch.y in (cropRect.top - edgeTouchMargin)..(cropRect.bottom + edgeTouchMargin)
    ) {
        return DragHandle.RIGHT
    }

    if (cropRect.contains(touch)) {
        return DragHandle.CENTER
    }

    return DragHandle.NONE
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

    val density = LocalDensity.current
    val cornerTouchRadius = remember(density) { with(density) { 36.dp.toPx() } }
    val edgeTouchMargin = remember(density) { with(density) { 24.dp.toPx() } }
    val minCropSize = remember(density) { with(density) { 60.dp.toPx() } }

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
            val left = (containerSize.width - displayW) / 2f
            val top = (containerSize.height - displayH) / 2f
            imageDisplayRect = Rect(left, top, left + displayW, top + displayH)
            val cropW = displayW * 0.85f
            val cropH = displayH * 0.85f
            cropRect = Rect(
                left = imageDisplayRect.left + (displayW - cropW) / 2f,
                top = imageDisplayRect.top + (displayH - cropH) / 2f,
                right = imageDisplayRect.left + (displayW + cropW) / 2f,
                bottom = imageDisplayRect.top + (displayH + cropH) / 2f
            )
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
                Surface(
                    color = Color.Black.copy(alpha = 0.9f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        contentAlignment = Alignment.Center
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
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(28.dp))
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
                            .pointerInput(imageDisplayRect, cropRect) {
                                detectDragGestures(
                                    onDragStart = { touch ->
                                        activeHandle = detectDragHandle(
                                            touch = touch,
                                            cropRect = cropRect,
                                            cornerTouchRadius = cornerTouchRadius,
                                            edgeTouchMargin = edgeTouchMargin
                                        )
                                    },
                                    onDragEnd = { activeHandle = DragHandle.NONE },
                                    onDragCancel = { activeHandle = DragHandle.NONE },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        if (activeHandle == DragHandle.NONE) return@detectDragGestures
                                        var left = cropRect.left
                                        var top = cropRect.top
                                        var right = cropRect.right
                                        var bottom = cropRect.bottom

                                        when (activeHandle) {
                                            DragHandle.TOP_LEFT -> {
                                                left = (left + dragAmount.x).coerceIn(imageDisplayRect.left, right - minCropSize)
                                                top = (top + dragAmount.y).coerceIn(imageDisplayRect.top, bottom - minCropSize)
                                            }
                                            DragHandle.TOP_RIGHT -> {
                                                right = (right + dragAmount.x).coerceIn(left + minCropSize, imageDisplayRect.right)
                                                top = (top + dragAmount.y).coerceIn(imageDisplayRect.top, bottom - minCropSize)
                                            }
                                            DragHandle.BOTTOM_LEFT -> {
                                                left = (left + dragAmount.x).coerceIn(imageDisplayRect.left, right - minCropSize)
                                                bottom = (bottom + dragAmount.y).coerceIn(top + minCropSize, imageDisplayRect.bottom)
                                            }
                                            DragHandle.BOTTOM_RIGHT -> {
                                                right = (right + dragAmount.x).coerceIn(left + minCropSize, imageDisplayRect.right)
                                                bottom = (bottom + dragAmount.y).coerceIn(top + minCropSize, imageDisplayRect.bottom)
                                            }
                                            DragHandle.TOP -> {
                                                top = (top + dragAmount.y).coerceIn(imageDisplayRect.top, bottom - minCropSize)
                                            }
                                            DragHandle.BOTTOM -> {
                                                bottom = (bottom + dragAmount.y).coerceIn(top + minCropSize, imageDisplayRect.bottom)
                                            }
                                            DragHandle.LEFT -> {
                                                left = (left + dragAmount.x).coerceIn(imageDisplayRect.left, right - minCropSize)
                                            }
                                            DragHandle.RIGHT -> {
                                                right = (right + dragAmount.x).coerceIn(left + minCropSize, imageDisplayRect.right)
                                            }
                                            DragHandle.CENTER -> {
                                                val dx = dragAmount.x; val dy = dragAmount.y
                                                val clampedDx = if (left + dx < imageDisplayRect.left) imageDisplayRect.left - left
                                                    else if (right + dx > imageDisplayRect.right) imageDisplayRect.right - right
                                                    else dx
                                                val clampedDy = if (top + dy < imageDisplayRect.top) imageDisplayRect.top - top
                                                    else if (bottom + dy > imageDisplayRect.bottom) imageDisplayRect.bottom - bottom
                                                    else dy
                                                left += clampedDx; right += clampedDx
                                                top += clampedDy; bottom += clampedDy
                                            }
                                            DragHandle.NONE -> {}
                                        }
                                        cropRect = Rect(left, top, right, bottom)
                                    }
                                )
                            }
                    ) {
                        drawImage(image = bitmap.asImageBitmap(), dstOffset = IntOffset(imageDisplayRect.left.toInt(), imageDisplayRect.top.toInt()), dstSize = IntSize(imageDisplayRect.width.toInt(), imageDisplayRect.height.toInt()))
                        clipPath(Path().apply { addRect(cropRect) }, clipOp = ClipOp.Difference) { drawRect(Color.Black.copy(alpha = 0.65f)) }
                        drawRect(color = Color.Yellow, topLeft = cropRect.topLeft, size = cropRect.size, style = Stroke(width = 2.dp.toPx()))

                        val gridColor = Color.Yellow.copy(alpha = 0.4f)
                        val thirdW = cropRect.width / 3f
                        val thirdH = cropRect.height / 3f
                        drawLine(gridColor, Offset(cropRect.left + thirdW, cropRect.top), Offset(cropRect.left + thirdW, cropRect.bottom), 1.dp.toPx())
                        drawLine(gridColor, Offset(cropRect.left + 2 * thirdW, cropRect.top), Offset(cropRect.left + 2 * thirdW, cropRect.bottom), 1.dp.toPx())
                        drawLine(gridColor, Offset(cropRect.left, cropRect.top + thirdH), Offset(cropRect.right, cropRect.top + thirdH), 1.dp.toPx())
                        drawLine(gridColor, Offset(cropRect.left, cropRect.top + 2 * thirdH), Offset(cropRect.right, cropRect.top + 2 * thirdH), 1.dp.toPx())

                        val hLen = 22.dp.toPx(); val hThick = 4.dp.toPx()
                        drawLine(Color.Yellow, cropRect.topLeft, cropRect.topLeft + Offset(hLen, 0f), hThick)
                        drawLine(Color.Yellow, cropRect.topLeft, cropRect.topLeft + Offset(0f, hLen), hThick)
                        drawLine(Color.Yellow, cropRect.topRight, cropRect.topRight + Offset(-hLen, 0f), hThick)
                        drawLine(Color.Yellow, cropRect.topRight, cropRect.topRight + Offset(0f, hLen), hThick)
                        drawLine(Color.Yellow, cropRect.bottomLeft, cropRect.bottomLeft + Offset(hLen, 0f), hThick)
                        drawLine(Color.Yellow, cropRect.bottomLeft, cropRect.bottomLeft + Offset(0f, -hLen), hThick)
                        drawLine(Color.Yellow, cropRect.bottomRight, cropRect.bottomRight + Offset(-hLen, 0f), hThick)
                        drawLine(Color.Yellow, cropRect.bottomRight, cropRect.bottomRight + Offset(0f, -hLen), hThick)

                        val edgeBarLen = 28.dp.toPx(); val edgeBarThick = 4.dp.toPx()
                        val topMid = Offset(cropRect.left + cropRect.width / 2f, cropRect.top)
                        drawLine(Color.Yellow, topMid - Offset(edgeBarLen / 2f, 0f), topMid + Offset(edgeBarLen / 2f, 0f), edgeBarThick)
                        val bottomMid = Offset(cropRect.left + cropRect.width / 2f, cropRect.bottom)
                        drawLine(Color.Yellow, bottomMid - Offset(edgeBarLen / 2f, 0f), bottomMid + Offset(edgeBarLen / 2f, 0f), edgeBarThick)
                        val leftMid = Offset(cropRect.left, cropRect.top + cropRect.height / 2f)
                        drawLine(Color.Yellow, leftMid - Offset(0f, edgeBarLen / 2f), leftMid + Offset(0f, edgeBarLen / 2f), edgeBarThick)
                        val rightMid = Offset(cropRect.right, cropRect.top + cropRect.height / 2f)
                        drawLine(Color.Yellow, rightMid - Offset(0f, edgeBarLen / 2f), rightMid + Offset(0f, edgeBarLen / 2f), edgeBarThick)
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
    val exif = ExifInterface(path)
    val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
    }
    return if (orientation != ExifInterface.ORIENTATION_UNDEFINED) { Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true) } else { bitmap }
}
