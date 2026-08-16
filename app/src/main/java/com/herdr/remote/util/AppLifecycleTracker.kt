package com.herdr.remote.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppLifecycleTracker {
    private val _isAppInForeground = MutableStateFlow(false)
    val isAppInForeground = _isAppInForeground.asStateFlow()

    private val _activeFocusedTabId = MutableStateFlow<String?>(null)
    val activeFocusedTabId = _activeFocusedTabId.asStateFlow()

    fun onActivityResumed() {
        _isAppInForeground.value = true
    }

    fun onActivityPaused() {
        _isAppInForeground.value = false
    }

    fun setFocusedTabId(tabId: String?) {
        _activeFocusedTabId.value = tabId
    }

    /**
     * Determines whether a system status bar notification should be shown for this tab.
     * 1. Do NOT show notifications for the currently active or focused tab while app is in foreground.
     * 2. SHOW notifications for background / unfocused tabs.
     * 3. SHOW notifications for all tabs when app is running in background or killed.
     */
    fun shouldShowNotificationForTab(tabId: String): Boolean {
        if (!_isAppInForeground.value) {
            return true
        }
        val currentTab = _activeFocusedTabId.value
        return currentTab == null || currentTab != tabId
    }
}
