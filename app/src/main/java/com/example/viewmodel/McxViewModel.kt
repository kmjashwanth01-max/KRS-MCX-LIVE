package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.JashxAiEngine
import com.example.data.McxRatesRepository
import com.example.data.VijayBullionFeed
import com.example.model.BullionCityRate
import com.example.model.ChatMessage
import com.example.model.CommodityContract
import com.example.model.MarketPrediction
import com.example.data.repository.AdminCloudDatabaseRepository
import com.example.data.repository.CloudAnnouncement
import com.example.model.OrderBookEntry
import com.example.model.PricePoint
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
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

    val contracts: StateFlow<List<CommodityContract>> = repository.contracts
    val activeVijayFeed: StateFlow<VijayBullionFeed> = repository.activeVijayFeed
    val historyMap: StateFlow<Map<String, List<PricePoint>>> = repository.historyMap
    val orderBook: StateFlow<List<OrderBookEntry>> = repository.orderBook
    val cityRates: StateFlow<List<BullionCityRate>> = repository.cityRates
    val isMarketOpen: StateFlow<Boolean> = repository.isMarketOpen
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

    // Official Announcements passed from the separate Admin Firestore Database
    private val _announcements = MutableStateFlow<List<CloudAnnouncement>>(emptyList())
    val announcements: StateFlow<List<CloudAnnouncement>> = _announcements.asStateFlow()
    private var announcementsJob: Job? = null

    init {
        // Automatically trigger initial JASHX AI commentary & prediction
        requestAiAnalysis()
        refreshPrediction()
        observeCloudAnnouncementsIfAuthenticated(application)

        // Continuously update JASHX AI with the latest 1-second Vijay Bullion feeds
        viewModelScope.launch {
            activeVijayFeed.collect { feed ->
                val current = selectedContract.value ?: contracts.value.firstOrNull { it.id == selectedContractId.value }
                val pred = jashxEngine.calculateLivePrediction(current, feed)
                _livePrediction.value = pred
            }
        }

        // Isolate chat history per individual user profile
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
                }
            }
        }
    }

    private fun observeCloudAnnouncementsIfAuthenticated(application: Application?) {
        if (application == null) return
        try {
            val auth = Firebase.auth
            val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                announcementsJob?.cancel()
                if (firebaseAuth.currentUser != null) {
                    announcementsJob = viewModelScope.launch {
                        try {
                            val cloudRepo = AdminCloudDatabaseRepository(application.applicationContext)
                            cloudRepo.observeActiveAnnouncements().collect { list ->
                                _announcements.value = list
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            android.util.Log.w("McxViewModel", "Announcements stream error: ${e.message}")
                        }
                    }
                } else {
                    _announcements.value = emptyList()
                }
            }
            auth.addAuthStateListener(listener)
        } catch (_: Throwable) {}
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
                _aiErrorMessage.value = "JASHX AI: ${e.message}"
            } finally {
                _isGeneratingAi.value = false
            }
        }
    }
}
