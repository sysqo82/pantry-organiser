package com.pantry.organiser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext

@Composable
fun ProductThumbnail(
    imageUrl: String?,
    localImageUri: String?,
    itemName: String,
    modifier: Modifier = Modifier,
    thumbnailSize: Dp? = 64.dp,
    updatedAt: Long = 0L
) {
    val context = LocalContext.current
    
    // Normalize inputs
    val validLocalUri = localImageUri?.takeIf { it.isNotBlank() && it != "N/A" }
    val validRemoteUrl = imageUrl?.takeIf { it.isNotBlank() && it != "N/A" }

    // Track which source we are currently trying to load. 
    var imageSource by remember(validLocalUri, validRemoteUrl) { 
        mutableStateOf(validLocalUri ?: validRemoteUrl) 
    }
    
    // Keep track of failed URIs in this session to avoid retrying them
    val failedUris = remember { mutableSetOf<String>() }

    // Effect to reset imageSource if inputs change (e.g. after sync updates the record)
    LaunchedEffect(validLocalUri, validRemoteUrl, updatedAt) {
        val bestSource = validLocalUri ?: validRemoteUrl
        
        // If updatedAt changed, we should allow retrying previously failed URIs
        // because a new photo might have been uploaded to the same URL/path.
        if (updatedAt != 0L) {
            failedUris.clear()
        }

        // Even if validRemoteUrl string is technically the same, we want to re-evaluate 
        // because it now contains the version parameter from toLocal()
        if (imageSource != bestSource && !failedUris.contains(bestSource)) {
            android.util.Log.d("ProductThumbnail", "[$itemName] Update detected (v=$updatedAt). New source: $bestSource")
            imageSource = bestSource
        }
    }

    if (imageSource != null) {
        LaunchedEffect(imageSource, updatedAt) {
            val type = if (imageSource == validLocalUri) "LOCAL" else "REMOTE"
            android.util.Log.d("ProductThumbnail", "[$itemName] Attempting load ($type): $imageSource")
        }
    }

    val model = remember(imageSource) {
        ImageRequest.Builder(context)
            .data(imageSource)
            .crossfade(true)
            .build()
    }
    
    val sizeModifier = if (thumbnailSize != null) Modifier.size(thumbnailSize) else Modifier

    Surface(
        modifier = modifier
            .then(sizeModifier)
            .clip(RoundedCornerShape(8.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        if (imageSource != null) {
            AsyncImage(
                model = model,
                contentDescription = itemName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = { state ->
                    val error = state.result.throwable
                    android.util.Log.e("ProductThumbnail", "[$itemName] Load failed for $imageSource: ${error.message}")
                    
                    imageSource?.let { failedUris.add(it) }

                    // If local image fails (e.g. file not found on synced device)
                    // and we have a remote fallback URL, switch to it.
                    if (imageSource == validLocalUri) {
                        if (validRemoteUrl != null && !failedUris.contains(validRemoteUrl)) {
                            android.util.Log.d("ProductThumbnail", "[$itemName] Falling back to remote URL: $validRemoteUrl")
                            imageSource = validRemoteUrl
                        } else {
                            android.util.Log.d("ProductThumbnail", "[$itemName] No remote fallback available (Remote URL: $validRemoteUrl, Failed: ${failedUris.contains(validRemoteUrl)})")
                            imageSource = null
                        }
                    } else {
                        // Both failed or no fallback, clear source to show placeholder
                        android.util.Log.d("ProductThumbnail", "[$itemName] Both sources failed or no fallback available.")
                        imageSource = null
                    }
                }
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Fastfood,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Text(
                    text = itemName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
