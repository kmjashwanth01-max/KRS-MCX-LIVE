package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BullionCityRate
import com.example.ui.theme.*
import java.text.DecimalFormat

@Composable
fun CityRatesView(
    cityRates: List<BullionCityRate>,
    modifier: Modifier = Modifier
) {
    val rupeeFormat = remember { DecimalFormat("##,##,##0") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("city_rates_container")
    ) {
        // Section Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "Cities",
                tint = GoldPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "INDIA MAJOR BULLION MARKETS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = GoldPrimary,
                    fontSize = 14.sp
                )
                Text(
                    text = "Spot benchmark with local octroi & logistical premium",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = TitaniumDarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, TitaniumBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                // Table header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(TitaniumCardBg)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CITY / HUB",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray,
                        modifier = Modifier.weight(1.3f)
                    )
                    Text(
                        text = "24K (10g)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = GoldPrimary,
                        modifier = Modifier.weight(1.2f)
                    )
                    Text(
                        text = "22K (10g)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1.2f)
                    )
                    Text(
                        text = "SILVER (1kg)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = SilverPrimary,
                        modifier = Modifier.weight(1.2f)
                    )
                }

                HorizontalDivider(
                    color = TitaniumBorder,
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                cityRates.forEachIndexed { index, cityRate ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1.3f)) {
                            Text(
                                text = cityRate.city,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                fontSize = 13.sp
                            )
                            Text(
                                text = cityRate.state,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                        }

                        Text(
                            text = "₹${rupeeFormat.format(cityRate.gold24k)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = GoldPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1.2f)
                        )

                        Text(
                            text = "₹${rupeeFormat.format(cityRate.gold22k)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1.2f)
                        )

                        Text(
                            text = "₹${rupeeFormat.format(cityRate.silver1kg)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = SilverPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1.2f)
                        )
                    }

                    if (index < cityRates.size - 1) {
                        HorizontalDivider(
                            color = TitaniumBorder.copy(alpha = 0.5f),
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}
