package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CommodityContract
import com.example.ui.theme.*
import java.text.DecimalFormat

data class BullionUnit(
    val name: String,
    val gramsEquivalent: Double,
    val description: String
)

@Composable
fun UnitConverterView(
    goldContract: CommodityContract?,
    silverContract: CommodityContract?,
    inputAmount: Double,
    selectedUnitName: String,
    onInputChange: (Double) -> Unit,
    onUnitChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val rupeeFormat = remember { DecimalFormat("##,##,##0.00") }
    val weightFormat = remember { DecimalFormat("0.####") }

    val units = listOf(
        BullionUnit("Tola (11.6638g)", 11.6638, "Traditional Indian benchmark"),
        BullionUnit("Pavan / Sovereign (8g)", 8.0, "South Indian wedding standard"),
        BullionUnit("Gram (g)", 1.0, "Metric standard"),
        BullionUnit("Troy Ounce (31.1035g)", 31.1035, "International COMEX bullion"),
        BullionUnit("Kilogram (1000g)", 1000.0, "Wholesale MCX Silver / Gold bar"),
        BullionUnit("Ratti (0.18g)", 0.18, "Traditional astrological gemstones & gold")
    )

    val currentUnit = units.find { it.name == selectedUnitName } ?: units[0]
    val totalGrams = inputAmount * currentUnit.gramsEquivalent

    val goldPerGram = (goldContract?.ltp ?: 146250.0) / 10.0
    val silverPerGram = (silverContract?.ltp ?: 193450.0) / 1000.0

    val totalGoldValuation = totalGrams * goldPerGram
    val totalSilverValuation = totalGrams * silverPerGram

    var inputText by remember(inputAmount) { mutableStateOf(inputAmount.toString()) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, TitaniumBorder, RoundedCornerShape(20.dp))
            .testTag("unit_converter_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = TitaniumCardSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = null,
                    tint = GoldPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "INDIAN BULLION UNIT CONVERTER",
                        color = TextWhitePrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Tola, Pavan, Grams, Troy Ounces & Live Value",
                        color = TextMutedSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Input Field
            OutlinedTextField(
                value = inputText,
                onValueChange = {
                    inputText = it
                    val d = it.toDoubleOrNull()
                    if (d != null) onInputChange(d)
                },
                label = { Text("Quantity to Convert", color = TextMutedSecondary) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldPrimary,
                    unfocusedBorderColor = TitaniumBorder,
                    focusedTextColor = TextWhitePrimary,
                    unfocusedTextColor = TextWhitePrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("converter_quantity_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Unit selection chips
            Text(text = "SELECT UNIT:", color = TextDimTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                units.take(3).forEach { u ->
                    val isSel = u.name == selectedUnitName
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) GoldGlow else TitaniumDarkSurface)
                            .border(1.dp, if (isSel) GoldPrimary else TitaniumBorder, RoundedCornerShape(8.dp))
                            .clickable { onUnitChange(u.name) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = u.name.substringBefore(" ("),
                            color = if (isSel) GoldLight else TextWhitePrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                units.drop(3).forEach { u ->
                    val isSel = u.name == selectedUnitName
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) GoldGlow else TitaniumDarkSurface)
                            .border(1.dp, if (isSel) GoldPrimary else TitaniumBorder, RoundedCornerShape(8.dp))
                            .clickable { onUnitChange(u.name) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = u.name.substringBefore(" ("),
                            color = if (isSel) GoldLight else TextWhitePrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Conversion Breakdown Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = TitaniumDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, TitaniumBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "WEIGHT EQUIVALENTS:",
                        color = TextDimTertiary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    units.forEach { targetUnit ->
                        if (targetUnit.name != selectedUnitName) {
                            val convertedQty = totalGrams / targetUnit.gramsEquivalent
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = targetUnit.name,
                                    color = TextMutedSecondary,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "${weightFormat.format(convertedQty)} ${targetUnit.name.substringBefore(" ")}",
                                    color = TextWhitePrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = TitaniumBorder, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "LIVE MCX CASH VALUE:",
                        color = GoldPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Gold 24K Value:", color = TextWhitePrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(text = "₹${rupeeFormat.format(totalGoldValuation)}", color = GoldLight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Silver 999 Value:", color = TextWhitePrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(text = "₹${rupeeFormat.format(totalSilverValuation)}", color = SilverLight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
