package com.example.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import com.example.data.VijayBullionFeed
import com.example.model.PriceTickDirection

data class WidgetTickDirections(
    val gold999: PriceTickDirection = PriceTickDirection.UNCHANGED,
    val silver1kg: PriceTickDirection = PriceTickDirection.UNCHANGED,
    val mcxGold: PriceTickDirection = PriceTickDirection.UNCHANGED,
    val mcxSilver: PriceTickDirection = PriceTickDirection.UNCHANGED,
    val spotGoldCent: PriceTickDirection = PriceTickDirection.UNCHANGED,
    val spotSilverCent: PriceTickDirection = PriceTickDirection.UNCHANGED,
    val spotInr: PriceTickDirection = PriceTickDirection.UNCHANGED
)

/**
 * Tracks real-time 1-second price movement directions (Green = UP, Red = DOWN)
 * across both home screen widgets, persists last verified quotes across background restarts,
 * and coordinates polling so McxRatesRepository and WidgetLiveTickerService never double-fetch.
 */
object WidgetPriceTracker {

    private const val PREFS_NAME = "krs_widget_tick_prefs"

    val COLOR_UP: Int = Color.parseColor("#00E676")
    val COLOR_DOWN: Int = Color.parseColor("#FF5252")
    val COLOR_NEUTRAL: Int = Color.WHITE

    val SUB_COLOR_UP: Int = Color.parseColor("#69F0AE")
    val SUB_COLOR_DOWN: Int = Color.parseColor("#FF8A80")
    val SUB_COLOR_NEUTRAL: Int = Color.parseColor("#9E9EA8")

    @Volatile
    private var cachedFeed: VijayBullionFeed? = null

    @Volatile
    private var cachedDirections: WidgetTickDirections = WidgetTickDirections()

    @Volatile
    private var lastRepoPushMs: Long = 0L

    @Volatile
    private var hasRealApi: Boolean = false

    fun recordRepoPush(nowMs: Long = System.currentTimeMillis()) {
        lastRepoPushMs = nowMs
    }

    fun shouldBackgroundServicePoll(nowMs: Long = System.currentTimeMillis()): Boolean {
        return (nowMs - lastRepoPushMs) > 1500L
    }

    fun colorForDirection(direction: PriceTickDirection): Int = when (direction) {
        PriceTickDirection.UP -> COLOR_UP
        PriceTickDirection.DOWN -> COLOR_DOWN
        PriceTickDirection.UNCHANGED -> COLOR_NEUTRAL
    }

    fun subColorForDirection(direction: PriceTickDirection): Int = when (direction) {
        PriceTickDirection.UP -> SUB_COLOR_UP
        PriceTickDirection.DOWN -> SUB_COLOR_DOWN
        PriceTickDirection.UNCHANGED -> SUB_COLOR_NEUTRAL
    }

    fun arrowForDirection(direction: PriceTickDirection): String = when (direction) {
        PriceTickDirection.UP -> "▲"
        PriceTickDirection.DOWN -> "▼"
        PriceTickDirection.UNCHANGED -> "•"
    }

    /**
     * Pure comparison of incoming prices vs previous prices.
     * - Strictly UP (> prev) -> Green
     * - Strictly DOWN (< prev) -> Red
     * - Equal (== prev) -> holds previous non-UNCHANGED color, or on cold start compares vs intraday midpoint.
     */
    fun evaluateFeedTicks(
        prevFeed: VijayBullionFeed?,
        prevDirs: WidgetTickDirections?,
        newFeed: VijayBullionFeed
    ): WidgetTickDirections {
        fun resolve(
            newPrice: Double,
            prevPrice: Double?,
            prevDir: PriceTickDirection?,
            high: Double = 0.0,
            low: Double = 0.0
        ): PriceTickDirection {
            if (newPrice <= 0.0) return prevDir ?: PriceTickDirection.UNCHANGED
            if (prevPrice != null && prevPrice > 0.0) {
                return when {
                    newPrice > prevPrice -> PriceTickDirection.UP
                    newPrice < prevPrice -> PriceTickDirection.DOWN
                    else -> prevDir?.takeIf { it != PriceTickDirection.UNCHANGED } ?: PriceTickDirection.UNCHANGED
                }
            }
            if (high > low && low > 0.0) {
                val mid = (high + low) / 2.0
                return if (newPrice >= mid) PriceTickDirection.UP else PriceTickDirection.DOWN
            }
            return prevDir?.takeIf { it != PriceTickDirection.UNCHANGED } ?: PriceTickDirection.UP
        }

        val newMcxGold = if (newFeed.mcxGoldLtp > 0.0) newFeed.mcxGoldLtp else newFeed.mcxGoldBid
        val prevMcxGold = prevFeed?.let { if (it.mcxGoldLtp > 0.0) it.mcxGoldLtp else it.mcxGoldBid }

        val newMcxSilver = if (newFeed.mcxSilverLtp > 0.0) newFeed.mcxSilverLtp else newFeed.mcxSilverBid
        val prevMcxSilver = prevFeed?.let { if (it.mcxSilverLtp > 0.0) it.mcxSilverLtp else it.mcxSilverBid }

        return WidgetTickDirections(
            gold999 = resolve(
                newFeed.goldBarRate10g,
                prevFeed?.goldBarRate10g,
                prevDirs?.gold999
            ),
            silver1kg = resolve(
                newFeed.silverBarRate1kg,
                prevFeed?.silverBarRate1kg,
                prevDirs?.silver1kg
            ),
            mcxGold = resolve(
                newMcxGold,
                prevMcxGold,
                prevDirs?.mcxGold,
                newFeed.mcxGoldHigh,
                newFeed.mcxGoldLow
            ),
            mcxSilver = resolve(
                newMcxSilver,
                prevMcxSilver,
                prevDirs?.mcxSilver,
                newFeed.mcxSilverHigh,
                newFeed.mcxSilverLow
            ),
            spotGoldCent = resolve(
                newFeed.spotGoldBid,
                prevFeed?.spotGoldBid,
                prevDirs?.spotGoldCent,
                newFeed.spotGoldHigh,
                newFeed.spotGoldLow
            ),
            spotSilverCent = resolve(
                newFeed.spotSilverBid,
                prevFeed?.spotSilverBid,
                prevDirs?.spotSilverCent,
                newFeed.spotSilverHigh,
                newFeed.spotSilverLow
            ),
            spotInr = resolve(
                newFeed.spotInrBid,
                prevFeed?.spotInrBid,
                prevDirs?.spotInr,
                newFeed.spotInrHigh,
                newFeed.spotInrLow
            )
        )
    }

