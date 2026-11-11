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
 * Separate Table 3: International Spot Rates & Currency
 * Features Yellow Header (SPOT RATES | BID | ASK | H|L) on Grey surface,
 * while BID and ASK rate boxes dynamically change between Green and Red according to live price ticks.
 */
@Composable
fun SpotRatesCentTableView(
    spotGold: CommodityContract?,
    spotSilver: CommodityContract?,
    spotInr: CommodityContract?,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val tableBorderColor = if (isDark) BullionYellow.copy(alpha = 0.65f) else Color(0xFFD97706)
    // Changed Spot Rates header bar to Yellow per user instruction
    val headerBgColor = if (isDark) BullionYellow else Color(0xFFFACC15)
    val headerTextColor = Color(0xFF111827)
    val bodyBgColor = if (isDark) TitaniumDarkSurface else Color(0xFFE5E7EB)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.5.dp, tableBorderColor, RoundedCornerShape(8.dp))
            .background(bodyBgColor)
            .testTag("spot_rates_cent_table_view")
    ) {
        // Table Header (Yellow)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBgColor)
                .padding(vertical = 10.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SPOT RATES",
                color = headerTextColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp,
                modifier = Modifier.weight(1.3f)
            )
            Text(
                text = "BID",
                color = headerTextColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1.1f)
            )
            Text(
                text = "ASK",
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

        // Row 1: GOLD($)
        SpotRateCentRowItem(
            name = "GOLD($)",
            symbol = "XAU/USD",
            contract = spotGold,
            defaultBid = 4070.77,
            defaultAsk = 4071.09,
            defaultHigh = 4107.00,
            defaultLow = 4035.58,
            isDollar = true
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(tableBorderColor)
        )

        // Row 2: SILVER($)
        SpotRateCentRowItem(
            name = "SILVER($)",
            symbol = "XAG/USD",
            contract = spotSilver,
            defaultBid = 52.01,
            defaultAsk = 52.02,
            defaultHigh = 52.21,
            defaultLow = 51.17,
            isDollar = true
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(tableBorderColor)
        )

        // Row 3: INR(₹)
        SpotRateCentRowItem(
            name = "INR(₹)",
            symbol = "USD/INR",
            contract = spotInr,
            defaultBid = 93.90,
            defaultAsk = 93.94,
            defaultHigh = 94.52,
            defaultLow = 93.81,
            isDollar = false
        )
    }
}

@Composable
private fun SpotRateCentRowItem(
    name: String,
    symbol: String,
    contract: CommodityContract?,
    defaultBid: Double,
    defaultAsk: Double,
    defaultHigh: Double,
    defaultLow: Double,
    isDollar: Boolean
) {
    val isDark = LocalIsDarkTheme.current
    val bidVal = contract?.bidPrice ?: defaultBid
    val askVal = contract?.askPrice ?: defaultAsk
    val highVal = contract?.high ?: defaultHigh
    val lowVal = contract?.low ?: defaultLow

    val infiniteTransition = rememberInfiniteTransition(label = "spotRowPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val bidIsUp = (contract?.bidTickDirection ?: PriceTickDirection.UP) == PriceTickDirection.UP
    val bidLightColor = if (bidIsUp) (if (isDark) MarketGreen else MarketGreenDark) else (if (isDark) MarketRed else MarketRedDark)

    val askIsUp = (contract?.askTickDirection ?: PriceTickDirection.UP) == PriceTickDirection.UP
    val askLightColor = if (askIsUp) (if (isDark) MarketGreen else MarketGreenDark) else (if (isDark) MarketRed else MarketRedDark)

    val bidBgColor by animateColorAsState(
        targetValue = when (contract?.bidTickDirection) {
            PriceTickDirection.UP -> if (isDark) MarketGreen.copy(alpha = 0.35f) else Color(0xFFDCFCE7)
            PriceTickDirection.DOWN -> if (isDark) MarketRed.copy(alpha = 0.35f) else Color(0xFFFEE2E2)
            else -> if (isDark) MarketGreen.copy(alpha = 0.35f) else Color(0xFFDCFCE7)
        },
        animationSpec = tween(400),
        label = "spotBidFlash"
    )

    val askBgColor by animateColorAsState(
        targetValue = when (contract?.askTickDirection) {
            PriceTickDirection.UP -> if (isDark) MarketGreen.copy(alpha = 0.35f) else Color(0xFFDCFCE7)
            PriceTickDirection.DOWN -> if (isDark) MarketRed.copy(alpha = 0.35f) else Color(0xFFFEE2E2)
            else -> if (isDark) MarketRed.copy(alpha = 0.35f) else Color(0xFFFEE2E2)
        },
        animationSpec = tween(400),
        label = "spotAskFlash"
    )

    val primaryTextColor = if (isDark) Color.White else Color(0xFF0F172A)
    val labelColor = if (isDark) BullionYellow else Color(0xFFB45309)
    val symbolColor = if (isDark) TextDimTertiary else Color(0xFF4B5563)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1.3f)) {
            Text(
                text = name,
                color = labelColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = symbol,
                color = symbolColor,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // BID box with dynamic Green/Red price tick color
        Box(
            modifier = Modifier
                .weight(1.1f)
                .clip(RoundedCornerShape(6.dp))
                .background(bidBgColor)
                .border(
                    1.dp,
                    bidLightColor.copy(alpha = 0.6f),
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
                        .background(bidLightColor.copy(alpha = pulseAlpha))
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = String.format("%.2f", bidVal),
                    color = primaryTextColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // ASK box with dynamic Green/Red price tick color
        Box(
            modifier = Modifier
                .weight(1.1f)
                .clip(RoundedCornerShape(6.dp))
                .background(askBgColor)
                .border(
                    1.dp,
                    askLightColor.copy(alpha = 0.6f),
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
                        .background(askLightColor.copy(alpha = pulseAlpha))
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = String.format("%.2f", askVal),
                    color = primaryTextColor,
                    fontSize = 15.sp,
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
                text = "H : ${String.format("%.2f", highVal)}",
                color = primaryTextColor,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "L : ${String.format("%.2f", lowVal)}",
                color = primaryTextColor,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
