package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(exception: Throwable, operationType: OperationType, path: String?): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val auth = try {
        FirebaseAuth.getInstance()
    } catch (_: Exception) {
        null
    }
    val currentUser = auth?.currentUser

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

@IgnoreExtraProperties
data class CloudUserAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fileName: String = "",
    val userTextFile: String = "",
    val username: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val city: String = "Coimbatore",
    val role: String = "Bullion Trader",
    val pin4Digit: String = "1234",
    @get:PropertyName("isDarkMode") val isDarkMode: Boolean = true,
    @get:PropertyName("isAppLockEnabled") val isAppLockEnabled: Boolean = false,
    @get:PropertyName("isBiometricEnabled") val isBiometricEnabled: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudUserFile(
    @DocumentId val id: String = "",
    val fileName: String = "",
    val userInfoText: String = "",
    val fileContentText: String = "",
    val username: String = "",
    val name: String = "",
    val pin4Digit: String = "1234",
    val city: String = "Coimbatore",
    val role: String = "Bullion Trader",
    val email: String = "",
    val phone: String = "",
    val themeMode: String = "Dark Titanium Mode",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudLoginEvent(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val name: String = "",
    val eventType: String = "",
    val savedTheme: String = "Dark",
    val totalAccountsCount: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudAdminMailLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val recipientEmail: String = "krsmcxofficial@gmail.com",
    val subject: String = "",
    val body: String = "",
    val eventType: String = "",
    val totalAccountsCount: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudAnnouncement(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val title: String = "",
    val message: String = "",
    val priority: String = "normal",
    @get:PropertyName("isActive") val isActive: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

/**
 * Separate Google Cloud Firestore Repository for Admin-controlled accounts, individual user text files,
 * login events, mail logs, and real-time official announcements.
 *
 * Security invariants:
 * - Always initialized with context.getString(R.string.firestore_database_id).
 * - Syncs each user into both `/user_files/{accountId}` (`<username>.txt`) and `/user_accounts/{accountId}`
 *   with all details (username, PIN, city, role, email, phone, theme, etc.) formatted in plain text.
 * - Also performs a direct parallel HTTPS REST write to Cloud Firestore when initialized with an Android Context
 *   so updates appear in the Firebase Console immediately even if the local gRPC queue had stale cached entries.
 */
class AdminCloudDatabaseRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val appContext: Context? = null
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth,
        appContext = context.applicationContext
    )

    companion object {
        private const val CLOUD_PROJECT_ID = "gen-lang-client-0250894533"

        val ADMIN_EMAILS = setOf(
            "kmjashwanth01@gmail.com",
            "krsmcxofficial@gmail.com"
        )

        private val restHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(6000, TimeUnit.MILLISECONDS)
                .readTimeout(6000, TimeUnit.MILLISECONDS)
                .build()
        }

        fun normalizeAccountId(rawUsernameOrId: String): String {
            val cleaned = rawUsernameOrId.trim().removePrefix("@").lowercase()
                .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                .take(128)
            return cleaned.ifBlank { "trader" }
        }

        /**
         * Single-line readable summary of all user information so it is immediately visible
         * without expanding multi-line text in the Firebase Console.
         */
        fun buildUserSingleLineSummary(
            username: String,
            name: String,
            pin4Digit: String,
            city: String,
            role: String,
            email: String,
            phone: String,
            isDarkMode: Boolean,
            isAppLockEnabled: Boolean,
            isBiometricEnabled: Boolean
        ): String {
            val cleanUser = username.trim().removePrefix("@").ifBlank { "trader" }
            val themeLabel = if (isDarkMode) "Dark Titanium Mode" else "Light Platinum Mode"
            return "FILE: ${normalizeAccountId(cleanUser)}.txt | USERNAME: @$cleanUser | PIN: $pin4Digit | CITY: ${city.ifBlank { "Coimbatore" }} | FULL NAME: ${name.ifBlank { cleanUser }} | ROLE: ${role.ifBlank { "Bullion Trader" }} | PHONE: ${phone.ifBlank { "Not provided" }} | EMAIL: ${email.ifBlank { "Not provided" }} | THEME: $themeLabel | APP LOCK: ${if (isAppLockEnabled) "ON" else "OFF"} | BIOMETRIC: ${if (isBiometricEnabled) "ON" else "OFF"}"
        }

        /**
         * Builds the formatted plain-text file content (`<username>.txt`) for an individual user.
         * Includes Username, Password / 4-Digit PIN, City, Full Name, Role, Email, Mobile Phone,
         * Theme Mode, App Lock, and Biometric settings in human-readable text form.
         */
        fun buildUserTextFileContent(
            userId: String,
            username: String,
            name: String,
            pin4Digit: String,
            city: String,
            role: String,
            email: String,
            phone: String,
            isDarkMode: Boolean,
            isAppLockEnabled: Boolean,
            isBiometricEnabled: Boolean
        ): String {
            val cleanUser = username.trim().removePrefix("@").ifBlank { "trader" }
            val fileName = "${normalizeAccountId(cleanUser)}.txt"
            val themeLabel = if (isDarkMode) "Dark Titanium Mode" else "Light Platinum Mode"
            return buildString {
                appendLine("==================================================")
                appendLine("KRS GOLD & SILVER — USER ACCOUNT FILE ($fileName)")
                appendLine("==================================================")
                appendLine("FILE_NAME          : $fileName")
                appendLine("USER_ID            : ${userId.ifBlank { cleanUser }}")
                appendLine("USERNAME           : $cleanUser")
                appendLine("FULL_NAME          : ${name.ifBlank { cleanUser }}")
                appendLine("PASSWORD_PIN       : $pin4Digit")
                appendLine("CITY               : ${city.ifBlank { "Coimbatore" }}")
                appendLine("ROLE               : ${role.ifBlank { "Bullion Trader" }}")
                appendLine("EMAIL              : ${email.ifBlank { "Not provided" }}")
                appendLine("MOBILE_PHONE       : ${phone.ifBlank { "Not provided" }}")
                appendLine("THEME_MODE         : $themeLabel")
                appendLine("IS_DARK_MODE       : $isDarkMode")
                appendLine("APP_LOCK_ENABLED   : $isAppLockEnabled")
                appendLine("BIOMETRIC_ENABLED  : $isBiometricEnabled")
                appendLine("==================================================")
            }.trim()
        }
    }

    private fun resolveDatabaseId(): String? {
        val ctx = appContext ?: return null
        return try {
            ctx.getString(R.string.firestore_database_id).takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun isoUtcNow(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    /**
     * Converts a map of Kotlin primitives into Firestore REST API v1 `{"fields": ...}` JSON format.
     */
    private fun toFirestoreRestFieldsJson(fieldsMap: Map<String, Any>): String {
        val fieldsObj = JSONObject()
        val nowIso = isoUtcNow()
        for ((k, v) in fieldsMap) {
            val valObj = JSONObject()
            when (v) {
                is Boolean -> valObj.put("booleanValue", v)
                is Int -> valObj.put("integerValue", v.toString())
                is Long -> valObj.put("integerValue", v.toString())
                is String -> {
                    if (v == "__SERVER_TIMESTAMP__") {
                        valObj.put("timestampValue", nowIso)
                    } else {
                        valObj.put("stringValue", v)
                    }
                }
                else -> valObj.put("stringValue", v.toString())
            }
            fieldsObj.put(k, valObj)
        }
        return JSONObject().put("fields", fieldsObj).toString()
    }

    /**
     * Direct HTTPS REST PATCH/POST to Cloud Firestore so documents update in the Firebase Console immediately,
     * bypassing any stuck offline mutation queue in the emulator's local SQLite cache.
     */
    private suspend fun syncViaFirestoreRestApi(
        collection: String,
        documentId: String?,
        fieldsMap: Map<String, Any>
    ) {
        val dbId = resolveDatabaseId() ?: return
        withContext(Dispatchers.IO) {
            try {
                val baseUrl = "https://firestore.googleapis.com/v1/projects/$CLOUD_PROJECT_ID/databases/$dbId/documents/$collection"
                val jsonBody = toFirestoreRestFieldsJson(fieldsMap)
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val reqBuilder = Request.Builder()
                    .header("Accept", "application/json")
                if (!documentId.isNullOrBlank()) {
                    reqBuilder.url("$baseUrl/$documentId")
                        .patch(jsonBody.toRequestBody(mediaType))
                } else {
                    reqBuilder.url(baseUrl)
                        .post(jsonBody.toRequestBody(mediaType))
                }
                restHttpClient.newCall(reqBuilder.build()).execute().close()
            } catch (_: Throwable) {}
        }
    }

    private fun resolveAccountId(username: String): String {
        val cleanUser = normalizeAccountId(username)
        if (cleanUser.isNotBlank() && cleanUser != "trader") return cleanUser
        return auth.currentUser?.uid?.let { normalizeAccountId(it) } ?: cleanUser
    }

    fun isCurrentUserAdmin(): Boolean {
        val email = auth.currentUser?.email?.lowercase()?.trim() ?: return false
        return email in ADMIN_EMAILS
    }

    /**
     * Creates or updates the user's dedicated text file document in `/user_files/{accountId}` (`<username>.txt`)
     * AND their account document in `/user_accounts/{accountId}`.
     * Uses direct atomic `.set()` without a prior `.get()` so cached local snapshots can never block writes.
     */
    suspend fun saveUserAccount(
        username: String,
        name: String,
        city: String,
        role: String,
        isDarkMode: Boolean,
        isAppLockEnabled: Boolean,
        isBiometricEnabled: Boolean,
        email: String = "",
        phone: String = "",
        pin4Digit: String = "1234",
        explicitAccountId: String? = null
    ): Result<Unit> = runCatching {
        val docId = explicitAccountId?.let { normalizeAccountId(it) } ?: resolveAccountId(username)
        val cleanUsername = username.trim().removePrefix("@").ifBlank { docId }.take(60)
        val cleanName = name.trim().ifBlank { cleanUsername }.take(100)
        val cleanCity = city.ifBlank { "Coimbatore" }.take(80)
        val cleanRole = role.ifBlank { "Bullion Trader" }.take(60)
        val cleanEmail = email.trim().take(120)
        val cleanPhone = phone.trim().take(40)
        val safePin = pin4Digit.trim().let { if (it.length in 4..32) it else "1234" }
        val fileName = "${docId}.txt".take(140)
        val themeLabel = if (isDarkMode) "Dark Titanium Mode" else "Light Platinum Mode"

        val singleLineInfo = buildUserSingleLineSummary(
            username = cleanUsername,
            name = cleanName,
            pin4Digit = safePin,
            city = cleanCity,
            role = cleanRole,
            email = cleanEmail,
            phone = cleanPhone,
            isDarkMode = isDarkMode,
            isAppLockEnabled = isAppLockEnabled,
            isBiometricEnabled = isBiometricEnabled
        ).take(2000)

        val textFileContent = buildUserTextFileContent(
            userId = docId,
            username = cleanUsername,
            name = cleanName,
            pin4Digit = safePin,
            city = cleanCity,
            role = cleanRole,
            email = cleanEmail,
            phone = cleanPhone,
            isDarkMode = isDarkMode,
            isAppLockEnabled = isAppLockEnabled,
            isBiometricEnabled = isBiometricEnabled
        ).take(5000)

        val fileRef = db.collection("user_files").document(docId)
        val docRef = db.collection("user_accounts").document(docId)

        // 0. Immediate parallel REST API write when running on Android device/emulator
        syncViaFirestoreRestApi(
            collection = "user_files",
            documentId = docId,
            fieldsMap = mapOf(
                "fileName" to fileName,
                "userInfoText" to singleLineInfo,
                "fileContentText" to textFileContent,
                "username" to cleanUsername,
                "name" to cleanName,
                "pin4Digit" to safePin,
                "city" to cleanCity,
                "role" to cleanRole,
                "email" to cleanEmail,
                "phone" to cleanPhone,
                "themeMode" to themeLabel,
                "createdAt" to "__SERVER_TIMESTAMP__",
                "updatedAt" to "__SERVER_TIMESTAMP__"
            )
        )
        syncViaFirestoreRestApi(
            collection = "user_accounts",
            documentId = docId,
            fieldsMap = mapOf(
                "userId" to docId,
                "fileName" to fileName,
                "userTextFile" to textFileContent,
                "username" to cleanUsername,
                "name" to cleanName,
                "email" to cleanEmail,
                "phone" to cleanPhone,
                "city" to cleanCity,
                "role" to cleanRole,
                "pin4Digit" to safePin,
                "isDarkMode" to isDarkMode,
                "isAppLockEnabled" to isAppLockEnabled,
                "isBiometricEnabled" to isBiometricEnabled,
                "createdAt" to "__SERVER_TIMESTAMP__",
                "updatedAt" to "__SERVER_TIMESTAMP__"
            )
        )

        // 1. Write dedicated per-user text file document in `/user_files/{docId}` via Firestore SDK
        try {
            val filePayload = mapOf(
                "fileName" to fileName,
                "userInfoText" to singleLineInfo,
                "fileContentText" to textFileContent,
                "username" to cleanUsername,
                "name" to cleanName,
                "pin4Digit" to safePin,
                "city" to cleanCity,
                "role" to cleanRole,
                "email" to cleanEmail,
                "phone" to cleanPhone,
                "themeMode" to themeLabel,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            fileRef.set(filePayload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, fileRef.path)
            throw e
        }

        // 2. Write user account document in `/user_accounts/{docId}` via Firestore SDK
        try {
            val accountPayload = mapOf(
                "userId" to docId,
                "fileName" to fileName,
                "userTextFile" to textFileContent,
                "username" to cleanUsername,
                "name" to cleanName,
                "email" to cleanEmail,
                "phone" to cleanPhone,
                "city" to cleanCity,
                "role" to cleanRole,
                "pin4Digit" to safePin,
                "isDarkMode" to isDarkMode,
                "isAppLockEnabled" to isAppLockEnabled,
                "isBiometricEnabled" to isBiometricEnabled,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(accountPayload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    /**
     * Fetches a specific account document (`/user_accounts/{targetUsernameOrId}`) by username or ID.
     */
    suspend fun getUserAccount(targetUsernameOrId: String? = null): Result<CloudUserAccount?> = runCatching {
        val rawId = targetUsernameOrId ?: auth.currentUser?.uid ?: return@runCatching null
        val docId = normalizeAccountId(rawId)
        val docRef = db.collection("user_accounts").document(docId)
        try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                null
            } else {
                val base = snap.toObject(CloudUserAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                base?.copy(
                    fileName = snap.getString("fileName") ?: base.fileName,
                    userTextFile = snap.getString("userTextFile") ?: base.userTextFile,
                    pin4Digit = snap.getString("pin4Digit") ?: base.pin4Digit,
                    email = snap.getString("email") ?: base.email,
                    phone = snap.getString("phone") ?: base.phone,
                    isDarkMode = snap.getBoolean("isDarkMode") ?: base.isDarkMode,
                    isAppLockEnabled = snap.getBoolean("isAppLockEnabled") ?: base.isAppLockEnabled,
                    isBiometricEnabled = snap.getBoolean("isBiometricEnabled") ?: base.isBiometricEnabled
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    /**
     * Fetches a user's dedicated text file document (`/user_files/{targetUsernameOrId}`).
     */
    suspend fun getUserTextFile(targetUsernameOrId: String): Result<CloudUserFile?> = runCatching {
        val docId = normalizeAccountId(targetUsernameOrId)
        val docRef = db.collection("user_files").document(docId)
        try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                null
            } else {
                snap.toObject(CloudUserFile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    /**
     * Appends a login / account creation record and an admin mail log to the external Firestore database.
     * Regular users can only write events; ONLY the Admin can read or list these collections.
     */
    suspend fun recordLoginAndMailToAdminDatabase(
        username: String,
        name: String,
        eventType: String,
        savedTheme: String,
        totalAccountsCount: Int,
        subject: String,
        body: String
    ): Result<String> = runCatching {
        val uid = auth.currentUser?.uid ?: resolveAccountId(username)
        val loginRef = db.collection("login_events").document()
        val mailRef = db.collection("admin_mail_logs").document()

        val safeCount = maxOf(1, totalAccountsCount)
        val cleanUser = username.trim().removePrefix("@").ifBlank { "trader" }.take(60)
        val cleanName = name.trim().ifBlank { "Trader" }.take(100)
        val cleanEvent = eventType.ifBlank { "USER_SIGN_IN" }.take(80)
        val cleanTheme = savedTheme.ifBlank { "Dark" }.take(40)
        val cleanSubject = subject.ifBlank { "[KRS Bullion] Account Alert" }.take(250)
        val cleanBody = body.ifBlank { "Account Alert" }.take(10000)

        syncViaFirestoreRestApi(
            collection = "login_events",
            documentId = loginRef.id,
            fieldsMap = mapOf(
                "userId" to uid.take(128),
                "username" to cleanUser,
                "name" to cleanName,
                "eventType" to cleanEvent,
                "savedTheme" to cleanTheme,
                "totalAccountsCount" to safeCount,
                "createdAt" to "__SERVER_TIMESTAMP__"
            )
        )
        syncViaFirestoreRestApi(
            collection = "admin_mail_logs",
            documentId = mailRef.id,
            fieldsMap = mapOf(
                "userId" to uid.take(128),
                "recipientEmail" to "krsmcxofficial@gmail.com",
                "subject" to cleanSubject,
                "body" to cleanBody,
                "eventType" to cleanEvent,
                "totalAccountsCount" to safeCount,
                "createdAt" to "__SERVER_TIMESTAMP__"
            )
        )

        val loginPayload = mapOf(
            "userId" to uid.take(128),
            "username" to cleanUser,
            "name" to cleanName,
            "eventType" to cleanEvent,
            "savedTheme" to cleanTheme,
            "totalAccountsCount" to safeCount,
            "createdAt" to FieldValue.serverTimestamp()
        )

        val mailPayload = mapOf(
            "userId" to uid.take(128),
            "recipientEmail" to "krsmcxofficial@gmail.com",
            "subject" to cleanSubject,
            "body" to cleanBody,
            "eventType" to cleanEvent,
            "totalAccountsCount" to safeCount,
            "createdAt" to FieldValue.serverTimestamp()
        )

        try {
            loginRef.set(loginPayload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, loginRef.path)
            throw e
        }

        try {
            mailRef.set(mailPayload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, mailRef.path)
            throw e
        }

        mailRef.id
    }

    /**
     * Initializes the `announcements` collection with a default welcome document (`welcome_krs_bullion`)
     * if it does not exist yet so the Admin can immediately view and edit `/announcements` in Firebase Console.
     */
    suspend fun bootstrapInitialAnnouncementIfNeeded(): Result<Unit> = runCatching {
        val docRef = db.collection("announcements").document("welcome_krs_bullion")
        val snap = docRef.get().await()
        if (!snap.exists()) {
            syncViaFirestoreRestApi(
                collection = "announcements",
                documentId = "welcome_krs_bullion",
                fieldsMap = mapOf(
                    "authorId" to "admin_km_jashwanth",
                    "title" to "KRS Gold & Silver Official Live Desk",
                    "message" to "Live 1-second MCX Bullion broadcast active. Official announcements are controlled by K.M. Jashwanth.",
                    "priority" to "high",
                    "isActive" to true,
                    "createdAt" to "__SERVER_TIMESTAMP__",
                    "updatedAt" to "__SERVER_TIMESTAMP__"
                )
            )
            val payload = mapOf(
                "authorId" to "admin_km_jashwanth",
                "title" to "KRS Gold & Silver Official Live Desk",
                "message" to "Live 1-second MCX Bullion broadcast active. Official announcements are controlled by K.M. Jashwanth.",
                "priority" to "high",
                "isActive" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
        }
    }

    /**
     * Real-time stream of active official announcements published by the Admin in the database.
     */
    fun observeActiveAnnouncements(): Flow<List<CloudAnnouncement>> = flow {
        val path = "announcements"
        emitAll(
            db.collection(path)
                .whereEqualTo("isActive", true)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudAnnouncement::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    // ==================== ADMIN-ONLY DATABASE OPERATIONS ====================

    /**
     * Publishes a new official announcement into `/announcements` (Strictly restricted to Admin in firestore.rules).
     */
    suspend fun publishAnnouncementAsAdmin(
        title: String,
        message: String,
        priority: String = "high"
    ): Result<String> = runCatching {
        val adminUid = auth.currentUser?.uid
            ?: throw IllegalStateException("Admin must be signed in before publishing announcements.")
        val docRef = db.collection("announcements").document()
        val validPriority = if (priority in listOf("normal", "high", "urgent")) priority else "high"
        val payload = mapOf(
            "authorId" to adminUid,
            "title" to title.trim().take(160),
            "message" to message.trim().take(2000),
            "priority" to validPriority,
            "isActive" to true,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    /**
     * Lists all login events in `/login_events` (Strictly restricted to Admin in firestore.rules).
     */
    suspend fun listLoginEventsForAdmin(): Result<List<CloudLoginEvent>> = runCatching {
        val path = "login_events"
        try {
            val snap = db.collection(path).get().await()
            snap.toObjects(CloudLoginEvent::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            throw e
        }
    }

    /**
     * Lists all admin mail logs in `/admin_mail_logs` (Strictly restricted to Admin in firestore.rules).
     */
    suspend fun listMailLogsForAdmin(): Result<List<CloudAdminMailLog>> = runCatching {
        val path = "admin_mail_logs"
        try {
            val snap = db.collection(path).get().await()
            snap.toObjects(CloudAdminMailLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            throw e
        }
    }

    /**
     * Lists all created user accounts in `/user_accounts` (Strictly restricted to Admin in firestore.rules).
     */
    suspend fun listAllUserAccountsForAdmin(): Result<List<CloudUserAccount>> = runCatching {
        val path = "user_accounts"
        try {
            val snap = db.collection(path).get().await()
            snap.toObjects(CloudUserAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            throw e
        }
    }
}
