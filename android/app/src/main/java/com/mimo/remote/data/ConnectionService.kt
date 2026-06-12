package com.mimo.remote.data

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mimo.remote.MainActivity
import com.mimo.remote.R
import com.mimo.remote.data.repository.MimoRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

@AndroidEntryPoint
class ConnectionService : Service() {

    @Inject
    lateinit var repository: MimoRepository

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        const val CHANNEL_ID = "mimo_remote_connection"
        const val NOTIFICATION_ID = 1001
        const val ACTION_CONNECT = "com.mimo.remote.CONNECT"
        const val ACTION_DISCONNECT = "com.mimo.remote.DISCONNECT"
        const val EXTRA_URL = "url"

        fun start(context: Context, url: String) {
            val intent = Intent(context, ConnectionService::class.java).apply {
                action = ACTION_CONNECT
                putExtra(EXTRA_URL, url)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ConnectionService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                startForeground(NOTIFICATION_ID, buildNotification("Connecting..."))
                repository.connect(url)

                // Monitor connection state
                scope.launch {
                    repository.connectionState.collectLatest { state ->
                        val notification = when {
                            state.isConnected -> buildNotification(
                                "Connected to ${state.cliDevice?.name ?: "MiMo Code"}"
                            )
                            state.isConnecting -> buildNotification("Connecting...")
                            else -> buildNotification("Disconnected")
                        }
                        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        nm.notify(NOTIFICATION_ID, notification)
                    }
                }
            }
            ACTION_DISCONNECT -> {
                repository.disconnect()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MiMo Remote Connection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent connection to MiMo Code"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val disconnectIntent = PendingIntent.getBroadcast(
            this,
            1,
            Intent(this, DisconnectReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("MiMo Remote")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_close, "Disconnect", disconnectIntent)
            .build()
    }
}
