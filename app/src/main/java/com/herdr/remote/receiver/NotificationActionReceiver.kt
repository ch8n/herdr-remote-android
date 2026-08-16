package com.herdr.remote.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.herdr.remote.HerdrApplication
import com.herdr.remote.util.NotificationHelper

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        val sessionId = intent.getStringExtra(NotificationHelper.EXTRA_SESSION_ID) ?: return
        val notifId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, 0)
        val toolId = intent.getStringExtra(NotificationHelper.EXTRA_TOOL_ID)

        when (intent.action) {
            NotificationHelper.ACTION_REPLY -> {
                val results = RemoteInput.getResultsFromIntent(intent)
                val replyText = results?.getCharSequence(NotificationHelper.KEY_TEXT_REPLY)?.toString()?.trim()

                if (!replyText.isNullOrBlank()) {
                    HerdrApplication.instance.sendChatMessage(sessionId, replyText)
                }

                if (notifId != 0) {
                    NotificationManagerCompat.from(context).cancel(notifId)
                }
            }

            NotificationHelper.ACTION_APPROVE -> {
                if (!toolId.isNullOrBlank()) {
                    HerdrApplication.instance.handlePermissionDecision(sessionId, toolId, approved = true)
                }

                if (notifId != 0) {
                    NotificationManagerCompat.from(context).cancel(notifId)
                }
            }

            NotificationHelper.ACTION_DENY -> {
                if (!toolId.isNullOrBlank()) {
                    HerdrApplication.instance.handlePermissionDecision(sessionId, toolId, approved = false)
                }

                if (notifId != 0) {
                    NotificationManagerCompat.from(context).cancel(notifId)
                }
            }

            NotificationHelper.ACTION_SELECT_CHOICE -> {
                val choiceText = intent.getStringExtra(NotificationHelper.EXTRA_CHOICE_TEXT)?.trim()
                if (!choiceText.isNullOrBlank()) {
                    HerdrApplication.instance.sendChatMessage(sessionId, choiceText)
                }

                if (notifId != 0) {
                    NotificationManagerCompat.from(context).cancel(notifId)
                }
            }
        }
    }
}
