package com.example.ui.components

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.JashxKnowledgeDatabase
import com.example.model.ChatMessage
import com.example.model.CommodityContract
import com.example.model.MarketPrediction
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun JashxAiMarketAnalystView(
    gold999: CommodityContract?,
    silver1kg: CommodityContract?,
    mcxGold: CommodityContract?,
    mcxSilver: CommodityContract?,
    spotGold: CommodityContract?,
    spotInr: CommodityContract?,
    isMarketOpen: Boolean,
    aiAnalysisText: String,
    isGenerating: Boolean,
    errorMessage: String?,
    prediction: MarketPrediction? = null,
    chatMessages: List<ChatMessage> = emptyList(),
    onGenerateAnalysis: () -> Unit,
    onRefreshPrediction: () -> Unit = {},
    onAskQuestion: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var customQuery by remember { mutableStateOf("") }
    val locationState by com.example.util.LocationServiceManager.locationState.collectAsState()
    val liveTrendingFeeds by JashxKnowledgeDatabase.liveTrendingFeeds.collectAsState()

    // Human Voice Speaking State (Manual-only when clicking "Speak aloud")
    var isSpeakingNow by remember { mutableStateOf(false) }
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(context) {
        var instance: TextToSpeech? = null
        try {
            instance = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val res = instance?.setLanguage(Locale("en", "IN"))
                    if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                        instance?.setLanguage(Locale.US)
                    }
                    instance?.setSpeechRate(1.0f)
                    instance?.setPitch(1.0f)
                }
            }
            ttsEngine = instance
        } catch (_: Throwable) {}

        onDispose {
            try {
                instance?.stop()
                instance?.shutdown()
            } catch (_: Throwable) {}
        }
    }

    fun formatCleanDisplay(rawText: String): String {
        return rawText
            .replace("(`kmjashwanth01@gmail.com`)", "")
            .replace("(kmjashwanth01@gmail.com)", "")
            .replace("kmjashwanth01@gmail.com", "")
            .replace("**", "")
            .replace("`", "")
            .replace(Regex("""\s+\."""), ".")
            .replace(Regex("""\s+,"""), ",")
            .trim()
    }

    fun speakTextLikeHuman(rawText: String) {
        try {
            val cleanSpeech = JashxKnowledgeDatabase.toNaturalSpeechText(formatCleanDisplay(rawText))
            if (cleanSpeech.isNotBlank()) {
                isSpeakingNow = true
                ttsEngine?.speak(cleanSpeech, TextToSpeech.QUEUE_FLUSH, null, "jashx_human_voice")
            }
        } catch (_: Throwable) {}
    }

    fun stopSpeaking() {
        try {
            ttsEngine?.stop()
            isSpeakingNow = false
        } catch (_: Throwable) {}
    }

    // Automatically trigger live RSS feeds refresh when opening the JASHX AI tab (never auto-speaks)
    LaunchedEffect(Unit) {
        JashxKnowledgeDatabase.refreshLiveFeedsAsync(force = false)
    }

    // Voice Input Launcher (Talk to JASHX AI like a human)
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = matches?.firstOrNull()?.trim()
            if (!spokenText.isNullOrBlank()) {
                onAskQuestion(spokenText)
            }
        }
    }

    val promptSuggestions = listOf(
        "📰 Latest Live Feeds",
        "✨ Who created you?",
        "📍 My Location & Weather",
        "🏛️ Who is the CM of Tamil Nadu?",
        "🌤️ What is the weather today?",
        "🇮🇳 Indian Politics Feed",
        "🌍 World Politics Feed",
        "🪙 How many grams in 1 Sovereign?",
        "🔮 1h Live Bullion Prediction",
        "🎯 MCX Buy/Sell Targets",
        "💍 Is today good for 22K jewelry?"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 0. JASHX AI Hero Brand Showcase Banner
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = TitaniumDarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, GoldPrimary),
            modifier = Modifier.fillMaxWidth().testTag("jashx_ai_hero_banner")
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_jashx_ai_logo),
                        contentDescription = "JASHX AI Logo",
                        modifier = Modifier
                            .size(58.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.5.dp, GoldPrimary, RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "JashX AI",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = GoldPrimary,
                                    fontSize = 18.sp,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = GoldPrimary.copy(alpha = 0.2f),
                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, GoldPrimary)
                                ) {
                                    Text(
                                        text = "HUMAN AI",
                                        color = GoldLight,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Human-Speaking AI • Live Trending Feeds",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "K.M. Jashwanth is the Founder of JASHX AI",
                            color = GoldLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(TitaniumCardBg)
                                .clickable {
                                    com.example.util.LocationServiceManager.refreshLocation(context)
                                    onAskQuestion("What is my location and weather?")
                                }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                .testTag("jashx_live_location_weather_pill")
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "Detected Location",
                                tint = MarketGreen,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${locationState.city}: ${String.format("%.1f", locationState.temperatureC)}°C • ${locationState.weatherCondition}",
                                color = TextWhitePrimary,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Live Trending Feeds Strip (Bullion • TN Politics • India • World)
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LIVE TRENDING FEEDS (TAP TO ASK JASHX AI)",
                            color = GoldLight,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Text(
                        text = "↻ Refresh Feeds",
                        color = GoldPrimary,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable {
                                JashxKnowledgeDatabase.refreshLiveFeedsAsync(force = true)
                                onAskQuestion("Give me the latest live feeds")
                            }
                            .padding(2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(liveTrendingFeeds) { item ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = TitaniumCardBg,
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, GoldPrimary.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .width(235.dp)
                                .clickable {
                                    onAskQuestion("Tell me about: ${item.title}")
                                }
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.category,
                                        color = GoldPrimary,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = item.source,
                                        color = TextDimTertiary,
                                        fontSize = 8.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.title,
                                    color = TextWhitePrimary,
                                    fontSize = 10.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // 1. Live Market Predictions Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = TitaniumDarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.55f)),
            modifier = Modifier.fillMaxWidth().testTag("jashx_prediction_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_jashx_ai_logo),
                            contentDescription = "JASHX AI Logo",
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .border(1.dp, GoldPrimary, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "JASHX AI Market Predictions",
                                color = TextWhitePrimary,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Autonomous Trend Bias • Pivot Points • Targets • Stop-Loss",
                                color = GoldLight,
                                fontSize = 10.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onRefreshPrediction,
                        modifier = Modifier.size(32.dp).testTag("refresh_prediction_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = GoldPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val pred = prediction
                if (pred != null) {
                    // Bias & Confidence Row
                    val biasColor = Color(pred.bias.signalColorHex)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(biasColor.copy(alpha = 0.12f))
                            .border(1.dp, biasColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = biasColor,
                                modifier = Modifier.size(10.dp)
                            ) {}
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = pred.bias.label,
                                    color = biasColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "Confidence: ${pred.confidencePercent}% • ${pred.horizon}",
                                    color = TextWhitePrimary.copy(alpha = 0.8f),
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Current LTP",
                                color = TextMutedSecondary,
                                fontSize = 9.sp
                            )
                            Text(
                                text = "₹${String.format("%,.0f", pred.currentPrice)}",
                                color = TextWhitePrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Targets & Stop Loss Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = TitaniumCardBg),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, GoldLight.copy(alpha = 0.4f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("TARGET 1", color = GoldLight, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("₹${String.format("%,.0f", pred.target1)}", color = TextWhitePrimary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }

                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = TitaniumCardBg),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, MarketGreen.copy(alpha = 0.5f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("TARGET 2", color = MarketGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("₹${String.format("%,.0f", pred.target2)}", color = TextWhitePrimary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }

                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = TitaniumCardBg),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, MarketRed.copy(alpha = 0.5f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("STOP LOSS", color = MarketRed, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("₹${String.format("%,.0f", pred.stopLoss)}", color = TextWhitePrimary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Floor Trader Pivot Levels (P, R1, S1, R2, S2)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = TitaniumCardSurface,
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, TitaniumBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("S2", color = MarketRed, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text("₹${String.format("%,.0f", pred.support2)}", color = TextWhitePrimary, fontSize = 10.5.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("S1", color = MarketRed.copy(alpha = 0.8f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text("₹${String.format("%,.0f", pred.support1)}", color = TextWhitePrimary, fontSize = 10.5.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("PIVOT (P)", color = GoldPrimary, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                                Text("₹${String.format("%,.0f", pred.pivotPoint)}", color = GoldLight, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("R1", color = MarketGreen.copy(alpha = 0.8f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text("₹${String.format("%,.0f", pred.resistance1)}", color = TextWhitePrimary, fontSize = 10.5.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("R2", color = MarketGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text("₹${String.format("%,.0f", pred.resistance2)}", color = TextWhitePrimary, fontSize = 10.5.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RSI(14): ${pred.rsi14} • ${pred.macdStatus}",
                            color = TextDimTertiary,
                            fontSize = 9.5.sp
                        )
                        Text(
                            text = "Sync: ${pred.generatedTimeStr}",
                            color = GoldLight,
                            fontSize = 9.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = pred.summaryInsight,
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Button: Request Comprehensive Commentary
                Button(
                    onClick = onGenerateAnalysis,
                    enabled = !isGenerating,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldPrimary,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .testTag("jashx_generate_analysis_button")
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = TitaniumBlack,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("JASHX AI is thinking & speaking...", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = "Analyze",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate Comprehensive Market Commentary", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }
                }
            }
        }

        // 2. Multi-Turn Conversational Human-Speaking JASHX AI Assistant
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = TitaniumDarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, TitaniumBorder),
            modifier = Modifier.fillMaxWidth().testTag("jashx_chat_card")
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_jashx_ai_logo),
                            contentDescription = "JASHX AI Logo",
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .border(1.dp, GoldPrimary, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "JASHX AI Human-Speaking Assistant",
                                color = TextWhitePrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Talk or type • Live 2026 Feeds • Natural Human Voice",
                                color = GoldLight,
                                fontSize = 9.sp
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isSpeakingNow) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MarketRed.copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(0.8.dp, MarketRed),
                                modifier = Modifier.clickable { stopSpeaking() }
                            ) {
                                Text(
                                    text = "⏹ Stop Voice",
                                    color = MarketRed,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Text(
                            text = "jashx-ai-v2.0",
                            color = GoldLight,
                            fontSize = 9.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Chat Messages Window
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(TitaniumCardBg)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    chatMessages.takeLast(6).forEach { msg ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (msg.isFromUser) Arrangement.End else Arrangement.Start,
                            verticalAlignment = Alignment.Top
                        ) {
                            if (!msg.isFromUser) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_jashx_ai_logo),
                                    contentDescription = "JASHX AI",
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .border(0.5.dp, GoldPrimary, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (msg.isFromUser) GoldPrimary else TitaniumCardSurface,
                                border = androidx.compose.foundation.BorderStroke(
                                    0.5.dp,
                                    if (msg.isFromUser) GoldLight else TitaniumBorder
                                ),
                                modifier = Modifier.widthIn(max = 285.dp)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                                    Text(
                                        text = if (msg.isFromUser) msg.text else formatCleanDisplay(msg.text),
                                        color = if (msg.isFromUser) Color.Black else TextWhitePrimary,
                                        fontSize = 11.5.sp,
                                        lineHeight = 16.sp
                                    )
                                    if (!msg.isFromUser && msg.text != "Thinking...") {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable { speakTextLikeHuman(msg.text) }
                                                .padding(vertical = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.VolumeUp,
                                                contentDescription = "Speak answer aloud",
                                                tint = GoldPrimary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Speak aloud",
                                                color = GoldLight,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (isGenerating) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_jashx_ai_logo),
                                contentDescription = "Loading",
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = GoldPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("JASHX AI is responding...", color = GoldLight, fontSize = 10.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Suggestion Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(promptSuggestions) { suggestion ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = TitaniumCardSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, TitaniumBorder),
                            modifier = Modifier.clickable {
                                onAskQuestion(suggestion)
                            }
                        ) {
                            Text(
                                text = suggestion,
                                color = GoldLight,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Input Bar (Voice Mic + Instant non-blocking submission + Keyboard Send support)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(TitaniumCardSurface)
                        .border(1.dp, TitaniumBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = customQuery,
                        onValueChange = { customQuery = it },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Send
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onSend = {
                                if (customQuery.isNotBlank()) {
                                    onAskQuestion(customQuery.trim())
                                    customQuery = ""
                                }
                            }
                        ),
                        placeholder = {
                            Text("Talk or ask JASHX AI anything (feeds, rates, politics...)", fontSize = 11.sp, color = TextDimTertiary)
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = TextWhitePrimary,
                            unfocusedTextColor = TextWhitePrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("jashx_chat_input")
                    )

                    // Voice Microphone Button (Speak to JASHX AI)
                    IconButton(
                        onClick = {
                            try {
                                stopSpeaking()
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to JASHX AI...")
                                }
                                speechRecognizerLauncher.launch(intent)
                            } catch (_: Throwable) {
                                Toast.makeText(context, "Voice input not supported on this device", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.size(36.dp).testTag("jashx_voice_mic_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Speak to JASHX AI",
                            tint = GoldPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (customQuery.isNotBlank()) {
                                onAskQuestion(customQuery.trim())
                                customQuery = ""
                            }
                        },
                        enabled = customQuery.isNotBlank(),
                        modifier = Modifier.testTag("jashx_send_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (customQuery.isNotBlank()) GoldPrimary else TextDimTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 3. Full Commentary & Insights Card
        AnimatedVisibility(visible = aiAnalysisText.isNotBlank() || errorMessage != null) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = TitaniumDarkSurface),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (errorMessage != null) Color(0xFFFF5252).copy(alpha = 0.5f) else GoldPrimary.copy(alpha = 0.3f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("jashx_response_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = "JASHX AI Output",
                                tint = GoldPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "JASHX AI Live Commentary",
                                color = TextWhitePrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (aiAnalysisText.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { speakTextLikeHuman(aiAnalysisText) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VolumeUp,
                                        contentDescription = "Speak Commentary",
                                        tint = GoldPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("JASHX AI Analysis", aiAnalysisText))
                                        Toast.makeText(context, "Copied analysis to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy Analysis",
                                        tint = TextMutedSecondary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage,
                            color = Color(0xFFFF5252),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Text(
                            text = formatCleanDisplay(aiAnalysisText),
                            color = TextWhitePrimary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

// Deprecation alias so existing references compile seamlessly
@Composable
fun GeminiMarketAnalystView(
    gold999: CommodityContract?,
    silver1kg: CommodityContract?,
    mcxGold: CommodityContract?,
    mcxSilver: CommodityContract?,
    spotGold: CommodityContract?,
    spotInr: CommodityContract?,
    isMarketOpen: Boolean,
    aiAnalysisText: String,
    isGenerating: Boolean,
    errorMessage: String?,
    prediction: MarketPrediction? = null,
    chatMessages: List<ChatMessage> = emptyList(),
    onGenerateAnalysis: () -> Unit,
    onRefreshPrediction: () -> Unit = {},
    onAskQuestion: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    JashxAiMarketAnalystView(
        gold999 = gold999,
        silver1kg = silver1kg,
        mcxGold = mcxGold,
        mcxSilver = mcxSilver,
        spotGold = spotGold,
        spotInr = spotInr,
        isMarketOpen = isMarketOpen,
        aiAnalysisText = aiAnalysisText,
        isGenerating = isGenerating,
        errorMessage = errorMessage,
        prediction = prediction,
        chatMessages = chatMessages,
        onGenerateAnalysis = onGenerateAnalysis,
        onRefreshPrediction = onRefreshPrediction,
        onAskQuestion = onAskQuestion,
        modifier = modifier
    )
}
