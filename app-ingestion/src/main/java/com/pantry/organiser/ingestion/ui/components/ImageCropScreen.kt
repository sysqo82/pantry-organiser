package com.pantry.organiser.ingestion.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream

@Composable
fun ImageCropScreen(
    bitmap: Bitmap,
    itemName: String,
    onCropSaved: (ByteArray, Bitmap) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val screenWidthPx = constraints.maxWidth.toFloat()
        val screenHeightPx = constraints.maxHeight.toFloat()

        val cropSizePx = minOf(screenWidthPx, screenHeightPx) * 0.85f
        val cropRectPx = Rect(
            left = (screenWidthPx - cropSizePx) / 2f,
            top = (screenHeightPx - cropSizePx) / 2f,
            right = (screenWidthPx + cropSizePx) / 2f,
            bottom = (screenHeightPx + cropSizePx) / 2f
        )

        // Interactive Canvas for image & crop mask
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 5f)
                        offset += pan
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val imageWidth = bitmap.width.toFloat()
                val imageHeight = bitmap.height.toFloat()

                val baseScale = minOf(screenWidthPx / imageWidth, screenHeightPx / imageHeight)
                val drawWidth = imageWidth * baseScale * scale
                val drawHeight = imageHeight * baseScale * scale

                val centerX = screenWidthPx / 2f + offset.x
                val centerY = screenHeightPx / 2f + offset.y

                val drawRect = Rect(
                    left = centerX - drawWidth / 2f,
                    top = centerY - drawHeight / 2f,
                    right = centerX + drawWidth / 2f,
                    bottom = centerY + drawHeight / 2f
                )

                drawImage(
                    image = bitmap.asImageBitmap(),
                    dstSize = IntSize(drawWidth.toInt(), drawHeight.toInt()),
                    dstOffset = IntOffset(drawRect.left.toInt(), drawRect.top.toInt())
                )

                // Dark semi-transparent scrim with 1:1 clear square cutout
                val fullPath = Path().apply { addRect(Rect(0f, 0f, screenWidthPx, screenHeightPx)) }
                val cropPath = Path().apply { addRoundRect(RoundRect(cropRectPx, CornerRadius(16f, 16f))) }
                val overlayPath = Path.combine(PathOperation.Difference, fullPath, cropPath)

                drawPath(overlayPath, Color.Black.copy(alpha = 0.7f))
                drawRoundRect(
                    color = Color.White,
                    topLeft = cropRectPx.topLeft,
                    size = Size(cropRectPx.width, cropRectPx.height),
                    cornerRadius = CornerRadius(16f, 16f),
                    style = Stroke(width = 3f)
                )
            }
        }

        // Header
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Crop Photo: $itemName",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Pinch to zoom, drag to position product in square frame",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }

        // Bottom Action Controls
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(24.dp)
                .fillMaxWidth(),
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
                    val croppedBitmap = cropBitmap(
                        source = bitmap,
                        scale = scale,
                        offset = offset,
                        screenWidthPx = screenWidthPx,
                        screenHeightPx = screenHeightPx,
                        cropRectPx = cropRectPx
                    )
                    val outputStream = ByteArrayOutputStream()
                    croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                    val bytes = outputStream.toByteArray()
                    onCropSaved(bytes, croppedBitmap)
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

private fun cropBitmap(
    source: Bitmap,
    scale: Float,
    offset: Offset,
    screenWidthPx: Float,
    screenHeightPx: Float,
    cropRectPx: Rect
): Bitmap {
    val srcWidth = source.width.toFloat()
    val srcHeight = source.height.toFloat()

    val baseScale = minOf(screenWidthPx / srcWidth, screenHeightPx / srcHeight)
    val totalScale = baseScale * scale

    val drawnLeft = screenWidthPx / 2f + offset.x - (srcWidth * totalScale) / 2f
    val drawnTop = screenHeightPx / 2f + offset.y - (srcHeight * totalScale) / 2f

    val srcCropLeft = ((cropRectPx.left - drawnLeft) / totalScale).coerceIn(0f, srcWidth - 1f)
    val srcCropTop = ((cropRectPx.top - drawnTop) / totalScale).coerceIn(0f, srcHeight - 1f)
    val srcCropRight = ((cropRectPx.right - drawnLeft) / totalScale).coerceIn(srcCropLeft + 1f, srcWidth)
    val srcCropBottom = ((cropRectPx.bottom - drawnTop) / totalScale).coerceIn(srcCropTop + 1f, srcHeight)

    val cropW = (srcCropRight - srcCropLeft).toInt().coerceAtLeast(1)
    val cropH = (srcCropBottom - srcCropTop).toInt().coerceAtLeast(1)

    val cropped = Bitmap.createBitmap(
        source,
        srcCropLeft.toInt(),
        srcCropTop.toInt(),
        cropW,
        cropH
    )

    return Bitmap.createScaledBitmap(cropped, 500, 500, true)
}
