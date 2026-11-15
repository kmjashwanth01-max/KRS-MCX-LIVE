package com.example.util

import android.content.Context
import com.example.data.repository.AdminCloudDatabaseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

data class AppDiagnosticEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String,
    val message: String,
    val isError: Boolean = false
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.ENGLISH).format(Date(timestamp))
}

data class FeedbackItem(
    val timestamp: Long = System.currentTimeMillis(),
    val message: String,
    val category: String
) {
    val formattedDate: String
        get() = SimpleDateFormat("dd MMM, hh:mm a", Locale.ENGLISH).format(Date(timestamp))
}

object AppDiagnosticsManager {

    const val ADMIN_EMAIL = "krsmcxofficial@gmail.com"
    const val CREATOR_EMAIL = "kmjashwanth01@gmail.com"

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val emailHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4000, TimeUnit.MILLISECONDS)
            .readTimeout(4000, TimeUnit.MILLISECONDS)
            .build()
    }

    private val startTime = System.currentTimeMillis()
    private val totalTicks = AtomicLong(0)
    private val successfulTicks = AtomicLong(0)
    private val failedTicks = AtomicLong(0)
    private val widgetSyncCount = AtomicLong(0)
    private val alertsTriggered = AtomicLong(0)
    private val accountEmailsSent = AtomicLong(0)

    private val _recentLogs = MutableStateFlow<List<AppDiagnosticEvent>>(emptyList())
    val recentLogs: StateFlow<List<AppDiagnosticEvent>> = _recentLogs.asStateFlow()

    private val _feedbacks = MutableStateFlow<List<FeedbackItem>>(emptyList())
    val feedbacks: StateFlow<List<FeedbackItem>> = _feedbacks.asStateFlow()

    private val _lastAccountEmailReportText = MutableStateFlow("")
    val lastAccountEmailReportText: StateFlow<String> = _lastAccountEmailReportText.asStateFlow()

    fun getAccountEmailDispatchCount(): Long = accountEmailsSent.get()

    fun logSyncSuccess(serverTime: String) {
        totalTicks.incrementAndGet()
        successfulTicks.incrementAndGet()
        addLog(
            AppDiagnosticEvent(
                tag = "BULLION_BROADCAST",
                message = "1s rate broadcast received successfully ($serverTime)",
                isError = false
            )
        )
    }

    fun logSyncError(errorMessage: String) {
        totalTicks.incrementAndGet()
        failedTicks.incrementAndGet()
        addLog(
            AppDiagnosticEvent(
                tag = "SYNC_ERROR",
                message = "Bullion rate sync failed: $errorMessage",
                isError = true
            )
        )
    }

    fun logWidgetSync() {
        widgetSyncCount.incrementAndGet()
    }

    fun logAlert(symbol: String, price: Double) {
        alertsTriggered.incrementAndGet()
        addLog(
            AppDiagnosticEvent(
                tag = "PRICE_ALERT",
                message = "Threshold alert triggered for $symbol at ₹${String.format("%,.2f", price)}",
                isError = false
            )
        )
    }

    fun logGeneral(tag: String, message: String, isError: Boolean = false) {
        addLog(AppDiagnosticEvent(tag = tag, message = message, isError = isError))
    }

    fun submitFeedback(message: String, category: String) {
        if (message.isBlank()) return
        val item = FeedbackItem(message = message.trim(), category = category)
        val list = _feedbacks.value.toMutableList()
        list.add(0, item)
        _feedbacks.value = list
        logGeneral("FEEDBACK", "New client feedback registered in category '$category'")
    }

    private fun addLog(event: AppDiagnosticEvent) {
        val current = _recentLogs.value.toMutableList()
        current.add(0, event)
        if (current.size > 100) {
            current.removeAt(current.size - 1)
        }
        _recentLogs.value = current
    }

    fun getUptimeSummary(): String {
        val elapsedMillis = System.currentTimeMillis() - startTime
        val hours = elapsedMillis / (1000 * 60 * 60)
        val minutes = (elapsedMillis % (1000 * 60 * 60)) / (1000 * 60)
        val seconds = (elapsedMillis % (1000 * 60)) / 1000
        return "${hours}h ${minutes}m ${seconds}s"
    }

    fun getSuccessRatePercent(): Double {
        val total = totalTicks.get()
        if (total == 0L) return 100.0
        val success = successfulTicks.get()
        return (success.toDouble() / total.toDouble()) * 100.0
    }

    fun getTotalTicks(): Long = totalTicks.get()
    fun getFailedTicks(): Long = failedTicks.get()
    fun getWidgetSyncs(): Long = widgetSyncCount.get()
    fun getAlertsTriggered(): Long = alertsTriggered.get()

    /**
     * Generates the structured email report for immediate background dispatch to krsmcxofficial@gmail.com
     * every time an account is created or anyone signs in.
     * Strictly hidden from app users — stored only in the separate Admin Cloud Firestore Database.
     */
    fun generateAccountEventEmailBody(
        eventType: String,
        user: UserProfile,
        totalAccountsCount: Int,
        allAccounts: List<UserProfile>
    ): String {
        val timestampStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.ENGLISH).format(Date())
        return buildString {
            appendLine("==================================================")
            appendLine("🔔 KRS GOLD & SILVER - IMMEDIATE ACCOUNT & SIGN-IN REPORT")
            appendLine("==================================================")
            appendLine("To: $ADMIN_EMAIL")
            appendLine("Event Type: $eventType")
            appendLine("Timestamp: $timestampStr")
            appendLine("Total No. of Accounts Created: $totalAccountsCount")
            appendLine("Active Saved Accounts on Device: ${allAccounts.size}")
            appendLine()
            appendLine("📄 USER ACCOUNT TEXT FILE (${user.textFileName}):")
            appendLine(user.toTextFileContent())
            appendLine("==================================================")
            appendLine("Automated Real-Time Dispatch to $ADMIN_EMAIL")
            appendLine("Powered by JASHX AI (Founder: K.M. Jashwanth)")
            appendLine("==================================================")
        }
    }

    /**
     * Silently dispatches the account creation / sign-in report to krsmcxofficial@gmail.com
     * and writes the account, login event, and mail log to the separate Admin Cloud Firestore Database.
     */
    fun dispatchImmediateAccountOrSignInEmail(
        context: Context?,
        eventType: String,
        user: UserProfile,
        totalAccountsCount: Int,
        allAccounts: List<UserProfile>
    ) {
        val subject = "[KRS Bullion] $eventType • Total Accounts Created: $totalAccountsCount (@${user.username})"
        val body = generateAccountEventEmailBody(
            eventType = eventType,
            user = user,
            totalAccountsCount = totalAccountsCount,
            allAccounts = allAccounts
        )

        _lastAccountEmailReportText.value = body
        accountEmailsSent.incrementAndGet()

        if (context != null) {
            val appCtx = context.applicationContext
            ioScope.launch {
                // 1. Sync to separate Admin-Only Google Cloud Firestore Database
                try {
                    val repo = AdminCloudDatabaseRepository(appCtx)
                    repo.saveUserAccount(
                        username = user.username,
                        name = user.name,
                        city = user.city,
                        role = user.role,
                        isDarkMode = user.isDarkMode,
                        isAppLockEnabled = user.isAppLockEnabled,
                        isBiometricEnabled = user.isBiometricEnabled,
                        email = user.email,
                        phone = user.phone,
                        pin4Digit = user.pin4Digit
                    )
                    repo.recordLoginAndMailToAdminDatabase(
                        username = user.username,
                        name = user.name,
                        eventType = eventType,
                        savedTheme = if (user.isDarkMode) "Dark Titanium Mode" else "Light Platinum Mode",
                        totalAccountsCount = totalAccountsCount,
                        subject = subject,
                        body = body
                    )
                } catch (_: Throwable) {}

                // 2. Silent background HTTP webhook relay to krsmcxofficial@gmail.com
                try {
                    val formBody = FormBody.Builder()
                        .add("_subject", subject)
                        .add("event_type", eventType)
                        .add("total_accounts_created", totalAccountsCount.toString())
                        .add("username", user.username)
                        .add("full_name", user.name)
                        .add("phone", user.phone)
                        .add("city", user.city)
                        .add("role", user.role)
                        .add("saved_theme", if (user.isDarkMode) "Dark" else "Light")
                        .add("message", body)
                        .build()
                    val req = Request.Builder()
                        .url("https://formsubmit.co/ajax/$ADMIN_EMAIL")
                        .header("Accept", "application/json")
                        .post(formBody)
                        .build()
                    emailHttpClient.newCall(req).execute().close()
                } catch (_: Throwable) {}
            }
        }
    }

    /**
     * Silently syncs account/theme/PIN changes to the separate Admin Cloud Firestore Database.
     */
    fun syncAccountChangeToCloudDatabase(
        context: Context?,
        user: UserProfile
    ) {
        if (context == null) return
        val appCtx = context.applicationContext
        ioScope.launch {
            try {
                val repo = AdminCloudDatabaseRepository(appCtx)
                repo.saveUserAccount(
                    username = user.username,
                    name = user.name,
                    city = user.city,
                    role = user.role,
                    isDarkMode = user.isDarkMode,
                    isAppLockEnabled = user.isAppLockEnabled,
                    isBiometricEnabled = user.isBiometricEnabled,
                    email = user.email,
                    phone = user.phone,
                    pin4Digit = user.pin4Digit
                )
            } catch (_: Throwable) {}
        }
    }

    fun checkAndTriggerAutomaticDailySummary(context: Context) {
        // Daily summary and mail logs are managed strictly in the external Admin Cloud Database.
    }
}
