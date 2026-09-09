package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class OrbState {
    IDLE,
    LISTENING,
    GENERATING
}

@Composable
fun GlowingOrb(
    state: OrbState,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 44.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbTransition")

    // Pulse animation depending on state
    val pulseDuration = when (state) {
        OrbState.LISTENING -> 800
        OrbState.GENERATING -> 1200
        OrbState.IDLE -> 3000
    }

    val maxScale = when (state) {
        OrbState.LISTENING -> 1.18f
        OrbState.GENERATING -> 1.12f
        OrbState.IDLE -> 1.06f
    }

    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = pulseDuration / 2, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbScale"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state == OrbState.GENERATING) 1800 else 12000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbRotation"
    )

    // Glow opacity pulse
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = if (state == OrbState.LISTENING) 0.85f else 0.70f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = pulseDuration / 2, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = modifier
            .size(sizeDp)
            .testTag("glowing_orb"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(sizeDp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = (size.minDimension / 2f) * 0.8f
            val currentRadius = baseRadius * scale

            val (centerColor, midColor, edgeColor, auraColor) = when (state) {
                OrbState.LISTENING -> listOf(
                    Color(0xFFFF8A80),
                    Color(0xFFFF5E7A),
                    Color(0xFFC62828),
                    Color(0x99FF5E7A)
                )
                OrbState.GENERATING -> listOf(
                    Color(0xFFE1BEE7),
                    Color(0xFFB388FF),
                    Color(0xFF5E35B1),
                    Color(0xAA9C27B0)
                )
                OrbState.IDLE -> listOf(
                    Color(0xFFB388FF),
                    Color(0xFF7B5EA7),
                    Color(0xFF4A3070),
                    Color(0x667B5EA7)
                )
            }

            // Draw outer ambient aura glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        auraColor.copy(alpha = glowAlpha),
                        auraColor.copy(alpha = glowAlpha * 0.4f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = currentRadius * 1.5f
                ),
                radius = currentRadius * 1.5f,
                center = center
            )

            // Draw main energy sphere with rotation
            rotate(degrees = if (state == OrbState.GENERATING) rotation else 0f, pivot = center) {
                val orbGradient = Brush.radialGradient(
                    colors = listOf(centerColor, midColor, edgeColor),
                    center = Offset(center.x - currentRadius * 0.3f, center.y - currentRadius * 0.3f),
                    radius = currentRadius * 1.25f
                )
                drawCircle(
                    brush = orbGradient,
                    radius = currentRadius,
                    center = center
                )
            }

            // Specular light reflection spot at top-left
            val highlightCenter = Offset(center.x - currentRadius * 0.35f, center.y - currentRadius * 0.35f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.65f),
                        Color.White.copy(alpha = 0.2f),
                        Color.Transparent
                    ),
                    center = highlightCenter,
                    radius = currentRadius * 0.45f
                ),
                radius = currentRadius * 0.45f,
                center = highlightCenter
            )
        }
    }
}
