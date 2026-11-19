package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CommodityContract
import com.example.model.ContractUnit
import com.example.model.PricePoint
import com.example.model.PriceTickDirection
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private enum class ChartFilter(val label: String) {
    ALL("All Live (7)"),
    SILVER("Silver"),
    GOLD("Gold"),
    MCX("MCX Futures"),
    SPOT_CENT("Spot Cent ($)")
}

private val chartTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.ENGLISH).apply {
    timeZone = TimeZone.getTimeZone("Asia/Kolkata")
}

private val ORDERED_CHART_IDS = listOf(
    "silver_1kg",
    "mcx_silver",
    "spot_silver",
    "gold_999",
    "mcx_gold",
    "spot_gold",
    "spot_inr"
)

@Composable
fun LiveChartsDashboardView(
    contracts: List<CommodityContract>,
    historyMap: Map<String, List<PricePoint>>,
    isMarketOpen: Boolean,
    isPhysicalBullionOpen: Boolean,
    liveIstClock: String,
    marketSessionDetail: String = "",
    modifier: Modifier = Modifier
) {
    var activeFilter by remember { mutableStateOf(ChartFilter.ALL) }
    val reopenNote = marketSessionDetail.ifBlank { "Reopens Today at 09:00 AM IST" }

    val orderedContracts = remember(contracts) {
        val byId = contracts.associateBy { it.id }
        val primary = ORDERED_CHART_IDS.mapNotNull { byId[it] }
        val remaining = contracts.filter { it.id !in ORDERED_CHART_IDS }
        primary + remaining
    }

    val filteredContracts = remember(orderedContracts, activeFilter) {
        when (activeFilter) {
            ChartFilter.ALL -> orderedContracts
            ChartFilter.SILVER -> orderedContracts.filter {
                it.id == "silver_1kg" || it.id == "mcx_silver" || it.id == "spot_silver"
            }
            ChartFilter.GOLD -> orderedContracts.filter {
                it.id == "gold_999" || it.id == "mcx_gold" || it.id == "spot_gold"
            }
            ChartFilter.MCX -> orderedContracts.filter {
                it.id == "mcx_gold" || it.id == "mcx_silver"
            }
            ChartFilter.SPOT_CENT -> orderedContracts.filter {
                it.id == "spot_gold" || it.id == "spot_silver" || it.id == "spot_inr"
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
            .testTag("live_1s_charts_dashboard"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Live 1s Charts Header Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = TitaniumDarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.55f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(GoldPrimary.copy(alpha = 0.16f))
                                .border(1.dp, GoldPrimary, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShowChart,
                                contentDescription = "Live 1s Charts",
                                tint = GoldPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Live 1-Second Bullion Graphs",
                                color = TextWhitePrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Silver 1kg • Gold 999 • MCX • Spot Cent ($) • Forex",
                                color = GoldLight,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isMarketOpen) MarketGreenGlow else Color(0xFF450A0A),
                        border = androidx.compose.foundation.BorderStroke(
                            0.8.dp,
                            if (isMarketOpen) MarketGreen else Color(0xFFFF5252)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isMarketOpen) MarketGreen else Color(0xFFFF5252))
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isMarketOpen) "1s LIVE" else "CLOSED",
                                color = if (isMarketOpen) MarketGreen else Color(0xFFFF8A80),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                // Filter Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(ChartFilter.entries) { filter ->
                        val selected = activeFilter == filter
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) GoldPrimary else TitaniumCardBg,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (selected) GoldPrimary else TitaniumBorder
                            ),
                            modifier = Modifier
                                .heightIn(min = 36.dp)
                                .clickable { activeFilter = filter }
                                .testTag("chart_filter_${filter.name.lowercase()}")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = filter.label,
                                    color = if (selected) Color.Black else TextWhitePrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Text(
                    text = if (isMarketOpen) {
                        "Streaming 1s IST Clock: $liveIstClock • Drag any graph to inspect second-by-second prices"
                    } else {
                        "Closed at 12:00 AM IST • $reopenNote • Graph prices hidden until reopen"
                    },
                    color = if (isMarketOpen) TextMutedSecondary else Color(0xFFFFC5C5),
                    fontSize = 9.5.sp
                )
            }
        }

        if (!isMarketOpen) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B1010)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF991B1B)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("charts_market_closed_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "MARKET CLOSED AT 12:00 AM IST",
                        color = Color(0xFFFF8A80),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "All live 1-second charts and bullion prices are hidden during market closure.",
                        color = Color(0xFFFCA5A5),
                        fontSize = 11.sp
                    )
                    Text(
                        text = reopenNote,
                        color = GoldLight,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Individual Live 1s Graph Card for Each Contract
        filteredContracts.forEach { contract ->
            val points = historyMap[contract.id].orEmpty()
            val isPhysicalContract = contract.id == "gold_999" || contract.id == "silver_1kg"
            val isContractClosed = !isMarketOpen || (isPhysicalContract && !isPhysicalBullionOpen)

            LiveOneSecondGraphCard(
                contract = contract,
                points = points,
                isClosed = isContractClosed,
                reopenNote = reopenNote,
                modifier = Modifier.testTag("live_chart_${contract.id}")
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun LiveOneSecondGraphCard(
    contract: CommodityContract,
    points: List<PricePoint>,
    isClosed: Boolean,
    reopenNote: String,
    modifier: Modifier = Modifier
) {
    var inspectedIndex by remember(contract.id) { mutableStateOf<Int?>(null) }

    val pulseTransition = rememberInfiniteTransition(label = "chart_pulse_${contract.id}")
    val pulseRadius by pulseTransition.animateFloat(
        initialValue = 4.5f,
        targetValue = 9.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_r"
    )

    val effectivePoints = remember(points, contract.ltp, contract.lastUpdatedMillis) {
        if (points.isEmpty()) {
            listOf(PricePoint(contract.lastUpdatedMillis, contract.ltp))
        } else {
            points
        }
    }

    val startPrice = effectivePoints.firstOrNull()?.price ?: contract.ltp
    val latestPrice = effectivePoints.lastOrNull()?.price ?: contract.ltp
    val windowDelta = latestPrice - startPrice
    val windowDeltaPct = if (startPrice > 0.0) (windowDelta / startPrice) * 100.0 else 0.0

    val trendIsUp = when {
        windowDelta > 0.0001 -> true
        windowDelta < -0.0001 -> false
        else -> contract.lastTickDirection != PriceTickDirection.DOWN
    }
    val lineColor = when {
        isClosed -> Color(0xFFFF5252)
        trendIsUp -> MarketGreen
        else -> Color(0xFFFF5252)
    }
    val arrowSymbol = when {
        isClosed -> "•"
        contract.lastTickDirection == PriceTickDirection.DOWN -> "▼"
        contract.lastTickDirection == PriceTickDirection.UP -> "▲"
        trendIsUp -> "▲"
        else -> "▼"
    }

    val minPrice = effectivePoints.minOfOrNull { it.price } ?: contract.ltp
    val maxPrice = effectivePoints.maxOfOrNull { it.price } ?: contract.ltp
    val midPrice = (minPrice + maxPrice) / 2.0

    val inspectedPoint = inspectedIndex?.let { idx ->
        effectivePoints.getOrNull(idx.coerceIn(0, effectivePoints.lastIndex))
    }

    val badgeLabel = when (contract.id) {
        "gold_999", "silver_1kg" -> "PHYSICAL 1s"
        "mcx_gold", "mcx_silver" -> "MCX FUTURES 1s"
        "spot_gold", "spot_silver" -> "SPOT CENT ($) 1s"
        else -> "FOREX 1s"
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TitaniumDarkSurface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isClosed) Color(0xFFFF5252).copy(alpha = 0.45f) else lineColor.copy(alpha = 0.4f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Card Header: Symbol + Live Price + Red/Green Delta
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when (contract.id) {
                                "spot_gold" -> "GOLD CENT ($/oz)"
                                "spot_silver" -> "SILVER CENT ($/oz)"
                                "mcx_gold" -> "MCX GOLD (10g)"
                                "mcx_silver" -> "MCX SILVER (1kg)"
                                "gold_999" -> "GOLD 999 (10g)"
                                "silver_1kg" -> "SILVER 1KG"
                                else -> contract.symbol
                            },
                            color = TextWhitePrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isClosed) Color(0xFF450A0A) else TitaniumCardBg
                        ) {
                            Text(
                                text = if (isClosed) "CLOSED" else badgeLabel,
                                color = if (isClosed) Color(0xFFFF8A80) else GoldLight,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = contract.name,
                        color = TextMutedSecondary,
                        fontSize = 10.sp
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (isClosed) "CLOSED" else formatChartPrice(latestPrice, contract.unit),
                        color = lineColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                    val deltaSign = if (windowDelta >= 0) "+" else ""
                    Text(
                        text = if (isClosed) {
                            "Prices Hidden"
                        } else {
                            "$arrowSymbol $deltaSign${formatChartDelta(windowDelta, contract.unit)} (${String.format("%.2f", windowDeltaPct)}%)"
                        },
                        color = lineColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (isClosed) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1F1012))
                        .border(0.8.dp, Color(0xFF991B1B), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "MARKET CLOSED • CHART HIDDEN",
                            color = Color(0xFFFF8A80),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = reopenNote,
                            color = GoldLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                return@Column
            }

            // Crosshair Inspection Bar (when dragging/tapping)
            if (inspectedPoint != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(TitaniumCardBg)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Tick Time: ${chartTimeFormat.format(Date(inspectedPoint.timestamp))}",
                        color = GoldLight,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Price: ${formatChartPrice(inspectedPoint.price, contract.unit)}",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Live 1-Second Canvas Plot + Right Y-Axis Price Scale
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(146.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0A0D13))
                    .border(0.6.dp, TitaniumBorder, RoundedCornerShape(10.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(effectivePoints) {
                            detectDragGestures(
                                onDragEnd = { inspectedIndex = null },
                                onDragCancel = { inspectedIndex = null },
                                onDrag = { change, _ ->
                                    if (effectivePoints.size > 1 && size.width > 0) {
                                        val ratio = (change.position.x / size.width).coerceIn(0f, 1f)
                                        inspectedIndex = (ratio * (effectivePoints.size - 1)).toInt()
                                    }
                                }
                            )
                        }
                        .pointerInput(effectivePoints) {
                            detectTapGestures { offset ->
                                if (effectivePoints.size > 1 && size.width > 0) {
                                    val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                                    inspectedIndex = (ratio * (effectivePoints.size - 1)).toInt()
                                }
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        if (w <= 0f || h <= 0f || effectivePoints.isEmpty()) return@Canvas

                        val rawRange = maxPrice - minPrice
                        val pad = if (rawRange > 0.0001) rawRange * 0.15 else (latestPrice * 0.0005).coerceAtLeast(0.05)
                        val plotMin = minPrice - pad
                        val plotMax = maxPrice + pad
                        val plotSpan = (plotMax - plotMin).coerceAtLeast(0.001)

                        fun yForPrice(price: Double): Float {
                            return (h - ((price - plotMin) / plotSpan * h)).toFloat().coerceIn(4f, h - 4f)
                        }

                        // Grid lines
                        for (g in 1..3) {
                            val gy = h * (g / 4f)
                            drawLine(
                                color = Color(0xFF1D2430),
                                start = Offset(0f, gy),
                                end = Offset(w, gy),
                                strokeWidth = 1f
                            )
                        }

                        val linePath = Path()
                        val fillPath = Path()

                        if (effectivePoints.size == 1) {
                            val y = h / 2f
                            linePath.moveTo(0f, y)
                            linePath.lineTo(w, y)
                            fillPath.moveTo(0f, h)
                            fillPath.lineTo(0f, y)
                            fillPath.lineTo(w, y)
                            fillPath.lineTo(w, h)
                            fillPath.close()
                        } else {
                            val stepX = w / (effectivePoints.size - 1).toFloat()
                            effectivePoints.forEachIndexed { idx, pt ->
                                val x = idx * stepX
                                val y = yForPrice(pt.price)
                                if (idx == 0) {
                                    linePath.moveTo(x, y)
                                    fillPath.moveTo(x, h)
                                    fillPath.lineTo(x, y)
                                } else {
                                    linePath.lineTo(x, y)
                                    fillPath.lineTo(x, y)
                                }
                                if (idx == effectivePoints.lastIndex) {
                                    fillPath.lineTo(x, h)
                                    fillPath.close()
                                }
                            }
                        }

                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    lineColor.copy(alpha = 0.28f),
                                    Color.Transparent
                                )
                            )
                        )

                        drawPath(
                            path = linePath,
                            color = lineColor,
                            style = Stroke(
                                width = 2.4.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )

                        // Dashed current price level
                        val lastY = if (effectivePoints.size == 1) h / 2f else yForPrice(latestPrice)
                        drawLine(
                            color = lineColor.copy(alpha = 0.45f),
                            start = Offset(0f, lastY),
                            end = Offset(w, lastY),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )

                        // Pulsing live 1s endpoint dot
                        val lastCenter = Offset(w - 2f, lastY)
                        drawCircle(
                            color = lineColor.copy(alpha = 0.28f),
                            radius = pulseRadius.dp.toPx(),
                            center = lastCenter
                        )
                        drawCircle(
                            color = lineColor,
                            radius = 3.5.dp.toPx(),
                            center = lastCenter
                        )

                        // Touch crosshair marker
                        val idx = inspectedIndex
                        if (idx != null && effectivePoints.size > 1) {
                            val clamped = idx.coerceIn(0, effectivePoints.lastIndex)
                            val cx = (clamped.toFloat() / (effectivePoints.size - 1).toFloat()) * w
                            val cy = yForPrice(effectivePoints[clamped].price)
                            drawLine(
                                color = Color.White.copy(alpha = 0.55f),
                                start = Offset(cx, 0f),
                                end = Offset(cx, h),
                                strokeWidth = 1f
                            )
                            drawCircle(
                                color = GoldPrimary,
                                radius = 4.dp.toPx(),
                                center = Offset(cx, cy)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Y-Axis Price Scale (High / Mid / Low)
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = formatCompactAxisPrice(maxPrice, contract.unit),
                        color = MarketGreen.copy(alpha = 0.85f),
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = formatCompactAxisPrice(midPrice, contract.unit),
                        color = TextMutedSecondary,
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = formatCompactAxisPrice(minPrice, contract.unit),
                        color = Color(0xFFFF8A80),
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Bottom X-Axis IST Timestamps & Bid/Ask Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val firstTime = chartTimeFormat.format(Date(effectivePoints.first().timestamp))
                val midTime = chartTimeFormat.format(Date(effectivePoints[effectivePoints.size / 2].timestamp))
                val lastTime = chartTimeFormat.format(Date(effectivePoints.last().timestamp))
                Text(text = firstTime, color = TextDimTertiary, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace)
                Text(text = midTime, color = TextDimTertiary, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace)
                Text(text = "$lastTime IST", color = GoldLight, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(TitaniumCardBg)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Bid: ${formatChartPrice(contract.bidPrice, contract.unit)}",
                    color = TextWhitePrimary,
                    fontSize = 9.5.sp
                )
                Text(
                    text = "Ask: ${formatChartPrice(contract.askPrice, contract.unit)}",
                    color = TextWhitePrimary,
                    fontSize = 9.5.sp
                )
                Text(
                    text = "1s Ticks: ${effectivePoints.size}",
                    color = GoldPrimary,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun formatChartPrice(value: Double, unit: ContractUnit): String {
    return when (unit) {
        ContractUnit.TROY_OUNCE -> "$${String.format(Locale.ENGLISH, "%.2f", value)}"
        ContractUnit.CURRENCY -> "₹${String.format(Locale.ENGLISH, "%.2f", value)}"
        else -> "₹${String.format(Locale.ENGLISH, "%,.0f", value)}"
    }
}

private fun formatCompactAxisPrice(value: Double, unit: ContractUnit): String {
    return when (unit) {
        ContractUnit.TROY_OUNCE -> "$${String.format(Locale.ENGLISH, "%.2f", value)}"
        ContractUnit.CURRENCY -> "₹${String.format(Locale.ENGLISH, "%.2f", value)}"
        else -> "₹${String.format(Locale.ENGLISH, "%,.0f", value)}"
    }
}

private fun formatChartDelta(delta: Double, unit: ContractUnit): String {
    return when (unit) {
        ContractUnit.TROY_OUNCE -> "$${String.format(Locale.ENGLISH, "%.2f", kotlin.math.abs(delta))}"
        ContractUnit.CURRENCY -> "₹${String.format(Locale.ENGLISH, "%.2f", kotlin.math.abs(delta))}"
        else -> "₹${String.format(Locale.ENGLISH, "%,.0f", kotlin.math.abs(delta))}"
    }
}
