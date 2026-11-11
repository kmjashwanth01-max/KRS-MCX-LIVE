package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MarketRateMode
import com.example.ui.theme.*

@Composable
fun LiveTickerHeader(
    isLiveActive: Boolean,
    tickCount: Long,
    lastTickTime: String,
    marketStatusText: String,
    isMarketOpen: Boolean,
    hapticEnabled: Boolean,
    activeBanner: String?,
    marketMode: MarketRateMode,
    onToggleLive: () -> Unit,
    onToggleHaptic: () -> Unit,
    onDismissBanner: () -> Unit,
    onSelectMarketMode: (MarketRateMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(TitaniumDarkSurface)
    ) {
        // Active Banner (for price alerts or notifications)
        AnimatedVisibility(visible = activeBanner != null) {
            if (activeBanner != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MarketGreenGlow)
                        .border(1.dp, MarketGreen, RoundedCornerShape(0.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "Alert notification",
                            tint = MarketGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = activeBanner,
                            color = TextWhitePrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    IconButton(
                        onClick = onDismissBanner,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss banner",
                            tint = TextMutedSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Top Status Bar: Live Indicator, Time, Market Session, Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Live Status & Time
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(TitaniumCardSurface)
                    .border(1.dp, TitaniumBorder, RoundedCornerShape(20.dp))
                    .clickable { onToggleLive() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .testTag("toggle_live_button")
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(
                            if (isLiveActive) MarketGreen.copy(alpha = pulseAlpha)
                            else TextDimTertiary
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isLiveActive) "LIVE 1s" else "PAUSED",
                    color = if (isLiveActive) MarketGreen else TextDimTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "• $lastTickTime",
                    color = TextMutedSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal
                )
            }

            // Market Session Status & Actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isMarketOpen) MarketGreenGlow else TitaniumCardElevated,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(
                        text = marketStatusText,
                        color = if (isMarketOpen) MarketGreen else TextMutedSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Haptic feedback toggle
                IconButton(
                    onClick = onToggleHaptic,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (hapticEnabled) GoldGlow else Color.Transparent)
                        .testTag("toggle_haptic_button")
                ) {
                    Icon(
                        imageVector = if (hapticEnabled) Icons.Default.Vibration else Icons.Default.Smartphone,
                        contentDescription = "Toggle haptic tick",
                        tint = if (hapticEnabled) GoldPrimary else TextDimTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Pause/Resume 1s streaming button
                IconButton(
                    onClick = onToggleLive,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(TitaniumCardSurface)
                        .testTag("play_pause_button")
                ) {
                    Icon(
                        imageVector = if (isLiveActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isLiveActive) "Pause 1s feed" else "Resume 1s feed",
                        tint = TextWhitePrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Live Bullion Reference Banner (KRS Bullion Real-Time Feed)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(TitaniumCardSurface)
                .border(1.dp, GoldPrimary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(MarketGreen)
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = "KRS BULLION REAL-TIME FEED",
                    color = GoldLight,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
            Text(
                text = "GOLD 1.46L (MCX 1.42L) • SILVER 1.90L",
                color = TextWhitePrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
