package com.pantry.organiser.ingestion.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

private enum class CropDragHandle {
    NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, TOP, BOTTOM, LEFT, RIGHT, CENTER
}

private fun detectDragHandle(
    touch: Offset,
    cropRect: Rect,
    cornerTouchRadius: Float,
    edgeTouchMargin: Float
): CropDragHandle {
    fun distance(p1: Offset, p2: Offset): Float {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return sqrt(dx * dx + dy * dy)
    }

    // 1. Check corner handles first
    if (distance(touch, cropRect.topLeft) <= cornerTouchRadius) return CropDragHandle.TOP_LEFT
    if (distance(touch, cropRect.topRight) <= cornerTouchRadius) return CropDragHandle.TOP_RIGHT
    if (distance(touch, cropRect.bottomLeft) <= cornerTouchRadius) return CropDragHandle.BOTTOM_LEFT
    if (distance(touch, cropRect.bottomRight) <= cornerTouchRadius) return CropDragHandle.BOTTOM_RIGHT

    // 2. Check edge handles
    if (touch.y in (cropRect.top - edgeTouchMargin)..(cropRect.top + edgeTouchMargin) &&
        touch.x in (cropRect.left - edgeTouchMargin)..(cropRect.right + edgeTouchMargin)
    ) {
        return CropDragHandle.TOP
    }
    if (touch.y in (cropRect.bottom - edgeTouchMargin)..(cropRect.bottom + edgeTouchMargin) &&
        touch.x in (cropRect.left - edgeTouchMargin)..(cropRect.right + edgeTouchMargin)
    ) {
        return CropDragHandle.BOTTOM
    }
    if (touch.x in (cropRect.left - edgeTouchMargin)..(cropRect.left + edgeTouchMargin) &&
        touch.y in (cropRect.top - edgeTouchMargin)..(cropRect.bottom + edgeTouchMargin)
    ) {
        return CropDragHandle.LEFT
    }
    if (touch.x in (cropRect.right - edgeTouchMargin)..(cropRect.right + edgeTouchMargin) &&
        touch.y in (cropRect.top - edgeTouchMargin)..(cropRect.bottom + edgeTouchMargin)
    ) {
        return CropDragHandle.RIGHT
    }

    // 3. Center inside crop rect
    if (cropRect.contains(touch)) {
        return CropDragHandle.CENTER
    }

    return CropDragHandle.NONE
}

