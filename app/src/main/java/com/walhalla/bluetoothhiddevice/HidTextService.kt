package com.walhalla.bluetoothhiddevice

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import android.widget.Toast

/**
 * Accepts a text extra and types it through the existing HID session.
 * Start explicitly, for example:
 * am startservice -n com.walhalla.bluetoothhiddevice/.HidTextService
 *   -a com.walhalla.bluetoothhiddevice.action.SEND_TEXT --es text "hello"
 */
class HidTextService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = readText(intent)
        if (text.isNullOrEmpty()) {
            Log.w(TAG, "Ignored start: text extra is missing or blank")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        Toast.makeText(
            applicationContext,
            getString(R.string.hid_text_received, maskText(text)),
            Toast.LENGTH_SHORT
        ).show()

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                try {
                    val manager = (service as HidForegroundService.LocalBinder).getService().hidManager
                    if (manager.isConnected()) {
                        manager.sendString(text)
                        Log.d(TAG, "Queued ${text.length} characters for HID")
                    } else {
                        Log.w(TAG, "HID host is not connected, text dropped")
                    }
                } catch (error: Exception) {
                    Log.e(TAG, "Failed to send text via HID", error)
                } finally {
                    unbindQuietly(this)
                    stopSelf(startId)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }

        val bound = bindService(
            Intent(this, HidForegroundService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )
        if (!bound) {
            Log.e(TAG, "Could not bind HID service")
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun unbindQuietly(connection: ServiceConnection) {
        runCatching { unbindService(connection) }
            .onFailure { Log.w(TAG, "Unbind failed", it) }
    }

    companion object {
        private const val TAG = "HidTextService"

        const val ACTION_SEND_TEXT = "com.walhalla.bluetoothhiddevice.action.SEND_TEXT"
        const val EXTRA_TEXT = "text"

        fun start(context: Context, text: String) {
            val intent = Intent(context, HidTextService::class.java).apply {
                action = ACTION_SEND_TEXT
                putExtra(EXTRA_TEXT, text)
            }
            context.startService(intent)
        }

        private fun maskText(text: String): String {
            val edge = when {
                text.length >= 8 -> 3
                text.length >= 3 -> 1
                else -> 0
            }
            if (edge == 0) return "***"
            val hidden = (text.length - edge * 2).coerceIn(3, 8)
            return text.take(edge) + "*".repeat(hidden) + text.takeLast(edge)
        }

        private fun readText(intent: Intent?): String? {
            if (intent == null) return null
            val raw = intent.getStringExtra(EXTRA_TEXT)
                ?: intent.getStringExtra(Intent.EXTRA_TEXT)
            return raw?.trim()?.takeIf { it.isNotEmpty() }
        }
    }
}
