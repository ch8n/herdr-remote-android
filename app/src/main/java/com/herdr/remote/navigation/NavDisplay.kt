package com.herdr.remote.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.herdr.remote.ui.theme.BackgroundDark

/**
 * Navigation 3 Display container that animates between destinations,
 * manages system back navigation gestures, and guarantees edge-to-edge window insets.
 */
@Composable
fun NavDisplay(
    navigator: Navigator,
    modifier: Modifier = Modifier,
    content: @Composable (ScreenKey) -> Unit
) {
    // Intercept hardware / gesture back button
    BackHandler(enabled = navigator.canGoBack()) {
        navigator.popBackStack()
    }

    var previousStackSize by remember { mutableIntStateOf(navigator.backStack.size) }
    val isForward = navigator.backStack.size >= previousStackSize
    previousStackSize = navigator.backStack.size

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        AnimatedContent(
            targetState = navigator.currentScreen,
            transitionSpec = {
                val animDuration = 280
                val easing = FastOutSlowInEasing

                if (isForward) {
                    // Forward slide in from right with subtle fade
                    (slideInHorizontally(
                        initialOffsetX = { fullWidth -> (fullWidth * 0.35f).toInt() },
                        animationSpec = tween(animDuration, easing = easing)
                    ) + fadeIn(animationSpec = tween(animDuration))) togetherWith
                            (slideOutHorizontally(
                                targetOffsetX = { fullWidth -> (-fullWidth * 0.25f).toInt() },
                                animationSpec = tween(animDuration, easing = easing)
                            ) + fadeOut(animationSpec = tween(animDuration / 2)))
                } else {
                    // Back slide in from left with subtle fade
                    (slideInHorizontally(
                        initialOffsetX = { fullWidth -> (-fullWidth * 0.25f).toInt() },
                        animationSpec = tween(animDuration, easing = easing)
                    ) + fadeIn(animationSpec = tween(animDuration))) togetherWith
                            (slideOutHorizontally(
                                targetOffsetX = { fullWidth -> (fullWidth * 0.35f).toInt() },
                                animationSpec = tween(animDuration, easing = easing)
                            ) + fadeOut(animationSpec = tween(animDuration / 2)))
                }
            },
            label = "Nav3DisplayAnimation",
            modifier = Modifier.fillMaxSize()
        ) { screen ->
            content(screen)
        }
    }
}
