package com.focuslock.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class LockForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "focuslock_channel_v2"
        const val NOTIF_ID = 1
        const val ACTION_START_LOCK = "com.focuslock.app.ACTION_START_LOCK"
    }

    private val handler = Handler(Looper.getMainLooper())

    private val actionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_START_LOCK) {
                SessionManager.startSession(this@LockForegroundService)
                scheduleCountdown()
            }
        }
    }

    // Fires every minute while session is active; ends session when time is up.
    private val countdownTick = object : Runnable {
        override fun run() {
            val ctx = this@LockForegroundService
            if (SessionManager.isSessionActive(ctx)) {
                val mins = SessionManager.getRemainingMinutes(ctx)
                updateNotification(active = true, remainingMinutes = mins)
                handler.postDelayed(this, 60_000L)
            } else {
                SessionManager.endSession(ctx)
                updateNotification(active = false, remainingMinutes = 0)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val filter = IntentFilter(ACTION_START_LOCK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(actionReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(actionReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification(
            active = SessionManager.isSessionActive(this),
            remainingMinutes = SessionManager.getRemainingMinutes(this)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        // Resume countdown if a session was active before (e.g., after reboot start)
        if (SessionManager.isSessionActive(this)) {
            handler.removeCallbacks(countdownTick)
            handler.post(countdownTick)
        }
        return START_STICKY
    }

    private fun scheduleCountdown() {
        handler.removeCallbacks(countdownTick)
        handler.post(countdownTick)
    }

    private fun updateNotification(active: Boolean, remainingMinutes: Long) {
        val notif = buildNotification(active, remainingMinutes)
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, notif)
    }

    private fun buildNotification(active: Boolean, remainingMinutes: Long): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("FocusLock")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOnlyAlertOnce(true)

        if (active) {
            builder.setContentText("Active — ${remainingMinutes}m remaining")
        } else {
            builder.setContentText("Tap to start a 1-hour focus session")
            val broadcastIntent = Intent(ACTION_START_LOCK).apply {
                `package` = packageName
            }
            val pi = PendingIntent.getBroadcast(
                this, 0, broadcastIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Start Lock (1hr)", pi)
        }
        return builder.build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "FocusLock Session", NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Shows lock session status and provides start button"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(countdownTick)
        try { unregisterReceiver(actionReceiver) } catch (_: Exception) {}
    }
}
