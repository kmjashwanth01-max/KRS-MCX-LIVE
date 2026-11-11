package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.theme.*

@Composable
fun JewelryCalculatorView(
    liveGoldLtp: Double,
    purity: GoldPurity,
    weightGrams: Double,
    makingType: MakingChargeType,
    makingRate: Double,
    includeHallmark: Boolean,
    billBreakdown: JewelryBillBreakdown?,
    onPurityChange: (GoldPurity) -> Unit,
    onWeightChange: (Double) -> Unit,
    onMakingTypeChange: (MakingChargeType) -> Unit,
    onMakingRateChange: (Double) -> Unit,
    onToggleHallmark: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var weightInput by remember(weightGrams) { mutableStateOf(weightGrams.toString()) }
    var makingInput by remember(makingRate) { mutableStateOf(makingRate.toString()) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Purity Selector
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = TitaniumDarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, TitaniumBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Gold Purity / Karat",
                    color = TextWhitePrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GoldPurity.entries.forEach { p ->
                        val isSel = purity == p
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) GoldGlow else TitaniumCardSurface)
                                .clickable { onPurityChange(p) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = p.name.substringBefore("_"),
                                color = if (isSel) GoldLight else TextMutedSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Inputs
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = TitaniumDarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, TitaniumBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Weight input
                OutlinedTextField(
                    value = weightInput,
                    onValueChange = {
                        weightInput = it
                        it.toDoubleOrNull()?.let { w -> onWeightChange(w) }
                    },
                    label = { Text("Ornament Weight (Grams)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("jewelry_weight_input")
                )

                // Making charge input
                OutlinedTextField(
                    value = makingInput,
                    onValueChange = {
                        makingInput = it
                        it.toDoubleOrNull()?.let { m -> onMakingRateChange(m) }
                    },
                    label = { Text("Making Charges (${makingType.label})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("jewelry_making_rate_input")
                )

                // Hallmark toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Include BIS Hallmark Fee (₹53.10)", color = TextWhitePrimary, fontSize = 12.sp)
                    Switch(
                        checked = includeHallmark,
                        onCheckedChange = onToggleHallmark
                    )
                }
            }
        }

        // Bill Breakdown
        if (billBreakdown != null) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = TitaniumDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("jewelry_bill_breakdown_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Official Jewelry Invoice Breakdown",
                        color = GoldLight,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider(color = TitaniumBorder)

                    BillLineItem("Gold Rate / Gram", "₹${String.format("%,.2f", billBreakdown.baseRatePerGram)}")
                    BillLineItem("Net Metal Value (${billBreakdown.weightGrams}g)", "₹${String.format("%,.2f", billBreakdown.metalValue)}")
                    BillLineItem("Making Charges", "₹${String.format("%,.2f", billBreakdown.makingChargeAmount)}")
                    BillLineItem("BIS Hallmark Fee", "₹${String.format("%,.2f", billBreakdown.hallmarkFee)}")
                    BillLineItem("Subtotal (Pre-Tax)", "₹${String.format("%,.2f", billBreakdown.subtotal)}")
                    BillLineItem("CGST (1.5%) + SGST (1.5%)", "₹${String.format("%,.2f", billBreakdown.totalGst)}")
                    HorizontalDivider(color = GoldPrimary.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Grand Total (Incl. 3% GST)", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "₹${String.format("%,.2f", billBreakdown.grandTotal)}",
                            color = GoldLight,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BillLineItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextMutedSecondary, fontSize = 11.5.sp)
        Text(value, color = TextWhitePrimary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
    }
}
