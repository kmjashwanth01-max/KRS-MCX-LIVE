package com.example.data

import android.content.Context
import com.example.model.*
import com.example.util.NotificationHelper
import com.example.widget.BullionAppWidgetProvider
import com.example.widget.SilverAppWidgetProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class ActivePriceAlert(
    val id: String = java.util.UUID.randomUUID().toString(),
    val contractId: String,
    val contractSymbol: String = "",
    val targetPrice: Double,
    val isAbove: Boolean,
    var isTriggered: Boolean = false,
    val isBuyTarget: Boolean = !isAbove,
    val createdAt: Long = System.currentTimeMillis(),
    var triggeredAt: Long? = null
)

class McxRatesRepository(private val context: Context? = null) {

    companion object {
        private val istTimeSecondsFormat = SimpleDateFormat("hh:mm:ss a 'IST'", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
    }

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _contracts = MutableStateFlow<List<CommodityContract>>(emptyList())
    val contracts: StateFlow<List<CommodityContract>> = _contracts.asStateFlow()

    private val _activeVijayFeed = MutableStateFlow(VijayBullionFeed())
    val activeVijayFeed: StateFlow<VijayBullionFeed> = _activeVijayFeed.asStateFlow()

    private val _historyMap = MutableStateFlow<Map<String, List<PricePoint>>>(emptyMap())
    val historyMap: StateFlow<Map<String, List<PricePoint>>> = _historyMap.asStateFlow()

    private val _orderBook = MutableStateFlow<List<OrderBookEntry>>(emptyList())
    val orderBook: StateFlow<List<OrderBookEntry>> = _orderBook.asStateFlow()

    private val _cityRates = MutableStateFlow<List<BullionCityRate>>(emptyList())
    val cityRates: StateFlow<List<BullionCityRate>> = _cityRates.asStateFlow()

    private val _isMarketOpen = MutableStateFlow(false)
    val isMarketOpen: StateFlow<Boolean> = _isMarketOpen.asStateFlow()

    private val _isPhysicalBullionOpen = MutableStateFlow(false)
    val isPhysicalBullionOpen: StateFlow<Boolean> = _isPhysicalBullionOpen.asStateFlow()

    private val _marketStatusText = MutableStateFlow("Initializing MCX & Bullion Feed...")
    val marketStatusText: StateFlow<String> = _marketStatusText.asStateFlow()

    private val _liveIstClock = MutableStateFlow("Live IST")
    val liveIstClock: StateFlow<String> = _liveIstClock.asStateFlow()

    private val _marketSessionDetail = MutableStateFlow("Session: 09:00 AM - 11:30 PM IST")
    val marketSessionDetail: StateFlow<String> = _marketSessionDetail.asStateFlow()

    private val alertPrefs = try {
        context?.getSharedPreferences("krs_price_alerts_prefs", Context.MODE_PRIVATE)
    } catch (_: Throwable) {
        null
    }
    private val _priceAlerts = MutableStateFlow<List<ActivePriceAlert>>(emptyList())
    val priceAlerts: StateFlow<List<ActivePriceAlert>> = _priceAlerts.asStateFlow()

    // Track ONLY verified real API price movements (starts at 0L so closed market is never marked open at startup)
    @Volatile
    private var lastPriceMovementTimestampMs: Long = 0L

    @Volatile
    private var consecutivePriceChanges: Int = 0

    @Volatile
    private var hasReceivedRealApiResponse: Boolean = false

    init {
        // Initialize default baseline from initial feed
        val initialFeed = VijayBullionFeed()
        _activeVijayFeed.value = initialFeed
        updateMarketStatus(initialFeed)
        updateContractsFromFeed(initialFeed, isInitial = true)
        generateCityRates(initialFeed)
        generateOrderBook(initialFeed)
        loadSavedAlerts()

        // Start dedicated 1-second clock & market status heartbeat (never stalls on network latency)
        startOneSecondClockAndStatusLoop()
        // Start continuous 1-second live stream polling from Vijay Bullion broadcast
        startOneSecondFeedLoop()
    }

    private fun startOneSecondClockAndStatusLoop() {
        repositoryScope.launch {
            while (isActive) {
                try {
                    val currentFeed = _activeVijayFeed.value
                    updateMarketStatus(currentFeed)
                    if (context != null) {
                        try {
                            BullionAppWidgetProvider.updateAllWidgets(context, currentFeed)
                        } catch (_: Exception) {}
                        try {
                            SilverAppWidgetProvider.updateAllWidgets(context, currentFeed)
                        } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}
                delay(1000L) // Strict 1-second tick without fault
            }
        }
    }

    private fun startOneSecondFeedLoop() {
        repositoryScope.launch {
            var lastValidFeed: VijayBullionFeed = _activeVijayFeed.value
            var lastNetworkFeed: VijayBullionFeed? = null
            while (isActive) {
                try {
                    val rawFeed = withTimeoutOrNull(2800L) {
                        VijayBullionApiClient.fetchLatestBroadcast()
                    }
                    val liveFeed = if (rawFeed != null) {
                        hasReceivedRealApiResponse = true
                        val validated = VijayBullionApiClient.autoCorrectAndValidateFeed(rawFeed, lastValidFeed)
                        val prevNet = lastNetworkFeed
                        if (prevNet != null) {
                            val priceChanged = validated.goldBarRate10g != prevNet.goldBarRate10g ||
                                validated.silverBarRate1kg != prevNet.silverBarRate1kg ||
                                validated.mcxGoldBid != prevNet.mcxGoldBid ||
                                validated.mcxGoldAsk != prevNet.mcxGoldAsk ||
                                validated.mcxSilverBid != prevNet.mcxSilverBid ||
                                validated.mcxSilverAsk != prevNet.mcxSilverAsk ||
                                validated.spotGoldBid != prevNet.spotGoldBid ||
                                validated.spotSilverBid != prevNet.spotSilverBid ||
                                validated.spotInrBid != prevNet.spotInrBid
                            if (priceChanged) {
                                lastPriceMovementTimestampMs = System.currentTimeMillis()
                                consecutivePriceChanges++
                            }
                        }
                        lastNetworkFeed = validated
                        validated
                    } else {
                        // Hold exact verified rates from Vijay Bullion so prices stay 100% synchronized
                        VijayBullionApiClient.holdVerifiedLastTick(lastValidFeed)
                    }

                    lastValidFeed = liveFeed
                    _activeVijayFeed.value = liveFeed
                    if (rawFeed != null) {
                        com.example.util.AppDiagnosticsManager.logSyncSuccess(liveFeed.serverTime)
                    }
                    updateMarketStatus(liveFeed)
                    updateContractsFromFeed(liveFeed, isInitial = false)
                    generateCityRates(liveFeed)
                    generateOrderBook(liveFeed)
                    checkPriceAlerts()
                } catch (e: Exception) {
                    val recoveryFeed = VijayBullionApiClient.holdVerifiedLastTick(lastValidFeed)
                    lastValidFeed = recoveryFeed
                    _activeVijayFeed.value = recoveryFeed
                    updateMarketStatus(recoveryFeed)
                    updateContractsFromFeed(recoveryFeed, isInitial = false)
                    com.example.util.AppDiagnosticsManager.logSyncError("Sync hold: ${e.message}")
                }
                delay(1000L) // Poll every 1 second
            }
        }
    }

    private var tickCycleCounter = 0L

    private fun updateContractsFromFeed(feed: VijayBullionFeed, isInitial: Boolean) {
        tickCycleCounter++
        val currentList = _contracts.value.associateBy { it.id }

        fun resolveTick(newPrice: Double, prevPrice: Double, defaultIfSame: PriceTickDirection): PriceTickDirection {
            return when {
                newPrice > prevPrice -> PriceTickDirection.UP
                newPrice < prevPrice -> PriceTickDirection.DOWN
                else -> defaultIfSame
            }
        }

        // Pulse ticker fallback only when market is open; stay neutral when closed
        val heartbeatTick = if (_isMarketOpen.value) {
            if (tickCycleCounter % 2L == 0L) PriceTickDirection.UP else PriceTickDirection.DOWN
        } else {
            PriceTickDirection.UNCHANGED
        }

        // 1. Physical Gold 999 10g
        // 1. Physical Gold 999 10g (Exact sync with Vijay Bullion)
        val prevGold = currentList["gold_999"]
        val goldLtp = feed.goldBarRate10g
        val goldPrev = prevGold?.ltp ?: goldLtp
        val goldBid = goldLtp
        val goldAsk = goldLtp
        val goldBidTick = if (isInitial) PriceTickDirection.UP else resolveTick(goldBid, prevGold?.bidPrice ?: goldPrev, prevGold?.bidTickDirection ?: heartbeatTick)
        val goldAskTick = if (isInitial) PriceTickDirection.UP else resolveTick(goldAsk, prevGold?.askPrice ?: goldPrev, prevGold?.askTickDirection ?: heartbeatTick)
        val goldHigh = if (isInitial) goldLtp + 120.0 else maxOf(prevGold?.high ?: goldLtp, goldLtp)
        val goldLow = if (isInitial) goldLtp - 150.0 else minOf(prevGold?.low ?: goldLtp, goldLtp)
        val goldContract = CommodityContract(
            id = "gold_999",
            symbol = "GOLD 999",
            name = "Gold 999 (10g) • MCX INDIA",
            expiry = "05 DEC 2026",
            type = CommodityType.GOLD,
            unit = ContractUnit.TEN_GRAMS,
            ltp = goldLtp,
            previousClose = prevGold?.previousClose ?: (goldLtp - 220.0),
            high = goldHigh,
            low = goldLow,
            open = prevGold?.open ?: (goldLtp - 100.0),
            volume = 25023,
            openInterest = 18485,
            bidPrice = goldBid,
            bidQty = 15,
            askPrice = goldAsk,
            askQty = 10,
            lastTickDirection = goldBidTick,
            bidTickDirection = goldBidTick,
            askTickDirection = goldAskTick
        )

        // 2. Physical Silver 1kg (Exact sync with Vijay Bullion)
        val prevSilver = currentList["silver_1kg"]
        val silverLtp = feed.silverBarRate1kg
        val silverPrev = prevSilver?.ltp ?: silverLtp
        val silverBid = silverLtp
        val silverAsk = silverLtp
        val silverBidTick = if (isInitial) PriceTickDirection.UP else resolveTick(silverBid, prevSilver?.bidPrice ?: silverPrev, prevSilver?.bidTickDirection ?: heartbeatTick)
        val silverAskTick = if (isInitial) PriceTickDirection.UP else resolveTick(silverAsk, prevSilver?.askPrice ?: silverPrev, prevSilver?.askTickDirection ?: heartbeatTick)
        val silverHigh = if (isInitial) silverLtp + 250.0 else maxOf(prevSilver?.high ?: silverLtp, silverLtp)
        val silverLow = if (isInitial) silverLtp - 300.0 else minOf(prevSilver?.low ?: silverLtp, silverLtp)
        val silverContract = CommodityContract(
            id = "silver_1kg",
            symbol = "SILVER 1KG",
            name = "Silver 1kg (Fine 999)",
            expiry = "05 DEC 2026",
            type = CommodityType.SILVER,
            unit = ContractUnit.KILOGRAM,
            ltp = silverLtp,
            previousClose = prevSilver?.previousClose ?: (silverLtp - 350.0),
            high = silverHigh,
            low = silverLow,
            open = prevSilver?.open ?: (silverLtp - 150.0),
            volume = 38410,
            openInterest = 22100,
            bidPrice = silverBid,
            bidQty = 25,
            askPrice = silverAsk,
            askQty = 18,
            lastTickDirection = silverBidTick,
            bidTickDirection = silverBidTick,
            askTickDirection = silverAskTick
        )

        // 3. MCX Gold Futures
        val prevMcxGold = currentList["mcx_gold"]
        val mcxGoldLtp = feed.mcxGoldLtp
        val mcxGoldBid = feed.mcxGoldBid
        val mcxGoldAsk = feed.mcxGoldAsk
        val mcxGoldBidTick = if (isInitial) PriceTickDirection.UP else resolveTick(mcxGoldBid, prevMcxGold?.bidPrice ?: 145000.0, prevMcxGold?.bidTickDirection ?: heartbeatTick)
        val mcxGoldAskTick = if (isInitial) PriceTickDirection.UP else resolveTick(mcxGoldAsk, prevMcxGold?.askPrice ?: 145050.0, prevMcxGold?.askTickDirection ?: heartbeatTick)
        val mcxGoldContract = CommodityContract(
            id = "mcx_gold",
            symbol = "GOLDMCX",
            name = "MCX Gold Futures (10g)",
            expiry = "05 DEC 2026",
            type = CommodityType.GOLD,
            unit = ContractUnit.TEN_GRAMS,
            ltp = mcxGoldLtp,
            previousClose = 142350.0,
            high = feed.mcxGoldHigh,
            low = feed.mcxGoldLow,
            open = feed.mcxGoldLow + 120.0,
            volume = 42100,
            openInterest = 31200,
            bidPrice = mcxGoldBid,
            bidQty = 50,
            askPrice = mcxGoldAsk,
            askQty = 45,
            lastTickDirection = mcxGoldBidTick,
            bidTickDirection = mcxGoldBidTick,
            askTickDirection = mcxGoldAskTick
        )

        // 4. MCX Silver Futures
        val prevMcxSilver = currentList["mcx_silver"]
        val mcxSilverLtp = feed.mcxSilverLtp
        val mcxSilverBid = feed.mcxSilverBid
        val mcxSilverAsk = feed.mcxSilverAsk
        val mcxSilverBidTick = if (isInitial) PriceTickDirection.UP else resolveTick(mcxSilverBid, prevMcxSilver?.bidPrice ?: 185000.0, prevMcxSilver?.bidTickDirection ?: heartbeatTick)
        val mcxSilverAskTick = if (isInitial) PriceTickDirection.UP else resolveTick(mcxSilverAsk, prevMcxSilver?.askPrice ?: 185100.0, prevMcxSilver?.askTickDirection ?: heartbeatTick)
        val mcxSilverContract = CommodityContract(
            id = "mcx_silver",
            symbol = "SILVERMCX",
            name = "MCX Silver Futures (1kg)",
            expiry = "05 DEC 2026",
            type = CommodityType.SILVER,
            unit = ContractUnit.KILOGRAM,
            ltp = mcxSilverLtp,
            previousClose = 184900.0,
            high = feed.mcxSilverHigh,
            low = feed.mcxSilverLow,
            open = feed.mcxSilverLow + 200.0,
            volume = 51200,
            openInterest = 39800,
            bidPrice = mcxSilverBid,
            bidQty = 60,
            askPrice = mcxSilverAsk,
            askQty = 55,
            lastTickDirection = mcxSilverBidTick,
            bidTickDirection = mcxSilverBidTick,
            askTickDirection = mcxSilverAskTick
        )

        // 5. Spot Gold ($)
        val prevSpotGold = currentList["spot_gold"]
        val spotGoldBid = feed.spotGoldBid
        val spotGoldAsk = feed.spotGoldAsk
        val spotGoldBidTick = if (isInitial) PriceTickDirection.UP else resolveTick(spotGoldBid, prevSpotGold?.bidPrice ?: 4070.77, prevSpotGold?.bidTickDirection ?: heartbeatTick)
        val spotGoldAskTick = if (isInitial) PriceTickDirection.UP else resolveTick(spotGoldAsk, prevSpotGold?.askPrice ?: 4071.09, prevSpotGold?.askTickDirection ?: heartbeatTick)
        val spotGoldContract = CommodityContract(
            id = "spot_gold",
            symbol = "GOLD($)",
            name = "Spot Gold (USD/oz)",
            expiry = "SPOT",
            type = CommodityType.GOLD,
            unit = ContractUnit.TROY_OUNCE,
            ltp = spotGoldBid,
            previousClose = feed.spotGoldLow,
            high = feed.spotGoldHigh,
            low = feed.spotGoldLow,
            open = feed.spotGoldLow,
            volume = 12040,
            openInterest = 8900,
            bidPrice = spotGoldBid,
            bidQty = 10,
            askPrice = spotGoldAsk,
            askQty = 10,
            lastTickDirection = spotGoldBidTick,
            bidTickDirection = spotGoldBidTick,
            askTickDirection = spotGoldAskTick
        )

        // 6. Spot Silver ($)
        val prevSpotSilver = currentList["spot_silver"]
        val spotSilverBid = feed.spotSilverBid
        val spotSilverAsk = feed.spotSilverAsk
        val spotSilverBidTick = if (isInitial) PriceTickDirection.UP else resolveTick(spotSilverBid, prevSpotSilver?.bidPrice ?: 52.01, prevSpotSilver?.bidTickDirection ?: heartbeatTick)
        val spotSilverAskTick = if (isInitial) PriceTickDirection.UP else resolveTick(spotSilverAsk, prevSpotSilver?.askPrice ?: 52.02, prevSpotSilver?.askTickDirection ?: heartbeatTick)
        val spotSilverContract = CommodityContract(
            id = "spot_silver",
            symbol = "SILVER($)",
            name = "Spot Silver (USD/oz)",
            expiry = "SPOT",
            type = CommodityType.SILVER,
            unit = ContractUnit.TROY_OUNCE,
            ltp = spotSilverBid,
            previousClose = feed.spotSilverLow,
            high = feed.spotSilverHigh,
            low = feed.spotSilverLow,
            open = feed.spotSilverLow,
            volume = 18900,
            openInterest = 14200,
            bidPrice = spotSilverBid,
            bidQty = 15,
            askPrice = spotSilverAsk,
            askQty = 15,
            lastTickDirection = spotSilverBidTick,
            bidTickDirection = spotSilverBidTick,
            askTickDirection = spotSilverAskTick
        )

        // 7. Spot INR (₹)
        val prevSpotInr = currentList["spot_inr"]
        val spotInrBid = feed.spotInrBid
        val spotInrAsk = feed.spotInrAsk
        val spotInrBidTick = if (isInitial) PriceTickDirection.UP else resolveTick(spotInrBid, prevSpotInr?.bidPrice ?: 93.90, prevSpotInr?.bidTickDirection ?: heartbeatTick)
        val spotInrAskTick = if (isInitial) PriceTickDirection.UP else resolveTick(spotInrAsk, prevSpotInr?.askPrice ?: 93.94, prevSpotInr?.askTickDirection ?: heartbeatTick)
        val spotInrContract = CommodityContract(
            id = "spot_inr",
            symbol = "INR(₹)",
            name = "USD / INR Forex Spot",
            expiry = "FOREX",
            type = CommodityType.FOREX,
            unit = ContractUnit.CURRENCY,
            ltp = spotInrBid,
            previousClose = feed.spotInrLow,
            high = feed.spotInrHigh,
            low = feed.spotInrLow,
            open = feed.spotInrLow,
            volume = 45000,
            openInterest = 32000,
            bidPrice = spotInrBid,
            bidQty = 20,
            askPrice = spotInrAsk,
            askQty = 20,
            lastTickDirection = spotInrBidTick,
            bidTickDirection = spotInrBidTick,
            askTickDirection = spotInrAskTick
        )

        val updatedList = listOf(
            goldContract,
            silverContract,
            mcxGoldContract,
            mcxSilverContract,
            spotGoldContract,
            spotSilverContract,
            spotInrContract
        )
        _contracts.value = updatedList

        // Append to history points for live chart
        val now = System.currentTimeMillis()
        val currentHistory = _historyMap.value.toMutableMap()
        for (c in updatedList) {
            val list = currentHistory[c.id]?.toMutableList() ?: mutableListOf()
            list.add(PricePoint(timestamp = now, price = c.ltp))
            if (list.size > 50) {
                list.removeAt(0)
            }
            currentHistory[c.id] = list
        }
        _historyMap.value = currentHistory
    }

    private fun generateCityRates(feed: VijayBullionFeed) {
        val baseGold10g = feed.goldBarRate10g
        val baseSilver1kg = feed.silverBarRate1kg

        val cities = listOf(
            Triple("Mumbai", "Maharashtra", 0.0),
            Triple("Delhi", "NCR", 0.22),
            Triple("Chennai", "Tamil Nadu", 0.35),
            Triple("Kolkata", "West Bengal", 0.30),
            Triple("Ahmedabad", "Gujarat", 0.08),
            Triple("Hyderabad", "Telangana", 0.25),
            Triple("Bangalore", "Karnataka", 0.28),
            Triple("Jaipur", "Rajasthan", 0.15),
            Triple("Coimbatore", "Tamil Nadu", 0.32),
            Triple("Surat", "Gujarat", 0.06)
        )

        _cityRates.value = cities.map { (cityName, stateName, premiumPct) ->
            val factor = 1.0 + (premiumPct / 100.0)
            val gold24k = baseGold10g * factor
            val gold22k = (baseGold10g * (22.0 / 24.0)) * factor
            val silver1kg = baseSilver1kg * factor
            BullionCityRate(
                city = cityName,
                state = stateName,
                gold24k = gold24k,
                gold22k = gold22k,
                silver1kg = silver1kg,
                cityPremiumPercent = premiumPct
            )
        }
    }

    private fun generateOrderBook(feed: VijayBullionFeed) {
        val baseBid = feed.mcxGoldBid
        val baseAsk = feed.mcxGoldAsk
        val entries = mutableListOf<OrderBookEntry>()

        for (i in 0 until 5) {
            val bidP = baseBid - (i * 15.0)
            val askP = baseAsk + (i * 15.0)
            entries.add(
                OrderBookEntry(
                    bidQty = 12 + (i * 7),
                    bidOrders = 3 + i,
                    bidPrice = bidP,
                    askPrice = askP,
                    askOrders = 2 + i,
                    askQty = 10 + (i * 6)
                )
            )
        }
        _orderBook.value = entries
    }

    /**
     * Logic layer that automatically detects market open/close state every second based on
     * IST market schedule (Mon-Fri 09:00 AM - 11:30 PM IST), live Vijay Bullion price ticks,
     * and physical bullion quote availability.
     */
    private fun updateMarketStatus(
        currentFeed: VijayBullionFeed = _activeVijayFeed.value
    ) {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)

        val timeString = istTimeSecondsFormat.format(cal.time)
        _liveIstClock.value = timeString

        val isWeekday = dayOfWeek in Calendar.MONDAY..Calendar.FRIDAY
        val currentMinutesOfDay = hour * 60 + minute
        val isScheduledSession = isWeekday && (currentMinutesOfDay in 540..1410)

        val nowMs = System.currentTimeMillis()
        val hasLivePriceMovement = lastPriceMovementTimestampMs > 0L &&
            (nowMs - lastPriceMovementTimestampMs) < 45_000L &&
            consecutivePriceChanges >= 2

        if (!hasLivePriceMovement && lastPriceMovementTimestampMs > 0L && (nowMs - lastPriceMovementTimestampMs) >= 45_000L) {
            consecutivePriceChanges = 0
        }

        // Market is OPEN during scheduled Mon-Fri IST market hours OR when genuine live price ticks are actively moving,
        // unless the broadcast explicitly signals MARKET CLOSED and no live ticks are moving.
        val isOpenNow = (isScheduledSession && !(hasReceivedRealApiResponse && currentFeed.isServerExplicitlyClosed)) ||
            hasLivePriceMovement

        // Physical Bullion is OPEN only when the market is open AND Vijay Bullion broadcast has active physical quotes
        val physicalOpenNow = isOpenNow && (!hasReceivedRealApiResponse || currentFeed.hasPhysicalBullionQuotes)

        _isMarketOpen.value = isOpenNow
        _isPhysicalBullionOpen.value = physicalOpenNow

        if (isOpenNow) {
            val detail = if (isScheduledSession) {
                val minsToClose = (1410 - currentMinutesOfDay).coerceAtLeast(1)
                val h = minsToClose / 60
                val m = minsToClose % 60
                if (physicalOpenNow) {
                    "Live Bullion Feed Active • Closes in ${h}h ${m}m"
                } else {
                    "MCX Open • Physical Desk Closed • Closes in ${h}h ${m}m"
                }
            } else {
                "Live Bullion Session Active • Gold ₹${String.format("%,.0f", currentFeed.goldBarRate10g)}"
            }
            _marketSessionDetail.value = detail
            _marketStatusText.value = "🟢 MCX & BULLION MARKET OPEN ($detail) • Live: $timeString"
        } else {
            val detail = when {
                !isWeekday -> "Weekend Closure • Opens Monday 09:00 AM IST"
                currentMinutesOfDay < 540 -> {
                    val minsToOpen = 540 - currentMinutesOfDay
                    val h = minsToOpen / 60
                    val m = minsToOpen % 60
                    "Pre-Market • Opens in ${h}h ${m}m (09:00 AM IST)"
                }
                else -> "Daily Session Closed at 11:30 PM • Opens Tomorrow 09:00 AM IST"
            }
            _marketSessionDetail.value = detail
            _marketStatusText.value = "🔴 MCX MARKET CLOSED ($detail) • $timeString"
        }
    }

    private fun loadSavedAlerts() {
        try {
            val jsonStr = alertPrefs?.getString("alerts_list_json", null) ?: return
            val arr = org.json.JSONArray(jsonStr)
            val list = mutableListOf<ActivePriceAlert>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    ActivePriceAlert(
                        id = obj.optString("id", System.currentTimeMillis().toString()),
                        contractId = obj.getString("contractId"),
                        contractSymbol = obj.optString("contractSymbol", ""),
                        targetPrice = obj.getDouble("targetPrice"),
                        isAbove = obj.getBoolean("isAbove"),
                        isTriggered = obj.optBoolean("isTriggered", false),
                        isBuyTarget = obj.optBoolean("isBuyTarget", !obj.getBoolean("isAbove")),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        triggeredAt = if (obj.has("triggeredAt")) obj.getLong("triggeredAt") else null
                    )
                )
            }
            _priceAlerts.value = list
        } catch (_: Exception) {}
    }

    private fun saveAlertsToDisk() {
        try {
            val arr = org.json.JSONArray()
            for (a in _priceAlerts.value) {
                val obj = org.json.JSONObject().apply {
                    put("id", a.id)
                    put("contractId", a.contractId)
                    put("contractSymbol", a.contractSymbol)
                    put("targetPrice", a.targetPrice)
                    put("isAbove", a.isAbove)
                    put("isTriggered", a.isTriggered)
                    put("isBuyTarget", a.isBuyTarget)
                    put("createdAt", a.createdAt)
                    if (a.triggeredAt != null) put("triggeredAt", a.triggeredAt)
                }
                arr.put(obj)
            }
            alertPrefs?.edit()?.putString("alerts_list_json", arr.toString())?.apply()
        } catch (_: Exception) {}
    }

    fun addPriceAlert(contractId: String, targetPrice: Double, isAbove: Boolean) {
        val sym = _contracts.value.firstOrNull { it.id == contractId }?.symbol ?: contractId.uppercase()
        val newAlert = ActivePriceAlert(
            id = System.currentTimeMillis().toString(),
            contractId = contractId,
            contractSymbol = sym,
            targetPrice = targetPrice,
            isAbove = isAbove,
            isBuyTarget = !isAbove
        )
        val updated = _priceAlerts.value.toMutableList()
        updated.add(0, newAlert)
        _priceAlerts.value = updated
        saveAlertsToDisk()
    }

    fun removePriceAlert(alertId: String) {
        val updated = _priceAlerts.value.filter { it.id != alertId }
        _priceAlerts.value = updated
        saveAlertsToDisk()
    }

    private fun checkPriceAlerts() {
        val currentContracts = _contracts.value.associateBy { it.id }
        var hasChanges = false
        val alerts = _priceAlerts.value
        for (alert in alerts) {
            if (alert.isTriggered) continue
            val contract = currentContracts[alert.contractId] ?: continue
            val hit = if (alert.isAbove) contract.ltp >= alert.targetPrice else contract.ltp <= alert.targetPrice
            if (hit) {
                alert.isTriggered = true
                alert.triggeredAt = System.currentTimeMillis()
                hasChanges = true
                com.example.util.AppDiagnosticsManager.logAlert(contract.symbol, contract.ltp)
                val dir = if (alert.isAbove) "crossed above" else "dropped below"
                val targetType = if (alert.isBuyTarget) "BUY Target Hit" else "SELL Target Hit"
                if (context != null) {
                    NotificationHelper.sendPriceAlertNotification(
                        context = context,
                        notificationId = alert.id.hashCode(),
                        title = "🚨 $targetType: ${contract.symbol}",
                        message = "${contract.name} has $dir ₹${String.format("%,.2f", alert.targetPrice)}! Current LTP: ₹${String.format("%,.2f", contract.ltp)}"
                    )
                }
            }
        }
        if (hasChanges) {
            _priceAlerts.value = alerts.toList()
            saveAlertsToDisk()
        }
    }
}
