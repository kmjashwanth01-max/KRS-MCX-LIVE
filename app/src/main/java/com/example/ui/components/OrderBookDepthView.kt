package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CommodityContract
import com.example.ui.theme.*

@Composable
fun OrderBookDepthView(
    contract: CommodityContract?,
    modifier: Modifier = Modifier
) {
    val bidBase = contract?.bidPrice ?: 146000.0
    val askBase = contract?.askPrice ?: 146050.0

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TitaniumDarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, TitaniumBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("order_book_depth_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "5-Depth Market Order Book",
                    color = TextWhitePrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${contract?.symbol ?: "MCX GOLD"} Real-Time Depth",
                    color = TextDimTertiary,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Order Book Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141720))
                    .padding(vertical = 6.dp, horizontal = 8.dp)
            ) {
                Text("ORDERS", color = TextDimTertiary, fontSize = 9.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                Text("QTY", color = TextDimTertiary, fontSize = 9.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text("BID (₹)", color = MarketGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.3f), textAlign = TextAlign.End)
                Spacer(modifier = Modifier.width(12.dp))
                Text("ASK (₹)", color = MarketRed, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.3f), textAlign = TextAlign.Start)
                Text("QTY", color = TextDimTertiary, fontSize = 9.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text("ORDERS", color = TextDimTertiary, fontSize = 9.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            }

            val rows = listOf(
                Pair(Pair(12, 18), Pair(bidBase, askBase)),
                Pair(Pair(8, 24), Pair(bidBase - 10.0, askBase + 10.0)),
                Pair(Pair(15, 30), Pair(bidBase - 25.0, askBase + 25.0)),
                Pair(Pair(6, 14), Pair(bidBase - 40.0, askBase + 40.0)),
                Pair(Pair(9, 20), Pair(bidBase - 60.0, askBase + 60.0))
            )

            rows.forEachIndexed { idx, (ordersQty, prices) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${ordersQty.first}", color = TextMutedSecondary, fontSize = 10.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                    Text("${ordersQty.second}", color = TextWhitePrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Text(String.format("%,.0f", prices.first), color = MarketGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.3f), textAlign = TextAlign.End)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(String.format("%,.0f", prices.second), color = MarketRed, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.3f), textAlign = TextAlign.Start)
                    Text("${ordersQty.second + 3}", color = TextWhitePrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Text("${ordersQty.first + 1}", color = TextMutedSecondary, fontSize = 10.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                }
            }
        }
    }
}
