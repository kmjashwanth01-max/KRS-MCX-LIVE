package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.HistoricalDataPoint
import com.example.model.PeriodSummary
import com.example.ui.theme.*

@Composable
fun HistoricalChartSection(
    periodSummary: PeriodSummary?,
    historicalPoints: List<HistoricalDataPoint>,
    selectedRange: String,
    onSelectRange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val ranges = listOf("1D", "1W", "1M", "1Y", "5Y")

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TitaniumDarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, TitaniumBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("historical_chart_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Historical Price Trends",
                        color = TextWhitePrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Period High: ₹${String.format("%,.0f", periodSummary?.highPrice ?: 146109.0)} | Low: ₹${String.format("%,.0f", periodSummary?.lowPrice ?: 141444.0)}",
                        color = GoldLight,
                        fontSize = 10.5.sp
                    )
                }

                if (periodSummary != null) {
                    val isPos = periodSummary.change >= 0
                    Text(
                        text = "${if (isPos) "+" else ""}${String.format("%.2f", periodSummary.changePercent)}%",
                        color = if (isPos) MarketGreen else MarketRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Chart area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0C0E14))
                    .padding(8.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    drawLine(Color(0xFF1E222D), Offset(0f, h * 0.33f), Offset(w, h * 0.33f), strokeWidth = 1f)
                    drawLine(Color(0xFF1E222D), Offset(0f, h * 0.66f), Offset(w, h * 0.66f), strokeWidth = 1f)

                    val path = Path()
                    path.moveTo(0f, h * 0.7f)
                    path.cubicTo(w * 0.25f, h * 0.85f, w * 0.45f, h * 0.3f, w * 0.7f, h * 0.45f)
                    path.cubicTo(w * 0.85f, h * 0.55f, w * 0.95f, h * 0.2f, w, h * 0.25f)

                    drawPath(
                        path = path,
                        color = GoldPrimary,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Range pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ranges.forEach { range ->
                    val isSel = selectedRange == range
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) GoldGlow else Color.Transparent)
                            .border(1.dp, if (isSel) GoldPrimary else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable { onSelectRange(range) }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = range,
                            color = if (isSel) GoldLight else TextDimTertiary,
                            fontSize = 11.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
