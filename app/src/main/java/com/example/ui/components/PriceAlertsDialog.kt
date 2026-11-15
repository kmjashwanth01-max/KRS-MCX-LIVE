package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.ActivePriceAlert
import com.example.model.CommodityContract
import com.example.ui.theme.*
import java.text.DecimalFormat

@Composable
fun PriceAlertsDialog(
    contracts: List<CommodityContract>,
    selectedContractId: String? = null,
    activeAlerts: List<ActivePriceAlert> = emptyList(),
    onDismiss: () -> Unit,
    onSetAlert: (contractId: String, targetPrice: Double, isAbove: Boolean) -> Unit,
    onRemoveAlert: ((String) -> Unit)? = null
) {
    val rupeeFormat = remember { DecimalFormat("##,##,##0.00") }
    
    // Choose primary contracts (Gold 999 10g, Silver 1kg, MCX Gold, MCX Silver)
    val eligibleContracts = remember(contracts) {
        if (contracts.isNotEmpty()) contracts else emptyList()
    }

    var chosenContractId by remember {
        mutableStateOf(selectedContractId ?: eligibleContracts.firstOrNull()?.id ?: "gold_999")
    }

    val currentContract = eligibleContracts.firstOrNull { it.id == chosenContractId } 
        ?: eligibleContracts.firstOrNull()

    var isBuyTarget by remember { mutableStateOf(false) } // False = Sell (Rises above), True = Buy (Drops below)
    var targetPriceInput by remember(chosenContractId, currentContract?.ltp) {
        mutableStateOf(String.format("%.0f", currentContract?.ltp ?: 146040.0))
    }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = TitaniumDarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.2.dp, GoldPrimary),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .padding(vertical = 12.dp)
                .testTag("price_alert_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(GoldPrimary.copy(alpha = 0.2f))
                                .border(1.dp, GoldPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "Alerts",
                                tint = GoldPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Gold & Silver Price Alerts",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextWhitePrimary,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Real-time Push Notifications • 1s Live Stream",
                                style = MaterialTheme.typography.labelSmall,
                                color = GoldLight,
                                fontSize = 10.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp).testTag("close_alert_dialog_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.LightGray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 1. Commodity Selector Chips
                Text(
                    text = "SELECT COMMODITY CONTRACT:",
                    color = TextDimTertiary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(eligibleContracts) { c ->
                        val isSelected = c.id == chosenContractId
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) GoldPrimary else TitaniumCardBg,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) GoldLight else TitaniumBorder
                            ),
                            modifier = Modifier
                                .clickable {
                                    chosenContractId = c.id
                                    targetPriceInput = String.format("%.0f", c.ltp)
                                    statusMessage = null
                                }
                                .testTag("select_contract_${c.id}")
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = c.symbol,
                                    color = if (isSelected) Color.Black else TextWhitePrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "₹${rupeeFormat.format(c.ltp)}",
                                    color = if (isSelected) Color.Black else GoldLight,
                                    fontSize = 10.5.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Current Contract Status Banner
                if (currentContract != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = TitaniumCardSurface,
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, TitaniumBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = currentContract.name,
                                    color = TextWhitePrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Current LTP: ₹${rupeeFormat.format(currentContract.ltp)}",
                                    color = GoldPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (currentContract.change >= 0) MarketGreen.copy(alpha = 0.2f) else MarketRed.copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(
                                    0.5.dp,
                                    if (currentContract.change >= 0) MarketGreen else MarketRed
                                )
                            ) {
                                Text(
                                    text = "${if (currentContract.change >= 0) "+" else ""}${currentContract.changePercent}%",
                                    color = if (currentContract.change >= 0) MarketGreen else MarketRed,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Buy vs Sell Target Mode
                Text(
                    text = "TARGET TYPE & CONDITION:",
                    color = TextDimTertiary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // BUY TARGET (Dip buying: drops below)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isBuyTarget) MarketGreen.copy(alpha = 0.2f) else TitaniumCardBg,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isBuyTarget) MarketGreen else TitaniumBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isBuyTarget = true }
                            .testTag("alert_target_buy")
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🛒 BUY TARGET",
                                color = if (isBuyTarget) MarketGreen else Color.LightGray,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Drops Below (≤ Target)",
                                color = TextDimTertiary,
                                fontSize = 9.5.sp
                            )
                        }
                    }

                    // SELL TARGET (Profit booking: rises above)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (!isBuyTarget) GoldPrimary.copy(alpha = 0.2f) else TitaniumCardBg,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (!isBuyTarget) GoldPrimary else TitaniumBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isBuyTarget = false }
                            .testTag("alert_target_sell")
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "📈 SELL TARGET",
                                color = if (!isBuyTarget) GoldPrimary else Color.LightGray,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Rises Above (≥ Target)",
                                color = TextDimTertiary,
                                fontSize = 9.5.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Quick Percentage Delta Presets
                val basePrice = currentContract?.ltp ?: 146040.0
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = if (isBuyTarget) {
                        listOf(-0.25, -0.5, -1.0, -2.0)
                    } else {
                        listOf(0.25, 0.5, 1.0, 2.0)
                    }
                    presets.forEach { pct ->
                        val calcPrice = basePrice * (1.0 + pct / 100.0)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = TitaniumCardSurface,
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, GoldLight.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    targetPriceInput = String.format("%.0f", calcPrice)
                                }
                        ) {
                            Text(
                                text = "${if (pct > 0) "+" else ""}$pct%",
                                color = if (pct > 0) GoldLight else MarketGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 4. Target Trigger Price TextField
                OutlinedTextField(
                    value = targetPriceInput,
                    onValueChange = { targetPriceInput = it },
                    label = { Text("Target Trigger Price (₹)") },
                    leadingIcon = {
                        Text(
                            text = "₹",
                            color = GoldPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = TitaniumBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("alert_price_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Activate Button
                Button(
                    onClick = {
                        val target = targetPriceInput.toDoubleOrNull()
                        if (target != null && target > 0) {
                            val isAbove = !isBuyTarget
                            onSetAlert(chosenContractId, target, isAbove)
                            statusMessage = "Alert activated for ${currentContract?.symbol ?: chosenContractId} at ₹${rupeeFormat.format(target)}!"
                        } else {
                            statusMessage = "Please enter a valid numeric target price."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldPrimary,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("save_alert_button")
                ) {
                    Icon(Icons.Default.AddAlert, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isBuyTarget) "Set BUY Target Alert" else "Set SELL Target Alert",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                AnimatedVisibility(visible = statusMessage != null) {
                    Text(
                        text = statusMessage ?: "",
                        color = GoldLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 5. Active & Triggered Price Alerts List
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ACTIVE & TRIGGERED ALERTS (${activeAlerts.size})",
                        color = TextDimTertiary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (activeAlerts.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = TitaniumCardBg,
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, TitaniumBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No price alerts currently set. Create a target threshold above to receive instant push notifications.",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        activeAlerts.forEach { alert ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = TitaniumCardSurface,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (alert.isTriggered) MarketRed.copy(alpha = 0.5f) else GoldPrimary.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("alert_item_${alert.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (alert.contractSymbol.isNotBlank()) alert.contractSymbol else alert.contractId.uppercase(),
                                                color = TextWhitePrimary,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (alert.isBuyTarget) MarketGreen.copy(alpha = 0.2f) else GoldPrimary.copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    text = if (alert.isBuyTarget) "BUY ≤" else "SELL ≥",
                                                    color = if (alert.isBuyTarget) MarketGreen else GoldLight,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Target Price: ₹${rupeeFormat.format(alert.targetPrice)}",
                                            color = GoldPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (alert.isTriggered) "🚨 TRIGGERED & NOTIFIED" else "🟢 Monitoring every 1s",
                                            color = if (alert.isTriggered) Color(0xFFFF8A80) else MarketGreen,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    if (onRemoveAlert != null) {
                                        IconButton(
                                            onClick = { onRemoveAlert(alert.id) },
                                            modifier = Modifier.size(32.dp).testTag("delete_alert_${alert.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Remove Alert",
                                                tint = Color.LightGray,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