    @Synchronized
    fun loadLastFeed(context: Context): VijayBullionFeed {
        cachedFeed?.let { return it }
        return try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.contains("spot_gold_bid")) {
                VijayBullionFeed().also { cachedFeed = it }
            } else {
                val restored = VijayBullionFeed(
                    goldBarRate10g = prefs.getFloat("gold_999", 146040f).toDouble(),
                    silverBarRate1kg = prefs.getFloat("silver_1kg", 189240f).toDouble(),
                    mcxGoldBid = prefs.getFloat("mcx_gold_bid", 145036f).toDouble(),
                    mcxGoldAsk = prefs.getFloat("mcx_gold_ask", 145074f).toDouble(),
                    mcxGoldHigh = prefs.getFloat("mcx_gold_high", 146109f).toDouble(),
                    mcxGoldLow = prefs.getFloat("mcx_gold_low", 144555f).toDouble(),
                    mcxGoldLtp = prefs.getFloat("mcx_gold_ltp", 145050f).toDouble(),
                    mcxSilverBid = prefs.getFloat("mcx_silver_bid", 185085f).toDouble(),
                    mcxSilverAsk = prefs.getFloat("mcx_silver_ask", 185139f).toDouble(),
                    mcxSilverHigh = prefs.getFloat("mcx_silver_high", 185339f).toDouble(),
                    mcxSilverLow = prefs.getFloat("mcx_silver_low", 184800f).toDouble(),
                    mcxSilverLtp = prefs.getFloat("mcx_silver_ltp", 185139f).toDouble(),
                    spotGoldBid = prefs.getFloat("spot_gold_bid", 4070.77f).toDouble(),
                    spotGoldAsk = prefs.getFloat("spot_gold_ask", 4071.09f).toDouble(),
                    spotGoldHigh = prefs.getFloat("spot_gold_high", 4107.00f).toDouble(),
                    spotGoldLow = prefs.getFloat("spot_gold_low", 4035.58f).toDouble(),
                    spotSilverBid = prefs.getFloat("spot_silver_bid", 52.01f).toDouble(),
                    spotSilverAsk = prefs.getFloat("spot_silver_ask", 52.02f).toDouble(),
                    spotSilverHigh = prefs.getFloat("spot_silver_high", 52.21f).toDouble(),
                    spotSilverLow = prefs.getFloat("spot_silver_low", 51.17f).toDouble(),
                    spotInrBid = prefs.getFloat("spot_inr_bid", 93.90f).toDouble(),
                    spotInrAsk = prefs.getFloat("spot_inr_ask", 93.94f).toDouble(),
                    spotInrHigh = prefs.getFloat("spot_inr_high", 94.52f).toDouble(),
                    spotInrLow = prefs.getFloat("spot_inr_low", 93.81f).toDouble(),
                    hasPhysicalBullionQuotes = prefs.getBoolean("has_physical", true),
                    isServerExplicitlyClosed = prefs.getBoolean("server_closed", false),
                    isBroadcastOpen = prefs.getBoolean("broadcast_open", true)
                )
                hasRealApi = prefs.getBoolean("has_real_api", false)
                cachedDirections = WidgetTickDirections(
                    gold999 = parseDir(prefs.getString("dir_gold999", null)),
                    silver1kg = parseDir(prefs.getString("dir_silver1kg", null)),
                    mcxGold = parseDir(prefs.getString("dir_mcxGold", null)),
                    mcxSilver = parseDir(prefs.getString("dir_mcxSilver", null)),
                    spotGoldCent = parseDir(prefs.getString("dir_spotGoldCent", null)),
                    spotSilverCent = parseDir(prefs.getString("dir_spotSilverCent", null)),
                    spotInr = parseDir(prefs.getString("dir_spotInr", null))
                )
                cachedFeed = restored
                restored
            }
        } catch (_: Throwable) {
            VijayBullionFeed().also { cachedFeed = it }
        }
    }

    @Synchronized
    fun hasReceivedRealResponse(context: Context): Boolean {
        if (hasRealApi) return true
        return try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getBoolean("has_real_api", false).also { hasRealApi = it }
        } catch (_: Throwable) {
            false
        }
    }

    @Synchronized
    fun updateWithFeed(
        context: Context,
        newFeed: VijayBullionFeed,
        markRealApi: Boolean = false
    ): WidgetTickDirections {
        val prev = cachedFeed ?: loadLastFeed(context)
        val dirs = evaluateFeedTicks(prev, cachedDirections, newFeed)
        val changed = prev != newFeed || dirs != cachedDirections || (markRealApi && !hasRealApi)

        cachedFeed = newFeed
        cachedDirections = dirs
        if (markRealApi) {
            hasRealApi = true
        }

        if (changed) {
            runCatching {
                context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putFloat("gold_999", newFeed.goldBarRate10g.toFloat())
                    .putFloat("silver_1kg", newFeed.silverBarRate1kg.toFloat())
                    .putFloat("mcx_gold_bid", newFeed.mcxGoldBid.toFloat())
                    .putFloat("mcx_gold_ask", newFeed.mcxGoldAsk.toFloat())
                    .putFloat("mcx_gold_high", newFeed.mcxGoldHigh.toFloat())
                    .putFloat("mcx_gold_low", newFeed.mcxGoldLow.toFloat())
                    .putFloat("mcx_gold_ltp", newFeed.mcxGoldLtp.toFloat())
                    .putFloat("mcx_silver_bid", newFeed.mcxSilverBid.toFloat())
                    .putFloat("mcx_silver_ask", newFeed.mcxSilverAsk.toFloat())
                    .putFloat("mcx_silver_high", newFeed.mcxSilverHigh.toFloat())
                    .putFloat("mcx_silver_low", newFeed.mcxSilverLow.toFloat())
                    .putFloat("mcx_silver_ltp", newFeed.mcxSilverLtp.toFloat())
                    .putFloat("spot_gold_bid", newFeed.spotGoldBid.toFloat())
                    .putFloat("spot_gold_ask", newFeed.spotGoldAsk.toFloat())
                    .putFloat("spot_gold_high", newFeed.spotGoldHigh.toFloat())
                    .putFloat("spot_gold_low", newFeed.spotGoldLow.toFloat())
                    .putFloat("spot_silver_bid", newFeed.spotSilverBid.toFloat())
                    .putFloat("spot_silver_ask", newFeed.spotSilverAsk.toFloat())
                    .putFloat("spot_silver_high", newFeed.spotSilverHigh.toFloat())
                    .putFloat("spot_silver_low", newFeed.spotSilverLow.toFloat())
                    .putFloat("spot_inr_bid", newFeed.spotInrBid.toFloat())
                    .putFloat("spot_inr_ask", newFeed.spotInrAsk.toFloat())
                    .putFloat("spot_inr_high", newFeed.spotInrHigh.toFloat())
                    .putFloat("spot_inr_low", newFeed.spotInrLow.toFloat())
                    .putBoolean("has_physical", newFeed.hasPhysicalBullionQuotes)
                    .putBoolean("server_closed", newFeed.isServerExplicitlyClosed)
                    .putBoolean("broadcast_open", newFeed.isBroadcastOpen)
                    .putBoolean("has_real_api", hasRealApi)
                    .putString("dir_gold999", dirs.gold999.name)
                    .putString("dir_silver1kg", dirs.silver1kg.name)
                    .putString("dir_mcxGold", dirs.mcxGold.name)
                    .putString("dir_mcxSilver", dirs.mcxSilver.name)
                    .putString("dir_spotGoldCent", dirs.spotGoldCent.name)
                    .putString("dir_spotSilverCent", dirs.spotSilverCent.name)
                    .putString("dir_spotInr", dirs.spotInr.name)
                    .apply()
            }
        }
        return dirs
    }

    fun hasActiveWidgets(context: Context): Boolean {
        return try {
            val mgr = AppWidgetManager.getInstance(context) ?: return false
            val bullionIds = mgr.getAppWidgetIds(ComponentName(context, BullionAppWidgetProvider::class.java))
            val silverIds = mgr.getAppWidgetIds(ComponentName(context, SilverAppWidgetProvider::class.java))
            (bullionIds != null && bullionIds.isNotEmpty()) ||
                (silverIds != null && silverIds.isNotEmpty())
        } catch (_: Throwable) {
            false
        }
    }

    private fun parseDir(raw: String?): PriceTickDirection {
        return try {
            if (raw.isNullOrBlank()) PriceTickDirection.UNCHANGED else PriceTickDirection.valueOf(raw)
        } catch (_: Throwable) {
            PriceTickDirection.UNCHANGED
        }
    }
}
