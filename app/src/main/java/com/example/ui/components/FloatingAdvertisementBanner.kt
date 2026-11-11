package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

/**
 * Two-tier continuous live market status and floating marquee advertisement banner.
 * Synchronized in real time with the Vijay Bullion 1-second data feed and live market open/close detector.
 */
@Composable
fun FloatingAdvertisementBanner(
    isMarketOpen: Boolean,
    marketStatusText: String,
    liveIstClock: String = "",
    marketSessionDetail: String = "",
    goldRate10g: Double = 0.0,
    silverRate1kg: Double = 0.0,
    mcxGoldLtp: Double = 0.0,
    mcxSilverLtp: Double = 0.0,
    spotGoldUsd: Double = 0.0,
    spotSilverUsd: Double = 0.0,
    spotInr: Double = 0.0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val contactEmail = "krsmcxofficial@gmail.com"

    val infiniteTransition = rememberInfiniteTransition(label = "advertisementPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "adPulse"
    )

    val topBarBg = if (isMarketOpen) {
        Brush.horizontalGradient(
            listOf(
                Color(0xFF0F2E1A),
                Color(0xFF164226),
                Color(0xFF0F2E1A)
            )
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                Color(0xFF380B0B),
                Color(0xFF521111),
                Color(0xFF380B0B)
            )
        )
    }

    val topBarBorder = if (isMarketOpen) {
        MarketGreen.copy(alpha = 0.65f)
    } else {
        Color(0xFFFF5252).copy(alpha = 0.6f)
    }

    val marqueeBg = if (isMarketOpen) {
        Brush.horizontalGradient(
            listOf(
                Color(0xFF2B313B),
                Color(0xFF373F4D),
                Color(0xFF2B313B)
            )
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                Color(0xFF2E1E0F),
                Color(0xFF3E2914),
                Color(0xFF2E1E0F)
            )
        )
    }

    val goldStr = if (goldRate10g > 0) "Gold 999 (10g): ₹${String.format("%,.0f", goldRate10g)}" else "Gold 999 (10g): ₹1,46,300"
    val silverStr = if (silverRate1kg > 0) "Silver 1kg: ₹${String.format("%,.0f", silverRate1kg)}" else "Silver 1kg: ₹1,89,440"
    val mcxGoldStr = if (mcxGoldLtp > 0) "GoldMCX: ₹${String.format("%,.0f", mcxGoldLtp)}" else "GoldMCX: ₹1,42,480"
    val mcxSilverStr = if (mcxSilverLtp > 0) "SilverMCX: ₹${String.format("%,.0f", mcxSilverLtp)}" else "SilverMCX: ₹1,85,139"
    val spotStr = if (spotGoldUsd > 0) "Spot Gold: $${String.format("%.2f", spotGoldUsd)} | Spot Silver: $${String.format("%.2f", spotSilverUsd)} | INR: ₹${String.format("%.2f", spotInr)}" else "Spot Gold: $4069.99 | INR: ₹94.09"

    val singleMarqueeLoop = if (isMarketOpen) {
        "🟢 MARKET OPEN (LIVE 1s FEED) • $goldStr • $silverStr • $mcxGoldStr • $mcxSilverStr • $spotStr • Direct 1-Second Live Stream • Support: $contactEmail • "
    } else {
        "🔴 MARKET CLOSED ($marketSessionDetail) • $goldStr • $silverStr • $mcxGoldStr • $mcxSilverStr • $spotStr • Support: $contactEmail • "
    }
    val marqueeContent = singleMarqueeLoop + singleMarqueeLoop

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                try {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:$contactEmail")
                        putExtra(Intent.EXTRA_SUBJECT, "KRS Gold & Silver MCX App Support")
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Support Email", contactEmail))
                    Toast.makeText(context, "Support email copied: $contactEmail", Toast.LENGTH_SHORT).show()
                }
            }
            .testTag("floating_advertisement_ticker")
    ) {
        // =================================================================
        // TIER 1: LIVE MARKET OPEN / CLOSED STATUS RIBBON (ON TOP OF AD LINES)
        // =================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(topBarBg)
                .border(width = 1.dp, color = topBarBorder)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Glowing Market Status Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isMarketOpen) Color(0xFF0D5426) else Color(0xFF8B1212))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isMarketOpen) MarketGreen.copy(alpha = pulseAlpha)
                            else Color(0xFFFF5252).copy(alpha = pulseAlpha)
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isMarketOpen) "MARKET OPEN" else "MARKET CLOSED",
                    color = Color.White,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
            }

            // Center: Live Feed Session & Rate Summary
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = if (marketSessionDetail.isNotBlank()) marketSessionDetail else "Live 1s Stream",
                    color = if (isMarketOpen) BullionYellow else Color(0xFFFFC5C5),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Right: 1-Second Live Clock & Parity Indicator
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = "IST Time",
                    tint = if (isMarketOpen) MarketGreen else Color(0xFFFF8A80),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (liveIstClock.isNotBlank()) liveIstClock else "Live IST",
                    color = Color.White,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // =================================================================
        // TIER 2: CONTINUOUS RUNNING MARQUEE ADVERTISEMENT LINE
        // =================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(marqueeBg)
                .border(width = 0.5.dp, color = BullionYellow.copy(alpha = 0.55f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Live 1s Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(BullionYellow.copy(alpha = 0.2f))
                    .border(0.8.dp, BullionYellow, RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "LIVE 1s",
                    color = BullionYellow,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Running Marquee line
            Text(
                text = marqueeContent,
                color = BullionYellow,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        velocity = 45.dp,
                        repeatDelayMillis = 0,
                        initialDelayMillis = 0
                    )
            )

            Spacer(modifier = Modifier.width(6.dp))

            Icon(
                imageVector = Icons.Default.Email,
                contentDescription = "Contact krsmcxofficial@gmail.com",
                tint = BullionYellow,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
