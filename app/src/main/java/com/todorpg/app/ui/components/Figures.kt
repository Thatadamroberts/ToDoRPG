package com.todorpg.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.todorpg.app.data.Hero
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The pose a hero shows when nothing else is going on. */
fun restingPose(hero: Hero): Pose = when {
    hero.isKo -> Pose.IDLE
    hero.hp * 4 <= hero.maxHp -> Pose.KNEEL
    else -> Pose.IDLE
}

fun frameMsFor(pose: Pose, eager: Boolean = false): Int = when (pose) {
    Pose.WALK -> 130
    Pose.CAST -> 120
    Pose.VICTORY -> 160
    Pose.KNEEL -> 700
    else -> if (eager) 300 else 480
}

/**
 * A hero sprite with personality: breathes and blinks, sometimes glances
 * around, kneels when badly hurt, lies down when KO'd. Pass [pose] to override
 * (the battle screen does this for attacks).
 */
@Composable
fun HeroFigure(
    hero: Hero,
    modifier: Modifier = Modifier,
    pose: Pose? = null,
    tint: () -> Color? = { null },
    fidget: Boolean = true,
) {
    val clock = LocalClock.current
    val p = pose ?: restingPose(hero)
    PixelSprite(
        com.todorpg.app.ui.components.Sprites.hero(hero.heroClass, p),
        modifier.graphicsLayer {
            if (hero.isKo && pose == null) {
                rotationZ = -90f
                translationY = size.height * 0.25f
            } else if (fidget && p == Pose.IDLE) {
                // Every so often, glance the other way for a moment.
                val t = (clock.value + hero.id * 1300L) % 9000L
                if (t < 700) scaleX = -1f
            }
        },
        frameMs = frameMsFor(p, eager = hero.charges > 0),
        phase = hero.id * 170,
        grayscale = hero.isKo && pose == null,
        tint = tint,
    )
}

/** Hop-and-spin cheer, e.g. when tapped or when a quest is done. */
class Cheer {
    val progress = Animatable(1f)

    suspend fun play() {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(650, easing = LinearEasing))
    }

    fun Modifier.cheer(): Modifier = graphicsLayer {
        val p = progress.value
        if (p < 1f) {
            translationY = -sin(p * PI.toFloat()) * size.height * 0.45f
            scaleX = cos(p * 2 * PI.toFloat())
        }
    }
}

@Composable
fun rememberCheer(): Cheer = remember { Cheer() }

/** A hero that cheers when tapped. */
@Composable
fun TappableHero(hero: Hero, modifier: Modifier = Modifier, onTap: () -> Unit = {}) {
    val cheer = rememberCheer()
    val scope = rememberCoroutineScope()
    Box(
        modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) {
            if (!hero.isKo) {
                onTap()
                scope.launch { cheer.play() }
            }
        }
    ) {
        with(cheer) {
            HeroFigure(
                hero,
                Modifier.fillMaxSize().cheer(),
                pose = if (cheer.progress.value < 1f) Pose.VICTORY else null,
            )
        }
    }
}

/** Plays a cheer whenever [trigger] increases (skipping the first composition). */
@Composable
fun CheerOnIncrease(cheer: Cheer, trigger: Int) {
    val last = remember { intArrayOf(trigger) }
    LaunchedEffect(trigger) {
        if (trigger > last[0]) cheer.play()
        last[0] = trigger
    }
}
