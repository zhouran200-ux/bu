package com.lucent.app.chat

import com.lucent.app.i18n.S

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.lucent.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class ChatGenerationService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observation: Job? = null
    private var latestStartId = 0
    private var shutdownHandled = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        shutdownHandled = false
        try {
            createNotificationChannel()
            val notification = buildNotification(ChatSessions.activeCount.value)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (error: RuntimeException) {
            android.util.Log.e(TAG, "Unable to protect chat generation in foreground", error)
            Toast.makeText(this, S.serviceStartFailed(error.message.orEmpty()), Toast.LENGTH_LONG).show()
            ChatSessions.stopAll()
            stopProtection()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_STOP) {
            ChatSessions.stopAll()
        }

        observation?.cancel()
        observation = serviceScope.launch {
            ChatSessions.activeCount.collect { count ->
                if (count == 0) {
                    stopProtectionIfIdle()
                } else {
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, buildNotification(count))
                }
            }
        }
        return START_NOT_STICKY
    }


    override fun onTimeout(startId: Int, fgsType: Int) {
        Toast.makeText(this, S.serviceTimeout, Toast.LENGTH_LONG).show()
        ChatSessions.stopAll()
        stopProtection()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        if (!shutdownHandled) ChatSessions.stopAll()
        super.onDestroy()
    }

    private fun stopProtectionIfIdle() {
        if (ChatSessions.activeCount.value != 0) return
        if (!stopSelfResult(latestStartId)) return
        shutdownHandled = true
        observation?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun stopProtection() {
        shutdownHandled = true
        observation?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(latestStartId)
    }

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, S.serviceChannelName, NotificationManager.IMPORTANCE_LOW)
                .apply { description = S.serviceChannelDescription }
        )
    }

    private fun buildNotification(count: Int): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ChatGenerationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(S.serviceTitle)
            .setContentText(if (count > 1) S.serviceTextMany(count) else S.serviceTextSingle)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .addAction(Notification.Action.Builder(null, S.serviceStopAll, stop).build())
            .build()
    }

    companion object {
        private const val TAG = "ChatGenerationService"
        private const val CHANNEL_ID = "chat_generation"
        private const val NOTIFICATION_ID = 4101
        private const val ACTION_STOP = "com.lucent.app.chat.STOP_ALL"

        fun ensureRunning(context: Context) {
            check(Looper.myLooper() == Looper.getMainLooper()) { "Start chat generation on the main thread" }
            check(ChatSessions.activeCount.value > 0) { "Foreground protection requires active generation" }
            ContextCompat.startForegroundService(
                context.applicationContext,
                Intent(context.applicationContext, ChatGenerationService::class.java)
            )
        }
    }
}
