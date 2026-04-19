/*
 * Copyright (c) 2026 Rekluz Games. All rights reserved.
 */

package com.rekluzgames.nikakudorimahjong.presentation.effects

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin



enum class ParticleMode {
    FALLING,
    DIRECTIONAL,
    WINDOW
}

enum class SpawnSide { LEFT, RIGHT }


data class PetalParticle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val rotation: Float,
    val rotationSpeed: Float,
    val alpha: Float,
    val color: Color,
    val width: Float,
    val height: Float,
    val life: Float,
    val driftFrequency: Float,
    val isFromBurst: Boolean = false
)

private fun PetalParticle.step(): PetalParticle {
    val decay = if (isFromBurst) 0.014f else 0.005f
    val newLife = life - decay
    val newAlpha = (if (newLife < 0.3f) newLife / 0.3f else 1f).coerceIn(0f, 1f)

    val newVy = if (isFromBurst) vy + 0.11f else vy

    return copy(
        x = x + vx + if (!isFromBurst) sin(life * driftFrequency) * 1.2f else 0f,
        y = y + newVy,
        vy = newVy,
        rotation = rotation + rotationSpeed,
        life = newLife,
        alpha = newAlpha
    )
}



private val petalColors = listOf(
    Color(0xFFFFB7C5), Color(0xFFFFC0CB), Color(0xFFFAE1DD), Color(0xFFFFD1DC)
)

private val burstColors = listOf(
    Color(0xFFFFD700),
    Color(0xFFFF4444),
    Color(0xFF44BB66),
    Color(0xFFFFFFFF),
    Color(0xFF00BFFF),
    Color(0xFFFF8C00),
)



private fun spawnBurst(pos: Offset, count: Int = 18): List<PetalParticle> {
    val angleStep = 360f / count
    return List(count) { i ->
        val angle = angleStep * i + (Math.random().toFloat() * angleStep * 0.8f)
        val angleRad = Math.toRadians(angle.toDouble()).toFloat()
        val speed = Math.random().toFloat() * 2.5f + 1.5f
        val size = Math.random().toFloat() * 7f + 3f

        PetalParticle(
            x = pos.x,
            y = pos.y,
            vx = cos(angleRad) * speed,
            vy = sin(angleRad) * speed,
            rotation = Math.random().toFloat() * 360f,
            rotationSpeed = Math.random().toFloat() * 18f - 9f,
            alpha = 1f,
            color = burstColors.random(),
            width = size,
            height = size * (Math.random().toFloat() * 0.6f + 0.5f),
            life = 1.0f,
            driftFrequency = 0f,
            isFromBurst = true
        )
    }
}


