package com.pantry.organiser.dashboard.ui.components

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun ProductThumbnail(
    imageUrl: String?,
    apiImageUrl: String? = null,
    localImageUrl: String? = null,
    localImageUri: String? = null,
    itemName: String,
    modifier: Modifier = Modifier,
    thumbnailSize: Dp? = 64.dp,
    updatedAt: Long = 0L,
    contentScale: ContentScale = ContentScale.Fit
) {
    val context = LocalContext.current

    val validLocalUri = localImageUri?.takeIf { it.isNotBlank() && it != "N/A" }
    val validLocalUrl = localImageUrl?.takeIf { it.isNotBlank() && it != "N/A" }
    val validRemoteUrl = imageUrl?.takeIf { it.isNotBlank() && it != "N/A" && !it.contains("/api/files/") }
    val validApiUrl = apiImageUrl?.takeIf { it.isNotBlank() && it != "N/A" && !it.contains("/api/files/") }

    var imageSource by remember(validLocalUri, validLocalUrl, validRemoteUrl, validApiUrl) {
        mutableStateOf(validLocalUri ?: validLocalUrl ?: validRemoteUrl ?: validApiUrl)
    }

    val failedUris = remember { mutableSetOf<String>() }

    LaunchedEffect(validLocalUri, validLocalUrl, validRemoteUrl, validApiUrl, updatedAt) {
        val bestSource = validLocalUri ?: validLocalUrl ?: validRemoteUrl ?: validApiUrl

        if (updatedAt != 0L) {
            failedUris.clear()
        }

        if (imageSource != bestSource && !failedUris.contains(bestSource)) {
            imageSource = bestSource
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
                contentScale = contentScale,
                onError = {
                    imageSource?.let { failedUris.add(it) }

                    when (imageSource) {
                        validLocalUri -> {
                            val next = validLocalUrl ?: validRemoteUrl ?: validApiUrl
                            imageSource = if (next != null && !failedUris.contains(next)) next else null
                        }
                        validLocalUrl -> {
                            val next = validRemoteUrl ?: validApiUrl
                            imageSource = if (next != null && !failedUris.contains(next)) next else null
                        }
                        validRemoteUrl -> {
                            imageSource = if (validApiUrl != null && !failedUris.contains(validApiUrl)) validApiUrl else null
                        }
                        else -> {
                            imageSource = null
                        }
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
