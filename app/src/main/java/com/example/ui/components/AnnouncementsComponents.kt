package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.repository.CloudAnnouncement
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private fun formatAnnouncementTimeIst(item: CloudAnnouncement): String {
    val updatedMs = item.updatedAt?.toDate()?.time ?: 0L
    val createdMs = item.createdAt?.toDate()?.time ?: 0L
    val bestMs = maxOf(updatedMs, createdMs)
    if (bestMs <= 0L) return "Latest Broadcast"
    val fmt = SimpleDateFormat("dd MMM yyyy, hh:mm a 'IST'", Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("Asia/Kolkata")
    }
    return fmt.format(Date(bestMs))
}

@Composable
private fun priorityColors(priority: String): Pair<Color, Color> {
    return when (priority.lowercase()) {
        "urgent" -> Color(0xFFFF5252) to Color(0xFF450A0A)
        "high" -> GoldPrimary to Color(0xFF332609)
        else -> MarketGreen to MarketGreenGlow
    }
}

/**
 * Dedicated Announcements ("Notices") tab displaying live official bulletins from Cloud Firestore
 * sorted newest/latest first.
 */
@Composable
fun AnnouncementsTabView(
    announcements: List<CloudAnnouncement>,
    isDarkMode: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBg = if (isDarkMode) TitaniumDarkSurface else Color.White
    val primaryText = if (isDarkMode) Color.White else Color(0xFF0F172A)
    val secondaryText = if (isDarkMode) Color.LightGray else Color(0xFF334155)
    val borderCol = if (isDarkMode) TitaniumBorder else Color(0xFFCBD5E1)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("announcements_tab_view"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Header Card with Live Cloud Sync & Refresh Button
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.2.dp, GoldPrimary.copy(alpha = 0.65f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(GoldPrimary.copy(alpha = 0.18f))
                            .border(1.dp, GoldPrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Campaign,
                            contentDescription = "Official Announcements",
                            tint = GoldPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Official KRS Announcements",
                            color = primaryText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Real-time cloud bulletins • Latest shown first",
                            color = if (isDarkMode) GoldLight else Color(0xFFB45309),
                            fontSize = 11.sp
                        )
                    }
                }

                FilledTonalIconButton(
                    onClick = onRefresh,
                    enabled = !isRefreshing,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("btn_refresh_announcements")
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = GoldPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Announcements",
                            tint = GoldPrimary
                        )
                    }
                }
            }
        }

        // 2. Announcements List (Latest Highlighted First)
        if (announcements.isEmpty()) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderCol),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("announcements_empty_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsNone,
                        contentDescription = "No announcements",
                        tint = GoldPrimary,
                        modifier = Modifier.size(34.dp)
                    )
                    Text(
                        text = "Connecting to KRS Live Notices...",
                        color = primaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Official market notices, holiday schedules, and rate alerts pushed by K.M. Jashwanth appear here automatically.",
                        color = secondaryText,
                        fontSize = 12.sp
                    )
                    OutlinedButton(
                        onClick = onRefresh,
                        border = BorderStroke(1.dp, GoldPrimary),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Fetch Latest Notices", color = GoldPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            announcements.forEachIndexed { index, item ->
                val isLatest = index == 0
                val (prioFg, prioBg) = priorityColors(item.priority)
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isLatest) {
                            if (isDarkMode) Color(0xFF211A0D) else Color(0xFFFFFBEB)
                        } else {
                            cardBg
                        }
                    ),
                    border = BorderStroke(
                        if (isLatest) 1.5.dp else 1.dp,
                        if (isLatest) GoldPrimary else borderCol
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(if (isLatest) "latest_announcement_card" else "announcement_card_$index")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isLatest) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = GoldPrimary
                                    ) {
                                        Text(
                                            text = "LATEST",
                                            color = Color.Black,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = prioBg
                                ) {
                                    Text(
                                        text = item.priority.uppercase(),
                                        color = prioFg,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = formatAnnouncementTimeIst(item),
                                color = if (isDarkMode) TextDimTertiary else Color(0xFF64748B),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Text(
                            text = item.title,
                            color = primaryText,
                            fontSize = if (isLatest) 15.5.sp else 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )

                        Text(
                            text = item.message,
                            color = secondaryText,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Shown automatically every time the app opens when active announcements exist,
 * keeping users immediately informed of the latest Cloud Database notice.
 */
@Composable
fun LatestAnnouncementsLaunchDialog(
    announcements: List<CloudAnnouncement>,
    onViewNoticesTab: () -> Unit,
    onDismiss: () -> Unit
) {
    val latest = announcements.firstOrNull() ?: return
    val (prioFg, prioBg) = priorityColors(latest.priority)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = TitaniumDarkSurface),
            border = BorderStroke(1.5.dp, GoldPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("launch_announcement_dialog")
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(GoldPrimary.copy(alpha = 0.2f))
                                .border(1.dp, GoldPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Campaign,
                                contentDescription = "Latest Announcement",
                                tint = GoldPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Latest Announcement",
                                color = GoldPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = formatAnnouncementTimeIst(latest),
                                color = Color.LightGray,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = prioBg
                    ) {
                        Text(
                            text = latest.priority.uppercase(),
                            color = prioFg,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1A1710),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = latest.title,
                            color = Color.White,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = latest.message,
                            color = Color(0xFFE2E8F0),
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                if (announcements.size > 1) {
                    Text(
                        text = "+${announcements.size - 1} more active notice(s) in Notices tab",
                        color = GoldLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, TitaniumBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_dismiss_launch_announcement")
                    ) {
                        Text("Got It", color = Color.LightGray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = onViewNoticesTab,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldPrimary,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                            .testTag("btn_open_notices_from_dialog")
                    ) {
                        Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Notices", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
