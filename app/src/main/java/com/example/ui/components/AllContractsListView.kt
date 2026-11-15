package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CommodityContract
import com.example.ui.theme.*

@Composable
fun AllContractsListView(
    contracts: List<CommodityContract>,
    selectedContractId: String,
    onSelectContract: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TitaniumDarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, TitaniumBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("all_contracts_list_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "All MCX Contracts & Derivatives",
                color = TextWhitePrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            contracts.filter { !it.id.startsWith("SPOT_") }.forEach { contract ->
                val isSelected = contract.id == selectedContractId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectContract(contract.id) }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = contract.symbol,
                            color = if (isSelected) GoldPrimary else TextWhitePrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${contract.name} • ${contract.expiry}",
                            color = TextDimTertiary,
                            fontSize = 10.sp
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "₹${String.format("%,.0f", contract.ltp)}",
                            color = TextWhitePrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${if (contract.isPositive) "+" else ""}${String.format("%.2f", contract.changePercent)}%",
                            color = if (contract.isPositive) MarketGreen else MarketRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                HorizontalDivider(color = TitaniumBorder.copy(alpha = 0.5f))
            }
        }
    }
}
