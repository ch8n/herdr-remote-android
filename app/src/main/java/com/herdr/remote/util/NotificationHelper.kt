package com.herdr.remote.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.herdr.remote.MainActivity
import com.herdr.remote.R
import com.herdr.remote.data.model.Message
import com.herdr.remote.data.model.Session
import com.herdr.remote.data.model.ToolExecution
import com.herdr.remote.receiver.NotificationActionReceiver

object NotificationHelper {
    const val CHANNEL_ID = "herdr_agent_tasks"
    const val CHANNEL_NAME = "Agent Task Updates"
    const val CHANNEL_PERMISSION_ID = "herdr_agent_permissions"
    const val CHANNEL_PERMISSION_NAME = "Agent Permission Requests"
    const val CHANNEL_QUESTIONS_ID = "herdr_agent_questions"
    const val CHANNEL_QUESTIONS_NAME = "Agent Questions & Choices"

    const val EXTRA_SESSION_ID = "extra_target_session_id"
    const val EXTRA_TOOL_ID = "extra_tool_id"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    const val EXTRA_CHOICE_TEXT = "extra_choice_text"

    const val KEY_TEXT_REPLY = "key_text_reply"
    const val ACTION_REPLY = "com.herdr.remote.ACTION_REPLY"
    const val ACTION_APPROVE = "com.herdr.remote.ACTION_APPROVE"
    const val ACTION_DENY = "com.herdr.remote.ACTION_DENY"
    const val ACTION_SELECT_CHOICE = "com.herdr.remote.ACTION_SELECT_CHOICE"

    fun initNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // Channel 1: Task Updates
            val taskChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when an agent completes a task or responds in a session"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(taskChannel)

            // Channel 2: Permissions (Max Importance with Urgent alert)
            val permChannel = NotificationChannel(
                CHANNEL_PERMISSION_ID,
                CHANNEL_PERMISSION_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Immediate approval requests for agent actions and tool executions"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(permChannel)

            // Channel 3: Questions & Multi-Choice
            val questionsChannel = NotificationChannel(
                CHANNEL_QUESTIONS_ID,
                CHANNEL_QUESTIONS_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Multi-choice options and interactive questions from agents"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(questionsChannel)
        }
    }

    fun canPostNotification(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun sendTaskCompletedNotification(context: Context, session: Session, message: Message) {
        initNotificationChannel(context)
        if (!canPostNotification(context)) return

        val notifId = session.id.hashCode()

        // Main Tap Intent (Opens Session in Tab)
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SESSION_ID, session.id)
        }
        val tapPendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Smart preset quick-reply choices on notification
        val smartChoices = arrayOf(
            "Looks good! 👍",
            "Run tests 🧪",
            "Explain in detail 🔍",
            "Deploy to Staging 🚀"
        )

        // Direct Reply RemoteInput Action with Multi-Choice Chips
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel("Reply to ${session.agentProfile.name}...")
            .setChoices(smartChoices)
            .build()

