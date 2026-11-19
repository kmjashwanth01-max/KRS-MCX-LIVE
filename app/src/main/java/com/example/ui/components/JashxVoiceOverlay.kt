package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionDisabled
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.JashxVoicePersona
import com.example.data.LiveVoiceStatus
import com.example.data.LiveVoiceUiState
import com.example.data.McxRatesRepository
import com.example.model.CommodityContract
import com.example.ui.theme.*
import com.example.util.BrandSanitizer
import kotlin.math.sin

/**
 * Full-Screen Studio-Style Animated Live Voice Experience for JASHX AI.
 * Features an audio-reactive glowing orb, live captions,
 * capture mode toggle (Auto Speech / Direct Audio), mute, tap-to-interrupt, and live 1s bullion context.
 */
@Composable
fun JashxVoiceOverlay(
    liveVoiceState: LiveVoiceUiState,
    gold999: CommodityContract?,
    silver1kg: CommodityContract?,
    isMarketOpen: Boolean,
    onDismiss: () -> Unit,
    onToggleMute: () -> Unit,
    onInterruptAndListen: () -> Unit,
    onSubmitAudioNow: () -> Unit,
    onSelectPersona: (JashxVoicePersona) -> Unit,
    onToggleCaptions: () -> Unit,
    onToggleCaptureMode: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val status = liveVoiceState.status
        val targetPrimaryColor = when {
            liveVoiceState.isMuted -> Color(0xFFF59E0B)
            status == LiveVoiceStatus.LISTENING -> MarketGreen
            status == LiveVoiceStatus.THINKING -> GoldPrimary
            status == LiveVoiceStatus.SPEAKING -> Color(0xFF38BDF8)
            status == LiveVoiceStatus.ERROR -> MarketRed
            else -> GoldLight
        }
        val primaryColor by animateColorAsState(
            targetValue = targetPrimaryColor,
            animationSpec = tween(420),
            label = "orbPrimaryColor"
        )
        val secondaryColor by animateColorAsState(
            targetValue = when {
                liveVoiceState.isMuted -> Color(0xFF92400E)
                status == LiveVoiceStatus.SPEAKING -> GoldPrimary
                status == LiveVoiceStatus.THINKING -> Color(0xFFA855F7)
                status == LiveVoiceStatus.LISTENING -> Color(0xFF34D399)
                else -> GoldDark
            },
            animationSpec = tween(420),
            label = "orbSecondaryColor"
        )

        val statusLabel = when {
            liveVoiceState.isMuted -> "MIC MUTED • TAP TO UNMUTE"
            status == LiveVoiceStatus.CONNECTING -> "INITIALIZING JASHX VOICE..."
            status == LiveVoiceStatus.LISTENING -> "LISTENING • SPEAK NATURALLY"
            status == LiveVoiceStatus.THINKING -> "THINKING • SYNCING LIVE DATA"
            status == LiveVoiceStatus.SPEAKING -> "JASHX AI SPEAKING • TAP LOGO TO INTERRUPT"
            status == LiveVoiceStatus.ERROR -> "VOICE NOTICE • TAP LOGO TO RETRY"
            else -> "JASHX AI VOICE READY"
        }

        val smoothedMic by animateFloatAsState(
            targetValue = liveVoiceState.micLevel.coerceIn(0f, 1f),
            animationSpec = tween(90),
            label = "smoothedMic"
        )

        val infiniteTransition = rememberInfiniteTransition(label = "jashxOrbMotion")
        val orbPhase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (Math.PI * 2.0).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "orbPhase"
        )
        val orbBreath by infiniteTransition.animateFloat(
            initialValue = 0.94f,
            targetValue = 1.07f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = if (status == LiveVoiceStatus.SPEAKING) 520 else 1200,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "orbBreath"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF06080E),
                            Color(0xFF0C111E),
                            Color(0xFF06080E)
                        )
                    )
                )
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag("jashx_fullscreen_voice_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. TOP HEADER ROW (Identity, Live 1s Feed Status, Capture Mode, Captions Toggle, Close)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_jashx_ai_logo),
                                contentDescription = "JASHX AI",
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, GoldPrimary, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "JASHX AI Voice",
                                    color = TextWhitePrimary,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "Real-Time Live Voice",
                                    color = GoldLight,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Capture Mode Switch (Auto Speech vs Direct Audio)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = TitaniumCardBg,
                                border = androidx.compose.foundation.BorderStroke(0.8.dp, GoldPrimary.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .heightIn(min = 38.dp)
                                    .clickable { onToggleCaptureMode() }
                                    .testTag("voice_capture_mode_toggle")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = liveVoiceState.captureModeLabel,
                                        color = GoldLight,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            IconButton(
                                onClick = onToggleCaptions,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(TitaniumCardBg)
                                    .testTag("voice_captions_toggle")
                            ) {
                                Icon(
                                    imageVector = if (liveVoiceState.showCaptions) {
                                        Icons.Default.ClosedCaption
                                    } else {
                                        Icons.Default.ClosedCaptionDisabled
                                    },
                                    contentDescription = "Toggle Live Captions",
                                    tint = if (liveVoiceState.showCaptions) GoldPrimary else TextMutedSecondary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(TitaniumCardBg)
                                    .testTag("voice_overlay_close_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Voice Overlay",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Live Bullion Feed Context Pill
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF121826),
                        border = androidx.compose.foundation.BorderStroke(0.7.dp, primaryColor.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (isMarketOpen) MarketGreen else Color(0xFFFF5252))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                val summaryText = if (isMarketOpen) {
                                    val gTxt = gold999?.ltp?.let { "Gold ₹${String.format("%,.0f", it)}" } ?: "Gold Syncing"
                                    val sTxt = silver1kg?.ltp?.let { "Silver ₹${String.format("%,.0f", it)}" } ?: "Silver Syncing"
                                    "1s Live Feed • $gTxt • $sTxt"
                                } else {
                                    "Market Closed • ${McxRatesRepository.getCompactReopenLabel()} • AI Active"
                                }
                                Text(
                                    text = summaryText,
                                    color = TextWhitePrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // 2. CENTERPIECE: JASHX AI AUDIO-REACTIVE LOGO + STATUS
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(230.dp)
                            .clip(CircleShape)
                            .clickable {
                                when {
                                    status == LiveVoiceStatus.SPEAKING || status == LiveVoiceStatus.THINKING -> onInterruptAndListen()
                                    status == LiveVoiceStatus.LISTENING && liveVoiceState.captureModeLabel == "Direct Audio" -> onSubmitAudioNow()
                                    status == LiveVoiceStatus.ERROR || status == LiveVoiceStatus.IDLE -> onInterruptAndListen()
                                    else -> onInterruptAndListen()
                                }
                            }
                            .testTag("voice_animated_orb")
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val baseRadius = size.minDimension * 0.35f
                            val energyScale = orbBreath + (smoothedMic * 0.18f)
                            val radius = baseRadius * energyScale

                            // Outer ambient radial halo behind JASHX AI logo
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        primaryColor.copy(alpha = 0.42f),
                                        secondaryColor.copy(alpha = 0.18f),
                                        Color.Transparent
                                    ),
                                    center = Offset(cx, cy),
                                    radius = size.minDimension * 0.495f
                                ),
                                radius = size.minDimension * 0.495f,
                                center = Offset(cx, cy)
                            )

                            // Subtle audio-reactive pulse rings around the JASHX AI logo
                            for (ring in 1..2) {
                                val phaseOffset = orbPhase + ring * 1.35f
                                val wobble = (sin(phaseOffset.toDouble()) * 4.5 * (1f + smoothedMic * 2.2f)).toFloat()
                                val ringRadius = radius + (ring * 14f) + wobble
                                drawCircle(
                                    color = primaryColor.copy(alpha = (0.50f - ring * 0.16f).coerceAtLeast(0.12f)),
                                    radius = ringRadius.coerceAtMost(size.minDimension * 0.485f),
                                    center = Offset(cx, cy),
                                    style = Stroke(width = (2.8f - ring * 0.6f).dp.toPx())
                                )
                            }
                        }

                        // Official User JASHX AI Logo Centerpiece
                        Image(
                            painter = painterResource(id = R.drawable.ic_jashx_ai_logo),
                            contentDescription = "JASHX AI Live Voice Logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(154.dp)
                                .graphicsLayer {
                                    val logoScale = (0.96f + (orbBreath - 0.94f) * 0.55f + smoothedMic * 0.14f)
                                        .coerceIn(0.92f, 1.15f)
                                    scaleX = logoScale
                                    scaleY = logoScale
                                }
                                .clip(CircleShape)
                                .border(2.2.dp, primaryColor.copy(alpha = 0.92f), CircleShape)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = primaryColor.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, primaryColor.copy(alpha = 0.65f))
                    ) {
                        Text(
                            text = statusLabel,
                            color = primaryColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.4.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }

                // 3. LIVE CAPTIONS PANEL + SAFE BOTTOM CONTROL DOCK
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AnimatedVisibility(visible = liveVoiceState.showCaptions) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF101522),
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, TitaniumBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 82.dp, max = 165.dp)
                                .testTag("voice_live_captions_box")
                        ) {
                            Column(
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val toolLabel = liveVoiceState.activeToolCallLabel
                                if (!toolLabel.isNullOrBlank()) {
                                    Text(
                                        text = BrandSanitizer.sanitizeForUserDisplay(toolLabel),
                                        color = GoldLight,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                val errMsg = liveVoiceState.errorMessage
                                if (!errMsg.isNullOrBlank()) {
                                    Text(
                                        text = BrandSanitizer.sanitizeForUserDisplay(errMsg),
                                        color = Color(0xFFFF8A80),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                if (liveVoiceState.liveUserTranscript.isNotBlank()) {
                                    Text(
                                        text = "You: ${BrandSanitizer.sanitizeForUserDisplay(liveVoiceState.liveUserTranscript)}",
                                        color = GoldLight,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        lineHeight = 16.sp
                                    )
                                }

                                if (liveVoiceState.liveAiTranscript.isNotBlank()) {
                                    Text(
                                        text = "JASHX AI: ${BrandSanitizer.sanitizeForUserDisplay(liveVoiceState.liveAiTranscript)}",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp
                                    )
                                } else if (errMsg.isNullOrBlank() && liveVoiceState.liveUserTranscript.isBlank()) {
                                    Text(
                                        text = "Ask about gold/silver rates, today's news, weather, calculations, or anything at all.",
                                        color = TextMutedSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    // Safe Bottom Call Controls (>= 48dp touch targets)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mute / Unmute Mic
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = onToggleMute,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(if (liveVoiceState.isMuted) Color(0xFFF59E0B) else TitaniumCardBg)
                                    .border(1.dp, if (liveVoiceState.isMuted) GoldLight else TitaniumBorder, CircleShape)
                                    .testTag("voice_mute_btn")
                            ) {
                                Icon(
                                    imageVector = if (liveVoiceState.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = if (liveVoiceState.isMuted) "Unmute Microphone" else "Mute Microphone",
                                    tint = if (liveVoiceState.isMuted) Color.Black else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (liveVoiceState.isMuted) "Unmute" else "Mute",
                                color = TextMutedSecondary,
                                fontSize = 10.sp
                            )
                        }

                        // Center Action: Interrupt / Speak Now / Send Audio
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = {
                                    if (status == LiveVoiceStatus.LISTENING && liveVoiceState.captureModeLabel == "Direct Audio") {
                                        onSubmitAudioNow()
                                    } else {
                                        onInterruptAndListen()
                                    }
                                },
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(primaryColor)
                                    .testTag("voice_interrupt_btn")
                            ) {
                                Icon(
                                    imageVector = if (status == LiveVoiceStatus.SPEAKING) {
                                        Icons.Default.RecordVoiceOver
                                    } else {
                                        Icons.Default.GraphicEq
                                    },
                                    contentDescription = "Interrupt or Speak Now",
                                    tint = Color.Black,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = when {
                                    status == LiveVoiceStatus.SPEAKING || status == LiveVoiceStatus.THINKING -> "Interrupt"
                                    status == LiveVoiceStatus.LISTENING && liveVoiceState.captureModeLabel == "Direct Audio" -> "Send Speech"
                                    status == LiveVoiceStatus.ERROR || status == LiveVoiceStatus.IDLE -> "Retry"
                                    else -> "Listening"
                                },
                                color = primaryColor,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // End Voice Session
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(MarketRed)
                                    .testTag("voice_end_call_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CallEnd,
                                    contentDescription = "End Live Voice Call",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "End",
                                color = Color(0xFFFF8A80),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
