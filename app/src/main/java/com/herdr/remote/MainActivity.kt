package com.herdr.remote

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.herdr.remote.navigation.NavDisplay
import com.herdr.remote.navigation.Navigator
import com.herdr.remote.navigation.ScreenKey
import com.herdr.remote.navigation.rememberNavigator
import com.herdr.remote.ui.chat.ChatScreen
import com.herdr.remote.ui.chat.ChatViewModel
import com.herdr.remote.ui.components.ModelSelectorScreen
import com.herdr.remote.ui.components.SettingsScreen
import com.herdr.remote.ui.theme.HerdrRemoteTheme
import com.herdr.remote.util.NotificationHelper

class MainActivity : ComponentActivity() {

    private val chatViewModel: ChatViewModel by viewModels()
    private var activeNavigator: Navigator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        NotificationHelper.initNotificationChannel(this)
        com.herdr.remote.worker.HerdrNotificationWorker.schedule(applicationContext)
        handleIntent(intent)

        setContent {
            HerdrRemoteTheme {
                val navigator = rememberNavigator(ScreenKey.Chat)
                activeNavigator = navigator

                val settings by chatViewModel.settings.collectAsState()

                NavDisplay(navigator = navigator) { screen ->
                    when (screen) {
                        is ScreenKey.Chat -> {
                            ChatScreen(
                                viewModel = chatViewModel,
                                onOpenSettings = { navigator.navigate(ScreenKey.Settings) }
                            )
                        }

                        is ScreenKey.Settings -> {
                            SettingsScreen(
                                currentSettings = settings,
                                onSaveSettings = { chatViewModel.saveSettings(it) },
                                onBack = { navigator.popBackStack() },
                                onOpenModelSelector = { modelId ->
                                    navigator.navigate(ScreenKey.ModelSelector(modelId))
                                }
                            )
                        }

                        is ScreenKey.ModelSelector -> {
                            ModelSelectorScreen(
                                selectedModelId = screen.currentModelId,
                                apiKey = settings.openRouterApiKey,
                                onSelectModel = { modelId ->
                                    chatViewModel.saveSettings(settings.copy(openRouterModel = modelId))
                                    navigator.popBackStack()
                                },
                                onBack = { navigator.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        com.herdr.remote.util.AppLifecycleTracker.onActivityResumed()
    }

    override fun onPause() {
        super.onPause()
        com.herdr.remote.util.AppLifecycleTracker.onActivityPaused()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val targetSessionId = intent?.getStringExtra(NotificationHelper.EXTRA_SESSION_ID)
        if (!targetSessionId.isNullOrBlank()) {
            chatViewModel.selectSession(targetSessionId)
            activeNavigator?.popToRoot()
        }
    }
}
