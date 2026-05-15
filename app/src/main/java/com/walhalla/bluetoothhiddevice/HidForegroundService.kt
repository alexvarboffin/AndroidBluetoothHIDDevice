package com.walhalla.bluetoothhiddevice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class HidForegroundService : Service() {

    private val CHANNEL_ID = "HidServiceChannel"
    private val NOTIFICATION_ID = 1
    private val binder = LocalBinder()
    
    lateinit var hidManager: HidDeviceManager
        private set

    inner class LocalBinder : Binder() {
        fun getService(): HidForegroundService = this@HidForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        hidManager = HidDeviceManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("HID Service is running in background")
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Bluetooth HID Device")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "HID Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
