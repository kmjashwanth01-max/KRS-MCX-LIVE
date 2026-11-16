package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.JashxAiEngine
import com.example.data.JashxKnowledgeDatabase
import com.example.data.JashxLiveVoiceManager
import com.example.data.JashxVoicePersona
import com.example.data.LiveVoiceUiState
import com.example.data.McxRatesRepository
import com.example.data.VijayBullionFeed
import com.example.model.BullionCityRate
import com.example.model.ChatMessage
import com.example.model.CommodityContract
import com.example.model.MarketPrediction
import com.example.data.repository.AdminCloudDatabaseRepository
import com.example.data.repository.CloudAnnouncement
import com.example.data.repository.CloudAppInstallation
import com.example.data.repository.CloudAppUpdateConfig
import com.example.data.repository.CloudAppUpdateRecord
import com.example.model.OrderBookEntry
import com.example.model.PricePoint
import com.example.util.BrandSanitizer
import com.example.util.NotificationHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class McxViewModel(application: Application? = null) : AndroidViewModel(application ?: Application()) {

    companion object {
        fun provideFactory(application: Application): androidx.lifecycle.ViewModelProvider.Factory =
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return McxViewModel(application) as T
                }
            }
    }

    private val repository = McxRatesRepository(application)
    private val jashxEngine = JashxAiEngine()
    private val liveVoiceManager = JashxLiveVoiceManager(
        scope = viewModelScope,
        application = application,
        onVoiceTurnCompleted = { userText, aiText ->
            appendVoiceTurnToChat(userText, aiText)
        }
    )

    val liveVoiceState: StateFlow<LiveVoiceUiState> = liveVoiceManager.uiState

    val contracts: StateFlow<List<CommodityContract>> = repository.contracts
    val activeVijayFeed: StateFlow<VijayBullionFeed> = repository.activeVijayFeed
    val historyMap: StateFlow<Map<String, List<PricePoint>>> = repository.historyMap
    val orderBook: StateFlow<List<OrderBookEntry>> = repository.orderBook
    val cityRates: StateFlow<List<BullionCityRate>> = repository.cityRates
    val isMarketOpen: StateFlow<Boolean> = repository.isMarketOpen
    val isPhysicalBullionOpen: StateFlow<Boolean> = repository.isPhysicalBullionOpen
    val marketStatusText: StateFlow<String> = repository.marketStatusText
    val liveIstClock: StateFlow<String> = repository.liveIstClock
    val marketSessionDetail: StateFlow<String> = repository.marketSessionDetail
    val priceAlerts: StateFlow<List<com.example.data.ActivePriceAlert>> = repository.priceAlerts

    private val _selectedContractId = MutableStateFlow("gold_999")
    val selectedContractId: StateFlow<String> = _selectedContractId.asStateFlow()

    val isDarkMode: StateFlow<Boolean> = com.example.util.UserAccountManager.isDarkMode

    fun toggleTheme() {
        com.example.util.UserAccountManager.toggleDarkModePreference()
    }

    fun setDarkMode(enabled: Boolean) {
        com.example.util.UserAccountManager.setDarkModePreference(enabled)
    }

    val selectedContract: StateFlow<CommodityContract?> = combine(contracts, selectedContractId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // JASHX AI Commentary States
    private val _aiAnalysisText = MutableStateFlow("")
    val aiAnalysisText: StateFlow<String> = _aiAnalysisText.asStateFlow()

    private val _isGeneratingAi = MutableStateFlow(false)
    val isGeneratingAi: StateFlow<Boolean> = _isGeneratingAi.asStateFlow()

    private val _aiErrorMessage = MutableStateFlow<String?>(null)
    val aiErrorMessage: StateFlow<String?> = _aiErrorMessage.asStateFlow()

    // Live Market Prediction State
    private val _livePrediction = MutableStateFlow<MarketPrediction?>(null)
    val livePrediction: StateFlow<MarketPrediction?> = _livePrediction.asStateFlow()

    // Multi-turn Conversational Chat History
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                text = "Vanakkam! 🙏 I am JASHX AI, your autonomous market intelligence assistant built by K.M. Jashwanth. Ask me anything from live bullion rates to current affairs, weather, or math!",
                isFromUser = false
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    // Official Announcements passed from the separate Admin Firestore Database (streamed for ALL users)
    private val _announcements = MutableStateFlow<List<CloudAnnouncement>>(emptyList())
    val announcements: StateFlow<List<CloudAnnouncement>> = _announcements.asStateFlow()
    private var announcementsJob: Job? = null

    private val _isRefreshingAnnouncements = MutableStateFlow(false)
    val isRefreshingAnnouncements: StateFlow<Boolean> = _isRefreshingAnnouncements.asStateFlow()

    private val _showAnnouncementLaunchDialog = MutableStateFlow(true)
    val showAnnouncementLaunchDialog: StateFlow<Boolean> = _showAnnouncementLaunchDialog.asStateFlow()

    // Remote OTA App Update State synced with Firestore /app_config/app_update
    private val _appUpdateConfig = MutableStateFlow<CloudAppUpdateConfig?>(null)
    val appUpdateConfig: StateFlow<CloudAppUpdateConfig?> = _appUpdateConfig.asStateFlow()

    private val _isCheckingForUpdate = MutableStateFlow(false)
    val isCheckingForUpdate: StateFlow<Boolean> = _isCheckingForUpdate.asStateFlow()

    private val _updateCheckMessage = MutableStateFlow<String?>(null)
    val updateCheckMessage: StateFlow<String?> = _updateCheckMessage.asStateFlow()

    private val _isPublishingUpdate = MutableStateFlow(false)
    val isPublishingUpdate: StateFlow<Boolean> = _isPublishingUpdate.asStateFlow()

    private val _updatePublishMessage = MutableStateFlow<String?>(null)
    val updatePublishMessage: StateFlow<String?> = _updatePublishMessage.asStateFlow()

    private val _updateDismissedForSession = MutableStateFlow(false)
    val updateDismissedForSession: StateFlow<Boolean> = _updateDismissedForSession.asStateFlow()

    private val _showUpdateDialogExplicitly = MutableStateFlow(false)
    val showUpdateDialogExplicitly: StateFlow<Boolean> = _showUpdateDialogExplicitly.asStateFlow()

    private val _appInstallations = MutableStateFlow<List<CloudAppInstallation>>(emptyList())
    val appInstallations: StateFlow<List<CloudAppInstallation>> = _appInstallations.asStateFlow()

    private val _updatesHistory = MutableStateFlow<List<CloudAppUpdateRecord>>(emptyList())
    val updatesHistory: StateFlow<List<CloudAppUpdateRecord>> = _updatesHistory.asStateFlow()

    private var lastForegroundUpdateCheckMs: Long = 0L
    private var lastNotifiedUpdateVersionCode: Long = 0L
    private var lastReportedUsername: String? = null

    init {
        // Warm live news feeds & trigger initial JASHX AI commentary + prediction
        JashxKnowledgeDatabase.refreshLiveFeedsAsync(force = true)
        requestAiAnalysis()
        refreshPrediction()
        bootstrapAndObserveAnnouncements(application)
        bootstrapAndObserveAppUpdate(application)

        // Continuously update JASHX AI (text & live voice) with the latest 1-second Vijay Bullion feeds
        viewModelScope.launch {
            activeVijayFeed.collect { feed ->
                val current = selectedContract.value ?: contracts.value.firstOrNull { it.id == selectedContractId.value }
                val pred = jashxEngine.calculateLivePrediction(current, feed)
                _livePrediction.value = pred
                liveVoiceManager.updateLiveTelemetry(
                    feed = feed,
                    isMarketOpen = isMarketOpen.value,
                    marketStatusText = marketStatusText.value,
                    prediction = pred
                )
            }
        }

        // Isolate chat history per individual user profile and sync installed app version telemetry
        viewModelScope.launch {
            com.example.util.UserAccountManager.currentUser.collect { user ->
                if (user != null) {
                    val saved = com.example.util.UserAccountManager.loadUserChat(user.id)
                    if (saved.isNotEmpty()) {
                        _chatMessages.value = saved
                    } else {
                        _chatMessages.value = listOf(
                            ChatMessage(
                                text = "Vanakkam, ${user.name}! 🙏 I am JASHX AI, your personal autonomous market assistant built by K.M. Jashwanth. Ask me anything from live bullion rates to current affairs, weather, or math!",
                                isFromUser = false
                            )
                        )
                    }
                    reportUserAppVersion(application, user.username)
                }
            }
        }
    }

    private fun reportUserAppVersion(application: Application?, username: String) {
        if (application == null) return
        val cleanUser = username.trim().removePrefix("@")
        if (cleanUser.isBlank() || cleanUser == lastReportedUsername) return
        lastReportedUsername = cleanUser
        viewModelScope.launch {
            runCatching {
                val model = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim()
                AdminCloudDatabaseRepository(application.applicationContext).reportAppInstallation(
                    username = cleanUser,
                    versionCode = BuildConfig.VERSION_CODE.toLong(),
                    versionName = BuildConfig.VERSION_NAME,
                    deviceModel = model
                )
            }
        }
    }

    private fun bootstrapAndObserveAnnouncements(application: Application?) {
        if (application == null) return
        announcementsJob?.cancel()
        announcementsJob = viewModelScope.launch {
            try {
                val cloudRepo = AdminCloudDatabaseRepository(application.applicationContext)
                cloudRepo.bootstrapInitialAnnouncementIfNeeded()
                cloudRepo.observeActiveAnnouncements().collect { list ->
                    _announcements.value = list
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                android.util.Log.w("McxViewModel", "Announcements stream error: ${t.message}")
            }
        }
    }

    fun refreshAnnouncements() {
        val appCtx = getApplication<Application>()
        viewModelScope.launch {
            _isRefreshingAnnouncements.value = true
            try {
                val cloudRepo = AdminCloudDatabaseRepository(appCtx)
                cloudRepo.bootstrapInitialAnnouncementIfNeeded()
                cloudRepo.getActiveAnnouncementsOnce().onSuccess { list ->
                    _announcements.value = list
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                android.util.Log.w("McxViewModel", "Manual announcement refresh error: ${t.message}")
            } finally {
                _isRefreshingAnnouncements.value = false
            }
        }
    }

    fun dismissAnnouncementLaunchDialog() {
        _showAnnouncementLaunchDialog.value = false
    }

    fun openAnnouncementLaunchDialog() {
        _showAnnouncementLaunchDialog.value = true
    }

    private fun bootstrapAndObserveAppUpdate(application: Application?) {
        if (application == null) return
        val cloudRepo = AdminCloudDatabaseRepository(application.applicationContext)
        viewModelScope.launch {
            // Never allow transient offline bootstrap errors to block the real-time update listener
            runCatching { cloudRepo.bootstrapInitialAppUpdateConfigIfNeeded() }
            launch {
                try {
                    cloudRepo.observeUpdatesHistory().collect { history ->
                        _updatesHistory.value = history
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    android.util.Log.w("McxViewModel", "Updates history stream error: ${t.message}")
                }
            }
            try {
                cloudRepo.observeAppUpdateConfig().collect { config ->
                    _appUpdateConfig.value = config
                    handleIncomingAppUpdateConfig(application, config)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                android.util.Log.w("McxViewModel", "App update stream error: ${t.message}")
            }
        }
    }

    private fun handleIncomingAppUpdateConfig(application: Application, config: CloudAppUpdateConfig?) {
        if (config == null) return
        val currentUsername = com.example.util.UserAccountManager.currentUser.value?.username
        val eligible = config.isUpdateEnabled &&
            config.latestVersionCode > BuildConfig.VERSION_CODE.toLong() &&
            config.appliesToUser(currentUsername)
        if (eligible && config.latestVersionCode > lastNotifiedUpdateVersionCode) {
            lastNotifiedUpdateVersionCode = config.latestVersionCode
            _updateDismissedForSession.value = false
            NotificationHelper.sendAppUpdateNotification(
                context = application.applicationContext,
                versionName = config.latestVersionName,
                versionCode = config.latestVersionCode,
                releaseNotes = config.releaseNotes
            )
        }
    }

    fun onAppForegrounded() {
        val now = System.currentTimeMillis()
        if (now - lastForegroundUpdateCheckMs < 15_000L) return
        lastForegroundUpdateCheckMs = now
        checkForAppUpdate(manualTrigger = false)
    }

    fun checkForAppUpdate(manualTrigger: Boolean = true) {
        val appCtx = getApplication<Application>()
        viewModelScope.launch {
            if (manualTrigger) {
                _isCheckingForUpdate.value = true
                _updateCheckMessage.value = null
            }
            try {
                val cloudRepo = AdminCloudDatabaseRepository(appCtx)
                runCatching {
                    cloudRepo.listUpdatesHistory().onSuccess { list ->
                        if (list.isNotEmpty()) _updatesHistory.value = list
                    }
                }
                val res = cloudRepo.getAppUpdateConfig()
                res.onSuccess { cfg ->
                    _appUpdateConfig.value = cfg
                    handleIncomingAppUpdateConfig(appCtx, cfg)
                    val installedCode = BuildConfig.VERSION_CODE.toLong()
                    val currentUsername = com.example.util.UserAccountManager.currentUser.value?.username
                    val eligible = cfg != null &&
                        cfg.isUpdateEnabled &&
                        cfg.latestVersionCode > installedCode &&
                        cfg.appliesToUser(currentUsername)
                    if (eligible && cfg != null) {
                        _updateCheckMessage.value = "Update v${cfg.latestVersionName} (Build ${cfg.latestVersionCode}) is available!"
                        if (manualTrigger) {
                            _updateDismissedForSession.value = false
                            _showUpdateDialogExplicitly.value = true
                        }
                    } else if (manualTrigger) {
                        _updateCheckMessage.value = "You're up to date (v${BuildConfig.VERSION_NAME} • Build $installedCode)."
                    }
                }.onFailure { err ->
                    if (manualTrigger) {
                        _updateCheckMessage.value = "Unable to check updates: ${err.localizedMessage ?: "Network unavailable"}"
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (manualTrigger) {
                    _updateCheckMessage.value = "Unable to check updates: ${e.localizedMessage ?: "Error"}"
                }
            } finally {
                if (manualTrigger) {
                    _isCheckingForUpdate.value = false
                }
            }
        }
    }

    fun openUpdateDialog() {
        _updateDismissedForSession.value = false
        _showUpdateDialogExplicitly.value = true
    }

    fun dismissUpdateDialog() {
        _showUpdateDialogExplicitly.value = false
        _updateDismissedForSession.value = true
    }

    fun loadAppInstallationsForAdmin() {
        val appCtx = getApplication<Application>()
        viewModelScope.launch {
            runCatching {
                AdminCloudDatabaseRepository(appCtx).listAppInstallationsForAdmin()
                    .onSuccess { list -> _appInstallations.value = list }
            }
        }
    }

    fun publishAppUpdateAsAdmin(
        latestVersionCode: Long,
        latestVersionName: String,
        apkDownloadUrl: String,
        releaseNotes: String,
        isForceUpdate: Boolean,
        isUpdateEnabled: Boolean,
        targetUsername: String = "",
        alsoBroadcastAnnouncement: Boolean = true
    ) {
        val appCtx = getApplication<Application>()
        viewModelScope.launch {
            _isPublishingUpdate.value = true
            _updatePublishMessage.value = null
            try {
                val cloudRepo = AdminCloudDatabaseRepository(appCtx)
                val cleanTarget = targetUsername.trim().removePrefix("@")
                val res = cloudRepo.publishAppUpdateAsAdmin(
                    latestVersionCode = latestVersionCode,
                    latestVersionName = latestVersionName,
                    apkDownloadUrl = apkDownloadUrl,
                    releaseNotes = releaseNotes,
                    isForceUpdate = isForceUpdate,
                    isUpdateEnabled = isUpdateEnabled,
                    targetUsername = cleanTarget
                )
                res.onSuccess {
                    if (alsoBroadcastAnnouncement && isUpdateEnabled) {
                        val scopeLabel = if (cleanTarget.isBlank()) "all users" else "@$cleanTarget"
                        val noticeMsg = buildString {
                            append("App update v$latestVersionName (Build $latestVersionCode) has been released for $scopeLabel. ")
                            if (releaseNotes.isNotBlank()) {
                                append(releaseNotes.trim())
                            }
                            append(" Open Settings → App Updates or tap the Update banner on Live tab to install.")
                        }
                        runCatching {
                            cloudRepo.publishAnnouncementAsAdmin(
                                title = "Update v$latestVersionName Available",
                                message = noticeMsg,
                                priority = if (isForceUpdate) "urgent" else "high"
                            )
                        }
                        refreshAnnouncements()
                    }
                    cloudRepo.getAppUpdateConfig().onSuccess { updatedCfg ->
                        if (updatedCfg != null) _appUpdateConfig.value = updatedCfg
                    }
                    cloudRepo.listUpdatesHistory().onSuccess { history ->
                        _updatesHistory.value = history
                    }
                    loadAppInstallationsForAdmin()
                    val audienceLabel = if (cleanTarget.isBlank()) "all users" else "@$cleanTarget"
                    _updatePublishMessage.value = if (isUpdateEnabled) {
                        "Saved in /updates database & published v$latestVersionName (Build $latestVersionCode) for $audienceLabel."
                    } else {
                        "Saved release v$latestVersionName in /updates database (prompt disabled)."
                    }
                }.onFailure { err ->
                    val rawErr = err.localizedMessage ?: err.message ?: "Sign in with Admin Cloud account first"
                    _updatePublishMessage.value = "Publish failed: ${BrandSanitizer.sanitizeForUserDisplay(rawErr)}"
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _updatePublishMessage.value = "Publish failed: ${e.localizedMessage ?: "Error"}"
            } finally {
                _isPublishingUpdate.value = false
            }
        }
    }

    fun selectContract(contractId: String) {
        _selectedContractId.value = contractId
        refreshPrediction()
    }

    fun refreshPrediction() {
        val current = selectedContract.value ?: contracts.value.firstOrNull { it.id == selectedContractId.value }
        val feed = activeVijayFeed.value
        val pred = jashxEngine.calculateLivePrediction(current, feed)
        _livePrediction.value = pred
    }

    fun setPriceAlert(contractId: String, targetPrice: Double, isAbove: Boolean) {
        repository.addPriceAlert(contractId, targetPrice, isAbove)
    }

    fun removePriceAlert(alertId: String) {
        repository.removePriceAlert(alertId)
    }

    private var activeAnalysisJob: kotlinx.coroutines.Job? = null
    private var activeChatJob: kotlinx.coroutines.Job? = null

    fun requestAiAnalysis() {
        activeAnalysisJob?.cancel()
        _aiErrorMessage.value = null

        val feed = activeVijayFeed.value
        val open = isMarketOpen.value
        val statusText = marketStatusText.value

        activeAnalysisJob = viewModelScope.launch {
            try {
                jashxEngine.streamMarketAnalysis(
                    feed = feed,
                    isMarketOpen = open,
                    sessionText = statusText
                ).collect { chunk ->
                    _aiAnalysisText.value = chunk
                }
                refreshPrediction()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("McxViewModel", "JASHX AI analysis error", e)
            }
        }
    }

    fun askAiQuestion(question: String) {
        val cleanQuestion = question.trim()
        if (cleanQuestion.isBlank()) return

        // Cancel any previous in-flight chat job so the new question is answered immediately
        activeChatJob?.cancel()
        _isGeneratingAi.value = true
        _aiErrorMessage.value = null

        val feed = activeVijayFeed.value
        val user = com.example.util.UserAccountManager.currentUser.value

        val userMsg = ChatMessage(text = cleanQuestion, isFromUser = true)
        val baseChat = _chatMessages.value
            .filterNot { !it.isFromUser && it.text == "Thinking..." }
            .toMutableList()
            .apply { add(userMsg) }

        // Instant check so if we already have a sub-10ms answer, it shows with zero "Thinking..." flicker
        val instantPreview = com.example.data.JashxKnowledgeDatabase.matchDirectRelevantFeed(cleanQuestion, feed)
        if (instantPreview != null) {
            _aiAnalysisText.value = instantPreview
            _chatMessages.value = baseChat + ChatMessage(text = instantPreview, isFromUser = false)
        } else {
            _chatMessages.value = baseChat + ChatMessage(text = "Thinking...", isFromUser = false)
        }

        activeChatJob = viewModelScope.launch {
            try {
                var latestAnswer = instantPreview ?: ""
                jashxEngine.chatWithJashxAi(
                    history = baseChat,
                    userMessage = cleanQuestion,
                    feed = feed
                ).collect { streamedAnswer ->
                    latestAnswer = streamedAnswer
                    _aiAnalysisText.value = streamedAnswer
                    val updatedList = baseChat.toMutableList().apply {
                        add(ChatMessage(text = streamedAnswer, isFromUser = false))
                    }
                    _chatMessages.value = updatedList
                }

                if (user != null && latestAnswer.isNotBlank()) {
                    com.example.util.UserAccountManager.saveUserChat(user.id, _chatMessages.value)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("McxViewModel", "JASHX AI chat error", e)
                _aiErrorMessage.value = BrandSanitizer.sanitizeForUserDisplay("JASHX AI: ${e.message ?: "Unable to connect"}")
            } finally {
                _isGeneratingAi.value = false
            }
        }
    }

    fun startLiveVoice() {
        liveVoiceManager.updateLiveTelemetry(
            feed = activeVijayFeed.value,
            isMarketOpen = isMarketOpen.value,
            marketStatusText = marketStatusText.value,
            prediction = _livePrediction.value
        )
        liveVoiceManager.startVoiceSession()
    }

    fun stopLiveVoice() {
        liveVoiceManager.stopVoiceSession()
    }

    fun selectVoicePersona(persona: JashxVoicePersona) {
        liveVoiceManager.selectPersona(persona)
    }

    fun clearLiveVoiceError() {
        liveVoiceManager.clearError()
    }

    private fun appendVoiceTurnToChat(userText: String, aiText: String) {
        val cleanUser = BrandSanitizer.sanitizeForUserDisplay(userText).trim()
        val cleanAi = BrandSanitizer.sanitizeForUserDisplay(aiText).trim()
        if (cleanUser.isBlank() && cleanAi.isBlank()) return

        val additions = buildList {
            if (cleanUser.isNotBlank()) add(ChatMessage(text = "🎙️ $cleanUser", isFromUser = true))
            if (cleanAi.isNotBlank()) add(ChatMessage(text = cleanAi, isFromUser = false))
        }
        val updated = (_chatMessages.value + additions).takeLast(40)
        _chatMessages.value = updated
        if (cleanAi.isNotBlank()) {
            _aiAnalysisText.value = cleanAi
        }
        val user = com.example.util.UserAccountManager.currentUser.value
        if (user != null) {
            com.example.util.UserAccountManager.saveUserChat(user.id, updated)
        }
    }

    override fun onCleared() {
        super.onCleared()
        liveVoiceManager.stopVoiceSession()
    }
}
