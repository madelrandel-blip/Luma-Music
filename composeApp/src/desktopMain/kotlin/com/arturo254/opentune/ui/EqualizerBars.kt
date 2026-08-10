package com.arturo254.opentune.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arturo254.opentune.player.PlayerManager
import kotlin.random.Random

object NowPlayingState {
    val currentSongId = mutableStateOf<String?>(null)
    val isPlaying = mutableStateOf(false)
    val isLoading = mutableStateOf(false)

    fun update() {
        currentSongId.value = PlayerManager.currentSong?.id
        isPlaying.value = PlayerManager.isPlaying
        isLoading.value = PlayerManager.isLoading
    }
}

@Composable
fun EqualizerBars(
    modifier: Modifier = Modifier,
    color: Color,
    animated: Boolean = true,
    barWidth: Dp = 3.dp,
    barSpacing: Dp = 2.dp,
    maxHeight: Dp = 16.dp
) {
    val specs = remember {
        List(4) { i ->
            val a = 0.15f + Random.nextFloat() * 0.35f
            val b = 0.5f + Random.nextFloat() * 0.5f
            Triple(a, b, 300 + i * 140)
        }
    }

    if (!animated) {
        Row(
            modifier = modifier.height(maxHeight),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(barSpacing)
        ) {
            specs.forEach { (a, _, _) ->
                Box(
                    modifier = Modifier
                        .width(barWidth)
                        .height(maxHeight * (0.3f + 0.7f * a))
                        .clip(RoundedCornerShape(2.dp))
                        .background(color)
                )
            }
        }
        return
    }

    val transition = rememberInfiniteTransition(label = "eq")
    val anims = specs.mapIndexed { i, (a, b, dur) ->
        transition.animateFloat(
            initialValue = a,
            targetValue = b,
            animationSpec = infiniteRepeatable(
                animation = tween(dur, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "eqBar$i"
        )
    }

    Row(
        modifier = modifier.height(maxHeight),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(barSpacing)
    ) {
        anims.forEach { anim ->
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(maxHeight * (0.2f + 0.8f * anim.value))
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}
