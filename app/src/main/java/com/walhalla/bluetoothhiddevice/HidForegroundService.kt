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
import android.util.Log
import androidx.core.app.NotificationCompat
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom

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

    // ---- LAN web control (off by default, started manually from the Type tab) ----
    private var webServer: HidWebServer? = null

    data class WebServerInfo(val url: String, val token: String)

    fun isWebServerRunning(): Boolean = webServer != null

    fun startWebServer(): WebServerInfo? {
        val prefs = getSharedPreferences("web_server", MODE_PRIVATE)
        val token = prefs.getString("token", null) ?: ByteArray(16).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
            .also { prefs.edit().putString("token", it).apply() }
        if (webServer == null) {
            val server = HidWebServer(WEB_SERVER_PORT, token, hidManager)
            try {
                server.start()
            } catch (e: Exception) {
                Log.e(WEB_TAG, "Web server failed to start on port $WEB_SERVER_PORT", e)
                return null
            }
            webServer = server
        }
        val ip = localIpv4() ?: "<phone-ip>"
        Log.i(WEB_TAG, "Web server listening: ip=$ip port=$WEB_SERVER_PORT url=http://$ip:$WEB_SERVER_PORT token=$token login=http://$ip:$WEB_SERVER_PORT/#$token")
        return WebServerInfo("http://$ip:$WEB_SERVER_PORT", token)
    }

    fun stopWebServer() {
        if (webServer != null) Log.i(WEB_TAG, "Web server stopped")
        webServer?.stop()
        webServer = null
    }

    private fun localIpv4(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()

    override fun onDestroy() {
        stopWebServer()
        super.onDestroy()
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
            .setSilent(true)
            .setOnlyAlertOnce(true)
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
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    companion object {
        // A channel's importance cannot be changed after creation, so the silent channel has a new id.
        private const val LEGACY_CHANNEL_ID = "HidServiceChannel"
        private const val CHANNEL_ID = "HidServiceChannelSilent"
        private const val NOTIFICATION_ID = 1
        private const val WEB_SERVER_PORT = 8080
        private const val WEB_TAG = "HidWebServer"

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
