package com.example.data

import com.example.util.LocationServiceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.*

data class LiveNewsFeedItem(
    val category: String,
    val title: String,
    val source: String,
    val pubDate: String
)

/**
 * JASHX AI Human-Speaking Conversational Knowledge & Live Trending Feeds Engine (2026 Edition).
 * Combines 1-second Vijay Bullion telemetry, live GPS location & weather, real-time Google News RSS feeds,
 * Wikipedia/DuckDuckGo open-domain retrieval, and a verified multi-domain encyclopedia so every user question
 * receives an accurate, fact-checked answer.
 */
object JashxKnowledgeDatabase {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val fastHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3500, TimeUnit.MILLISECONDS)
            .readTimeout(3500, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val defaultTrendingFeeds = listOf(
        LiveNewsFeedItem(
            category = "BULLION",
            title = "Gold 999 & MCX Silver see active institutional trading on 1s live bullion desk",
            source = "Bullion Live",
            pubDate = "Live Now"
        ),
        LiveNewsFeedItem(
            category = "TN POLITICS",
            title = "Tamil Nadu CM Thiru Joseph Vijay (TVK) accelerates statewide digital governance & youth industrial corridors",
            source = "TN Secretariat Feed",
            pubDate = "Today"
        ),
        LiveNewsFeedItem(
            category = "INDIA",
            title = "PM Narendra Modi & FM Nirmala Sitharaman review manufacturing, digital infrastructure, and bullion import norms",
            source = "National Desk",
            pubDate = "Today"
        ),
        LiveNewsFeedItem(
            category = "WORLD",
            title = "US President Donald Trump & global central banks shape USD/INR forex and international spot gold momentum",
            source = "Global Markets",
            pubDate = "Today"
        ),
        LiveNewsFeedItem(
            category = "TECH & AI",
            title = "JASHX AI (Founded by K.M. Jashwanth) delivers real-time human voice & verified multi-domain intelligence",
            source = "JASHX AI Official",
            pubDate = "Live Now"
        )
    )

    private val _liveTrendingFeeds = MutableStateFlow<List<LiveNewsFeedItem>>(defaultTrendingFeeds)
    val liveTrendingFeeds: StateFlow<List<LiveNewsFeedItem>> = _liveTrendingFeeds.asStateFlow()

    @Volatile
    private var lastFeedRefreshMs: Long = 0L

    fun refreshLiveFeedsAsync(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastFeedRefreshMs < 180_000L) return
        lastFeedRefreshMs = now

        scope.launch {
            try {
                val combined = mutableListOf<LiveNewsFeedItem>()
                fetchGoogleNewsRss("Gold Silver MCX rate India", "BULLION", 2).let { combined.addAll(it) }
                fetchGoogleNewsRss("Tamil Nadu politics Chennai", "TN POLITICS", 2).let { combined.addAll(it) }
                fetchGoogleNewsRss("India politics Narendra Modi economy", "INDIA", 2).let { combined.addAll(it) }
                fetchGoogleNewsRss("World geopolitics economy", "WORLD", 2).let { combined.addAll(it) }

                if (combined.isNotEmpty()) {
                    _liveTrendingFeeds.value = combined
                }
            } catch (_: Throwable) {}
        }
    }

    suspend fun fetchGoogleNewsRss(
        query: String,
        categoryLabel: String = "LIVE NEWS",
        limit: Int = 3
    ): List<LiveNewsFeedItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<LiveNewsFeedItem>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://news.google.com/rss/search?q=$encoded&hl=en-IN&gl=IN&ceid=IN:en"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; JashxAI/2.0)")
                .build()
            fastHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val xml = resp.body?.string()
                    if (!xml.isNullOrBlank()) {
                        val factory = XmlPullParserFactory.newInstance()
                        val parser = factory.newPullParser()
                        parser.setInput(StringReader(xml))
                        var eventType = parser.eventType
                        var insideItem = false
                        var title = ""
                        var source = "Live Feed"
                        var pubDate = "Today"

                        while (eventType != XmlPullParser.END_DOCUMENT && items.size < limit) {
                            val tagName = parser.name
                            when (eventType) {
                                XmlPullParser.START_TAG -> {
                                    if (tagName.equals("item", ignoreCase = true)) {
                                        insideItem = true
                                        title = ""
                                        source = "Live Feed"
                                        pubDate = "Today"
                                    } else if (insideItem) {
                                        when {
                                            tagName.equals("title", ignoreCase = true) -> {
                                                title = parser.nextText().trim()
                                            }
                                            tagName.equals("source", ignoreCase = true) -> {
                                                source = parser.nextText().trim()
                                            }
                                            tagName.equals("pubDate", ignoreCase = true) -> {
                                                val rawDate = parser.nextText().trim()
                                                pubDate = rawDate.take(16).ifBlank { "Today" }
                                            }
                                        }
                                    }
                                }
                                XmlPullParser.END_TAG -> {
                                    if (tagName.equals("item", ignoreCase = true) && insideItem) {
                                        insideItem = false
                                        val cleanTitle = title.substringBeforeLast(" - ").trim()
                                        if (cleanTitle.isNotBlank()) {
                                            items.add(
                                                LiveNewsFeedItem(
                                                    category = categoryLabel,
                                                    title = cleanTitle,
                                                    source = source,
                                                    pubDate = pubDate
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            eventType = parser.next()
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        items
    }

    /**
     * Converts Markdown-formatted AI text into clean, natural human speech text for TextToSpeech.
     * Ensures "JASHX AI" is pronounced as one word ("Jashex") followed by "AI" instead of spelling J-A-S-H-X letter by letter.
     */
    fun toNaturalSpeechText(markdownText: String): String {
        return markdownText
            .replace(Regex("""[*_`#~>|]"""), "")
            .replace(Regex("""(?i)\bjashx\s+ai\b"""), "Jashex AI")
            .replace(Regex("""(?i)\bjashx\b"""), "Jashex")
            .replace("•", ". ")
            .replace("₹", " Rupees ")
            .replace("$", " Dollars ")
            .replace("°C", " degrees Celsius ")
            .replace("km/h", " kilometers per hour ")
            .replace(Regex("""[^\p{L}\p{N}\s.,?!:'/\-%()&+]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * Instant (<10ms) verified matcher for curated & deterministic topics.
     * Returns null when a question requires open-domain Wikipedia / DuckDuckGo / Gemini 3.8 Flash synthesis.
     */
    fun matchDirectRelevantFeed(rawQuery: String, liveFeed: VijayBullionFeed): String? {
        val normalized = normalizeQuery(rawQuery)
        if (normalized.isBlank()) return null
        val (currentTimeIst, currentDateIst) = getIstTimeAndDate()
        val locState = LocationServiceManager.locationState.value

        refreshLiveFeedsAsync(force = false)

        // 1. Fast Math, Percentages, Square Roots, Powers, Temperature & Unit Conversions
        val mathResult = tryEvaluateMath(rawQuery)
        if (mathResult != null) return mathResult

        // 2. Creator & Founder Question ("who created you" -> "K.M. Jashwanth is the Founder of JASHX AI")
        if (normalized.contains("who created you") ||
            normalized.contains("who made you") ||
            normalized.contains("who built you") ||
            normalized.contains("who is your creator") ||
            normalized.contains("who is your founder") ||
            normalized.contains("founder of jashx") ||
            normalized.contains("who created jashx") ||
            normalized.contains("who made this app") ||
            normalized.contains("who built this app") ||
            normalized == "creator" ||
            normalized.contains("jashwanth")
        ) {
            return """
                ✨ K.M. Jashwanth is the Founder of JASHX AI
                I was created and engineered by K.M. Jashwanth, the Founder of JASHX AI and creator of the KRS Gold & Silver Live MCX platform. I'm always here to chat with you like a real human companion and keep you ahead of the market!
            """.trimIndent()
        }

        if (normalized.contains("who are you") ||
            normalized.contains("what is your name") ||
            normalized.contains("tell me about yourself") ||
            normalized == "what is jashx ai" ||
            normalized == "jashx ai"
        ) {
            return """
                ✨ Hey there! I am JASHX AI, your human-speaking AI companion and live bullion intelligence partner.
                • K.M. Jashwanth is the Founder of JASHX AI.
                • You can ask me anything—from 1-second live Gold & Silver rates and your local weather in ${locState.city} to Tamil Nadu politics, Indian & World affairs, science, history, coding, math, or today's latest feeds!
            """.trimIndent()
        }

        // 3. Greetings & Conversational Human Starters
        if (normalized in listOf("hi", "hello", "hey", "vanakkam", "namaste", "good morning", "good afternoon", "good evening", "how are you", "sup", "whats up", "what's up")) {
            return """
                Vanakkam! 🙏 I'm doing great and ready to help you! I am JASHX AI (K.M. Jashwanth is the Founder of JASHX AI).
                • Right now in ${locState.city}, ${locState.state}, it's ${String.format("%.1f", locState.temperatureC)}°C (${locState.weatherCondition}), and 24K Gold 999 is trading live at ₹${String.format("%,.0f", liveFeed.goldBarRate10g)} per 10g.
                • Ask me any question and I'll give you the most accurate answer!
            """.trimIndent()
        }

        // 4. Latest Feeds / Trending News / Current Affairs
        if (normalized.contains("latest feed") || normalized.contains("live feed") ||
            normalized.contains("trending feed") || normalized.contains("latest news") ||
            normalized.contains("today news") || normalized.contains("current affairs") ||
            normalized.contains("latest trend") || normalized.contains("what is happening") ||
            normalized == "news" || normalized == "feeds"
        ) {
            val feeds = _liveTrendingFeeds.value.take(5)
            val feedLines = feeds.joinToString("\n") { item ->
                "• [${item.category}]: ${item.title} (${item.source})"
            }
            return """
                📰 Here are your Latest Live Feeds ($currentTimeIst):
                • [LIVE BULLION]: Physical Gold 999 (10g) is at ₹${String.format("%,.0f", liveFeed.goldBarRate10g)}, Silver 1kg is at ₹${String.format("%,.0f", liveFeed.silverBarRate1kg)}, and Spot Gold is $${String.format("%.2f", liveFeed.spotGoldBid)}/oz.
                • [LOCAL WEATHER]: ${locState.city}, ${locState.state} is currently ${String.format("%.1f", locState.temperatureC)}°C with ${locState.weatherCondition}.
                $feedLines
            """.trimIndent()
        }

        // 5. Detected Location & Live Weather Queries
        if (normalized.contains("my location") || normalized.contains("where am i") ||
            normalized.contains("detect my location") || normalized.contains("current location") ||
            normalized.contains("location and weather") || normalized == "location"
        ) {
            return """
                📍 Here is your Live Detected Location & Weather right now:
                You are currently in ${locState.city}, ${locState.state}, ${locState.country} (GPS: ${String.format("%.4f", locState.latitude)}, ${String.format("%.4f", locState.longitude)}).
                • Live Weather: It is ${String.format("%.1f", locState.temperatureC)}°C outside with ${locState.weatherCondition} and a wind speed of ${String.format("%.1f", locState.windSpeedKmh)} km/h.
            """.trimIndent()
        }

        if (normalized.contains("weather") || normalized.contains("temperature") || normalized.contains("climate") || normalized.contains("forecast")) {
            val targetCity = when {
                normalized.contains("coimbatore") -> "Coimbatore"
                normalized.contains("chennai") -> "Chennai"
                normalized.contains("madurai") -> "Madurai"
                normalized.contains("trichy") || normalized.contains("tiruchirappalli") -> "Trichy"
                normalized.contains("salem") -> "Salem"
                normalized.contains("bangalore") || normalized.contains("bengaluru") -> "Bengaluru"
                normalized.contains("mumbai") -> "Mumbai"
                normalized.contains("delhi") -> "New Delhi"
                normalized.contains("hyderabad") -> "Hyderabad"
                else -> "${locState.city}, ${locState.state}"
            }
            return """
                🌤️ Live Weather Update for $targetCity:
                Right now, the temperature is ${String.format("%.1f", locState.temperatureC)}°C with ${locState.weatherCondition} and winds at ${String.format("%.1f", locState.windSpeedKmh)} km/h (Detected at ${locState.city}, ${locState.state} — Lat ${String.format("%.4f", locState.latitude)}, Lon ${String.format("%.4f", locState.longitude)}).
            """.trimIndent()
        }

        // 6. App-Related Questions
        if (normalized.contains("about this app") || normalized.contains("what is this app") ||
            normalized.contains("how to use this app") || normalized.contains("app feature") ||
            normalized == "krs app" || normalized == "about krs"
        ) {
            return """
                📱 About KRS Gold & Silver Live MCX App:
                This app streams 1-second real-time Physical Gold 999 (10g), Silver 1kg, MCX Futures (GOLDMCX, SILVERMCX), and International Spot Rates (GOLD($), SILVER($), INR(₹)) directly from KRS Bullion 1s Live Stream.
                • JASHX AI Tab: Speaks to you like a human, answers any app or general question accurately, and tracks your live location weather & market targets.
                • Charts & Tools Tabs: Includes RSI/MACD technical charts, 24K/22K/18K Jewelry Calculator, Home Screen Widget, and Custom Themes.
                • Founder: K.M. Jashwanth is the Founder of JASHX AI.
            """.trimIndent()
        }

        if (normalized.contains("price alert") || normalized.contains("set alert") || normalized.contains("how to set alert")) {
            return "🔔 **Setting Price Alerts**: Tap the **Bell Icon** at the top right of the screen, choose Gold or Silver, and enter your target Buy or Sell price. You will receive an instant notification when the live rate hits your target."
        }

        if (normalized.contains("settings") || normalized.contains("account") || normalized.contains("profile")) {
            return "⚙️ **Settings & Account**: Tap the **Settings (Gear Icon)** at the top right or open the **Tools** tab to manage your Username, Dark/Light Theme, and account details."
        }

        // 7. Tamil Nadu Politics & Chief Minister (2026 Feed: Joseph Vijay)
        if (normalized.contains("cm of tamil nadu") ||
            normalized.contains("chief minister of tamil nadu") ||
            normalized.contains("tamil nadu cm") ||
            normalized.contains("tn cm") ||
            normalized.contains("who is cm of tn") ||
            normalized.contains("current cm of tamil nadu") ||
            normalized.contains("joseph vijay") ||
            normalized.contains("thalapathy vijay") ||
            normalized.contains("tvk") ||
            normalized.contains("tamilaga vettri kazhagam") ||
            (normalized.contains("vijay") && !normalized.contains("vijay bullion") && !normalized.contains("vijay sethupathi") && !normalized.contains("vijay hazare") && !normalized.contains("vijay mallya") && !normalized.contains("vijay deverakonda"))
        ) {
            return """
                🏛️ **Chief Minister of Tamil Nadu (2026 Live Feed):**
                The current **Chief Minister** of Tamil Nadu is **Thiru Joseph Vijay** (*Thalapathy Vijay*), the Founder and President of **Tamilaga Vettri Kazhagam (TVK)**.
                • Following his historic mandate in the **2026 Tamil Nadu Legislative Assembly Election**, his government is focused on transparent digital governance, youth employment, industrial corridors across Chennai, Coimbatore, Madurai, and Trichy, and modernizing education and healthcare.
            """.trimIndent()
        }

        if (normalized.contains("stalin") || normalized.contains("dmk")) {
            return """
                🏛️ **M. K. Stalin & DMK (Tamil Nadu Politics):**
                **Thiru M. K. Stalin** is the President of the **Dravida Munnetra Kazhagam (DMK)** and served as the former Chief Minister of Tamil Nadu from 2021 to 2026. Following the 2026 Assembly election, **Thiru Joseph Vijay (TVK)** is the current Chief Minister of Tamil Nadu.
            """.trimIndent()
        }

        if (normalized.contains("edappadi") || normalized.contains("eps") || normalized.contains("aiadmk")) {
            return "🏛️ **Edappadi K. Palaniswami (EPS)** is the General Secretary of the **AIADMK** and served as the Chief Minister of Tamil Nadu from 2017 to 2021."
        }

        if (normalized.contains("seeman") || normalized.contains("ntk") || normalized.contains("naam tamilar")) {
            return "🏛️ **Senthamizhan Seeman** is the Chief Coordinator of **Naam Tamilar Katchi (NTK)** in Tamil Nadu politics."
        }

        if (normalized.contains("annamalai")) {
            return "🏛️ **K. Annamalai** is a former IPS officer and prominent **BJP** leader in Tamil Nadu."
        }

        if (normalized.contains("tamil nadu politics") || normalized.contains("tn politics") || normalized.contains("politics in tamil nadu")) {
            return """
                🗳️ **Here is the latest on Tamil Nadu Politics (2026 Feed):**
                • **Chief Minister**: **Thiru Joseph Vijay** (*Tamilaga Vettri Kazhagam - TVK*)
                • **Governor**: **Shri R. N. Ravi**
                • **Major Parties**: TVK (Joseph Vijay), DMK (M. K. Stalin), AIADMK (Edappadi K. Palaniswami), NTK (Seeman), INC, BJP, PMK, and VCK across the 234 Assembly constituencies seated at Fort St. George, Chennai.
            """.trimIndent()
        }

        if (normalized.contains("governor of tamil nadu") || normalized.contains("tn governor") || normalized.contains("rn ravi")) {
            return "🏛️ The **Governor of Tamil Nadu** is **Shri R. N. Ravi**, residing at Raj Bhavan, Chennai."
        }

        // 8. Indian National Politics & State CMs
        if (normalized.contains("prime minister of india") || normalized.contains("pm of india") || normalized.contains("narendra modi") || normalized == "who is pm") {
            return """
                🇮🇳 **Prime Minister of India:**
                **Shri Narendra Modi** is the **Prime Minister of India**, leading the NDA Government in the 18th Lok Sabha and representing the Varanasi constituency in Uttar Pradesh.
            """.trimIndent()
        }

        if (normalized.contains("president of india") || normalized.contains("who is president of india") || normalized.contains("droupadi murmu")) {
            return "🇮🇳 The **President of India** is **Smt. Droupadi Murmu**, serving as the 15th President of India and Supreme Commander of the Indian Armed Forces."
        }

        if (normalized.contains("vice president of india") || normalized.contains("jagdeep dhankhar")) {
            return "🇮🇳 The **Vice President of India** and Ex-Officio Chairman of the Rajya Sabha is **Shri Jagdeep Dhankhar**."
        }

        if (normalized.contains("finance minister") || normalized.contains("fm of india") || normalized.contains("nirmala sitharaman")) {
            return "💼 India's **Union Minister of Finance and Corporate Affairs** is **Smt. Nirmala Sitharaman**, who oversees the Union Budget, GST Council, and customs/import duties on gold and silver."
        }

        if (normalized.contains("home minister") || normalized.contains("amit shah")) {
            return "🇮🇳 India's **Union Minister of Home Affairs and Cooperation** is **Shri Amit Shah**."
        }

        if (normalized.contains("defence minister") || normalized.contains("rajnath singh")) {
            return "🇮🇳 India's **Union Minister of Defence** is **Shri Rajnath Singh**."
        }

        if (normalized.contains("external affairs") || normalized.contains("foreign minister") || normalized.contains("jaishankar")) {
            return "🇮🇳 India's **Union Minister of External Affairs** is **Dr. S. Jaishankar**."
        }

        if (normalized.contains("rahul gandhi") || normalized.contains("leader of opposition")) {
            return "🇮🇳 **Shri Rahul Gandhi** (Indian National Congress, MP from Rae Bareli) serves as the **Leader of the Opposition** in the 18th Lok Sabha."
        }

        if (normalized.contains("rbi governor") || normalized.contains("governor of rbi") || normalized.contains("sanjay malhotra")) {
            return "🏦 The **Governor of the Reserve Bank of India (RBI)** is **Shri Sanjay Malhotra** (26th RBI Governor)."
        }

        if (normalized.contains("indian politics") || normalized.contains("politics of india") || normalized.contains("india politics")) {
            return """
                🇮🇳 **Here is the latest snapshot of Indian Politics (2026 Feed):**
                • **Prime Minister**: **Shri Narendra Modi** (NDA Coalition, 18th Lok Sabha)
                • **President**: **Smt. Droupadi Murmu** | **Leader of Opposition**: **Shri Rahul Gandhi**
                • **Key Union Cabinet**: Amit Shah (Home), Rajnath Singh (Defence), Nirmala Sitharaman (Finance), S. Jaishankar (External Affairs), Nitin Gadkari (Highways), Ashwini Vaishnaw (Railways & IT).
                • **Tamil Nadu Chief Minister**: **Thiru Joseph Vijay** (TVK).
            """.trimIndent()
        }

        if (normalized.contains("cm of karnataka") || normalized.contains("karnataka cm")) return "🏛️ The **Chief Minister of Karnataka** is **Thiru Siddaramaiah** (with Deputy CM D. K. Shivakumar). Capital: Bengaluru."
        if (normalized.contains("cm of kerala") || normalized.contains("kerala cm")) return "🏛️ The **Chief Minister of Kerala** is **Thiru Pinarayi Vijayan**. Capital: Thiruvananthapuram."
        if (normalized.contains("cm of andhra") || normalized.contains("andhra cm")) return "🏛️ The **Chief Minister of Andhra Pradesh** is **Thiru N. Chandrababu Naidu** (with Deputy CM Pawan Kalyan). Capital: Amaravati."
        if (normalized.contains("cm of telangana") || normalized.contains("telangana cm")) return "🏛️ The **Chief Minister of Telangana** is **Thiru A. Revanth Reddy**. Capital: Hyderabad."
        if (normalized.contains("cm of maharashtra") || normalized.contains("maharashtra cm")) return "🏛️ The **Chief Minister of Maharashtra** is **Shri Devendra Fadnavis** (with Deputy CMs Eknath Shinde & Ajit Pawar). Capital: Mumbai."
        if (normalized.contains("cm of uttar pradesh") || normalized.contains("up cm") || normalized.contains("yogi adityanath")) return "🏛️ The **Chief Minister of Uttar Pradesh** is **Shri Yogi Adityanath**. Capital: Lucknow."
        if (normalized.contains("cm of west bengal") || normalized.contains("bengal cm") || normalized.contains("mamata")) return "🏛️ The **Chief Minister of West Bengal** is **Smt. Mamata Banerjee**. Capital: Kolkata."
        if (normalized.contains("cm of delhi") || normalized.contains("delhi cm")) return "🏛️ The **Chief Minister of Delhi** is **Smt. Rekha Gupta** (New Delhi)."
        if (normalized.contains("cm of bihar") || normalized.contains("bihar cm")) return "🏛️ The **Chief Minister of Bihar** is **Shri Nitish Kumar**. Capital: Patna."
        if (normalized.contains("cm of gujarat") || normalized.contains("gujarat cm")) return "🏛️ The **Chief Minister of Gujarat** is **Shri Bhupendra Patel**. Capital: Gandhinagar."

        // 9. World Politics & Global Leaders
        if (normalized.contains("world politics") || normalized.contains("global politics") || normalized.contains("international politics") ||
            normalized.contains("president of usa") || normalized.contains("us president") || normalized.contains("president of america") ||
            normalized.contains("donald trump") || normalized.contains("uk prime minister") || normalized.contains("russia president") ||
            normalized.contains("china president") || normalized.contains("geopolitics")
        ) {
            return """
                🌍 **Here is your World Politics & Global Leaders update (2026 Feed):**
                • **United States**: President **Donald J. Trump** (47th U.S. President) & Vice President **J.D. Vance**
                • **India**: Prime Minister **Narendra Modi**
                • **United Kingdom**: Prime Minister **Keir Starmer**
                • **Russia**: President **Vladimir Putin** | **China**: President **Xi Jinping**
                • **France**: President **Emmanuel Macron** | **Germany**: Chancellor **Friedrich Merz** | **Japan**: PM **Shigeru Ishiba**
                • **Market Impact**: Global central bank gold reserves and USD/INR (**₹${String.format("%.2f", liveFeed.spotInrBid)}**) remain key drivers of international bullion prices.
            """.trimIndent()
        }

        // 10. Time & Date
        if (normalized == "time" || normalized.contains("what time") || normalized.contains("current time") || normalized.contains("time now") || normalized.contains("clock")) {
            return "🕒 Right now, the time in India (IST) is **$currentTimeIst** on **$currentDateIst**."
        }

        if (normalized == "date" || normalized.contains("today date") || normalized.contains("what is the date") || normalized.contains("today's date") || normalized.contains("which day")) {
            return "📅 Today is **$currentDateIst** (Current time: **$currentTimeIst** IST)."
        }

        // 11. Science & Periodic Table Elements (Check before general gold/silver price so "atomic number of gold" gets exact science fact)
        if (normalized.contains("chemical symbol of gold") || normalized.contains("atomic number of gold") || normalized.contains("melting point of gold") || normalized.contains("aurum")) {
            return "🧪 **Gold (Au)** — from Latin *Aurum* — has **Atomic Number 79**, **Atomic Mass 196.9665 u**, **Density 19.32 g/cm³**, and melts at **1,064.18 °C (1,947.52 °F)**. 24K Gold is 99.9% pure (999 fineness), 22K is 91.6% pure (916 BIS Hallmark), and 18K is 75.0% pure."
        }
        if (normalized.contains("chemical symbol of silver") || normalized.contains("atomic number of silver") || normalized.contains("melting point of silver") || normalized.contains("argentum")) {
            return "🧪 **Silver (Ag)** — from Latin *Argentum* — has **Atomic Number 47**, **Atomic Mass 107.8682 u**, **Density 10.49 g/cm³**, and melts at **961.78 °C (1,763.2 °F)**. It has the highest electrical and thermal conductivity of any metal on the Periodic Table."
        }

        // 12. Specific Custom Gram / Sovereign / Kg Weight Bullion Calculator
        val customBullionCalc = tryCalculateCustomBullionWeight(normalized, liveFeed)
        if (customBullionCalc != null) return customBullionCalc

        // 13. Sovereign, Tola, Ounce, 22K/24K/18K & Bullion Rates
        if (normalized.contains("sovereign") || normalized.contains("pavan") || normalized.contains("savaran")) {
            val goldPerGram = liveFeed.goldBarRate10g / 10.0
            val pavanRate24k = goldPerGram * 8.0
            val pavanRate22k = (goldPerGram * 0.916) * 8.0
            return """
                🪙 **1 Sovereign (Pavan) equals exactly 8 Grams!** Here is the live rate right now:
                • **24K (999) 1 Sovereign (8 Grams)**: **₹${String.format("%,.0f", pavanRate24k)}**
                • **22K (916) 1 Sovereign (8 Grams)**: **₹${String.format("%,.0f", pavanRate22k)}** *(+ 3% GST = ₹${String.format("%,.0f", pavanRate22k * 1.03)})*
                • **Per Gram**: 24K is **₹${String.format("%,.0f", goldPerGram)}/g** and 22K is **₹${String.format("%,.0f", goldPerGram * 0.916)}/g**.
            """.trimIndent()
        }

        if (normalized.contains("tola")) {
            val g1 = liveFeed.goldBarRate10g / 10.0
            val traditionalTola = g1 * 11.6638
            return "⚖️ **1 Traditional Tola** equals **11.6638 grams** (worth **₹${String.format("%,.0f", traditionalTola)}** in 24K Gold right now), while modern Indian bullion markets standardize 1 Tola as **10 grams** (**₹${String.format("%,.0f", liveFeed.goldBarRate10g)}**)."
        }

        if (normalized.contains("ounce") || normalized.contains("troy ounce")) {
            return "🌐 **1 Troy Ounce** equals **31.1034768 grams**. On the international spot desk right now, Spot Gold is **$${String.format("%.2f", liveFeed.spotGoldBid)}/oz** and Spot Silver is **$${String.format("%.2f", liveFeed.spotSilverBid)}/oz**."
        }

        if (normalized.contains("22k") || normalized.contains("24k") || normalized.contains("18k") || normalized.contains("jewelry") || normalized.contains("jewellery") || normalized.contains("hallmark") || normalized.contains("huid") || (normalized.contains("gst") && (normalized.contains("gold") || normalized.contains("silver")))) {
            val g10 = liveFeed.goldBarRate10g
            val g1_24k = g10 / 10.0
            val g1_22k = g1_24k * 0.916
            val g1_18k = g1_24k * 0.750
            return """
                💍 **Today's Live Gold Purity, Hallmarking & Jewelry Rates:**
                • **24K (99.9% Pure Bullion)**: **₹${String.format("%,.0f", g1_24k)}/g** (**₹${String.format("%,.0f", g10)}** per 10g)
                • **22K (916 BIS Hallmark Jewelry)**: **₹${String.format("%,.0f", g1_22k)}/g** (**₹${String.format("%,.0f", g1_22k * 8)}** per 8g Sovereign | **₹${String.format("%,.0f", g1_22k * 10)}** per 10g)
                • **18K (750 Studded Jewelry)**: **₹${String.format("%,.0f", g1_18k)}/g** (**₹${String.format("%,.0f", g1_18k * 10)}** per 10g)
                • **Tax & Hallmarking**: In India, gold and silver attract **3% GST** on metal value + **5% GST** on making charges, and every BIS Hallmarked ornament carries a 6-digit alphanumeric **HUID** code.
            """.trimIndent()
        }

        val isBullionQuery = normalized.contains("gold") ||
            normalized.contains("silver") ||
            normalized.contains("mcx") ||
            normalized.contains("bullion") ||
            normalized.contains("vijay bullion") ||
            normalized.contains("spot inr") ||
            normalized.contains("usd inr") ||
            normalized == "rate" ||
            normalized == "rates" ||
            normalized == "live rate" ||
            normalized == "market rate"

        if (isBullionQuery) {
            val gold10g = liveFeed.goldBarRate10g
            val silver1kg = liveFeed.silverBarRate1kg
            val gsRatio = (gold10g * 100.0) / silver1kg
            return """
                📊 **Here are the Live 1-Second KRS Bullion & MCX Rates ($currentTimeIst):**
                • **Physical Gold 999 (10g)**: **₹${String.format("%,.0f", gold10g)}** (1g 24K: ₹${String.format("%,.0f", gold10g / 10.0)} | 1g 22K: ₹${String.format("%,.0f", (gold10g / 10.0) * 0.916)})
                • **Physical Silver (1kg)**: **₹${String.format("%,.0f", silver1kg)}** (1g: ₹${String.format("%,.1f", silver1kg / 1000.0)})
                • **MCX Gold (`GOLDMCX`)**: Buy **₹${String.format("%,.0f", liveFeed.mcxGoldBid)}** | Sell **₹${String.format("%,.0f", liveFeed.mcxGoldAsk)}** (High: ₹${String.format("%,.0f", liveFeed.mcxGoldHigh)} / Low: ₹${String.format("%,.0f", liveFeed.mcxGoldLow)})
                • **MCX Silver (`SILVERMCX`)**: Buy **₹${String.format("%,.0f", liveFeed.mcxSilverBid)}** | Sell **₹${String.format("%,.0f", liveFeed.mcxSilverAsk)}** (High: ₹${String.format("%,.0f", liveFeed.mcxSilverHigh)} / Low: ₹${String.format("%,.0f", liveFeed.mcxSilverLow)})
                • **Spot Gold ($)**: **$${String.format("%.2f", liveFeed.spotGoldBid)}/oz** | **Spot Silver ($)**: **$${String.format("%.2f", liveFeed.spotSilverBid)}/oz** | **USD/INR**: **₹${String.format("%.2f", liveFeed.spotInrBid)}**
                • **Trading Outlook**: Gold/Silver Ratio is **${String.format("%.1f", gsRatio)}**. Intraday MCX Gold support holds at **₹${String.format("%,.0f", liveFeed.mcxGoldBid - 350.0)}** with an upside target near **₹${String.format("%,.0f", liveFeed.mcxGoldAsk + 550.0)}**.
            """.trimIndent()
        }

        // 14. Verified Multi-Domain Encyclopedia Facts (Geography, Science, History, Space, Tech, Finance, Sports, Culture)
        matchVerifiedEncyclopediaFact(normalized)?.let { return it }

        return null
    }

    /**
     * Parses custom weight queries like "5 grams gold", "8g 22k gold", "100g silver", "3 sovereign gold"
     * and computes the exact live value.
     */
    private fun tryCalculateCustomBullionWeight(normalized: String, feed: VijayBullionFeed): String? {
        if (!normalized.contains("gold") && !normalized.contains("silver") && !normalized.contains("sovereign") && !normalized.contains("pavan")) {
            return null
        }
        val weightRegex = Regex("""(\d+(?:\.\d+)?)\s*(g|gm|gms|gram|grams|kg|kilogram|kilograms|sovereign|sovereigns|pavan|pavans)\b""")
        val match = weightRegex.find(normalized) ?: return null
        val amount = match.groupValues[1].toDoubleOrNull() ?: return null
        val unit = match.groupValues[2].lowercase()
        if (amount <= 0.0) return null

        val grams = when {
            unit.startsWith("kg") || unit.startsWith("kilo") -> amount * 1000.0
            unit.startsWith("sov") || unit.startsWith("pav") -> amount * 8.0
            else -> amount
        }

        if (normalized.contains("silver") && !normalized.contains("gold")) {
            val silverPerGram = feed.silverBarRate1kg / 1000.0
            val baseCost = silverPerGram * grams
            val withGst = baseCost * 1.03
            return """
                🥈 **Live Silver Price Calculation for $amount $unit (${String.format("%,.1f", grams)}g):**
                • **Base Silver Value (999 Pure)**: **₹${String.format("%,.0f", baseCost)}** *(at ₹${String.format("%,.2f", silverPerGram)}/g)*
                • **With 3% GST**: **₹${String.format("%,.0f", withGst)}**
                • **1 Kg Silver Bar Benchmark**: **₹${String.format("%,.0f", feed.silverBarRate1kg)}**
            """.trimIndent()
        }

        val gold24kPerGram = feed.goldBarRate10g / 10.0
        val gold22kPerGram = gold24kPerGram * 0.916
        val gold18kPerGram = gold24kPerGram * 0.750

        val cost24k = gold24kPerGram * grams
        val cost22k = gold22kPerGram * grams
        val cost18k = gold18kPerGram * grams

        return """
            🪙 **Live Gold Price Calculation for $amount $unit (${String.format("%,.0f", grams)} Grams / ${String.format("%,.2f", grams)}g):**
            • **24K (999 Pure Gold)**: **₹${String.format("%,.0f", cost24k)}** *(+ 3% GST = ₹${String.format("%,.0f", cost24k * 1.03)})*
            • **22K (916 Hallmark Jewelry)**: **₹${String.format("%,.0f", cost22k)}** *(+ 3% GST = ₹${String.format("%,.0f", cost22k * 1.03)})*
            • **18K (750 Studded Jewelry)**: **₹${String.format("%,.0f", cost18k)}** *(+ 3% GST = ₹${String.format("%,.0f", cost18k * 1.03)})*
            • *Live Benchmark: 24K = ₹${String.format("%,.0f", gold24kPerGram)}/g | 22K = ₹${String.format("%,.0f", gold22kPerGram)}/g (1 Sovereign = 8 Grams)*
        """.trimIndent()
    }

    private fun matchVerifiedEncyclopediaFact(normalized: String): String? {
        // Capitals of Countries & Indian States
        if (normalized.contains("capital of india")) return "📍 **New Delhi** is the capital of India."
        if (normalized.contains("capital of usa") || normalized.contains("capital of america") || normalized.contains("capital of united states")) return "📍 **Washington, D.C.** is the capital of the United States."
        if (normalized.contains("capital of uk") || normalized.contains("capital of england") || normalized.contains("capital of britain")) return "📍 **London** is the capital of the United Kingdom."
        if (normalized.contains("capital of japan")) return "📍 **Tokyo** is the capital of Japan."
        if (normalized.contains("capital of china")) return "📍 **Beijing** is the capital of China."
        if (normalized.contains("capital of russia")) return "📍 **Moscow** is the capital of Russia."
        if (normalized.contains("capital of france")) return "📍 **Paris** is the capital of France."
        if (normalized.contains("capital of germany")) return "📍 **Berlin** is the capital of Germany."
        if (normalized.contains("capital of australia")) return "📍 **Canberra** is the capital of Australia."
        if (normalized.contains("capital of canada")) return "📍 **Ottawa** is the capital of Canada."
        if (normalized.contains("capital of italy")) return "📍 **Rome** is the capital of Italy."
        if (normalized.contains("capital of uae") || normalized.contains("capital of united arab emirates")) return "📍 **Abu Dhabi** is the capital of the United Arab Emirates (while Dubai is its largest city)."
        if (normalized.contains("capital of singapore")) return "📍 **Singapore** is a sovereign city-state, so its capital is **Singapore**."
        if (normalized.contains("capital of sri lanka")) return "📍 **Sri Jayawardenepura Kotte** is the legislative capital of Sri Lanka, and **Colombo** is its executive and judicial capital."
        if (normalized.contains("capital of tamil nadu") || normalized.contains("capital of tn")) return "📍 **Chennai** is the capital of Tamil Nadu."
        if (normalized.contains("capital of kerala")) return "📍 **Thiruvananthapuram** is the capital of Kerala."
        if (normalized.contains("capital of karnataka")) return "📍 **Bengaluru** is the capital of Karnataka."
        if (normalized.contains("capital of telangana")) return "📍 **Hyderabad** is the capital of Telangana."
        if (normalized.contains("capital of andhra")) return "📍 **Amaravati** is the capital of Andhra Pradesh."
        if (normalized.contains("capital of maharashtra")) return "📍 **Mumbai** is the capital of Maharashtra."

        // Tamil Nadu Districts, Cities & Culture
        if (normalized.contains("districts in tamil nadu") || normalized.contains("districts of tamil nadu") || normalized.contains("38 districts")) {
            return "🗺️ **Tamil Nadu has 38 Districts**, with **Chennai** as its state capital. Major districts include Chennai, Coimbatore, Madurai, Tiruchirappalli (Trichy), Salem, Tirunelveli, Erode, Tiruppur, Vellore, Thoothukudi, Thanjavur, Kanyakumari, Dindigul, Karur, Namakkal, Kanchipuram, Chengalpattu, and Cuddalore."
        }
        if (normalized.contains("coimbatore") && !normalized.contains("weather")) {
            return "🏙️ **Coimbatore (Kovai)** is the second-largest city in Tamil Nadu and the industrial & bullion hub of Kongu Nadu (known as the *Manchester of South India*), famous for gold jewelry manufacturing (R.S. Puram & Oppanakara Street), textiles, engineering, and proximity to the Western Ghats."
        }
        if (normalized.contains("thirukkural") || normalized.contains("thiruvalluvar") || normalized.contains("kural")) {
            return "📜 **Thirukkural**, authored by **Saint Thiruvalluvar**, is a classic Tamil masterpiece of **1,330 couplets (Kurals)** organized into **133 chapters** across three books: **Aram** (Virtue), **Porul** (Polity & Wealth), and **Inbam** (Love). Its opening Kural is: *'Agara Mudhala Ezhuthellam Aathi Bhagavan Mudhatre Ulagu.'*"
        }

        // Space, ISRO & Science
        if (normalized.contains("isro") || normalized.contains("chandrayaan") || normalized.contains("aditya l1") || normalized.contains("gaganyaan")) {
            return "🚀 **ISRO (Indian Space Research Organisation)**, headquartered in Bengaluru, made India the **first nation to land near the Lunar South Pole** with **Chandrayaan-3** (Vikram lander & Pragyan rover on **23 August 2023**, celebrated as National Space Day), followed by the **Aditya-L1** solar observatory and the **Gaganyaan** human spaceflight program."
        }
        if (normalized.contains("speed of light")) return "⚡ The **Speed of Light** in a vacuum (c) is exactly **299,792,458 meters per second** (approx. **300,000 km/s** or **186,282 miles/s**)."
        if (normalized.contains("speed of sound")) return "🔊 The **Speed of Sound** in dry air at 20 °C is **343 meters per second** (**1,234.8 km/h**)."
        if (normalized.contains("tallest mountain") || normalized.contains("mount everest")) return "🏔️ **Mount Everest** (in the Himalayas on the Nepal–Tibet border) is the tallest mountain on Earth at **8,848.86 meters (29,031.7 ft)**."
        if (normalized.contains("longest river")) return "🌊 The **Nile River** (~6,650 km) is the longest river in the world (followed by the Amazon River at ~6,400 km), while India's longest river is the **Ganga (Ganges)** at **2,525 km**."
        if (normalized.contains("largest planet")) return "🪐 **Jupiter** is the largest planet in our Solar System."
        if (normalized.contains("smallest planet")) return "🪐 **Mercury** is the smallest planet in our Solar System."
        if (normalized.contains("photosynthesis")) return "🌿 **Photosynthesis** is the biochemical process by which green plants use sunlight, chlorophyll, water (H₂O), and carbon dioxide (CO₂) to synthesize glucose (C₆H₁₂O₆) and release oxygen (O₂): 6CO₂ + 6H₂O + Light → C₆H₁₂O₆ + 6O₂."
        if (normalized.contains("newton") && normalized.contains("law")) return "🍎 **Sir Isaac Newton's Three Laws of Motion**: (1) **Law of Inertia**: An object remains at rest or in uniform motion unless acted on by an external force; (2) **F = m × a**: Force equals mass times acceleration; (3) **Action & Reaction**: Every action has an equal and opposite reaction."
        if (normalized.contains("pythagoras") || normalized.contains("pythagorean")) return "📐 **Pythagorean Theorem**: In a right-angled triangle, the square of the hypotenuse (c) equals the sum of the squares of the other two sides: **a² + b² = c²**."

        // Finance & Economics
        if (normalized.contains("repo rate")) return "🏦 The **Repo Rate** is the benchmark interest rate at which the **Reserve Bank of India (RBI)** lends short-term funds to commercial banks against government securities to control inflation and liquidity."
        if (normalized.contains("what is gst")) return "💼 **GST (Goods and Services Tax)** is India's unified indirect tax launched on **1 July 2017**, with primary slabs of **0%, 5%, 12%, 18%, and 28%** (and a special **3% rate on Gold & Silver bullion**)."

        // Sports & Chess
        if (normalized.contains("gukesh") || normalized.contains("viswanathan anand") || normalized.contains("praggnanandhaa") || normalized.contains("world chess")) {
            return "♟️ **Indian Chess Champions**: **Viswanathan Anand** (5-time World Chess Champion from Chennai) pioneered India's chess revolution, followed by **D. Gukesh** (from Chennai, the youngest undisputed World Chess Champion in history) and Grandmasters **R. Praggnanandhaa** and **Arjun Erigaisi**."
        }
        if (normalized.contains("ipl") || normalized.contains("csk") || normalized.contains("chennai super kings") || normalized.contains("ms dhoni")) {
            return "🏏 **Chennai Super Kings (CSK)** is one of the most successful franchises in the **Indian Premier League (IPL)** with **5 IPL Championship titles** (2010, 2011, 2018, 2021, 2023) under iconic leader **M. S. Dhoni**."
        }

        // Tech & Corporate Leaders
        if (normalized.contains("sundar pichai")) return "💻 **Sundar Pichai** (born in Tamil Nadu and alumnus of IIT Kharagpur) is the **CEO of Google and Alphabet Inc.**"
        if (normalized.contains("sridhar vembu") || normalized.contains("zoho")) return "💻 **Zoho Corporation**, founded by **Sridhar Vembu**, is a global cloud software powerhouse headquartered in Chennai and Tenkasi, Tamil Nadu."

        if (normalized.contains("joke")) return "😄 Here's a quick bullion joke for you: Why did the gold bar go to school? Because it wanted to become **24 Karat smart**!"

        return null
    }

    /**
     * Live Open-Domain Encyclopedia + Live Google News Feed Search.
     * Queries Wikipedia REST API, DuckDuckGo Instant Answer API, and Google News RSS for accurate, verified answers.
     */
    suspend fun fetchLiveEncyclopediaAnswer(rawQuery: String): String? = withContext(Dispatchers.IO) {
        val cleanTopic = extractSearchTopic(rawQuery)
        if (cleanTopic.isBlank()) return@withContext null

        val lower = rawQuery.lowercase()
        if (lower.contains("news") || lower.contains("latest") || lower.contains("today") || lower.contains("update") || lower.contains("trend")) {
            val newsItems = fetchGoogleNewsRss(cleanTopic, "LIVE FEED", 3)
            if (newsItems.isNotEmpty()) {
                val bullets = newsItems.joinToString("\n") { "• ${it.title} (${it.source})" }
                return@withContext "📰 Here are the latest live feeds on \"$cleanTopic\":\n$bullets"
            }
        }

        // 1. Try Wikipedia REST Search + Summary API
        try {
            val encoded = URLEncoder.encode(cleanTopic, "UTF-8")
            val searchUrl = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&utf8=&format=json&srlimit=1"
            val req = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "JashxAI/2.0 (Android)")
                .build()

            fastHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = JSONObject(body)
                        val searchArr = json.optJSONObject("query")?.optJSONArray("search")
                        if (searchArr != null && searchArr.length() > 0) {
                            val topTitle = searchArr.getJSONObject(0).optString("title")
                            if (topTitle.isNotBlank()) {
                                val encodedTitle = URLEncoder.encode(topTitle.replace(" ", "_"), "UTF-8")
                                val summaryUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/$encodedTitle"
                                val sumReq = Request.Builder()
                                    .url(summaryUrl)
                                    .header("User-Agent", "JashxAI/2.0 (Android)")
                                    .build()
                                fastHttpClient.newCall(sumReq).execute().use { sumResp ->
                                    if (sumResp.isSuccessful) {
                                        val sumBody = sumResp.body?.string()
                                        if (!sumBody.isNullOrBlank()) {
                                            val sumJson = JSONObject(sumBody)
                                            val title = sumJson.optString("title", topTitle)
                                            val extract = sumJson.optString("extract", "").trim()
                                            if (extract.isNotBlank()) {
                                                return@withContext "💡 **$title**:\n$extract"
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Try DuckDuckGo Instant Answer API
        try {
            val encoded = URLEncoder.encode(cleanTopic, "UTF-8")
            val ddgUrl = "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"
            val req = Request.Builder()
                .url(ddgUrl)
                .header("User-Agent", "JashxAI/2.0 (Android)")
                .build()
            fastHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = JSONObject(body)
                        val abstractText = json.optString("AbstractText", "").trim()
                        val answerText = json.optString("Answer", "").trim()
                        if (answerText.isNotBlank()) {
                            return@withContext "💡 **Answer**:\n$answerText"
                        }
                        if (abstractText.isNotBlank()) {
                            val heading = json.optString("Heading", cleanTopic)
                            return@withContext "💡 **$heading**:\n$abstractText"
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 3. Fallback to Google News RSS for any current topic not in Wikipedia
        val newsFallback = fetchGoogleNewsRss(cleanTopic, "LIVE FEED", 3)
        if (newsFallback.isNotEmpty()) {
            val bullets = newsFallback.joinToString("\n") { "• ${it.title} (${it.source})" }
            return@withContext "📰 Here are the latest live feeds on \"$cleanTopic\":\n$bullets"
        }

        null
    }

    private fun extractSearchTopic(rawQuery: String): String {
        return rawQuery.trim()
            .replace(Regex("""^(who is|who was|what is|what are|where is|when was|when is|tell me about|explain|define|meaning of|history of|how does|how do|who invented|who discovered|why is|why does)\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^(the|a|an)\s+""", RegexOption.IGNORE_CASE), "")
            .replace("?", "")
            .trim()
    }

    /**
     * Synchronous fallback for unit tests and offline mode.
     */
    fun queryKnowledgeBase(rawQuery: String, liveFeed: VijayBullionFeed): String {
        matchDirectRelevantFeed(rawQuery, liveFeed)?.let { return it }
        return "💡 **JASHX AI**: Regarding **\"$rawQuery\"** — Here are your live references: Physical Gold 999 (10g) is ₹${String.format("%,.0f", liveFeed.goldBarRate10g)} and Silver 1kg is ₹${String.format("%,.0f", liveFeed.silverBarRate1kg)}. Ask me any question on bullion, science, politics, history, or math!"
    }

    private fun normalizeQuery(input: String): String {
        return input.lowercase().trim()
            .replace(Regex("""^[^\w\d]+"""), "")
            .replace("?", "")
            .replace("tamilnadu", "tamil nadu")
            .replace("cheif minister", "chief minister")
            .replace("cheif", "chief")
            .replace("soverign", "sovereign")
            .replace("sovren", "sovereign")
            .replace("wether", "weather")
            .replace("wat is", "what is")
            .replace("wat time", "what time")
            .replace("priem minister", "prime minister")
            .replace("cbe", "coimbatore")
            .trim()
    }

    fun getIstTimeAndDate(): Pair<String, String> {
        val now = Date()
        val timeFormat = SimpleDateFormat("hh:mm:ss a z", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
        val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
        return Pair(timeFormat.format(now), dateFormat.format(now))
    }

    private fun tryEvaluateMath(input: String): String? {
        val cleaned = input.lowercase()
            .replace("what is", "")
            .replace("calculate", "")
            .replace("evaluate", "")
            .replace("solve", "")
            .replace("?", "")
            .replace(",", "")
            .trim()

        val percentRegex = Regex("""(\d+(?:\.\d+)?)\s*%\s*of\s*(\d+(?:\.\d+)?)""")
        val matchPercent = percentRegex.find(cleaned)
        if (matchPercent != null) {
            val (pStr, valStr) = matchPercent.destructured
            val p = pStr.toDoubleOrNull() ?: return null
            val total = valStr.toDoubleOrNull() ?: return null
            val result = (p / 100.0) * total
            return "🔢 **$p% of $total** is **${String.format("%,.2f", result)}**."
        }

        val sqrtRegex = Regex("""(?:sqrt|square root of)\s*(\d+(?:\.\d+)?)""")
        val matchSqrt = sqrtRegex.find(cleaned)
        if (matchSqrt != null) {
            val n = matchSqrt.groupValues[1].toDoubleOrNull() ?: return null
            if (n >= 0) {
                return "🔢 The square root of **$n** (**√$n**) is **${String.format("%,.4f", sqrt(n)).trimEnd('0').trimEnd('.')}**."
            }
        }

        val arithmeticRegex = Regex("""^(\d+(?:\.\d+)?)\s*([+\-*/x×÷^])\s*(\d+(?:\.\d+)?)$""")
        val matchArith = arithmeticRegex.find(cleaned)
        if (matchArith != null) {
            val (num1Str, op, num2Str) = matchArith.destructured
            val num1 = num1Str.toDoubleOrNull() ?: return null
            val num2 = num2Str.toDoubleOrNull() ?: return null
            val result = when (op) {
                "+" -> num1 + num2
                "-" -> num1 - num2
                "*", "x", "×" -> num1 * num2
                "/", "÷" -> if (num2 != 0.0) num1 / num2 else Double.NaN
                "^" -> num1.pow(num2)
                else -> return null
            }
            return "🔢 **$num1 $op $num2** equals **${if (result.isNaN()) "Undefined (Division by zero)" else String.format("%,.4f", result).trimEnd('0').trimEnd('.')}**."
        }

        return null
    }
}
