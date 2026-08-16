package com.herdr.remote.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.herdr.remote.data.model.Message
import com.herdr.remote.data.model.MessageSender
import com.herdr.remote.data.model.MessageStatus
import com.herdr.remote.data.network.HerdrConnectionService
import com.herdr.remote.data.repository.SettingsRepository
import com.herdr.remote.util.AppLifecycleTracker
import com.herdr.remote.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit

class HerdrNotificationWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val settingsRepository = SettingsRepository(context)
    private val connectionService = HerdrConnectionService()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val settings = settingsRepository.settings.value
            val serverUrl = settings.herdrServerUrl
            if (serverUrl.isBlank()) {
                return@withContext Result.success()
            }

            // Probe Herdr Node for active background tabs
            val result = connectionService.testConnection(serverUrl)
            if (result.isSuccess && result.remoteSessions.isNotEmpty()) {
                for (session in result.remoteSessions) {
                    // Check if notification should be shown (background tab or app in background/killed)
                    if (AppLifecycleTracker.shouldShowNotificationForTab(session.id)) {
                        val sampleMsg = Message(
                            id = UUID.randomUUID().toString(),
                            sessionId = session.id,
                            sender = MessageSender.AGENT,
                            content = "⚡ Tab '${session.title}' has updates on Herdr desktop.",
                            status = MessageStatus.SENT
                        )
                        NotificationHelper.sendTaskCompletedNotification(context, session, sampleMsg)
                    }
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "herdr_background_notification_sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<HerdrNotificationWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