@Composable
fun ImageCropScreen(
    bitmap: Bitmap,
    itemName: String,
    onCropSaved: (ByteArray, Bitmap) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var imageDisplayRect by remember { mutableStateOf(Rect.Zero) }
    var cropRect by remember { mutableStateOf(Rect.Zero) }
    var activeHandle by remember { mutableStateOf(CropDragHandle.NONE) }

    val density = LocalDensity.current
    val cornerTouchRadius = remember(density) { with(density) { 36.dp.toPx() } }
    val edgeTouchMargin = remember(density) { with(density) { 24.dp.toPx() } }
    val minCropSize = remember(density) { with(density) { 60.dp.toPx() } }

    LaunchedEffect(containerSize, bitmap) {
        if (containerSize.width > 0 && containerSize.height > 0) {
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
            val newDisplayRect = Rect(left, top, left + displayW, top + displayH)
            imageDisplayRect = newDisplayRect

            // Initial crop box: 85% of displayed image size
            val cropW = newDisplayRect.width * 0.85f
            val cropH = newDisplayRect.height * 0.85f
            val cropLeft = newDisplayRect.left + (newDisplayRect.width - cropW) / 2f
            val cropTop = newDisplayRect.top + (newDisplayRect.height - cropH) / 2f
            cropRect = Rect(cropLeft, cropTop, cropLeft + cropW, cropTop + cropH)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Crop Photo: $itemName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Drag corners or edges to adjust frame, drag center to move",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }

            // Interactive Crop Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onGloballyPositioned { containerSize = it.size }
            ) {
                if (containerSize.width > 0 && containerSize.height > 0 && cropRect != Rect.Zero) {
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
                                    onDragEnd = { activeHandle = CropDragHandle.NONE },
                                    onDragCancel = { activeHandle = CropDragHandle.NONE },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        if (activeHandle == CropDragHandle.NONE) return@detectDragGestures

                                        var left = cropRect.left
                                        var top = cropRect.top
                                        var right = cropRect.right
                                        var bottom = cropRect.bottom

                                        when (activeHandle) {
                                            CropDragHandle.TOP_LEFT -> {
                                                left = (left + dragAmount.x).coerceIn(imageDisplayRect.left, right - minCropSize)
                                                top = (top + dragAmount.y).coerceIn(imageDisplayRect.top, bottom - minCropSize)
                                            }
                                            CropDragHandle.TOP_RIGHT -> {
                                                right = (right + dragAmount.x).coerceIn(left + minCropSize, imageDisplayRect.right)
                                                top = (top + dragAmount.y).coerceIn(imageDisplayRect.top, bottom - minCropSize)
                                            }
                                            CropDragHandle.BOTTOM_LEFT -> {
                                                left = (left + dragAmount.x).coerceIn(imageDisplayRect.left, right - minCropSize)
                                                bottom = (bottom + dragAmount.y).coerceIn(top + minCropSize, imageDisplayRect.bottom)
                                            }
                                            CropDragHandle.BOTTOM_RIGHT -> {
                                                right = (right + dragAmount.x).coerceIn(left + minCropSize, imageDisplayRect.right)
                                                bottom = (bottom + dragAmount.y).coerceIn(top + minCropSize, imageDisplayRect.bottom)
                                            }
                                            CropDragHandle.TOP -> {
                                                top = (top + dragAmount.y).coerceIn(imageDisplayRect.top, bottom - minCropSize)
                                            }
                                            CropDragHandle.BOTTOM -> {
                                                bottom = (bottom + dragAmount.y).coerceIn(top + minCropSize, imageDisplayRect.bottom)
                                            }
                                            CropDragHandle.LEFT -> {
                                                left = (left + dragAmount.x).coerceIn(imageDisplayRect.left, right - minCropSize)
                                            }
                                            CropDragHandle.RIGHT -> {
                                                right = (right + dragAmount.x).coerceIn(left + minCropSize, imageDisplayRect.right)
                                            }
                                            CropDragHandle.CENTER -> {
                                                val dx = dragAmount.x
                                                val dy = dragAmount.y
                                                val clampedDx = if (left + dx < imageDisplayRect.left) imageDisplayRect.left - left
                                                    else if (right + dx > imageDisplayRect.right) imageDisplayRect.right - right
                                                    else dx
                                                val clampedDy = if (top + dy < imageDisplayRect.top) imageDisplayRect.top - top
                                                    else if (bottom + dy > imageDisplayRect.bottom) imageDisplayRect.bottom - bottom
                                                    else dy
                                                left += clampedDx
                                                right += clampedDx
                                                top += clampedDy
                                                bottom += clampedDy
                                            }
                                            CropDragHandle.NONE -> {}
                                        }
                                        cropRect = Rect(left, top, right, bottom)
                                    }
                                )
                            }
                    ) {
                        // 1. Draw image bitmap inside display bounds
                        drawImage(
                            image = bitmap.asImageBitmap(),
                            dstOffset = IntOffset(imageDisplayRect.left.toInt(), imageDisplayRect.top.toInt()),
                            dstSize = IntSize(imageDisplayRect.width.toInt(), imageDisplayRect.height.toInt())
                        )

                        // 2. Dim background outside cropRect
                        clipPath(Path().apply { addRect(cropRect) }, clipOp = ClipOp.Difference) {
                            drawRect(Color.Black.copy(alpha = 0.65f))
                        }

                        // 3. Crop rect border
                        drawRect(
                            color = Color.White,
                            topLeft = cropRect.topLeft,
                            size = cropRect.size,
                            style = Stroke(width = 2.dp.toPx())
                        )

                        // 4. Rule of thirds grid
                        val gridColor = Color.White.copy(alpha = 0.35f)
                        val gridStroke = 1.dp.toPx()

                        val thirdW = cropRect.width / 3f
                        val thirdH = cropRect.height / 3f

                        drawLine(gridColor, Offset(cropRect.left + thirdW, cropRect.top), Offset(cropRect.left + thirdW, cropRect.bottom), gridStroke)
                        drawLine(gridColor, Offset(cropRect.left + 2 * thirdW, cropRect.top), Offset(cropRect.left + 2 * thirdW, cropRect.bottom), gridStroke)
                        drawLine(gridColor, Offset(cropRect.left, cropRect.top + thirdH), Offset(cropRect.right, cropRect.top + thirdH), gridStroke)
                        drawLine(gridColor, Offset(cropRect.left, cropRect.top + 2 * thirdH), Offset(cropRect.right, cropRect.top + 2 * thirdH), gridStroke)

                        // 5. Corner Handles (thick L-shapes)
                        val hLen = 22.dp.toPx()
                        val hThick = 4.dp.toPx()
                        val accentColor = Color.White

                        // Top-Left
                        drawLine(accentColor, cropRect.topLeft, cropRect.topLeft + Offset(hLen, 0f), hThick)
                        drawLine(accentColor, cropRect.topLeft, cropRect.topLeft + Offset(0f, hLen), hThick)

                        // Top-Right
                        drawLine(accentColor, cropRect.topRight, cropRect.topRight + Offset(-hLen, 0f), hThick)
                        drawLine(accentColor, cropRect.topRight, cropRect.topRight + Offset(0f, hLen), hThick)

                        // Bottom-Left
                        drawLine(accentColor, cropRect.bottomLeft, cropRect.bottomLeft + Offset(hLen, 0f), hThick)
                        drawLine(accentColor, cropRect.bottomLeft, cropRect.bottomLeft + Offset(0f, -hLen), hThick)

                        // Bottom-Right
                        drawLine(accentColor, cropRect.bottomRight, cropRect.bottomRight + Offset(-hLen, 0f), hThick)
                        drawLine(accentColor, cropRect.bottomRight, cropRect.bottomRight + Offset(0f, -hLen), hThick)

                        // 6. Edge Handles (centered pill indicators)
                        val edgeBarLen = 28.dp.toPx()
                        val edgeBarThick = 4.dp.toPx()

                        // Top edge
                        val topMid = Offset(cropRect.left + cropRect.width / 2f, cropRect.top)
                        drawLine(accentColor, topMid - Offset(edgeBarLen / 2f, 0f), topMid + Offset(edgeBarLen / 2f, 0f), edgeBarThick)

                        // Bottom edge
                        val bottomMid = Offset(cropRect.left + cropRect.width / 2f, cropRect.bottom)
                        drawLine(accentColor, bottomMid - Offset(edgeBarLen / 2f, 0f), bottomMid + Offset(edgeBarLen / 2f, 0f), edgeBarThick)

                        // Left edge
                        val leftMid = Offset(cropRect.left, cropRect.top + cropRect.height / 2f)
                        drawLine(accentColor, leftMid - Offset(0f, edgeBarLen / 2f), leftMid + Offset(0f, edgeBarLen / 2f), edgeBarThick)

                        // Right edge
                        val rightMid = Offset(cropRect.right, cropRect.top + cropRect.height / 2f)
                        drawLine(accentColor, rightMid - Offset(0f, edgeBarLen / 2f), rightMid + Offset(0f, edgeBarLen / 2f), edgeBarThick)
                    }
                }
            }

            // Bottom Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedIconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = Color.White)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                }

                Button(
                    onClick = {
                        if (cropRect != Rect.Zero && imageDisplayRect != Rect.Zero) {
                            val croppedBitmap = cropBitmap(
                                source = bitmap,
                                cropRect = cropRect,
                                imageDisplayRect = imageDisplayRect
                            )
                            val outputStream = ByteArrayOutputStream()
                            croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                            val bytes = outputStream.toByteArray()
                            onCropSaved(bytes, croppedBitmap)
                        }
                    },
                    modifier = Modifier.height(56.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save & Apply Photo", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun cropBitmap(
    source: Bitmap,
    cropRect: Rect,
    imageDisplayRect: Rect
): Bitmap {
    val srcWidth = source.width.toFloat()
    val srcHeight = source.height.toFloat()

    val scaleX = srcWidth / imageDisplayRect.width
    val scaleY = srcHeight / imageDisplayRect.height

    val srcCropLeft = ((cropRect.left - imageDisplayRect.left) * scaleX).coerceIn(0f, srcWidth - 1f)
    val srcCropTop = ((cropRect.top - imageDisplayRect.top) * scaleY).coerceIn(0f, srcHeight - 1f)
    val srcCropRight = ((cropRect.right - imageDisplayRect.left) * scaleX).coerceIn(srcCropLeft + 1f, srcWidth)
    val srcCropBottom = ((cropRect.bottom - imageDisplayRect.top) * scaleY).coerceIn(srcCropTop + 1f, srcHeight)

    val cropW = (srcCropRight - srcCropLeft).toInt().coerceAtLeast(1)
    val cropH = (srcCropBottom - srcCropTop).toInt().coerceAtLeast(1)

    val cropped = Bitmap.createBitmap(
        source,
        srcCropLeft.toInt(),
        srcCropTop.toInt(),
        cropW,
        cropH
    )

    // Limit maximum dimension to 1200px while maintaining the exact cropped aspect ratio
    val maxDim = maxOf(cropped.width, cropped.height)
    return if (maxDim > 1200) {
        val scale = 1200f / maxDim
        val targetW = (cropped.width * scale).toInt().coerceAtLeast(1)
        val targetH = (cropped.height * scale).toInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(cropped, targetW, targetH, true)
    } else {
        cropped
    }
}
