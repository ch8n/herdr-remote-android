package com.herdr.remote

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.herdr.remote.ui.chat.ChatScreen
import com.herdr.remote.ui.chat.ChatViewModel
import com.herdr.remote.ui.theme.HerdrRemoteTheme
import com.herdr.remote.util.NotificationHelper

class MainActivity : ComponentActivity() {

    private val chatViewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        NotificationHelper.initNotificationChannel(this)
        handleIntent(intent)

        setContent {
            HerdrRemoteTheme {
                ChatScreen(viewModel = chatViewModel)
            }
        }
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
        }
    }
}
