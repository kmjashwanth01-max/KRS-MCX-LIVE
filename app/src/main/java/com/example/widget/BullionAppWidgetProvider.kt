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

class BullionAppWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH_RATES = "com.example.widget.ACTION_REFRESH_RATES"
        const val ACTION_START_TICKER = "com.example.widget.ACTION_START_TICKER"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val timeSecondsFormat = SimpleDateFormat("hh:mm:ss a", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }

        // Cached latest feed so cold widget updates have instant accurate values
        @Volatile
        private var lastKnownFeed: VijayBullionFeed = VijayBullionFeed()

        @Volatile
        private var isLastFetchSuccessful: Boolean = true

        @Volatile
        private var hasReceivedRealApiResponse: Boolean = false

        @Volatile
        private var lastDirections: WidgetTickDirections = WidgetTickDirections()

        private var tickerJob: Job? = null

        fun isMcxMarketOpen(): Boolean {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
            return com.example.data.McxRatesRepository.isMcxMarketOpenForCalendar(cal)
        }

        fun getMarketReopenLabel(serverExplicitlyClosed: Boolean = false): String {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
            return com.example.data.McxRatesRepository.getCompactReopenLabel(cal, serverExplicitlyClosed)
        }

        fun startOneSecondWidgetTicker(context: Context) {
            val appContext = context.applicationContext
            WidgetLiveTickerService.ensureRunning(appContext)
            if (tickerJob?.isActive == true) return

            lastKnownFeed = WidgetPriceTracker.loadLastFeed(appContext)
            hasReceivedRealApiResponse = hasReceivedRealApiResponse || WidgetPriceTracker.hasReceivedRealResponse(appContext)

            tickerJob = scope.launch {
                while (isActive) {
                    if (!WidgetPriceTracker.hasActiveWidgets(appContext)) {
                        delay(2000L)
                        continue
                    }
                    try {
                        if (WidgetPriceTracker.shouldBackgroundServicePoll()) {
                            val rawFeed = withTimeoutOrNull(2500L) {
                                VijayBullionApiClient.fetchLatestBroadcast()
                            }
                            if (rawFeed != null) {
                                val validated = VijayBullionApiClient.autoCorrectAndValidateFeed(rawFeed, lastKnownFeed)
                                updateAllWidgets(appContext, validated, markSynced = true)
                            } else {
                                updateAllWidgets(appContext, lastKnownFeed, markSynced = hasReceivedRealApiResponse)
                            }
                        }
                    } catch (_: Exception) {
                        updateAllWidgets(appContext, lastKnownFeed, markSynced = hasReceivedRealApiResponse)
                    }
                    delay(1000L) // Exact 1-second continuous live update
                }
            }
        }

        fun updateWidgetViews(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            feed: VijayBullionFeed,
            directions: WidgetTickDirections = lastDirections
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_bullion_rates)

            val timeStr = timeSecondsFormat.format(Date())
            val realResponseSeen = hasReceivedRealApiResponse || WidgetPriceTracker.hasReceivedRealResponse(context)
            val serverClosed = realResponseSeen && feed.isServerExplicitlyClosed
            val marketOpen = isMcxMarketOpen() && !serverClosed
            val physicalOpen = marketOpen && (!realResponseSeen || feed.hasPhysicalBullionQuotes)
            val reopenNote = getMarketReopenLabel(serverClosed)

            val gold10gStr = if (physicalOpen) "₹${String.format("%,.0f", feed.goldBarRate10g)}" else "CLOSED"
            val silver1kgStr = if (physicalOpen) "₹${String.format("%,.0f", feed.silverBarRate1kg)}" else "CLOSED"
            val goldCentStr = if (marketOpen) "$${String.format("%.2f", feed.spotGoldBid)}" else "CLOSED"
            val silverCentStr = if (marketOpen) "$${String.format("%.2f", feed.spotSilverBid)}" else "CLOSED"

            val inrArrow = WidgetPriceTracker.arrowForDirection(directions.spotInr)
            val spotStr = if (marketOpen) {
                "Forex: USD/INR ₹${String.format("%.2f", feed.spotInrBid)} $inrArrow • Spot 1s"
            } else {
                "Closed at 12:00 AM IST • $reopenNote"
            }

            val statusStr = when {
                !marketOpen -> "🔴 Closed • $reopenNote • $timeStr"
                isLastFetchSuccessful || realResponseSeen -> "🟢 Live 1s • $timeStr"
                else -> "🟡 Syncing • $timeStr"
            }
            val statusColor = when {
                !marketOpen -> Color.parseColor("#FF8A80")
                isLastFetchSuccessful || realResponseSeen -> Color.parseColor("#00E676")
                else -> Color.parseColor("#FFD700")
            }

            // Gold 999 (10g)
            views.setTextViewText(R.id.widget_gold_price, gold10gStr)
            if (physicalOpen) {
                views.setTextColor(R.id.widget_gold_price, WidgetPriceTracker.colorForDirection(directions.gold999))
                views.setTextViewText(R.id.widget_gold_sub, "Physical ${WidgetPriceTracker.arrowForDirection(directions.gold999)}")
                views.setTextColor(R.id.widget_gold_sub, WidgetPriceTracker.subColorForDirection(directions.gold999))
            } else {
                views.setTextColor(R.id.widget_gold_price, WidgetPriceTracker.COLOR_DOWN)
                views.setTextViewText(R.id.widget_gold_sub, reopenNote)
                views.setTextColor(R.id.widget_gold_sub, WidgetPriceTracker.SUB_COLOR_DOWN)
            }

            // Silver 1kg
            views.setTextViewText(R.id.widget_silver_price, silver1kgStr)
            if (physicalOpen) {
                views.setTextColor(R.id.widget_silver_price, WidgetPriceTracker.colorForDirection(directions.silver1kg))
                views.setTextViewText(R.id.widget_silver_sub, "Physical ${WidgetPriceTracker.arrowForDirection(directions.silver1kg)}")
                views.setTextColor(R.id.widget_silver_sub, WidgetPriceTracker.subColorForDirection(directions.silver1kg))
            } else {
                views.setTextColor(R.id.widget_silver_price, WidgetPriceTracker.COLOR_DOWN)
                views.setTextViewText(R.id.widget_silver_sub, reopenNote)
                views.setTextColor(R.id.widget_silver_sub, WidgetPriceTracker.SUB_COLOR_DOWN)
            }

            // Gold Cent ($)
            views.setTextViewText(R.id.widget_mcx_gold_label, "GOLD CENT ($)")
            views.setTextViewText(R.id.widget_mcx_gold_price, goldCentStr)
            if (marketOpen) {
                views.setTextColor(R.id.widget_mcx_gold_price, WidgetPriceTracker.colorForDirection(directions.spotGoldCent))
                views.setTextViewText(
                    R.id.widget_mcx_gold_sub,
                    "Spot Cent/Oz ${WidgetPriceTracker.arrowForDirection(directions.spotGoldCent)}"
                )
                views.setTextColor(R.id.widget_mcx_gold_sub, WidgetPriceTracker.subColorForDirection(directions.spotGoldCent))
            } else {
                views.setTextColor(R.id.widget_mcx_gold_price, WidgetPriceTracker.COLOR_DOWN)
                views.setTextViewText(R.id.widget_mcx_gold_sub, reopenNote)
                views.setTextColor(R.id.widget_mcx_gold_sub, WidgetPriceTracker.SUB_COLOR_DOWN)
            }

            // Silver Cent ($)
            views.setTextViewText(R.id.widget_mcx_silver_label, "SILVER CENT ($)")
            views.setTextViewText(R.id.widget_mcx_silver_price, silverCentStr)
            if (marketOpen) {
                views.setTextColor(R.id.widget_mcx_silver_price, WidgetPriceTracker.colorForDirection(directions.spotSilverCent))
                views.setTextViewText(
                    R.id.widget_mcx_silver_sub,
                    "Spot Cent/Oz ${WidgetPriceTracker.arrowForDirection(directions.spotSilverCent)}"
                )
                views.setTextColor(R.id.widget_mcx_silver_sub, WidgetPriceTracker.subColorForDirection(directions.spotSilverCent))
            } else {
                views.setTextColor(R.id.widget_mcx_silver_price, WidgetPriceTracker.COLOR_DOWN)
                views.setTextViewText(R.id.widget_mcx_silver_sub, reopenNote)
                views.setTextColor(R.id.widget_mcx_silver_sub, WidgetPriceTracker.SUB_COLOR_DOWN)
            }

            views.setTextViewText(R.id.widget_spot_summary, spotStr)
            views.setTextColor(
                R.id.widget_spot_summary,
                when {
                    !marketOpen -> Color.parseColor("#FF8A80")
                    directions.spotInr == com.example.model.PriceTickDirection.UNCHANGED -> Color.parseColor("#9E9EA8")
                    else -> WidgetPriceTracker.subColorForDirection(directions.spotInr)
                }
            )
            views.setTextViewText(R.id.widget_status, statusStr)
            views.setTextColor(R.id.widget_status, statusColor)

            // Click entire widget body or any rate card -> Opens full App (MainActivity)
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

            // Click refresh icon -> Immediate live rate sync
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

        fun updateAllWidgets(
            context: Context,
            feed: VijayBullionFeed,
            markSynced: Boolean? = null
        ) {
            try {
                lastKnownFeed = feed
                if (markSynced == true) {
                    isLastFetchSuccessful = true
                    hasReceivedRealApiResponse = true
                } else if (markSynced == false && !hasReceivedRealApiResponse) {
                    isLastFetchSuccessful = false
                }
                val dirs = WidgetPriceTracker.updateWithFeed(
                    context = context,
                    newFeed = feed,
                    markRealApi = (markSynced == true)
                )
                lastDirections = dirs
                val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
                val thisWidget = ComponentName(context, BullionAppWidgetProvider::class.java)
                val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                if (allWidgetIds != null && allWidgetIds.isNotEmpty()) {
                    for (id in allWidgetIds) {
                        try {
                            updateWidgetViews(context, appWidgetManager, id, feed, dirs)
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
        lastKnownFeed = WidgetPriceTracker.loadLastFeed(context)
        val dirs = WidgetPriceTracker.updateWithFeed(context, lastKnownFeed)
        lastDirections = dirs
        startOneSecondWidgetTicker(context)
        for (appWidgetId in appWidgetIds) {
            updateWidgetViews(context, appWidgetManager, appWidgetId, lastKnownFeed, dirs)
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        tickerJob?.cancel()
        tickerJob = null
        WidgetLiveTickerService.stopIfNoWidgets(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        startOneSecondWidgetTicker(context)
        if (intent.action == ACTION_REFRESH_RATES) {
            scope.launch {
                try {
                    val liveFeed = withTimeoutOrNull(2500L) {
                        VijayBullionApiClient.fetchLatestBroadcast()
                    }
                    if (liveFeed != null) {
                        val validated = VijayBullionApiClient.autoCorrectAndValidateFeed(liveFeed, lastKnownFeed)
                        updateAllWidgets(context, validated, markSynced = true)
                    } else {
                        updateAllWidgets(context, lastKnownFeed, markSynced = hasReceivedRealApiResponse)
                    }
                } catch (_: Exception) {
                    updateAllWidgets(context, lastKnownFeed, markSynced = hasReceivedRealApiResponse)
                }
            }
        }
    }
}

