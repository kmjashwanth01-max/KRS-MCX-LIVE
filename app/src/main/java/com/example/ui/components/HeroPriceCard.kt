package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CommodityContract
import com.example.model.CommodityType
import com.example.model.PriceTickDirection
import com.example.ui.theme.*
import java.text.DecimalFormat
import java.util.Locale

@Composable
fun HeroPriceCard(
    contract: CommodityContract?,
    onSelectGold: () -> Unit,
    onSelectSilver: () -> Unit,
    onSelectMcxGold: () -> Unit = {},
    onSelectMcxSilver: () -> Unit = {},
    onOpenAlertModal: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (contract == null) return

    val isDark = LocalIsDarkTheme.current
    val isGold = contract.type == CommodityType.GOLD
    val isPositive = contract.change >= 0

    // Decimal formatter for Indian Rupees
    val rupeeFormat = remember { DecimalFormat("##,##,##0.00") }
    val intFormat = remember { DecimalFormat("##,##,###") }

    // Tick flash color animation: briefly flashes green/red when tick changes, then returns to normal
    val flashColor by animateColorAsState(
        targetValue = when (contract.lastTickDirection) {
            PriceTickDirection.UP -> if (isDark) MarketGreen else MarketGreenDark
            PriceTickDirection.DOWN -> if (isDark) MarketRed else MarketRedDark
            PriceTickDirection.UNCHANGED -> if (isGold) (if (isDark) GoldPrimary else GoldLightMode) else (if (isDark) SilverPrimary else Color(0xFF1E293B))
        },
        animationSpec = tween(durationMillis = 600),
        label = "priceFlash"
    )

    val cardBorderColor = if (isDark) {
        if (isGold) GoldPrimary.copy(alpha = 0.35f) else SilverPrimary.copy(alpha = 0.35f)
    } else {
        if (isGold) GoldLightMode.copy(alpha = 0.4f) else Color(0xFFCBD5E1)
    }

    val cardBackgroundGradient = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                TitaniumCardSurface,
                TitaniumCardElevated
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.White,
                Color(0xFFF8FAFC)
            )
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.2.dp, cardBorderColor, RoundedCornerShape(24.dp))
            .testTag("hero_price_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDark) TitaniumCardSurface else Color.White)
    ) {
        Column(
            modifier = Modifier
                .background(cardBackgroundGradient)
                .padding(16.dp)
        ) {
            // Gold / Silver / MCX Futures Quick Selector Tabs & Expiry Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Commodity Toggle Pills
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(TitaniumDarkSurface)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val tabs = listOf(
                        Triple("MCX_GOLD", "Gold 999", onSelectGold),
                        Triple("MCX_GOLD_FUT", "MCX Gold", onSelectMcxGold),
                        Triple("MCX_SILVER", "Silver 1kg", onSelectSilver),
                        Triple("MCX_SILVER_FUT", "MCX Silver", onSelectMcxSilver)
                    )

                    tabs.forEach { (id, label, onClick) ->
                        val isSelected = contract.id == id
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    when {
                                        isSelected && id.contains("GOLD") -> GoldPrimary
                                        isSelected && id.contains("SILVER") -> SilverPrimary
                                        else -> Color.Transparent
                                    }
                                )
                                .clickable { onClick() }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) TitaniumBlack else TextMutedSecondary,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Alert Button
                IconButton(
                    onClick = onOpenAlertModal,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(TitaniumDarkSurface)
                        .border(1.dp, TitaniumBorder, CircleShape)
                        .testTag("set_alert_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AddAlert,
                        contentDescription = "Set price alert",
                        tint = if (isGold) GoldPrimary else SilverPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sub-header: Symbol & Unit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${contract.name} • MCX INDIA",
                    color = TextMutedSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "EXP: ${contract.expiry}",
                    color = TextDimTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Big Live Price (LTP) with Dynamic Tick Flash
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "₹",
                            color = flashColor,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = rupeeFormat.format(contract.ltp),
                            color = flashColor,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp,
                            modifier = Modifier.testTag("live_ltp_text")
                        )
                    }
                    Text(
                        text = "Quoted per ${contract.unit.label}",
                        color = TextDimTertiary,
                        fontSize = 12.sp
                    )
                }

                // Percent & Value Change Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isPositive) MarketGreenGlow else MarketRedGlow,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isPositive) MarketGreen.copy(alpha = 0.5f) else MarketRed.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isPositive) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                            contentDescription = if (isPositive) "Price Up" else "Price Down",
                            tint = if (isPositive) MarketGreen else MarketRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "${if (isPositive) "+" else ""}₹${rupeeFormat.format(contract.change)} (${String.format(Locale.ENGLISH, "%.2f", contract.percentChange)}%)",
                            color = if (isPositive) MarketGreen else MarketRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Day Range Slider (Low to High representation)
            val range = (contract.high - contract.low).coerceAtLeast(1.0)
            val progress = ((contract.ltp - contract.low) / range).coerceIn(0.0, 1.0).toFloat()

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "L: ₹${rupeeFormat.format(contract.low)}",
                        color = TextDimTertiary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "24h Range",
                        color = TextMutedSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "H: ₹${rupeeFormat.format(contract.high)}",
                        color = TextDimTertiary,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(TitaniumDarkSurface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = progress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(MarketRed.copy(alpha = 0.7f), MarketGreen)
                                )
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = TitaniumBorder, thickness = 1.dp)
            Spacer(modifier = Modifier.height(14.dp))

            // Intraday Financial Metrics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricColumn(title = "OPEN", value = "₹${rupeeFormat.format(contract.open)}")
                MetricColumn(title = "PREV CLOSE", value = "₹${rupeeFormat.format(contract.previousClose)}")
                MetricColumn(title = "VOLUME", value = intFormat.format(contract.volume))
                MetricColumn(title = "OI (LOTS)", value = intFormat.format(contract.openInterest))
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Retail bullion equivalent (Jewelry helper)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isDark) TitaniumDarkSurface else Color(0xFFF1F5F9))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Retail 1 Gram Spot Rate:",
                    color = if (isDark) TextMutedSecondary else Color(0xFF475569),
                    fontSize = 12.sp
                )
                Text(
                    text = "₹${rupeeFormat.format(contract.perGramRate)} / g",
                    color = if (isDark) {
                        if (isGold) GoldLight else SilverLight
                    } else {
                        if (isGold) GoldLightMode else Color(0xFF1E293B)
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun MetricColumn(title: String, value: String) {
    val isDark = LocalIsDarkTheme.current
    Column {
        Text(
            text = title,
            color = if (isDark) TextDimTertiary else Color(0xFF64748B),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = if (isDark) TextWhitePrimary else Color(0xFF0F172A),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
