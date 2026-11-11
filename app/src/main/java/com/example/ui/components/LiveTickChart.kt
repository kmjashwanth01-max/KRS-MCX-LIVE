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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CommodityContract
import com.example.ui.theme.*

@Composable
fun LiveTickChart(
    contract: CommodityContract?,
    selectedRange: String,
    onSelectRange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val ranges = listOf("1s LIVE", "1D", "1W", "1M", "1Y", "5Y")

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TitaniumDarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, TitaniumBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("live_tick_chart_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${contract?.symbol ?: "MCX GOLD"} Intraday Chart",
                        color = TextWhitePrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "1-Second Real-Time Price Movement",
                        color = TextDimTertiary,
                        fontSize = 10.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MarketGreenGlow
                ) {
                    Text(
                        text = "• 1s STREAM",
                        color = MarketGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Chart Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0C0E14))
                    .padding(8.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Grid lines
                    drawLine(Color(0xFF1E222D), Offset(0f, h * 0.25f), Offset(w, h * 0.25f), strokeWidth = 1f)
                    drawLine(Color(0xFF1E222D), Offset(0f, h * 0.5f), Offset(w, h * 0.5f), strokeWidth = 1f)
                    drawLine(Color(0xFF1E222D), Offset(0f, h * 0.75f), Offset(w, h * 0.75f), strokeWidth = 1f)

                    // Wave curve
                    val path = Path()
                    path.moveTo(0f, h * 0.65f)
                    path.cubicTo(w * 0.2f, h * 0.8f, w * 0.35f, h * 0.45f, w * 0.5f, h * 0.5f)
                    path.cubicTo(w * 0.65f, h * 0.55f, w * 0.85f, h * 0.2f, w, h * 0.3f)

                    drawPath(
                        path = path,
                        color = GoldPrimary,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Pulsing latest tick point
                    drawCircle(
                        color = GoldPrimary,
                        radius = 4.dp.toPx(),
                        center = Offset(w, h * 0.3f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Range Selectors
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ranges.forEach { range ->
                    val isSel = selectedRange == range
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSel) GoldGlow else Color.Transparent)
                            .border(1.dp, if (isSel) GoldPrimary else Color.Transparent, RoundedCornerShape(6.dp))
                            .clickable { onSelectRange(range) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = range,
                            color = if (isSel) GoldLight else TextDimTertiary,
                            fontSize = 10.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
