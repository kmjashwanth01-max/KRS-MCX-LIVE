package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.VijayBullionApiClient
import com.example.data.VijayBullionFeed
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class BullionAppWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH_RATES = "com.example.widget.ACTION_REFRESH_RATES"
        const val ACTION_START_TICKER = "com.example.widget.ACTION_START_TICKER"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val timeSecondsFormat = SimpleDateFormat("hh:mm:ss a", Locale.ENGLISH)

        // Cached latest feed so cold widget updates have instant accurate values
        @Volatile
        private var lastKnownFeed: VijayBullionFeed = VijayBullionFeed()

        @Volatile
        private var isLastFetchSuccessful: Boolean = true

        private var tickerJob: Job? = null

        fun isMcxMarketOpen(): Boolean {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val minute = cal.get(Calendar.MINUTE)

            val isWeekday = dayOfWeek in Calendar.MONDAY..Calendar.FRIDAY
            val currentMinutesOfDay = hour * 60 + minute
            // MCX Session: 09:00 AM (540 mins) to 11:30 PM (1410 mins)
            return isWeekday && (currentMinutesOfDay in 540..1410)
        }

        fun startOneSecondWidgetTicker(context: Context) {
            val appContext = context.applicationContext
            if (tickerJob?.isActive == true) return

            tickerJob = scope.launch {
                while (isActive) {
                    try {
                        val liveFeed = VijayBullionApiClient.fetchLatestBroadcast()
                        if (liveFeed != null) {
                            lastKnownFeed = liveFeed
                            isLastFetchSuccessful = true
                            updateAllWidgets(appContext, liveFeed)
                        } else {
                            isLastFetchSuccessful = false
                            updateAllWidgets(appContext, lastKnownFeed)
                        }
                    } catch (_: Exception) {
                        isLastFetchSuccessful = false
                        updateAllWidgets(appContext, lastKnownFeed)
                    }
                    delay(1000L) // Exact 1-second continuous live update
                }
            }
        }

        fun updateWidgetViews(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            feed: VijayBullionFeed
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_bullion_rates)

            // 1. Format Live Bullion Prices: Physical Indian Gold/Silver + International Spot Cent Rates (MCX removed per user request)
            val gold10gStr = "₹${String.format("%,.0f", feed.goldBarRate10g)}"
            val silver1kgStr = "₹${String.format("%,.0f", feed.silverBarRate1kg)}"
            val goldCentStr = "$${String.format("%.2f", feed.spotGoldBid)}"
            val silverCentStr = "$${String.format("%.2f", feed.spotSilverBid)}"
            val spotStr = "Forex: USD/INR ₹${String.format("%.2f", feed.spotInrBid)} • Spot 1s"
            val timeStr = timeSecondsFormat.format(Date())
            val marketOpen = isMcxMarketOpen()

            val statusStr = when {
                !isLastFetchSuccessful -> "🟡 Syncing • $timeStr"
                !marketOpen -> "🔴 Closed • $timeStr"
                else -> "🟢 Live 1s • $timeStr"
            }

            views.setTextViewText(R.id.widget_gold_price, gold10gStr)
            views.setTextViewText(R.id.widget_silver_price, silver1kgStr)
            views.setTextViewText(R.id.widget_mcx_gold_label, "GOLD CENT ($)")
            views.setTextViewText(R.id.widget_mcx_gold_price, goldCentStr)
            views.setTextViewText(R.id.widget_mcx_gold_sub, "Spot Cent / Oz")
            views.setTextViewText(R.id.widget_mcx_silver_label, "SILVER CENT ($)")
            views.setTextViewText(R.id.widget_mcx_silver_price, silverCentStr)
            views.setTextViewText(R.id.widget_mcx_silver_sub, "Spot Cent / Oz")
            views.setTextViewText(R.id.widget_spot_summary, spotStr)
            views.setTextViewText(R.id.widget_status, statusStr)

            // 2. Click entire widget body or any rate card -> Opens full App (MainActivity)
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingLaunch = PendingIntent.getActivity(
                context,
                appWidgetId,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingLaunch)
            views.setOnClickPendingIntent(R.id.widget_gold_box, pendingLaunch)
            views.setOnClickPendingIntent(R.id.widget_silver_box, pendingLaunch)
            views.setOnClickPendingIntent(R.id.widget_mcx_gold_box, pendingLaunch)
            views.setOnClickPendingIntent(R.id.widget_mcx_silver_box, pendingLaunch)

            // 3. Click refresh icon -> Immediate live rate sync from Vijay Bullion API
            val refreshIntent = Intent(context, BullionAppWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_RATES
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            }
            val pendingRefresh = PendingIntent.getBroadcast(
                context,
                appWidgetId + 1000,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_refresh, pendingRefresh)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun updateAllWidgets(context: Context, feed: VijayBullionFeed) {
            try {
                lastKnownFeed = feed
                val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
                val thisWidget = ComponentName(context, BullionAppWidgetProvider::class.java)
                val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                if (allWidgetIds != null && allWidgetIds.isNotEmpty()) {
                    for (id in allWidgetIds) {
                        try {
                            updateWidgetViews(context, appWidgetManager, id, feed)
                        } catch (_: Throwable) {}
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        startOneSecondWidgetTicker(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        startOneSecondWidgetTicker(context)
        for (appWidgetId in appWidgetIds) {
            updateWidgetViews(context, appWidgetManager, appWidgetId, lastKnownFeed)
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        tickerJob?.cancel()
        tickerJob = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        startOneSecondWidgetTicker(context)
        if (intent.action == ACTION_REFRESH_RATES) {
            scope.launch {
                try {
                    val liveFeed = VijayBullionApiClient.fetchLatestBroadcast()
                    if (liveFeed != null) {
                        lastKnownFeed = liveFeed
                        isLastFetchSuccessful = true
                        updateAllWidgets(context, liveFeed)
                    } else {
                        isLastFetchSuccessful = false
                        updateAllWidgets(context, lastKnownFeed)
                    }
                } catch (_: Exception) {
                    isLastFetchSuccessful = false
                    updateAllWidgets(context, lastKnownFeed)
                }
            }
        }
    }
}
