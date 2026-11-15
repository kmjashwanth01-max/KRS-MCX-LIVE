package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.VijayBullionApiClient
import com.example.data.VijayBullionFeed
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Dedicated Silver-Only Home Screen Widget showing ONLY:
 * 1) Silver MCX (1kg Futures LTP + Bid/Ask)
 * 2) Silver 1kg (Physical Bar rate, automatically displaying "Closed" when market/physical desk is closed)
 */
class SilverAppWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH_SILVER_RATES = "com.example.widget.ACTION_REFRESH_SILVER_RATES"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val timeSecondsFormat = SimpleDateFormat("hh:mm:ss a", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }

        @Volatile
        private var lastKnownFeed: VijayBullionFeed = VijayBullionFeed()

        @Volatile
        private var isLastFetchSuccessful: Boolean = true

        @Volatile
        private var hasReceivedRealApiResponse: Boolean = false

        private var tickerJob: Job? = null

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
                            hasReceivedRealApiResponse = true
                            updateAllWidgets(appContext, liveFeed)
                        } else {
                            isLastFetchSuccessful = false
                            updateAllWidgets(appContext, lastKnownFeed)
                        }
                    } catch (_: Exception) {
                        isLastFetchSuccessful = false
                        updateAllWidgets(appContext, lastKnownFeed)
                    }
                    delay(1000L)
                }
            }
        }

        fun formatSilverRatesForWidget(
            feed: VijayBullionFeed,
            marketOpen: Boolean,
            physicalOpen: Boolean
        ): SilverWidgetDisplay {
            val mcxLtp = if (feed.mcxSilverLtp > 0) feed.mcxSilverLtp else feed.mcxSilverBid
            val mcxPriceStr = "₹${String.format("%,.0f", mcxLtp)}"
            val mcxSubStr = "B:₹${String.format("%,.0f", feed.mcxSilverBid)} • A:₹${String.format("%,.0f", feed.mcxSilverAsk)}"
            val physicalPriceStr = if (physicalOpen && feed.silverBarRate1kg > 0) {
                "₹${String.format("%,.0f", feed.silverBarRate1kg)}"
            } else {
                "Closed"
            }
            val physicalSubStr = if (physicalOpen && feed.silverBarRate1kg > 0) {
                "Physical 1kg"
            } else {
                "Market Closed"
            }
            val summaryStr = if (marketOpen) {
                "Silver MCX + 1kg Physical • 1s Live"
            } else {
                "MCX Closed • Opens Mon-Fri 09:00 AM IST"
            }
            return SilverWidgetDisplay(
                mcxSilverPrice = mcxPriceStr,
                mcxSilverSub = mcxSubStr,
                physicalSilver1kgPrice = physicalPriceStr,
                physicalSilver1kgSub = physicalSubStr,
                summaryText = summaryStr
            )
        }

        fun updateWidgetViews(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            feed: VijayBullionFeed
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_silver_rates)
            val timeStr = timeSecondsFormat.format(Date())
            val marketOpen = BullionAppWidgetProvider.isMcxMarketOpen() &&
                !(hasReceivedRealApiResponse && feed.isServerExplicitlyClosed)
            val physicalOpen = marketOpen && (!hasReceivedRealApiResponse || feed.hasPhysicalBullionQuotes)

            val display = formatSilverRatesForWidget(feed, marketOpen, physicalOpen)
            val statusStr = when {
                !marketOpen -> "🔴 Closed • $timeStr"
                !isLastFetchSuccessful -> "🟡 Syncing • $timeStr"
                else -> "🟢 Live 1s • $timeStr"
            }

            views.setTextViewText(R.id.silver_widget_mcx_price, display.mcxSilverPrice)
            views.setTextViewText(R.id.silver_widget_mcx_sub, display.mcxSilverSub)
            views.setTextViewText(R.id.silver_widget_physical_price, display.physicalSilver1kgPrice)
            views.setTextColor(
                R.id.silver_widget_physical_price,
                if (physicalOpen) Color.WHITE else Color.parseColor("#FF5252")
            )
            views.setTextViewText(R.id.silver_widget_physical_sub, display.physicalSilver1kgSub)
            views.setTextColor(
                R.id.silver_widget_physical_sub,
                if (physicalOpen) Color.parseColor("#81C784") else Color.parseColor("#FF8A80")
            )
            views.setTextViewText(R.id.silver_widget_summary, display.summaryText)
            views.setTextViewText(R.id.silver_widget_status, statusStr)

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingLaunch = PendingIntent.getActivity(
                context,
                appWidgetId + 4000,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.silver_widget_root, pendingLaunch)
            views.setOnClickPendingIntent(R.id.silver_widget_mcx_box, pendingLaunch)
            views.setOnClickPendingIntent(R.id.silver_widget_physical_box, pendingLaunch)

            val refreshIntent = Intent(context, SilverAppWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_SILVER_RATES
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            }
            val pendingRefresh = PendingIntent.getBroadcast(
                context,
                appWidgetId + 5000,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.silver_widget_btn_refresh, pendingRefresh)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun updateAllWidgets(context: Context, feed: VijayBullionFeed) {
            try {
                lastKnownFeed = feed
                val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
                val thisWidget = ComponentName(context, SilverAppWidgetProvider::class.java)
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
        if (intent.action == ACTION_REFRESH_SILVER_RATES) {
            scope.launch {
                try {
                    val liveFeed = VijayBullionApiClient.fetchLatestBroadcast()
                    if (liveFeed != null) {
                        lastKnownFeed = liveFeed
                        isLastFetchSuccessful = true
                        hasReceivedRealApiResponse = true
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

data class SilverWidgetDisplay(
    val mcxSilverPrice: String,
    val mcxSilverSub: String,
    val physicalSilver1kgPrice: String,
    val physicalSilver1kgSub: String,
    val summaryText: String
)
