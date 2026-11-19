package com.example.widget

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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.VijayBullionApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Keeps both K.R.S. Home Screen Widgets (4-rate Gold/Silver/Cent + Silver MCX/1kg/Cent)
 * updating strictly every 1 second with real-time red/green price ticks even while the app
 * UI is backgrounded. Automatically shuts itself down when zero widgets remain pinned.
 */
class WidgetLiveTickerService : Service() {

    companion object {
        private const val CHANNEL_ID = "krs_widget_live_ticker"
        private const val NOTIFICATION_ID = 4901

        @Volatile
        private var isRunning: Boolean = false

        fun ensureRunning(context: Context) {
            if (isRunning) return
            if (!WidgetPriceTracker.hasActiveWidgets(context)) return
            try {
                val intent = Intent(context.applicationContext, WidgetLiveTickerService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context.applicationContext, intent)
                } else {
                    context.applicationContext.startService(intent)
                }
            } catch (_: Throwable) {
                // Background start restrictions (e.g., ForegroundServiceStartNotAllowedException on Android 12+):
                // Widget provider coroutine ticker and next foreground session will resume cleanly.
            }
        }

        fun stopIfNoWidgets(context: Context) {
            if (!WidgetPriceTracker.hasActiveWidgets(context)) {
                runCatching {
                    context.applicationContext.stopService(
                        Intent(context.applicationContext, WidgetLiveTickerService::class.java)
                    )
                }
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tickerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        promoteToForeground()
        startTickerLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground()
        if (tickerJob?.isActive != true) {
            startTickerLoop()
        }
        return START_STICKY
    }

    private fun promoteToForeground() {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
                if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "Live Widget 1s Price Ticker",
                        NotificationManager.IMPORTANCE_MIN
                    ).apply {
                        description = "Streams real-time 1-second Gold, Silver & Cent rates to pinned home screen widgets"
                        setShowBadge(false)
                        setSound(null, null)
                        enableVibration(false)
                    }
                    nm.createNotificationChannel(channel)
                }
            }

            val openAppIntent = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_krs_app_logo)
                .setContentTitle("K.R.S. Live 1s Widget Sync")
                .setContentText("Updating MCX, Physical & Spot Cent widgets every second")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setSilent(true)
                .setOngoing(true)
                .setContentIntent(openAppIntent)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Throwable) {}
    }

    private fun startTickerLoop() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive) {
                if (!WidgetPriceTracker.hasActiveWidgets(this@WidgetLiveTickerService)) {
                    stopSelf()
                    break
                }

                if (WidgetPriceTracker.shouldBackgroundServicePoll()) {
                    try {
                        val lastKnown = WidgetPriceTracker.loadLastFeed(this@WidgetLiveTickerService)
                        val rawFeed = withTimeoutOrNull(2500L) {
                            VijayBullionApiClient.fetchLatestBroadcast()
                        }
                        val validFeed = if (rawFeed != null) {
                            VijayBullionApiClient.autoCorrectAndValidateFeed(rawFeed, lastKnown)
                        } else {
                            lastKnown
                        }
                        val syncedNow = rawFeed != null || WidgetPriceTracker.hasReceivedRealResponse(this@WidgetLiveTickerService)
                        BullionAppWidgetProvider.updateAllWidgets(
                            this@WidgetLiveTickerService,
                            validFeed,
                            markSynced = syncedNow
                        )
                        SilverAppWidgetProvider.updateAllWidgets(
                            this@WidgetLiveTickerService,
                            validFeed,
                            markSynced = syncedNow
                        )
                    } catch (_: Throwable) {}
                }
                delay(1000L)
            }
        }
    }

    override fun onDestroy() {
        isRunning = false
        tickerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
