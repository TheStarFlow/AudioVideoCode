package com.zzs.media.base

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

@Composable
fun BasicCompose(
    dpWidth: Float = 1280.0f,
    dpHeight: Float = 720.0f,
    useWidth: Boolean = true,
    content: @Composable () -> Unit
) {
    val displayMetrics = LocalContext.current.resources.displayMetrics
    val widthPixel = displayMetrics.widthPixels
    val heightPixel = displayMetrics.heightPixels
    val targetDensityWidth = widthPixel / dpWidth
    val targetDensityHeight = heightPixel / dpHeight
    MaterialTheme {
        CompositionLocalProvider(
            LocalDensity provides Density(
                density = if (useWidth) targetDensityWidth else targetDensityHeight
            ),
        ) {
            content()
        }
    }

}