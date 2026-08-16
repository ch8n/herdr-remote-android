package com.herdr.remote.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * Type-safe destination keys representing Navigation 3 screens.
 */
sealed interface ScreenKey {
    /** Main Chat Workspace with session tabs and agent interface */
    data object Chat : ScreenKey

    /** Settings & Preferences with Tailscale and OpenRouter configuration */
    data object Settings : ScreenKey

    /** Fullscreen / Searchable OpenRouter Model Browser */
    data class ModelSelector(val currentModelId: String) : ScreenKey
}

/**
 * Navigation state and backstack manager following Navigation 3 UDF principles.
 */
@Stable
class Navigator(
    val backStack: SnapshotStateList<ScreenKey>
) {
    val currentScreen: ScreenKey
        get() = backStack.lastOrNull() ?: ScreenKey.Chat

    fun canGoBack(): Boolean = backStack.size > 1

    fun navigate(screen: ScreenKey) {
        // Prevent duplicate top-of-stack entry
        if (backStack.lastOrNull() != screen) {
            backStack.add(screen)
        }
    }

    fun popBackStack(): Boolean {
        return if (canGoBack()) {
            backStack.removeAt(backStack.lastIndex)
            true
        } else {
            false
        }
    }

    fun popToRoot() {
        while (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    fun replace(screen: ScreenKey) {
        if (backStack.isNotEmpty()) {
            backStack.removeAt(backStack.lastIndex)
        }
        backStack.add(screen)
    }
}

/**
 * Remember Navigation 3 Navigator state across recompositions.
 */
@Composable
fun rememberNavigator(startDestination: ScreenKey = ScreenKey.Chat): Navigator {
    val backStack = remember { mutableStateListOf(startDestination) }
    return remember(backStack) { Navigator(backStack) }
}
