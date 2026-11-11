package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CommodityContract
import com.example.model.PriceTickDirection
import com.example.ui.theme.*

/**
 * Separate Table 1: Physical Bullion Spot Rates
 * Features Yellow Header (PHYSICAL BULLION | BUY | SELL | H|L) on Grey surface,
 * while BUY and SELL rate boxes dynamically change between Green and Red according to live price ticks.
 */
@Composable
fun PhysicalBullionTableView(
    physicalGold: CommodityContract?,
    physicalSilver: CommodityContract?,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val tableBorderColor = if (isDark) BullionYellow.copy(alpha = 0.65f) else Color(0xFFD97706)
    val headerBgColor = if (isDark) BullionYellow else Color(0xFFFACC15)
    val headerTextColor = Color(0xFF111827)
    val bodyBgColor = if (isDark) TitaniumDarkSurface else Color(0xFFE5E7EB)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.5.dp, tableBorderColor, RoundedCornerShape(8.dp))
            .background(bodyBgColor)
            .testTag("physical_bullion_table_view")
    ) {
        // Table Header Row (Yellow)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBgColor)
                .padding(vertical = 10.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PHYSICAL BULLION",
                color = headerTextColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp,
                modifier = Modifier.weight(1.3f)
            )
            Text(
                text = "BUY",
                color = headerTextColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1.1f)
            )
            Text(
                text = "SELL",
                color = headerTextColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1.1f)
            )
            Text(
                text = "H|L",
                color = headerTextColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(0.9f)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(tableBorderColor)
        )

        // Row 1: GOLD 999
        PhysicalBullionRowItem(
            name = "GOLD 999 (₹)",
            unitLabel = "10g RTGS",
            contract = physicalGold,
            nameColor = if (isDark) BullionYellow else Color(0xFFB45309)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(tableBorderColor)
        )

        // Row 2: SILVER 1KG
        PhysicalBullionRowItem(
            name = "SILVER 1KG (₹)",
            unitLabel = "1kg Bar",
            contract = physicalSilver,
            nameColor = if (isDark) BullionYellow else Color(0xFF1E293B)
        )
    }
}

@Composable
private fun PhysicalBullionRowItem(
    name: String,
    unitLabel: String,
    contract: CommodityContract?,
    nameColor: Color
) {
    val isDark = LocalIsDarkTheme.current
    val buyPrice = contract?.bidPrice ?: contract?.ltp ?: 0.0
    val sellPrice = contract?.askPrice ?: (buyPrice + if (name.contains("GOLD")) 60.0 else 100.0)
    val highPrice = contract?.high ?: (buyPrice + 400.0)
    val lowPrice = contract?.low ?: (buyPrice - 400.0)

    val infiniteTransition = rememberInfiniteTransition(label = "physRowPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Dynamic Green & Red according to live price tick direction
    val buyIsUp = (contract?.bidTickDirection ?: PriceTickDirection.UP) == PriceTickDirection.UP
    val buyLightColor = if (buyIsUp) (if (isDark) MarketGreen else MarketGreenDark) else (if (isDark) MarketRed else MarketRedDark)

    val sellIsUp = (contract?.askTickDirection ?: PriceTickDirection.UP) == PriceTickDirection.UP
    val sellLightColor = if (sellIsUp) (if (isDark) MarketGreen else MarketGreenDark) else (if (isDark) MarketRed else MarketRedDark)

    val buyBgColor by animateColorAsState(
        targetValue = when (contract?.bidTickDirection) {
            PriceTickDirection.UP -> if (isDark) MarketGreen.copy(alpha = 0.35f) else Color(0xFFDCFCE7)
            PriceTickDirection.DOWN -> if (isDark) MarketRed.copy(alpha = 0.35f) else Color(0xFFFEE2E2)
            else -> if (isDark) MarketGreen.copy(alpha = 0.35f) else Color(0xFFDCFCE7)
        },
        animationSpec = tween(400),
        label = "buyFlash"
    )

    val sellBgColor by animateColorAsState(
        targetValue = when (contract?.askTickDirection) {
            PriceTickDirection.UP -> if (isDark) MarketGreen.copy(alpha = 0.35f) else Color(0xFFDCFCE7)
            PriceTickDirection.DOWN -> if (isDark) MarketRed.copy(alpha = 0.35f) else Color(0xFFFEE2E2)
            else -> if (isDark) MarketRed.copy(alpha = 0.35f) else Color(0xFFFEE2E2)
        },
        animationSpec = tween(400),
        label = "sellFlash"
    )

    val primaryTextColor = if (isDark) Color.White else Color(0xFF0F172A)
    val subTextColor = if (isDark) TextDimTertiary else Color(0xFF4B5563)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1.3f)) {
            Text(
                text = name,
                color = nameColor,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.3.sp
            )
            Text(
                text = unitLabel,
                color = subTextColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // BUY box with dynamic Green/Red price tick color
        Box(
            modifier = Modifier
                .weight(1.1f)
                .clip(RoundedCornerShape(6.dp))
                .background(buyBgColor)
                .border(
                    1.dp,
                    buyLightColor.copy(alpha = 0.6f),
                    RoundedCornerShape(6.dp)
                )
                .padding(vertical = 4.dp, horizontal = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(buyLightColor.copy(alpha = pulseAlpha))
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = String.format("%,.0f", buyPrice),
                    color = primaryTextColor,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // SELL box with dynamic Green/Red price tick color
        Box(
            modifier = Modifier
                .weight(1.1f)
                .clip(RoundedCornerShape(6.dp))
                .background(sellBgColor)
                .border(
                    1.dp,
                    sellLightColor.copy(alpha = 0.6f),
                    RoundedCornerShape(6.dp)
                )
                .padding(vertical = 4.dp, horizontal = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(sellLightColor.copy(alpha = pulseAlpha))
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = String.format("%,.0f", sellPrice),
                    color = primaryTextColor,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
            }
        }

        Column(
            modifier = Modifier.weight(0.9f),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "H : ${String.format("%,.0f", highPrice)}",
                color = primaryTextColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "L : ${String.format("%,.0f", lowPrice)}",
                color = primaryTextColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
