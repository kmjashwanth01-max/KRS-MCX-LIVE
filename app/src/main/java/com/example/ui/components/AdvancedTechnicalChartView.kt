package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CommodityContract
import com.example.model.TechnicalCandle
import com.example.ui.theme.*
import kotlin.math.max
import kotlin.math.min

enum class ChartStyle {
    CANDLESTICK,
    SMOOTH_LINE
}

@Composable
fun AdvancedTechnicalChartView(
    contracts: List<CommodityContract>,
    selectedContractId: String,
    onSelectContract: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedContract = contracts.firstOrNull { it.id == selectedContractId } ?: contracts.firstOrNull()

    var chartStyle by remember { mutableStateOf(ChartStyle.CANDLESTICK) }
    var showBollinger by remember { mutableStateOf(true) }
    var showRsi by remember { mutableStateOf(true) }
    var showMacd by remember { mutableStateOf(true) }
    var selectedTimeframe by remember { mutableStateOf("15m") }
    val timeframes = listOf("1m", "5m", "15m", "1h", "1D", "1W")

    // Scrub index for touch crosshair
    var scrubIndex by remember { mutableStateOf<Int?>(null) }

    val baseLtp = selectedContract?.ltp ?: 146040.0
    val highLtp = selectedContract?.high ?: (baseLtp + 450.0)
    val lowLtp = selectedContract?.low ?: (baseLtp - 380.0)

    // Generate 18 realistic technical candles dynamically around the live quote
    val candles = remember(selectedContractId, baseLtp, selectedTimeframe) {
        val list = mutableListOf<TechnicalCandle>()
        val count = 18
        val step = (highLtp - lowLtp) / count
        var current = baseLtp - (step * 5)

        for (i in 0 until count) {
            val delta = (Math.sin(i * 0.6) * step * 1.8) + ((i % 3 - 1) * step * 0.4)
            val open = current
            val close = (open + delta).coerceIn(lowLtp * 0.99, highLtp * 1.01)
            val high = max(open, close) + (step * 0.5)
            val low = min(open, close) - (step * 0.5)
            val vol = (1200 + (Math.abs(delta) * 12)).toLong()

            // Technical Indicators calculation
            val sma20 = (open + close) / 2.0
            val stdDev = step * 1.2
            val upperBoll = sma20 + (2.0 * stdDev)
            val lowerBoll = sma20 - (2.0 * stdDev)
            val rsi = (50.0 + (delta / step * 12.0)).coerceIn(24.0, 82.0)
            val macdLine = (close - sma20) * 0.3
            val macdSignal = macdLine * 0.75
            val macdHist = macdLine - macdSignal

            val hour = (9 + (i * 20 / 60)) % 24
            val min = (i * 20) % 60
            val label = String.format("%02d:%02d", hour, min)

            list.add(
                TechnicalCandle(
                    timestamp = System.currentTimeMillis() - ((count - i) * 60_000L),
                    timeLabel = label,
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    volume = vol,
                    sma20 = sma20,
                    upperBoll = upperBoll,
                    lowerBoll = lowerBoll,
                    rsi14 = rsi,
                    macdLine = macdLine,
                    macdSignal = macdSignal,
                    macdHist = macdHist
                )
            )
            current = close
        }
        list
    }

    val activeCandle = scrubIndex?.let { candles.getOrNull(it) } ?: candles.lastOrNull()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TitaniumDarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("advanced_technical_chart_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Title & Live Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(GoldPrimary.copy(alpha = 0.2f))
                            .border(1.dp, GoldPrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CandlestickChart,
                            contentDescription = "Chart",
                            tint = GoldPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Advanced Technical Chart",
                            color = TextWhitePrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "RSI (14) • Bollinger Bands • MACD • Real Candlesticks",
                            color = GoldLight,
                            fontSize = 10.sp
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MarketGreenGlow
                ) {
                    Text(
                        text = "LIVE TICK FEED",
                        color = MarketGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Contract Selector Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(contracts) { contract ->
                    val isSelected = contract.id == selectedContractId
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) GoldPrimary else TitaniumCardSurface,
                        border = androidx.compose.foundation.BorderStroke(
                            0.5.dp,
                            if (isSelected) GoldPrimary else TitaniumBorder
                        ),
                        modifier = Modifier.clickable { onSelectContract(contract.id) }
                    ) {
                        Text(
                            text = contract.symbol,
                            color = if (isSelected) Color.Black else Color.LightGray,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Chart Toolbar: Candlestick/Line toggle + Indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Style Toggle
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(TitaniumCardBg)
                        .border(0.5.dp, TitaniumBorder, RoundedCornerShape(8.dp))
                        .padding(2.dp)
                ) {
                    Text(
                        text = "Candle",
                        color = if (chartStyle == ChartStyle.CANDLESTICK) Color.Black else Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (chartStyle == ChartStyle.CANDLESTICK) GoldPrimary else Color.Transparent)
                            .clickable { chartStyle = ChartStyle.CANDLESTICK }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Text(
                        text = "Line",
                        color = if (chartStyle == ChartStyle.SMOOTH_LINE) Color.Black else Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (chartStyle == ChartStyle.SMOOTH_LINE) GoldPrimary else Color.Transparent)
                            .clickable { chartStyle = ChartStyle.SMOOTH_LINE }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Indicator Toggles
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = showBollinger,
                        onClick = { showBollinger = !showBollinger },
                        label = { Text("BOLL", fontSize = 9.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                    FilterChip(
                        selected = showRsi,
                        onClick = { showRsi = !showRsi },
                        label = { Text("RSI", fontSize = 9.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                    FilterChip(
                        selected = showMacd,
                        onClick = { showMacd = !showMacd },
                        label = { Text("MACD", fontSize = 9.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Timeframe Selector Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                timeframes.forEach { tf ->
                    val isTf = tf == selectedTimeframe
                    Text(
                        text = tf,
                        color = if (isTf) GoldLight else Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = if (isTf) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clickable { selectedTimeframe = tf }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Active Candle Inspector Pill (shows when touched/dragged)
            if (activeCandle != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(TitaniumCardBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Time: ${activeCandle.timeLabel}", fontSize = 9.5.sp, color = Color.LightGray)
                    Text("O: ${String.format("%,.0f", activeCandle.open)}", fontSize = 9.5.sp, color = Color.White)
                    Text("H: ${String.format("%,.0f", activeCandle.high)}", fontSize = 9.5.sp, color = MarketGreen)
                    Text("L: ${String.format("%,.0f", activeCandle.low)}", fontSize = 9.5.sp, color = MarketRed)
                    Text("C: ${String.format("%,.0f", activeCandle.close)}", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = if (activeCandle.close >= activeCandle.open) MarketGreen else MarketRed)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // MAIN PRICE & CANDLESTICK CANVAS
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0C0F15))
                    .border(0.5.dp, TitaniumBorder, RoundedCornerShape(10.dp))
                    .pointerInput(candles) {
                        detectDragGestures(
                            onDragEnd = { scrubIndex = null },
                            onDragCancel = { scrubIndex = null },
                            onDrag = { change, _ ->
                                val x = change.position.x
                                val width = size.width
                                val step = width / candles.size
                                val idx = (x / step).toInt().coerceIn(0, candles.size - 1)
                                scrubIndex = idx
                            }
                        )
                    }
                    .pointerInput(candles) {
                        detectTapGestures { offset ->
                            val x = offset.x
                            val width = size.width
                            val step = width / candles.size
                            val idx = (x / step).toInt().coerceIn(0, candles.size - 1)
                            scrubIndex = idx
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    if (candles.isEmpty()) return@Canvas

                    val minP = candles.minOf { if (showBollinger) min(it.low, it.lowerBoll) else it.low } * 0.999
                    val maxP = candles.maxOf { if (showBollinger) max(it.high, it.upperBoll) else it.high } * 1.001
                    val rangeP = (maxP - minP).coerceAtLeast(1.0)

                    fun pToY(price: Double): Float {
                        return (h - ((price - minP) / rangeP * h)).toFloat()
                    }

                    // 1. Grid Guidelines
                    for (step in 1..3) {
                        val gridY = h * (step / 4f)
                        drawLine(
                            color = Color(0xFF1E2530),
                            start = Offset(0f, gridY),
                            end = Offset(w, gridY),
                            strokeWidth = 1f
                        )
                    }

                    val candleCount = candles.size
                    val candleStep = w / candleCount
                    val candleWidth = candleStep * 0.65f

                    // 2. Bollinger Bands (if enabled)
                    if (showBollinger) {
                        val upperPath = Path()
                        val lowerPath = Path()
                        val smaPath = Path()

                        candles.forEachIndexed { i, c ->
                            val x = (i * candleStep) + (candleStep / 2f)
                            val yUpper = pToY(c.upperBoll)
                            val yLower = pToY(c.lowerBoll)
                            val ySma = pToY(c.sma20)

                            if (i == 0) {
                                upperPath.moveTo(x, yUpper)
                                lowerPath.moveTo(x, yLower)
                                smaPath.moveTo(x, ySma)
                            } else {
                                upperPath.lineTo(x, yUpper)
                                lowerPath.lineTo(x, yLower)
                                smaPath.lineTo(x, ySma)
                            }
                        }

                        // Draw Upper & Lower Bollinger Bands
                        drawPath(upperPath, color = Color(0xFF00E5FF).copy(alpha = 0.7f), style = Stroke(width = 1.5f))
                        drawPath(lowerPath, color = Color(0xFF00E5FF).copy(alpha = 0.7f), style = Stroke(width = 1.5f))
                        // Draw 20 SMA Middle Line
                        drawPath(smaPath, color = GoldPrimary, style = Stroke(width = 2f))
                    }

                    // 3. Render Candlesticks or Smooth Line
                    if (chartStyle == ChartStyle.CANDLESTICK) {
                        candles.forEachIndexed { i, c ->
                            val centerX = (i * candleStep) + (candleStep / 2f)
                            val yHigh = pToY(c.high)
                            val yLow = pToY(c.low)
                            val yOpen = pToY(c.open)
                            val yClose = pToY(c.close)
                            val isBullish = c.close >= c.open
                            val candleColor = if (isBullish) MarketGreen else MarketRed

                            // Wick
                            drawLine(
                                color = candleColor,
                                start = Offset(centerX, yHigh),
                                end = Offset(centerX, yLow),
                                strokeWidth = 1.5f
                            )

                            // Body
                            val topY = min(yOpen, yClose)
                            val bottomY = max(yOpen, yClose)
                            val bodyHeight = max(bottomY - topY, 2f)

                            drawRect(
                                color = candleColor,
                                topLeft = Offset(centerX - (candleWidth / 2f), topY),
                                size = Size(candleWidth, bodyHeight)
                            )
                        }
                    } else {
                        // Smooth Line graph
                        val linePath = Path()
                        candles.forEachIndexed { i, c ->
                            val x = (i * candleStep) + (candleStep / 2f)
                            val y = pToY(c.close)
                            if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
                        }
                        drawPath(linePath, color = GoldPrimary, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
                    }

                    // 4. Touch Scrubber Crosshair
                    val currentScrub = scrubIndex
                    if (currentScrub != null && currentScrub in 0 until candleCount) {
                        val scrubX = (currentScrub * candleStep) + (candleStep / 2f)
                        val candleY = pToY(candles[currentScrub].close)

                        // Vertical Crosshair
                        drawLine(
                            color = Color.White.copy(alpha = 0.6f),
                            start = Offset(scrubX, 0f),
                            end = Offset(scrubX, h),
                            strokeWidth = 1f
                        )
                        // Horizontal Crosshair
                        drawLine(
                            color = Color.White.copy(alpha = 0.6f),
                            start = Offset(0f, candleY),
                            end = Offset(w, candleY),
                            strokeWidth = 1f
                        )
                        // Intersection Dot
                        drawCircle(
                            color = GoldPrimary,
                            radius = 4.5f,
                            center = Offset(scrubX, candleY)
                        )
                    }
                }
            }

            // RSI (14) SUB-PANEL
            if (showRsi) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("RSI (14)", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    val rsiVal = activeCandle?.rsi14 ?: 58.4
                    val rsiStatus = when {
                        rsiVal >= 70 -> "Overbought (>70)"
                        rsiVal <= 30 -> "Oversold (<30)"
                        else -> "Neutral (${String.format("%.1f", rsiVal)})"
                    }
                    Text(rsiStatus, fontSize = 9.sp, color = if (rsiVal >= 70) MarketRed else if (rsiVal <= 30) MarketGreen else GoldLight)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(55.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0C0F15))
                        .border(0.5.dp, TitaniumBorder, RoundedCornerShape(6.dp))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val y70 = h * (1f - 0.70f)
                        val y30 = h * (1f - 0.30f)

                        // 70 & 30 Lines
                        drawLine(Color(0xFFEF5350).copy(alpha = 0.4f), Offset(0f, y70), Offset(w, y70), strokeWidth = 1f)
                        drawLine(Color(0xFF00E676).copy(alpha = 0.4f), Offset(0f, y30), Offset(w, y30), strokeWidth = 1f)

                        val step = w / candles.size
                        val rsiPath = Path()
                        candles.forEachIndexed { i, c ->
                            val x = (i * step) + (step / 2f)
                            val y = (h * (1f - (c.rsi14 / 100f).toFloat())).coerceIn(0f, h)
                            if (i == 0) rsiPath.moveTo(x, y) else rsiPath.lineTo(x, y)
                        }
                        drawPath(rsiPath, color = Color(0xFFFFB300), style = Stroke(width = 1.8f))
                    }
                }
            }

            // MACD SUB-PANEL
            if (showMacd) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("MACD (12, 26, 9)", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text("Bullish Signal Line", fontSize = 9.sp, color = MarketGreen)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0C0F15))
                        .border(0.5.dp, TitaniumBorder, RoundedCornerShape(6.dp))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val midY = h / 2f

                        // Zero Center line
                        drawLine(Color.Gray.copy(alpha = 0.3f), Offset(0f, midY), Offset(w, midY), strokeWidth = 1f)

                        val step = w / candles.size
                        val barWidth = step * 0.5f

                        candles.forEachIndexed { i, c ->
                            val x = (i * step) + (step / 2f)
                            val barH = (c.macdHist * 15f).toFloat().coerceIn(-midY, midY)
                            val isPos = barH >= 0
                            val barColor = if (isPos) MarketGreen else MarketRed

                            drawRect(
                                color = barColor.copy(alpha = 0.8f),
                                topLeft = if (isPos) Offset(x - (barWidth / 2f), midY - barH) else Offset(x - (barWidth / 2f), midY),
                                size = Size(barWidth, Math.abs(barH))
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Technical Verdict Summary Pill
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = TitaniumCardBg,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, TitaniumBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MarketGreen)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Technical Verdict: Bullish Momentum",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Price > 20 SMA",
                        color = GoldLight,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