        val replyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_REPLY
            putExtra(EXTRA_SESSION_ID, session.id)
            putExtra(EXTRA_NOTIFICATION_ID, notifId)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            notifId + 1,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val replyAction = NotificationCompat.Action.Builder(
            R.mipmap.ic_launcher,
            "💬 Reply",
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()

        val cleanPreview = message.content
            .replace("```[a-zA-Z]*".toRegex(), "")
            .replace("```", "")
            .replace("#", "")
            .replace("*", "")
            .trim()
            .take(150)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("⚡ ${session.agentProfile.name} • Task Completed")
            .setContentText(if (cleanPreview.isNotBlank()) cleanPreview else "Agent finished executing your instructions.")
            .setSubText(session.title)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(if (cleanPreview.isNotBlank()) cleanPreview else "Task completed in session: ${session.title}")
                    .setSummaryText(session.agentProfile.role)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tapPendingIntent)
            .addAction(replyAction)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        } catch (e: SecurityException) {
            // Ignored
        }
    }

    fun sendPermissionRequestNotification(
        context: Context,
        session: Session,
        tool: ToolExecution,
        prompt: String? = null
    ) {
        initNotificationChannel(context)
        if (!canPostNotification(context)) return

        val notifId = (session.id + tool.id).hashCode()

        // Tap Intent (Navigates to tab)
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SESSION_ID, session.id)
        }
        val tapPendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Approve Intent
        val approveIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_APPROVE
            putExtra(EXTRA_SESSION_ID, session.id)
            putExtra(EXTRA_TOOL_ID, tool.id)
            putExtra(EXTRA_NOTIFICATION_ID, notifId)
        }
        val approvePendingIntent = PendingIntent.getBroadcast(
            context,
            notifId + 10,
            approveIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Deny Intent
        val denyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_DENY
            putExtra(EXTRA_SESSION_ID, session.id)
            putExtra(EXTRA_TOOL_ID, tool.id)
            putExtra(EXTRA_NOTIFICATION_ID, notifId)
        }
        val denyPendingIntent = PendingIntent.getBroadcast(
            context,
            notifId + 20,
            denyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Direct Reply RemoteInput with quick permission options
        val permissionChoices = arrayOf(
            "Approve once",
            "Approve for session",
            "Deny and suggest dry-run",
            "Skip step"
        )

        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel("Provide instructions or pick option...")
            .setChoices(permissionChoices)
            .build()

        val replyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_REPLY
            putExtra(EXTRA_SESSION_ID, session.id)
            putExtra(EXTRA_NOTIFICATION_ID, notifId)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            notifId + 30,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val approveAction = NotificationCompat.Action.Builder(
            R.mipmap.ic_launcher,
            "✅ Allow",
            approvePendingIntent
        ).build()

        val denyAction = NotificationCompat.Action.Builder(
            R.mipmap.ic_launcher,
            "❌ Deny",
            denyPendingIntent
        ).build()

        val replyAction = NotificationCompat.Action.Builder(
            R.mipmap.ic_launcher,
            "💬 Options / Reply",
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()

        val descriptionText = prompt ?: "Tool '${tool.toolName}' requests permission to execute on system:\n${tool.argumentsJson}"

        val notification = NotificationCompat.Builder(context, CHANNEL_PERMISSION_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("⚠️ Permission Request • ${session.agentProfile.name}")
            .setContentText("Tool '${tool.toolName}' requires your approval")
            .setSubText("Approval Needed")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(descriptionText)
                    .setSummaryText(session.title)
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(tapPendingIntent)
            .addAction(approveAction)
            .addAction(denyAction)
            .addAction(replyAction)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        } catch (e: SecurityException) {
            // Ignored
        }
    }

    fun sendMultiChoiceNotification(
        context: Context,
        session: Session,
        question: String,
        choices: List<String>
    ) {
        initNotificationChannel(context)
        if (!canPostNotification(context)) return

        val notifId = (session.id + question).hashCode()

        // Tap Intent (Navigates to tab)
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SESSION_ID, session.id)
        }
        val tapPendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_QUESTIONS_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("❓ Choice Required • ${session.agentProfile.name}")
            .setContentText(question)
            .setSubText(session.title)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$question\n\nSelect an option below:")
                    .setSummaryText("Multi-Choice Question")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tapPendingIntent)

        // 1. Direct Multi-Choice Action Buttons (first 3 choices get dedicated buttons)
        choices.take(3).forEachIndexed { index, choiceText ->
            val choiceIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_SELECT_CHOICE
                putExtra(EXTRA_SESSION_ID, session.id)
                putExtra(EXTRA_CHOICE_TEXT, choiceText)
                putExtra(EXTRA_NOTIFICATION_ID, notifId)
            }
            val choicePendingIntent = PendingIntent.getBroadcast(
                context,
                notifId + 100 + index,
                choiceIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val action = NotificationCompat.Action.Builder(
                R.mipmap.ic_launcher,
                choiceText.take(24),
                choicePendingIntent
            ).build()

            builder.addAction(action)
        }

        // 2. Direct Reply Action with full choices dropdown / chips array
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel("Pick option or type custom answer...")
            .setChoices(choices.map { it as CharSequence }.toTypedArray())
            .build()

        val replyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_REPLY
            putExtra(EXTRA_SESSION_ID, session.id)
            putExtra(EXTRA_NOTIFICATION_ID, notifId)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            notifId + 200,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val replyAction = NotificationCompat.Action.Builder(
            R.mipmap.ic_launcher,
            "💬 More Options / Custom",
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()

        builder.addAction(replyAction)

        try {
            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        } catch (e: SecurityException) {
            // Ignored
        }
    }
}
