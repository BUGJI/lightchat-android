package com.benton.lightchat.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun PredictiveBackOverlay(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable (progress: Float, direction: Float) -> Unit,
) {
    val progress = remember { Animatable(0f) }
    var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    val scope = rememberCoroutineScope()

    PredictiveBackHandler(enabled = enabled) { events ->
        try {
            events.collect { event ->
                swipeEdge = event.swipeEdge
                progress.snapTo(event.progress.coerceIn(0f, 1f))
            }
            progress.animateTo(1f, tween(120))
            onDismiss()
        } catch (cancellation: CancellationException) {
            scope.launch {
                progress.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                )
            }
            throw cancellation
        }
    }

    val slidesRight = swipeEdge != BackEventCompat.EDGE_RIGHT

    Box(modifier = modifier.fillMaxSize()) {
        content(progress.value, if (slidesRight) 1f else -1f)
    }
}
