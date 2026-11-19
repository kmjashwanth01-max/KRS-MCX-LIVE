package com.example.ui.components

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Widgets
import com.example.util.ApkExportHelper
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.CommodityContract
import com.example.model.PriceTickDirection
import com.example.ui.theme.*
import com.example.widget.BullionAppWidgetProvider
import com.example.widget.SilverAppWidgetProvider

private fun tickColor(direction: PriceTickDirection?, fallbackUp: Boolean = true): Color {
    return when (direction) {
        PriceTickDirection.UP -> MarketGreen
        PriceTickDirection.DOWN -> Color(0xFFFF5252)
        else -> if (fallbackUp) MarketGreen else Color(0xFFFF5252)
    }
}

private fun tickArrow(direction: PriceTickDirection?): String {
    return when (direction) {
        PriceTickDirection.DOWN -> "▼"
        else -> "▲"
    }
}

@Composable
fun HomeScreenWidgetPromoCard(
    goldContract: CommodityContract?,
    silverContract: CommodityContract?,
    mcxSilverContract: CommodityContract? = null,
    spotGoldContract: CommodityContract? = null,
    spotSilverContract: CommodityContract? = null,
    isMarketOpen: Boolean = true,
    isPhysicalBullionOpen: Boolean = true,
    liveIstClock: String = "",
    marketSessionDetail: String = "",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val reopenNote = marketSessionDetail.ifBlank { "Reopens 09:00 AM IST" }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TitaniumDarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_screen_widget_promo_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(GoldPrimary.copy(alpha = 0.15f))
                            .border(1.dp, GoldPrimary.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Widgets,
                            contentDescription = "Widget",
                            tint = GoldPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Home Screen Widget (4 Rates)",
                            color = TextWhitePrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Gold 999, Silver 1kg, Gold Cent ($) & Silver Cent ($)",
                            color = GoldLight,
                            fontSize = 10.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isMarketOpen) MarketGreenGlow else Color(0xFF450A0A)
                ) {
                    Text(
                        text = if (isMarketOpen) "1s SYNC" else "CLOSED",
                        color = if (isMarketOpen) MarketGreen else Color(0xFFFF8A80),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Mini Widget Visual Preview Box (4 rates 2x2 grid)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF141518))
                    .border(1.dp, GoldPrimary.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_krs_app_logo),
                                contentDescription = "K.R.S. Gold & Silver Logo",
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "K.R.S. Live Widget",
                                color = TextWhitePrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = if (isMarketOpen) {
                                "🟢 Live 1s${if (liveIstClock.isNotBlank()) " • $liveIstClock" else ""}"
                            } else {
                                "🔴 Closed • $reopenNote"
                            },
                            color = if (isMarketOpen) MarketGreen else Color(0xFFFF8A80),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Row 1: Physical Bullion Spot
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val physOpen = isMarketOpen && isPhysicalBullionOpen
                        val goldTick = goldContract?.bidTickDirection
                        val goldColor = if (physOpen) tickColor(goldTick) else Color(0xFFFF5252)
                        // Rate 1: Gold 999
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1F2026))
                                .padding(vertical = 4.dp, horizontal = 6.dp)
                        ) {
                            Text("GOLD 999 (10g)", color = GoldLight, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (physOpen) "₹${String.format("%,.0f", goldContract?.ltp ?: 146040.0)}" else "CLOSED",
                                color = goldColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = if (physOpen) "Physical ${tickArrow(goldTick)}" else reopenNote,
                                color = if (physOpen) goldColor.copy(alpha = 0.85f) else Color(0xFFFF8A80),
                                fontSize = 7.5.sp
                            )
                        }

                        val silverTick = silverContract?.bidTickDirection
                        val silverColor = if (physOpen) tickColor(silverTick) else Color(0xFFFF5252)
                        // Rate 2: Silver 1kg
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1F2026))
                                .padding(vertical = 4.dp, horizontal = 6.dp)
                        ) {
                            Text("SILVER 1KG", color = Color(0xFFE0E0E0), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (physOpen) "₹${String.format("%,.0f", silverContract?.ltp ?: 189240.0)}" else "CLOSED",
                                color = silverColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = if (physOpen) "Physical ${tickArrow(silverTick)}" else reopenNote,
                                color = if (physOpen) silverColor.copy(alpha = 0.85f) else Color(0xFFFF8A80),
                                fontSize = 7.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Row 2: International Spot Cent Rates (hidden when market closes at 12 AM)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val spotGoldTick = spotGoldContract?.bidTickDirection
                        val spotGoldColor = if (isMarketOpen) tickColor(spotGoldTick) else Color(0xFFFF5252)
                        // Rate 3: Gold Cent ($)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1F2026))
                                .padding(vertical = 4.dp, horizontal = 6.dp)
                        ) {
                            Text("GOLD CENT ($)", color = GoldLight, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (isMarketOpen) "$${String.format("%.2f", spotGoldContract?.ltp ?: 4070.77)}" else "CLOSED",
                                color = spotGoldColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = if (isMarketOpen) "Spot Cent/Oz ${tickArrow(spotGoldTick)}" else reopenNote,
                                color = spotGoldColor.copy(alpha = 0.85f),
                                fontSize = 7.5.sp
                            )
                        }

                        val spotSilverTick = spotSilverContract?.bidTickDirection
                        val spotSilverColor = if (isMarketOpen) tickColor(spotSilverTick) else Color(0xFFFF5252)
                        // Rate 4: Silver Cent ($)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1F2026))
                                .padding(vertical = 4.dp, horizontal = 6.dp)
                        ) {
                            Text("SILVER CENT ($)", color = Color(0xFFE0E0E0), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (isMarketOpen) "$${String.format("%.2f", spotSilverContract?.ltp ?: 52.01)}" else "CLOSED",
                                color = spotSilverColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = if (isMarketOpen) "Spot Cent/Oz ${tickArrow(spotSilverTick)}" else reopenNote,
                                color = spotSilverColor.copy(alpha = 0.85f),
                                fontSize = 7.5.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action: 1-Tap Pin to Home Screen Button
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val appWidgetManager = context.getSystemService(AppWidgetManager::class.java)
                        val provider = ComponentName(context, BullionAppWidgetProvider::class.java)
                        if (appWidgetManager != null && appWidgetManager.isRequestPinAppWidgetSupported) {
                            appWidgetManager.requestPinAppWidget(provider, null, null)
                        } else {
                            Toast.makeText(
                                context,
                                "Long press your home screen, tap 'Widgets' and add KRS Gold & Silver",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            context,
                            "Long press your home screen, tap 'Widgets' and select KRS Gold & Silver",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GoldPrimary,
                    contentColor = TitaniumBlack
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .testTag("pin_widget_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Pin Widget",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Add Widget to Home Screen (All 4 Rates)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            HorizontalDivider(color = TitaniumBorder.copy(alpha = 0.7f))

            Spacer(modifier = Modifier.height(12.dp))

            // Dedicated Silver Widget (Silver MCX + 1kg Silver + Silver Cent $)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Silver Widget (MCX, 1kg & Cent $)",
                        color = Color(0xFFE2E8F0),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Shows Silver MCX (1kg), Physical Silver 1kg & Silver Cent ($) 1s",
                        color = GoldLight,
                        fontSize = 10.sp
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E293B)
                ) {
                    Text(
                        text = "SILVER 3-IN-1",
                        color = Color(0xFF93C5FD),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF141518))
                    .border(1.dp, Color(0xFF93C5FD).copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .padding(10.dp)
                    .testTag("silver_only_widget_preview")
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_krs_app_logo),
                                contentDescription = "K.R.S. Silver Live Logo",
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "K.R.S. Silver Live",
                                color = Color(0xFFE2E8F0),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = if (isMarketOpen) {
                                "🟢 Live 1s${if (liveIstClock.isNotBlank()) " • $liveIstClock" else ""}"
                            } else {
                                "🔴 Closed • $reopenNote"
                            },
                            color = if (isMarketOpen) MarketGreen else Color(0xFFFF8A80),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val physOpenSilver = isMarketOpen && isPhysicalBullionOpen
                    val mcxSilverTick = mcxSilverContract?.bidTickDirection
                    val mcxSilverColor = if (isMarketOpen) tickColor(mcxSilverTick) else Color(0xFFFF5252)
                    val physSilverTick = silverContract?.bidTickDirection
                    val physSilverColor = if (physOpenSilver) tickColor(physSilverTick) else Color(0xFFFF5252)
                    val silverCentTick = spotSilverContract?.bidTickDirection
                    val silverCentColor = if (isMarketOpen) tickColor(silverCentTick) else Color(0xFFFF5252)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1F2026))
                                .padding(vertical = 6.dp, horizontal = 8.dp)
                        ) {
                            Text(
                                text = "SILVER MCX (1kg)",
                                color = Color(0xFF93C5FD),
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isMarketOpen) "₹${String.format("%,.0f", mcxSilverContract?.ltp ?: 185139.0)}" else "CLOSED",
                                color = mcxSilverColor,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = if (isMarketOpen) {
                                    "${tickArrow(mcxSilverTick)} B:₹${String.format("%,.0f", mcxSilverContract?.bidPrice ?: 185085.0)} • A:₹${String.format("%,.0f", mcxSilverContract?.askPrice ?: 185139.0)}"
                                } else {
                                    reopenNote
                                },
                                color = mcxSilverColor.copy(alpha = 0.85f),
                                fontSize = 7.5.sp
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1F2026))
                                .padding(vertical = 6.dp, horizontal = 8.dp)
                        ) {
                            Text(
                                text = "SILVER 1KG",
                                color = Color(0xFFE2E8F0),
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (physOpenSilver) "₹${String.format("%,.0f", silverContract?.ltp ?: 189440.0)}" else "CLOSED",
                                color = physSilverColor,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = if (physOpenSilver) "Physical 1kg ${tickArrow(physSilverTick)}" else reopenNote,
                                color = if (physOpenSilver) physSilverColor.copy(alpha = 0.85f) else Color(0xFFFF8A80),
                                fontSize = 7.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Live 1s Silver Cent ($) strip inside Silver Widget preview
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1F2026))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "SILVER CENT ($)",
                                color = Color(0xFFE2E8F0),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isMarketOpen) "Spot Cent/Oz 1s ${tickArrow(silverCentTick)}" else reopenNote,
                                color = silverCentColor.copy(alpha = 0.85f),
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = if (isMarketOpen) "$${String.format("%.2f", spotSilverContract?.ltp ?: 52.01)}" else "CLOSED",
                            color = silverCentColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val appWidgetManager = context.getSystemService(AppWidgetManager::class.java)
                        val provider = ComponentName(context, SilverAppWidgetProvider::class.java)
                        if (appWidgetManager != null && appWidgetManager.isRequestPinAppWidgetSupported) {
                            appWidgetManager.requestPinAppWidget(provider, null, null)
                        } else {
                            Toast.makeText(
                                context,
                                "Long press your home screen, tap 'Widgets' and add KRS Silver Live (MCX, 1kg & Cent $)",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            context,
                            "Long press your home screen, tap 'Widgets' and select KRS Silver Live",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF93C5FD)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFBFDBFE)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("pin_silver_widget_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Pin Silver Widget",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Add Silver Widget (MCX, 1kg & Cent $)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action: Direct Save / Share APK File from Home Screen
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { ApkExportHelper.saveApkToDownloads(context) },
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldLight),
                    modifier = Modifier.weight(1f).height(36.dp).testTag("home_save_apk_btn")
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = GoldLight, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Save APK", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { ApkExportHelper.shareApkFile(context) },
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldLight),
                    modifier = Modifier.weight(1f).height(36.dp).testTag("home_share_apk_btn")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = GoldLight, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Share APK File", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
