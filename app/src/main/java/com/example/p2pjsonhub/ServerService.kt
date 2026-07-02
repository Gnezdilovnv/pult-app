package com.example.p2pjsonhub

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class ServerService : Service() {
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "p2p_json_channel"
        fun start(context: Context) {
            context.startService(Intent(context, ServerService::class.java))
        }
        fun stop(context: Context) {
            context.stopService(Intent(context, ServerService::class.java))
        }
    }

    private var httpServer: HttpServer? = null
    private var p2pManager: P2PManager? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Запуск..."))
        Storage.init(this)
        p2pManager = P2PManager(this)
        httpServer = HttpServer(8080)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        httpServer?.start()
        p2pManager?.start()
        updateNotification("Сервер активен на порту 8080")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        httpServer?.stop()
        p2pManager?.stop()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "P2P JSON Hub",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("P2P JSON Hub")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }
}