@Composable
fun ParticleOverlay(
    triggerVictoryStorm: Boolean,
    modifier: Modifier = Modifier,
    selectionPositions: List<Offset> = emptyList(),
    isScoreEntryActive: Boolean = false,
    mode: ParticleMode = ParticleMode.FALLING,
    spawnSide: SpawnSide = SpawnSide.LEFT,
    windStrength: Float = 1.5f
) {
    var particles by remember { mutableStateOf<List<PetalParticle>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameMillis {
                particles = particles
                    .map { it.step() }
                    .filter { it.life > 0f }
            }
        }
    }

    LaunchedEffect(selectionPositions) {
        if (selectionPositions.isNotEmpty()) {
            delay(150)
            val newParticles = selectionPositions.flatMap { spawnBurst(it) }
            particles = particles + newParticles
        }
    }

    LaunchedEffect(triggerVictoryStorm, mode, spawnSide) {
        if (!triggerVictoryStorm) return@LaunchedEffect

        while (isActive) {

            val newPetals = when (mode) {

                ParticleMode.FALLING -> {
                    List(3) {
                        PetalParticle(
                            x = (Math.random().toFloat() * (canvasSize.width + 200f)) - 100f,
                            y = -50f,
                            vx = Math.random().toFloat() * 1.2f - 0.6f,
                            vy = Math.random().toFloat() * 2f + 2f,
                            rotation = Math.random().toFloat() * 360f,
                            rotationSpeed = Math.random().toFloat() * 4f - 2f,
                            alpha = 1f,
                            color = petalColors.random(),
                            width = 15f,
                            height = 10f,
                            life = 2.5f,
                            driftFrequency = Math.random().toFloat() * 3f + 1f,
                            isFromBurst = false
                        )
                    }
                }

                ParticleMode.WINDOW -> {
                    val windowRight = canvasSize.width * 0.42f
                    val windowTop = canvasSize.height * 0.32f
                    val windowBottom = canvasSize.height * 0.72f

                    List(1) {
                        val depth = Math.random().toFloat()
                        val goRight = Math.random().toFloat() < 0.6f
                        val sizeBase = Math.random().toFloat() * 6f + 4f
                        val scaleFactor = 0.5f + depth * 0.7f

                        PetalParticle(
                            x = windowRight - Math.random().toFloat() * canvasSize.width * 0.04f,
                            y = windowTop + Math.random().toFloat() * (windowBottom - windowTop),
                            vx = if (goRight) {
                                (0.6f + depth * 1.0f) + Math.random().toFloat() * 0.5f
                            } else {
                                -((0.3f + depth * 0.6f) + Math.random().toFloat() * 0.3f)
                            },
                            vy = Math.random().toFloat() * 1.2f + 0.4f,
                            rotation = Math.random().toFloat() * 360f,
                            rotationSpeed = Math.random().toFloat() * 3f - 1.5f,
                            alpha = 0.3f + depth * 0.65f,
                            color = petalColors.random(),
                            width = sizeBase * scaleFactor,
                            height = sizeBase * scaleFactor * 0.7f,
                            life = 3.0f,
                            driftFrequency = Math.random().toFloat() * 0.025f + 0.008f + depth * 0.01f,
                            isFromBurst = false
                        )
                    }
                }

                ParticleMode.DIRECTIONAL -> {
                    val fromLeft = spawnSide == SpawnSide.LEFT

                    List(1) {
                        val startX = if (fromLeft) -40f else canvasSize.width + 40f
                        val baseVX = if (fromLeft) windStrength else -windStrength

                        PetalParticle(
                            x = startX,
                            y = Math.random().toFloat() * canvasSize.height * 0.6f,
                            vx = baseVX + (Math.random().toFloat() * 0.6f - 0.3f),
                            vy = Math.random().toFloat() * 1.2f + 1.2f,
                            rotation = Math.random().toFloat() * 360f,
                            rotationSpeed = Math.random().toFloat() * 3f - 1.5f,
                            alpha = 1f,
                            color = petalColors.random(),
                            width = 14f,
                            height = 9f,
                            life = 3.0f,
                            driftFrequency = Math.random().toFloat() * 2f + 1f,
                            isFromBurst = false
                        )
                    }
                }
            }

            particles = particles + newPetals
            withFrameMillis { }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .zIndex(1f)
    ) {
        canvasSize = size
        val centerX = size.width / 2
        val centerY = size.height / 2
        val left = centerX - 180f
        val right = centerX + 180f
        val top = centerY - 220f
        val bottom = centerY + 220f

        particles.forEach { p ->
            val shouldSkip = !p.isFromBurst && isScoreEntryActive &&
                    p.x in left..right && p.y in top..bottom

            if (!shouldSkip) {
                withTransform({
                    rotate(degrees = p.rotation, pivot = Offset(p.x, p.y))
                }) {
                    if (p.isFromBurst) {
                        drawRect(
                            color = p.color.copy(alpha = p.alpha),
                            topLeft = Offset(p.x - p.width / 2f, p.y - p.height / 2f),
                            size = Size(p.width, p.height)
                        )
                    } else {
                        drawOval(
                            color = p.color.copy(alpha = p.alpha),
                            topLeft = Offset(p.x - p.width / 2f, p.y - p.height / 2f),
                            size = Size(p.width, p.height)
                        )
                    }
                }
            }
        }
    }
}