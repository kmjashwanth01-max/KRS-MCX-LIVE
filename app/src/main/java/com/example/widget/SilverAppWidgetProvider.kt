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
import com.example.model.PriceTickDirection
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Dedicated Silver Home Screen Widget showing:
 * 1) Silver MCX (1kg Futures LTP + Bid/Ask)
 * 2) Silver 1kg (Physical Bar rate, automatically displaying "Closed" when market/physical desk is closed)
 * 3) Silver Cent ($) (1-second live international Spot Silver USD/oz)
 * All rates update every 1 second with Green/Red movement colors.
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

        @Volatile
        private var lastDirections: WidgetTickDirections = WidgetTickDirections()

        private var tickerJob: Job? = null

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
                    delay(1000L)
                }
            }
        }

        fun formatSilverRatesForWidget(
            feed: VijayBullionFeed,
            marketOpen: Boolean,
            physicalOpen: Boolean,
            directions: WidgetTickDirections = WidgetTickDirections(),
            reopenNote: String = "Reopens 9:00 AM IST"
        ): SilverWidgetDisplay {
            val mcxLtp = if (feed.mcxSilverLtp > 0) feed.mcxSilverLtp else feed.mcxSilverBid
            val mcxPriceStr = if (marketOpen) "₹${String.format("%,.0f", mcxLtp)}" else "CLOSED"
            val mcxArrow = WidgetPriceTracker.arrowForDirection(directions.mcxSilver)
            val mcxSubStr = if (marketOpen) {
                "$mcxArrow B:₹${String.format("%,.0f", feed.mcxSilverBid)} • A:₹${String.format("%,.0f", feed.mcxSilverAsk)}"
            } else {
                reopenNote
            }
            val physicalPriceStr = if (marketOpen && physicalOpen && feed.silverBarRate1kg > 0) {
                "₹${String.format("%,.0f", feed.silverBarRate1kg)}"
            } else {
                "CLOSED"
            }
            val physicalSubStr = if (marketOpen && physicalOpen && feed.silverBarRate1kg > 0) {
                "Physical 1kg ${WidgetPriceTracker.arrowForDirection(directions.silver1kg)}"
            } else {
                reopenNote
            }
            val centPriceStr = if (marketOpen) "$${String.format("%.2f", feed.spotSilverBid)}" else "CLOSED"
            val centSubStr = if (marketOpen) {
                "Spot Cent/Oz ${WidgetPriceTracker.arrowForDirection(directions.spotSilverCent)}"
            } else {
                "Closed at 12 AM • $reopenNote"
            }
            val summaryStr = if (marketOpen) {
                "Silver MCX + 1kg + Cent ($) • 1s Live"
            } else {
                "Market Closed (12 AM) • $reopenNote"
            }
            return SilverWidgetDisplay(
                mcxSilverPrice = mcxPriceStr,
                mcxSilverSub = mcxSubStr,
                physicalSilver1kgPrice = physicalPriceStr,
                physicalSilver1kgSub = physicalSubStr,
                summaryText = summaryStr,
                silverCentPrice = centPriceStr,
                silverCentSub = centSubStr,
                mcxDirection = if (marketOpen) directions.mcxSilver else PriceTickDirection.DOWN,
                physicalDirection = if (marketOpen && physicalOpen) directions.silver1kg else PriceTickDirection.DOWN,
                centDirection = if (marketOpen) directions.spotSilverCent else PriceTickDirection.DOWN
            )
        }

        fun updateWidgetViews(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            feed: VijayBullionFeed,
            directions: WidgetTickDirections = lastDirections
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_silver_rates)
            val timeStr = timeSecondsFormat.format(Date())
            val realResponseSeen = hasReceivedRealApiResponse || WidgetPriceTracker.hasReceivedRealResponse(context)
            val serverClosed = realResponseSeen && feed.isServerExplicitlyClosed
            val marketOpen = BullionAppWidgetProvider.isMcxMarketOpen() && !serverClosed
            val physicalOpen = marketOpen && (!realResponseSeen || feed.hasPhysicalBullionQuotes)
            val reopenNote = BullionAppWidgetProvider.getMarketReopenLabel(serverClosed)

            val display = formatSilverRatesForWidget(feed, marketOpen, physicalOpen, directions, reopenNote)
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

            // Box 1: Silver MCX (1kg)
            views.setTextViewText(R.id.silver_widget_mcx_price, display.mcxSilverPrice)
            views.setTextColor(
                R.id.silver_widget_mcx_price,
                if (marketOpen) WidgetPriceTracker.colorForDirection(display.mcxDirection) else WidgetPriceTracker.COLOR_DOWN
            )
            views.setTextViewText(R.id.silver_widget_mcx_sub, display.mcxSilverSub)
            views.setTextColor(
                R.id.silver_widget_mcx_sub,
                when {
                    !marketOpen -> WidgetPriceTracker.SUB_COLOR_DOWN
                    display.mcxDirection == PriceTickDirection.UNCHANGED -> Color.parseColor("#94A3B8")
                    else -> WidgetPriceTracker.subColorForDirection(display.mcxDirection)
                }
            )

            // Box 2: Silver 1kg (Physical)
            views.setTextViewText(R.id.silver_widget_physical_price, display.physicalSilver1kgPrice)
            views.setTextColor(
                R.id.silver_widget_physical_price,
                if (marketOpen && physicalOpen) WidgetPriceTracker.colorForDirection(display.physicalDirection) else WidgetPriceTracker.COLOR_DOWN
            )
            views.setTextViewText(R.id.silver_widget_physical_sub, display.physicalSilver1kgSub)
            views.setTextColor(
                R.id.silver_widget_physical_sub,
                if (marketOpen && physicalOpen) WidgetPriceTracker.subColorForDirection(display.physicalDirection) else WidgetPriceTracker.SUB_COLOR_DOWN
            )

            // Box 3: Silver Cent ($)
            views.setTextViewText(R.id.silver_widget_cent_price, display.silverCentPrice)
            views.setTextColor(
                R.id.silver_widget_cent_price,
                if (marketOpen) WidgetPriceTracker.colorForDirection(display.centDirection) else WidgetPriceTracker.COLOR_DOWN
            )
            views.setTextViewText(R.id.silver_widget_cent_sub, display.silverCentSub)
            views.setTextColor(
                R.id.silver_widget_cent_sub,
                if (marketOpen) WidgetPriceTracker.subColorForDirection(display.centDirection) else WidgetPriceTracker.SUB_COLOR_DOWN
            )

            views.setTextViewText(R.id.silver_widget_summary, display.summaryText)
            views.setTextColor(
                R.id.silver_widget_summary,
                if (marketOpen) Color.parseColor("#CBD5E1") else Color.parseColor("#FF8A80")
            )
            views.setTextViewText(R.id.silver_widget_status, statusStr)
            views.setTextColor(R.id.silver_widget_status, statusColor)

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
            views.setOnClickPendingIntent(R.id.silver_widget_cent_box, pendingLaunch)

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
                val thisWidget = ComponentName(context, SilverAppWidgetProvider::class.java)
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
        if (intent.action == ACTION_REFRESH_SILVER_RATES) {
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

data class SilverWidgetDisplay(
    val mcxSilverPrice: String,
    val mcxSilverSub: String,
    val physicalSilver1kgPrice: String,
    val physicalSilver1kgSub: String,
    val summaryText: String,
    val silverCentPrice: String = "$52.01",
    val silverCentSub: String = "Spot Cent/Oz •",
    val mcxDirection: PriceTickDirection = PriceTickDirection.UNCHANGED,
    val physicalDirection: PriceTickDirection = PriceTickDirection.UNCHANGED,
    val centDirection: PriceTickDirection = PriceTickDirection.UNCHANGED
)

