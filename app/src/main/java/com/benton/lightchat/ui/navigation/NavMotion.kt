package com.benton.lightchat.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigationevent.NavigationEvent

private const val SLIDE_DURATION = 320
private const val POP_DURATION = 300
private const val ENTER_OFFSET_DIVISOR = 4
private const val EXIT_OFFSET_DIVISOR = 8

object NavMotion {

    fun enter(layoutDirection: LayoutDirection): EnterTransition {
        val direction = direction(layoutDirection)
        return slideInHorizontally(
            animationSpec = tween(SLIDE_DURATION, easing = FastOutSlowInEasing),
            initialOffsetX = { it * direction / ENTER_OFFSET_DIVISOR },
        ) +
            scaleIn(
                animationSpec = tween(SLIDE_DURATION, easing = FastOutSlowInEasing),
                initialScale = 0.96f,
            )
    }

    fun exit(layoutDirection: LayoutDirection): ExitTransition {
        val direction = direction(layoutDirection)
        return slideOutHorizontally(
            animationSpec = tween(SLIDE_DURATION, easing = FastOutSlowInEasing),
            targetOffsetX = { -it * direction / EXIT_OFFSET_DIVISOR },
        ) +
            scaleOut(
                animationSpec = tween(SLIDE_DURATION, easing = FastOutSlowInEasing),
                targetScale = 0.96f,
            )
    }

    fun popEnter(layoutDirection: LayoutDirection): EnterTransition {
        val direction = direction(layoutDirection)
        return slideInHorizontally(
            animationSpec = tween(POP_DURATION, easing = FastOutSlowInEasing),
            initialOffsetX = { -it * direction / ENTER_OFFSET_DIVISOR },
        ) +
            scaleIn(
                animationSpec = tween(POP_DURATION, easing = FastOutSlowInEasing),
                initialScale = 0.96f,
            )
    }

    fun popExit(layoutDirection: LayoutDirection): ExitTransition {
        val direction = direction(layoutDirection)
        return slideOutHorizontally(
            animationSpec = tween(POP_DURATION, easing = FastOutSlowInEasing),
            targetOffsetX = { it * direction / ENTER_OFFSET_DIVISOR },
        ) +
            scaleOut(
                animationSpec = tween(POP_DURATION, easing = FastOutSlowInEasing),
                targetScale = 0.94f,
            )
    }

    fun predictivePopEnter(swipeEdge: Int): EnterTransition {
        val direction = predictiveDirection(swipeEdge)
        return slideInHorizontally(
            animationSpec = tween(POP_DURATION, easing = FastOutSlowInEasing),
            initialOffsetX = { -it * direction / ENTER_OFFSET_DIVISOR },
        ) +
            scaleIn(
                animationSpec = tween(POP_DURATION, easing = FastOutSlowInEasing),
                initialScale = 0.96f,
            )
    }

    fun predictivePopExit(swipeEdge: Int): ExitTransition {
        val direction = predictiveDirection(swipeEdge)
        return slideOutHorizontally(
            animationSpec = tween(POP_DURATION, easing = FastOutSlowInEasing),
            targetOffsetX = { it * direction / ENTER_OFFSET_DIVISOR },
        ) +
            scaleOut(
                animationSpec = tween(POP_DURATION, easing = FastOutSlowInEasing),
                targetScale = 0.94f,
            )
    }

    private fun direction(layoutDirection: LayoutDirection): Int =
        if (layoutDirection == LayoutDirection.Ltr) 1 else -1

    internal fun predictiveDirection(swipeEdge: Int): Int =
        if (swipeEdge == NavigationEvent.EDGE_RIGHT) -1 else 1
}
