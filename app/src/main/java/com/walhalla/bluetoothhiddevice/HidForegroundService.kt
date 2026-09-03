package com.walhalla.bluetoothhiddevice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class HidForegroundService : Service() {

    private val binder = LocalBinder()
    private var isInForeground = false
    private var lastNotificationContent = "HID connection is active"
    
    lateinit var hidManager: HidDeviceManager
        private set

    inner class LocalBinder : Binder() {
        fun getService(): HidForegroundService = this@HidForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        hidManager = HidDeviceManager(this)
        createNotificationChannel()
        hidManager.setConnectionListener { status, isConnected ->
            if (isInForeground) {
                postNotification(status, isConnected)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                hidManager.disconnectCurrent()
                postNotification(
                    content = lastNotificationContent,
                    isConnected = hidManager.isConnected()
                )
            }
            ACTION_OPEN_CALCULATOR -> {
                if (hidManager.isConnected()) {
                    hidManager.sendOpenCalculatorShortcut()
                }
            }
            ACTION_STOP -> {
                stopForegroundMode()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val content = intent?.getStringExtra(EXTRA_NOTIFICATION_CONTENT)
                    ?: "HID connection is kept alive in background"
                startForegroundMode(content)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    fun startForegroundMode(content: String = "HID connection is active") {
        lastNotificationContent = content
        val notification = createNotification(content, hidManager.isConnected())
        startForeground(NOTIFICATION_ID, notification)
        isInForeground = true
    }

    fun stopForegroundMode() {
        if (!isInForeground) return
        stopForeground(STOP_FOREGROUND_REMOVE)
        isInForeground = false
    }

    fun isForegroundModeActive(): Boolean = isInForeground

    private fun postNotification(content: String, isConnected: Boolean) {
        lastNotificationContent = content
        val notification = createNotification(content, isConnected)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(content: String, isConnected: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_OPEN_APP,
            openAppIntent,
            pendingIntentFlags()
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(openAppPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setAutoCancel(false)

        if (isConnected) {
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notification_action_disconnect),
                servicePendingIntent(ACTION_DISCONNECT, REQUEST_DISCONNECT)
            )
            builder.addAction(
                android.R.drawable.ic_menu_sort_by_size,
                getString(R.string.notification_action_calculator),
                servicePendingIntent(ACTION_OPEN_CALCULATOR, REQUEST_OPEN_CALCULATOR)
            )
        }

        builder.addAction(
            android.R.drawable.ic_media_pause,
            getString(R.string.notification_action_stop),
            servicePendingIntent(ACTION_STOP, REQUEST_STOP)
        )

        return builder.build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, HidForegroundService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            pendingIntentFlags()
        )
    }

    private fun pendingIntentFlags(): Int {
        val immutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
        return PendingIntent.FLAG_UPDATE_CURRENT or immutable
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "HidServiceChannel"
        private const val NOTIFICATION_ID = 1

        private const val EXTRA_NOTIFICATION_CONTENT = "notification_content"

        private const val ACTION_DISCONNECT = "com.walhalla.bluetoothhiddevice.action.DISCONNECT"
        private const val ACTION_OPEN_CALCULATOR = "com.walhalla.bluetoothhiddevice.action.OPEN_CALCULATOR"
        private const val ACTION_STOP = "com.walhalla.bluetoothhiddevice.action.STOP"

        private const val REQUEST_OPEN_APP = 0
        private const val REQUEST_DISCONNECT = 1
        private const val REQUEST_OPEN_CALCULATOR = 2
        private const val REQUEST_STOP = 3
    }
}
