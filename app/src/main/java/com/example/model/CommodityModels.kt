package com.example.model

enum class CommodityType {
    GOLD,
    SILVER,
    FOREX
}

enum class ContractUnit(val displayName: String, val shortName: String, val defaultLotSize: Int) {
    TEN_GRAMS("10 Grams", "10g", 1),
    KILOGRAM("1 Kilogram", "1kg", 1),
    GRAM("1 Gram", "1g", 10),
    TROY_OUNCE("Troy Ounce", "1 oz", 1),
    CURRENCY("Currency Pair", "INR", 1);

    val label: String get() = shortName
}

enum class PriceTickDirection {
    UP,
    DOWN,
    UNCHANGED
}

data class PricePoint(
    val timestamp: Long,
    val price: Double
)

data class HistoricalDataPoint(
    val timeLabel: String,
    val price: Double,
    val high: Double,
    val low: Double,
    val volume: Long
)

data class PeriodSummary(
    val period: String,
    val startPrice: Double,
    val endPrice: Double,
    val highPrice: Double,
    val lowPrice: Double,
    val change: Double,
    val changePercent: Double
)

enum class MarketRateMode(val title: String, val subtitle: String) {
    HIGH_BULLION_2LAKH("Market Rate (~2.0L)", "Gold 1.47L • Silver 1.91L • MCX 1.42L"),
    HISTORIC_2024("Historic MCX (~75k-90k)", "Pre-2024 Gold 78k • Silver 92k")
}

enum class AlertCondition(val symbol: String, val label: String) {
    ABOVE("≥", "Crosses Above"),
    BELOW("≤", "Drops Below")
}

data class PriceAlert(
    val id: String,
    val contractId: String,
    val contractSymbol: String,
    val targetPrice: Double,
    val condition: AlertCondition,
    val isTriggered: Boolean = false,
    val triggeredAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class GoldPurity(val label: String, val purityFactor: Double, val hallmark: String) {
    K24_999("24K Pure Gold (999)", 1.000, "BIS 999"),
    K22_916("22K Standard Jewelry (916)", 0.916, "BIS 916"),
    K18_750("18K Diamond Jewelry (750)", 0.750, "BIS 750"),
    K14_585("14K Light Jewelry (585)", 0.585, "BIS 585");

    val multiplier: Double get() = purityFactor
}

enum class MakingChargeType(val label: String) {
    PERCENTAGE("Percentage (%)"),
    FLAT_PER_GRAM("Flat Per Gram (₹/g)")
}

data class JewelryBillBreakdown(
    val purity: GoldPurity,
    val weightGrams: Double,
    val baseRatePerGram: Double,
    val metalValue: Double,
    val makingChargeAmount: Double,
    val hallmarkFee: Double,
    val subtotal: Double,
    val cgstAmount: Double,
    val sgstAmount: Double,
    val totalGst: Double,
    val grandTotal: Double
)

data class BullionCityRate(
    val city: String,
    val state: String,
    val gold24k: Double,
    val gold22k: Double,
    val silver1kg: Double,
    val cityPremiumPercent: Double
)

data class OrderBookEntry(
    val bidQty: Int,
    val bidOrders: Int,
    val bidPrice: Double,
    val askPrice: Double,
    val askOrders: Int,
    val askQty: Int
)

data class CommodityContract(
    val id: String,
    val symbol: String,
    val name: String,
    val expiry: String,
    val type: CommodityType,
    val unit: ContractUnit,
    val ltp: Double,
    val previousClose: Double,
    val high: Double,
    val low: Double,
    val open: Double,
    val volume: Long,
    val openInterest: Long,
    val bidPrice: Double,
    val bidQty: Int,
    val askPrice: Double,
    val askQty: Int,
    val lastTickDirection: PriceTickDirection = PriceTickDirection.UNCHANGED,
    val bidTickDirection: PriceTickDirection = PriceTickDirection.UNCHANGED,
    val askTickDirection: PriceTickDirection = PriceTickDirection.UNCHANGED,
    val lastUpdatedMillis: Long = System.currentTimeMillis()
) {
    val change: Double get() = ltp - previousClose
    val changePercent: Double get() = if (previousClose > 0) (change / previousClose) * 100.0 else 0.0
    val percentChange: Double get() = changePercent
    val perGramRate: Double get() = when (unit) {
        ContractUnit.TEN_GRAMS -> ltp / 10.0
        ContractUnit.KILOGRAM -> ltp / 1000.0
        else -> ltp
    }
    val spread: Double get() = (askPrice - bidPrice).coerceAtLeast(0.0)
    val isPositive: Boolean get() = change >= 0.0
}

enum class MarketTrendBias(val label: String, val signalColorHex: Long) {
    STRONG_BULLISH("STRONG BULLISH", 0xFF00E676),
    MILD_BULLISH("MILD BULLISH", 0xFF69F0AE),
    NEUTRAL_SIDEWAYS("NEUTRAL / CONSOLIDATION", 0xFFFFD54F),
    MILD_BEARISH("MILD BEARISH", 0xFFFF8A80),
    STRONG_BEARISH("STRONG BEARISH", 0xFFFF1744)
}

data class MarketPrediction(
    val contractId: String,
    val contractName: String,
    val bias: MarketTrendBias,
    val confidencePercent: Int,
    val currentPrice: Double,
    val pivotPoint: Double,
    val resistance1: Double,
    val resistance2: Double,
    val support1: Double,
    val support2: Double,
    val target1: Double,
    val target2: Double,
    val stopLoss: Double,
    val volatility: String,
    val horizon: String,
    val rsi14: Double,
    val macdStatus: String,
    val summaryInsight: String,
    val generatedTimeStr: String
)

data class ChatMessage(
    val id: String = System.currentTimeMillis().toString() + "_" + (100..999).random(),
    val text: String,
    val isFromUser: Boolean,
    val timestampMillis: Long = System.currentTimeMillis()
)

data class TechnicalCandle(
    val timestamp: Long,
    val timeLabel: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
    val sma20: Double,
    val upperBoll: Double,
    val lowerBoll: Double,
    val rsi14: Double,
    val macdLine: Double,
    val macdSignal: Double,
    val macdHist: Double
)
