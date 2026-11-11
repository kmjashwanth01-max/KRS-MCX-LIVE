package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import android.widget.Toast
import com.example.model.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.util.AppCheckUtil
import com.example.util.BiometricAuthManager
import com.example.util.NotificationHelper
import com.example.viewmodel.McxViewModel

class MainActivity : androidx.appcompat.app.AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI, ViewModels, or Firebase operations
        com.example.util.configureAppCheck(this, intent)

        // Global crash guard to log and prevent uncaught process termination
        try {
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                android.util.Log.e("CrashGuard", "Suppressed crash on ${thread.name}: ${throwable.message}", throwable)
                defaultHandler?.uncaughtException(thread, throwable)
            }
        } catch (_: Throwable) {}

        // 2. Initialize User Account Manager and App Lock safely
        try {
            com.example.util.UserAccountManager.init(this)
        } catch (t: Throwable) {
            android.util.Log.e("MainActivity", "UserAccountManager init failed", t)
        }

        // 3. Setup Notification Channel safely
        try {
            NotificationHelper.createNotificationChannel(this)
        } catch (t: Throwable) {
            android.util.Log.e("MainActivity", "NotificationHelper init failed", t)
        }

        setContent {
            val context = androidx.compose.ui.platform.LocalContext.current

            val locationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { _ ->
                com.example.util.LocationServiceManager.refreshLocation(context)
            }

            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { _ -> }

            LaunchedEffect(Unit) {
                try {
                    // Always trigger initial location & live weather detection
                    com.example.util.LocationServiceManager.refreshLocation(context)

                    val hasLocationPerm = com.example.util.LocationServiceManager.hasLocationPermission(context)
                    if (!hasLocationPerm) {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } catch (_: Throwable) {}
            }

            val viewModel: McxViewModel = viewModel(
                factory = McxViewModel.provideFactory(application)
            )
            val isDarkMode by viewModel.isDarkMode.collectAsState()
            KrsBullionTheme(darkTheme = isDarkMode) {
                MainAppScreen(
                    viewModel = viewModel,
                    onRequestLocationPermission = {
                        if (!com.example.util.LocationServiceManager.hasLocationPermission(context)) {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        } else {
                            com.example.util.LocationServiceManager.refreshLocation(context)
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            com.example.widget.BullionAppWidgetProvider.startOneSecondWidgetTicker(this)
            com.example.util.AppDiagnosticsManager.checkAndTriggerAutomaticDailySummary(this)
        } catch (t: Throwable) {
            android.util.Log.e("MainActivity", "onResume failed", t)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: McxViewModel,
    onRequestLocationPermission: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? androidx.fragment.app.FragmentActivity
    val locationState by com.example.util.LocationServiceManager.locationState.collectAsState()

    val isDarkMode by viewModel.isDarkMode.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    var showAlertModal by remember { mutableStateOf(false) }
    var chartRange by remember { mutableStateOf("1s LIVE") }
    var showSettingsModal by remember { mutableStateOf(false) }
    var showCreateAccountFromLock by remember { mutableStateOf(false) }

    val priceAlerts by viewModel.priceAlerts.collectAsState()

    val currentUser by com.example.util.UserAccountManager.currentUser.collectAsState()
    val isAppLocked by com.example.util.UserAccountManager.isAppLocked.collectAsState()

    val contracts by viewModel.contracts.collectAsState()
    val activeFeed by viewModel.activeVijayFeed.collectAsState()
    val selectedContract by viewModel.selectedContract.collectAsState()
    val selectedContractId by viewModel.selectedContractId.collectAsState()
    val historyMap by viewModel.historyMap.collectAsState()
    val orderBook by viewModel.orderBook.collectAsState()
    val cityRates by viewModel.cityRates.collectAsState()
    val isMarketOpen by viewModel.isMarketOpen.collectAsState()
    val marketStatusText by viewModel.marketStatusText.collectAsState()
    val liveIstClock by viewModel.liveIstClock.collectAsState()
    val marketSessionDetail by viewModel.marketSessionDetail.collectAsState()

    val aiAnalysisText by viewModel.aiAnalysisText.collectAsState()
    val isGeneratingAi by viewModel.isGeneratingAi.collectAsState()
    val aiErrorMessage by viewModel.aiErrorMessage.collectAsState()
    val livePrediction by viewModel.livePrediction.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val announcements by viewModel.announcements.collectAsState()

    // Unit converter helper states (for Tools tab)
    var converterInput by remember { mutableStateOf(10.0) }
    var converterUnit by remember { mutableStateOf("Tola (11.6638g)") }

    val gold999 = contracts.firstOrNull { it.id == "gold_999" }
    val silver1kg = contracts.firstOrNull { it.id == "silver_1kg" }
    val mcxGold = contracts.firstOrNull { it.id == "mcx_gold" }
    val mcxSilver = contracts.firstOrNull { it.id == "mcx_silver" }

    // Bottom Spot Contracts from 1-second live contracts list with real tick directions
    val spotGold = contracts.firstOrNull { it.id == "spot_gold" }
    val spotSilver = contracts.firstOrNull { it.id == "spot_silver" }
    val spotInr = contracts.firstOrNull { it.id == "spot_inr" }
    val requiresOnboarding by com.example.util.UserAccountManager.requiresOnboarding.collectAsState()

    // 1. If newly downloaded or no personal account registered, open Sign In / Create Account page FIRST!
    if (requiresOnboarding || currentUser == null) {
        AccountAuthScreen(
            onAuthSuccess = {
                // Successfully authenticated
            }
        )
        return
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isDarkMode) TitaniumDarkSurface else Color(0xFFFFFFFF))
                    .statusBarsPadding()
            ) {
                // 1. TOP BRAND & JASHX AI HEADER ROW
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left Brand: K.R.S. Gold & Silver x JASHX AI Logo + Title
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.testTag("app_brand_header")
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_krs_app_logo),
                            contentDescription = "K.R.S. Gold & Silver Logo",
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, GoldPrimary, RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "K.R.S.",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isDarkMode) GoldPrimary else Color(0xFFB45309),
                                fontSize = 17.sp,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Live MCX Bullion",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDarkMode) Color.LightGray else Color(0xFF475569),
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Right: " Powered By JASHX AI " badge with logo
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isDarkMode) GoldPrimary.copy(alpha = 0.15f) else Color(0xFFFEF3C7),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isDarkMode) GoldPrimary.copy(alpha = 0.6f) else Color(0xFFF59E0B)
                        ),
                        modifier = Modifier
                            .clickable { selectedTab = 1 }
                            .testTag("top_powered_by_jashx_ai")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_jashx_ai_logo),
                                contentDescription = "JASHX AI Logo",
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = " Powered By JASHX AI ",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkMode) GoldPrimary else Color(0xFF92400E),
                                fontSize = 10.5.sp
                            )
                        }
                    }
                }

                // 2. SUB-HEADER ACTION RIBBON (Stream Status & Guaranteed High-Visibility Buttons)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Live 1-Second Pulse Indicator + Live Detected Location & Weather
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onRequestLocationPermission() }
                            .testTag("header_location_weather_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MarketGreen)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "KRS Bullion 1s Live Stream",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDarkMode) MarketGreen else Color(0xFF15803D),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "Live Location & Weather",
                                tint = if (isDarkMode) GoldPrimary else Color(0xFFB45309),
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "${locationState.city}: ${String.format("%.1f", locationState.temperatureC)}°C • ${locationState.weatherCondition}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDarkMode) GoldLight else Color(0xFF334155),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Right: Price Alerts, Theme Toggle & Settings Buttons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Price Alerts Bell Button (with active count badge!)
                        val activeAlertsCount = priceAlerts.count { !it.isTriggered }
                        IconButton(
                            onClick = { showAlertModal = true },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (isDarkMode) TitaniumCardBg else Color(0xFFF1F5F9))
                                .border(1.dp, if (isDarkMode) TitaniumBorder else Color(0xFFCBD5E1), CircleShape)
                                .testTag("alert_bell_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (activeAlertsCount > 0) {
                                        Badge(
                                            containerColor = GoldPrimary,
                                            contentColor = Color.Black
                                        ) {
                                            Text("$activeAlertsCount", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = "Price Alerts",
                                    tint = if (isDarkMode) GoldPrimary else Color(0xFFB45309),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // 2. Theme Toggle Button (Dark / Light Mode)
                        IconButton(
                            onClick = { viewModel.toggleTheme() },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (isDarkMode) TitaniumCardBg else Color(0xFFF1F5F9))
                                .border(1.dp, if (isDarkMode) TitaniumBorder else Color(0xFFCBD5E1), CircleShape)
                                .testTag("theme_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = if (isDarkMode) "Switch to Light Mode" else "Switch to Dark Mode",
                                tint = if (isDarkMode) GoldPrimary else Color(0xFFB45309),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // 3. Settings & App Lock Button
                        IconButton(
                            onClick = { showSettingsModal = true },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (isDarkMode) TitaniumCardBg else Color(0xFFF1F5F9))
                                .border(1.dp, if (isDarkMode) TitaniumBorder else Color(0xFFCBD5E1), CircleShape)
                                .testTag("top_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings & App Lock",
                                tint = if (isDarkMode) GoldPrimary else Color(0xFFB45309),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Real-Time Market Status on Top of Running Advertisement Lines (Synced with Vijay Bullion)
                FloatingAdvertisementBanner(
                    isMarketOpen = isMarketOpen,
                    marketStatusText = marketStatusText,
                    liveIstClock = liveIstClock,
                    marketSessionDetail = marketSessionDetail,
                    goldRate10g = gold999?.ltp ?: activeFeed.goldBarRate10g,
                    silverRate1kg = silver1kg?.ltp ?: activeFeed.silverBarRate1kg,
                    mcxGoldLtp = mcxGold?.ltp ?: activeFeed.mcxGoldLtp,
                    mcxSilverLtp = mcxSilver?.ltp ?: activeFeed.mcxSilverLtp,
                    spotGoldUsd = spotGold?.ltp ?: activeFeed.spotGoldBid,
                    spotSilverUsd = spotSilver?.ltp ?: activeFeed.spotSilverBid,
                    spotInr = spotInr?.ltp ?: activeFeed.spotInrBid
                )
            }
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth().background(if (isDarkMode) TitaniumDarkSurface else Color(0xFFFFFFFF))) {
                // Persistent Creator Attribution Footer
                Text(
                    text = "This app was build by K.M. Jashwanth",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDarkMode) Color.Gray.copy(alpha = 0.8f) else Color(0xFF64748B),
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .testTag("footer_attribution")
                )

                val navItemColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = if (isDarkMode) Color.Black else Color.White,
                    selectedTextColor = if (isDarkMode) GoldPrimary else Color(0xFFB45309),
                    indicatorColor = if (isDarkMode) GoldPrimary else Color(0xFFB45309),
                    unselectedIconColor = if (isDarkMode) Color.LightGray else Color(0xFF64748B),
                    unselectedTextColor = if (isDarkMode) Color.LightGray else Color(0xFF64748B)
                )

                NavigationBar(
                    containerColor = if (isDarkMode) TitaniumDarkSurface else Color(0xFFFFFFFF),
                    contentColor = if (isDarkMode) Color.White else Color(0xFF0F172A),
                    tonalElevation = 8.dp,
                    modifier = Modifier.height(64.dp)
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.ShowChart, contentDescription = "Live") },
                        label = { Text("Live", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = navItemColors,
                        modifier = Modifier.testTag("tab_live")
                    )

                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = {
                            Image(
                                painter = painterResource(id = R.drawable.ic_jashx_ai_logo),
                                contentDescription = "JASHX AI Logo",
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                            )
                        },
                        label = { Text("JASHX AI", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = navItemColors,
                        modifier = Modifier.testTag("tab_jashx_ai")
                    )

                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.CandlestickChart, contentDescription = "Charts") },
                        label = { Text("Charts", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = navItemColors,
                        modifier = Modifier.testTag("tab_charts")
                    )

                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Default.Widgets, contentDescription = "Tools") },
                        label = { Text("Tools", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = navItemColors,
                        modifier = Modifier.testTag("tab_tools")
                    )
                }
            }
        },
        containerColor = if (isDarkMode) TitaniumBackground else TitaniumLightBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> {
                    // LIVE TAB: Physical Bullion, MCX Futures, Spot Matrix, Hero Card, Widget Promo, Vijay Board, Ticker Chart, Order Book, Cities
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Official Announcements Banner controlled exclusively by Admin from separate Cloud Database
                        if (announcements.isNotEmpty()) {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDarkMode) Color(0xFF1F190E) else Color(0xFFFEF3C7)
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.2.dp, GoldPrimary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("admin_official_announcements_card")
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Campaign,
                                            contentDescription = "Official Announcement",
                                            tint = GoldPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "OFFICIAL KRS BULLION ANNOUNCEMENT",
                                            color = GoldPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                    announcements.take(3).forEach { item ->
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Text(
                                                text = item.title,
                                                color = if (isDarkMode) Color.White else Color(0xFF0F172A),
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = item.message,
                                                color = if (isDarkMode) Color.LightGray else Color(0xFF334155),
                                                fontSize = 11.sp,
                                                lineHeight = 15.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 1. Physical Bullion (Gold 999 10g & Silver 1kg)
                        PhysicalBullionTableView(
                            physicalGold = gold999,
                            physicalSilver = silver1kg
                        )

                        // 2. MCX Futures (GoldMCX & SilverMCX) with maroon red header
                        McxFuturesTableView(
                            mcxGold = mcxGold,
                            mcxSilver = mcxSilver
                        )

                        // 3. International Spot Rates & Forex (Gold $, Silver $, INR ₹)
                        SpotRatesCentTableView(
                            spotGold = spotGold,
                            spotSilver = spotSilver,
                            spotInr = spotInr
                        )

                        // 4. Hero Price Card (matching requested reference image)
                        HeroPriceCard(
                            contract = selectedContract,
                            onSelectGold = { viewModel.selectContract("gold_999") },
                            onSelectSilver = { viewModel.selectContract("silver_1kg") },
                            onSelectMcxGold = { viewModel.selectContract("mcx_gold") },
                            onSelectMcxSilver = { viewModel.selectContract("mcx_silver") },
                            onOpenAlertModal = { showAlertModal = true }
                        )

                        // 5. Home Screen Widget Promo Card (with 1-tap Pin button and 4 rates preview: Gold 999, Silver 1kg, Gold Cent $, Silver Cent $)
                        HomeScreenWidgetPromoCard(
                            goldContract = gold999,
                            silverContract = silver1kg,
                            spotGoldContract = spotGold,
                            spotSilverContract = spotSilver
                        )

                        // 6. Direct 1-Second Vijay Bullion Stream Rate Board
                        VijayBullionRateBoard(
                            contracts = contracts,
                            selectedContractId = selectedContractId,
                            onSelectContract = { viewModel.selectContract(it) }
                        )

                        // 7. Live Tick Canvas Chart for selected contract
                        val historyPoints = historyMap[selectedContractId] ?: emptyList()
                        LiveTickChart(
                            contract = selectedContract,
                            selectedRange = chartRange,
                            onSelectRange = { chartRange = it }
                        )

                        // 8. Order Book Market Depth
                        OrderBookDepthView(contract = selectedContract)

                        // 9. Indian Regional City Rates
                        CityRatesView(cityRates = cityRates)

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                1 -> {
                    // JASHX AI TAB: Autonomous Bullion Intelligence & Live Market Predictions
                    JashxAiMarketAnalystView(
                        gold999 = gold999,
                        silver1kg = silver1kg,
                        mcxGold = mcxGold,
                        mcxSilver = mcxSilver,
                        spotGold = spotGold,
                        spotInr = spotInr,
                        isMarketOpen = isMarketOpen,
                        aiAnalysisText = aiAnalysisText,
                        isGenerating = isGeneratingAi,
                        errorMessage = aiErrorMessage,
                        prediction = livePrediction,
                        chatMessages = chatMessages,
                        onGenerateAnalysis = { viewModel.requestAiAnalysis() },
                        onRefreshPrediction = { viewModel.refreshPrediction() },
                        onAskQuestion = { query -> viewModel.askAiQuestion(query) },
                        modifier = Modifier.padding(12.dp)
                    )
                }

                2 -> {
                    // CHARTS TAB: Advanced Technical Analysis, RSI, Bollinger Bands, MACD, and Candlesticks
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. Advanced Technical Chart with Candlestick/Line toggle, Bollinger Bands, RSI(14), MACD, and Touch Scrubber
                        AdvancedTechnicalChartView(
                            contracts = contracts,
                            selectedContractId = selectedContractId,
                            onSelectContract = { viewModel.selectContract(it) }
                        )

                        val baseLtp = selectedContract?.ltp ?: 146000.0
                        val histPoints = remember(selectedContractId, baseLtp) {
                            listOf(
                                HistoricalDataPoint("09:15", baseLtp - 200, baseLtp + 100, baseLtp - 300, 1500),
                                HistoricalDataPoint("11:30", baseLtp - 100, baseLtp + 200, baseLtp - 150, 3200),
                                HistoricalDataPoint("14:00", baseLtp + 150, baseLtp + 350, baseLtp - 50, 4800),
                                HistoricalDataPoint("17:30", baseLtp + 280, baseLtp + 400, baseLtp + 100, 6100),
                                HistoricalDataPoint("20:00", baseLtp, baseLtp + 500, baseLtp - 100, 7800)
                            )
                        }
                        val summary = remember(selectedContract, chartRange) {
                            PeriodSummary(
                                period = chartRange,
                                startPrice = (selectedContract?.ltp ?: 146000.0) - 250.0,
                                endPrice = selectedContract?.ltp ?: 146000.0,
                                highPrice = selectedContract?.high ?: 148340.0,
                                lowPrice = selectedContract?.low ?: 144555.0,
                                change = selectedContract?.change ?: 250.0,
                                changePercent = selectedContract?.changePercent ?: 1.66
                            )
                        }

                        HistoricalChartSection(
                            periodSummary = summary,
                            historicalPoints = histPoints,
                            selectedRange = chartRange,
                            onSelectRange = { chartRange = it }
                        )

                        LiveTickChart(
                            contract = selectedContract,
                            selectedRange = chartRange,
                            onSelectRange = { chartRange = it }
                        )

                        AllContractsListView(
                            contracts = contracts,
                            selectedContractId = selectedContractId,
                            onSelectContract = { viewModel.selectContract(it) }
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                3 -> {
                    // TOOLS TAB: Home Screen Widget Promo, Unit Converter, City Rates & Support Info
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Security, Accounts & 4-Digit App Lock Quick Access Card
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = if (isDarkMode) TitaniumDarkSurface else Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showSettingsModal = true }
                                .testTag("tools_settings_card")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
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
                                            imageVector = Icons.Default.Security,
                                            contentDescription = null,
                                            tint = GoldPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Settings & Accounts",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDarkMode) Color.White else Color(0xFF0F172A)
                                        )
                                        Text(
                                            text = "Account: ${currentUser?.name ?: "K.M. Jashwanth"} • @${currentUser?.username ?: "jashwanth"} • ${currentUser?.city ?: "Coimbatore"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isDarkMode) GoldLight else Color(0xFFB45309),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Open Settings",
                                    tint = GoldPrimary
                                )
                            }
                        }

                        // Appearance & Theme Mode Card
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = if (isDarkMode) TitaniumDarkSurface else Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isDarkMode) TitaniumBorder else Color(0xFFCBD5E1)),
                            modifier = Modifier.fillMaxWidth().testTag("theme_settings_card")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                                        contentDescription = "Theme Mode",
                                        tint = if (isDarkMode) GoldPrimary else Color(0xFFB45309),
                                        modifier = Modifier.size(26.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = if (isDarkMode) "Dark Titanium Mode" else "Light Platinum Mode",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDarkMode) Color.White else Color(0xFF0F172A)
                                        )
                                        Text(
                                            text = "High-contrast bullion display • Exact rates untouched",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isDarkMode) TextDimTertiary else Color(0xFF64748B),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                Switch(
                                    checked = isDarkMode,
                                    onCheckedChange = { viewModel.setDarkMode(it) }
                                )
                            }
                        }

                        // Home Screen Widget Promo Card (with 4 rates preview: Gold 999, Silver 1kg, Gold Cent $, Silver Cent $)
                        HomeScreenWidgetPromoCard(
                            goldContract = gold999,
                            silverContract = silver1kg,
                            spotGoldContract = spotGold,
                            spotSilverContract = spotSilver
                        )

                        UnitConverterView(
                            goldContract = gold999,
                            silverContract = silver1kg,
                            inputAmount = converterInput,
                            selectedUnitName = converterUnit,
                            onInputChange = { converterInput = it },
                            onUnitChange = { converterUnit = it }
                        )

                        CityRatesView(cityRates = cityRates)

                        // Official Support & App Information Card
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = if (isDarkMode) TitaniumDarkSurface else Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isDarkMode) TitaniumBorder else Color(0xFFCBD5E1)),
                            modifier = Modifier.fillMaxWidth().testTag("app_info_card")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "KRS Gold & Silver Information",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDarkMode) GoldPrimary else Color(0xFFB45309)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "• Official Live Rates Feed: Direct MCX & Spot Live Broadcast\n" +
                                            "• Update Frequency: Every 1 Second\n" +
                                            "• Help & Inquiries: krsmcxofficial@gmail.com\n" +
                                            "• Built by: K.M. Jashwanth\n" +
                                            "• Home Screen Widget: Enabled with instant 1-tap pin",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isDarkMode) Color.LightGray else Color(0xFF475569),
                                    lineHeight = 20.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            // Price Alert Configuration Dialog
            if (showAlertModal) {
                PriceAlertsDialog(
                    contracts = contracts,
                    selectedContractId = selectedContractId,
                    activeAlerts = priceAlerts,
                    onDismiss = { showAlertModal = false },
                    onSetAlert = { contractId, targetPrice, isAbove ->
                        viewModel.setPriceAlert(contractId, targetPrice, isAbove)
                    },
                    onRemoveAlert = { alertId ->
                        viewModel.removePriceAlert(alertId)
                    }
                )
            }

            // Full Settings & App Lock Modal View
            if (showSettingsModal) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { showSettingsModal = false },
                    properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = {
                                    Text(
                                        text = "Settings & Accounts",
                                        fontWeight = FontWeight.Bold,
                                        color = GoldPrimary,
                                        fontSize = 17.sp
                                    )
                                },
                                navigationIcon = {
                                    IconButton(onClick = { showSettingsModal = false }) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(containerColor = TitaniumDarkSurface)
                            )
                        },
                        containerColor = TitaniumBackground
                    ) { innerPad ->
                        SettingsView(
                            isDarkMode = isDarkMode,
                            onToggleTheme = { viewModel.toggleTheme() },
                            onSignOut = {
                                showSettingsModal = false
                            },
                            modifier = Modifier
                                .padding(innerPad)
                                .padding(14.dp)
                        )
                    }
                }
            }
        }
    }
}
