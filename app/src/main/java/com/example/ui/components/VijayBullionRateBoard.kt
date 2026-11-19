package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CommodityContract
import com.example.model.PriceTickDirection
import com.example.ui.theme.*

enum class BullionBoardCategory(val title: String) {
    ALL("All Products"),
    GOLD("Gold Pure"),
    SILVER("Silver Pure"),
    JEWELRY("RTGS Purity")
}

@Composable
fun VijayBullionRateBoard(
    contracts: List<CommodityContract>,
    selectedContractId: String,
    onSelectContract: (String) -> Unit,
    isMarketOpen: Boolean = true,
    isPhysicalBullionOpen: Boolean = true,
    marketSessionDetail: String = "",
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    var selectedCategory by remember { mutableStateOf(BullionBoardCategory.ALL) }

    val baseContracts = when {
        !isMarketOpen -> emptyList()
        isPhysicalBullionOpen -> contracts
        else -> contracts.filterNot { it.id == "gold_999" || it.id == "silver_1kg" }
    }

    val filteredList = when (selectedCategory) {
        BullionBoardCategory.ALL -> baseContracts.filter { !it.id.startsWith("SPOT_") }
        BullionBoardCategory.GOLD -> baseContracts.filter { it.id.contains("GOLD") && !it.id.startsWith("SPOT_") }
        BullionBoardCategory.SILVER -> baseContracts.filter { it.id.contains("SILVER") && !it.id.startsWith("SPOT_") }
        BullionBoardCategory.JEWELRY -> baseContracts.filter { it.id.contains("22K") || it.id.contains("18K") || it.id.contains("995") }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "KRS Real-Time Rate Board",
                color = if (isDark) TextWhitePrimary else Color(0xFF0F172A),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isMarketOpen) "🟢 Live 1s Updates" else "🔴 Market Closed",
                color = if (isMarketOpen) {
                    if (isDark) MarketGreen else MarketGreenDark
                } else {
                    if (isDark) Color(0xFFFF5252) else MarketRedDark
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (isMarketOpen) {
            // Category Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BullionBoardCategory.entries.forEach { cat ->
                    val isSelected = selectedCategory == cat
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) {
                            if (isDark) GoldGlow else Color(0xFFFEF3C7)
                        } else {
                            if (isDark) TitaniumDarkSurface else Color(0xFFF1F5F9)
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) (if (isDark) GoldPrimary else Color(0xFFB45309)) else (if (isDark) TitaniumBorder else Color(0xFFE2E8F0))
                        ),
                        modifier = Modifier
                            .clickable { selectedCategory = cat }
                            .testTag("rate_board_tab_${cat.name.lowercase()}")
                    ) {
                        Text(
                            text = cat.title,
                            color = if (isSelected) {
                                if (isDark) GoldLight else Color(0xFFB45309)
                            } else {
                                if (isDark) TextMutedSecondary else Color(0xFF64748B)
                            },
                            fontSize = 10.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        // Rate Cards
        if (!isMarketOpen || filteredList.isEmpty()) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF241010) else Color(0xFFFEF2F2)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth().testTag("rate_board_closed_notice")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (!isMarketOpen) {
                            "Market closes at 12:00 AM IST. Live rates are hidden while trading is closed."
                        } else {
                            "Physical bullion rates in this category are closed right now and will appear when the market opens."
                        },
                        color = if (isDark) Color(0xFFFFC5C5) else Color(0xFF7F1D1D),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!isMarketOpen) {
                        Text(
                            text = marketSessionDetail.ifBlank { "Reopens at 09:00 AM IST" },
                            color = if (isDark) Color(0xFFFF8A80) else Color(0xFFB91C1C),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                filteredList.take(6).forEach { contract ->
                    BullionDualRateCard(
                        contract = contract,
                        isSelected = contract.id == selectedContractId,
                        onClick = { onSelectContract(contract.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BullionDualRateCard(
    contract: CommodityContract,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val buyFlash by animateColorAsState(
        targetValue = when (contract.bidTickDirection) {
            PriceTickDirection.UP -> if (isDark) MarketGreen.copy(alpha = 0.35f) else Color(0xFFDCFCE7)
            PriceTickDirection.DOWN -> if (isDark) MarketRed.copy(alpha = 0.35f) else Color(0xFFFEE2E2)
            else -> if (isDark) Color(0xFF1E232F) else Color(0xFFF8FAFC)
        },
        animationSpec = tween(350),
        label = "buyBoxFlash"
    )

    val sellFlash by animateColorAsState(
        targetValue = when (contract.askTickDirection) {
            PriceTickDirection.UP -> if (isDark) MarketGreen.copy(alpha = 0.35f) else Color(0xFFDCFCE7)
            PriceTickDirection.DOWN -> if (isDark) MarketRed.copy(alpha = 0.35f) else Color(0xFFFEE2E2)
            else -> if (isDark) Color(0xFF1E232F) else Color(0xFFF8FAFC)
        },
        animationSpec = tween(350),
        label = "sellBoxFlash"
    )

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDark) TitaniumDarkSurface else Color.White),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) (if (isDark) GoldPrimary else Color(0xFFB45309)) else (if (isDark) TitaniumBorder else Color(0xFFCBD5E1))
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Product info
            Column(modifier = Modifier.weight(1.1f)) {
                Text(
                    text = contract.symbol,
                    color = if (isDark) GoldLight else Color(0xFFB45309),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${contract.name} • ${contract.unit.displayName}",
                    color = if (isDark) TextDimTertiary else Color(0xFF64748B),
                    fontSize = 10.sp
                )
            }

            // Dual Buy/Sell boxes (Dynamic Green & Red according to price movement)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Buy Box
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(buyFlash)
                        .border(
                            1.dp,
                            if (isDark) MarketGreen.copy(alpha = 0.4f) else Color(0xFFBBF7D0),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "BUY",
                            fontSize = 9.sp,
                            color = if (isDark) MarketGreen else MarketGreenDark,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "₹${String.format("%,.0f", contract.bidPrice)}",
                            fontSize = 13.sp,
                            color = if (isDark) Color.White else Color(0xFF0F172A),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Sell Box
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(sellFlash)
                        .border(
                            1.dp,
                            if (isDark) MarketRed.copy(alpha = 0.4f) else Color(0xFFFECACA),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "SELL",
                            fontSize = 9.sp,
                            color = if (isDark) MarketRed else MarketRedDark,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "₹${String.format("%,.0f", contract.askPrice)}",
                            fontSize = 13.sp,
                            color = if (isDark) Color.White else Color(0xFF0F172A),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
