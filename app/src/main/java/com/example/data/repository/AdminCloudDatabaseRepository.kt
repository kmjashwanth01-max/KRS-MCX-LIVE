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
import kotlinx.coroutines.flow.combine
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

@IgnoreExtraProperties
data class CloudAppUpdateConfig(
    @DocumentId val id: String = "",
    val latestVersionCode: Long = 1L,
    val latestVersionName: String = "1.0",
    val apkDownloadUrl: String = "",
    val releaseNotes: String = "",
    @get:PropertyName("isForceUpdate") val isForceUpdate: Boolean = false,
    @get:PropertyName("isUpdateEnabled") val isUpdateEnabled: Boolean = false,
    val updatedBy: String = "",
    val targetUsername: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun appliesToUser(username: String?): Boolean {
        val target = targetUsername.trim().removePrefix("@").lowercase()
        if (target.isBlank() || target == "all" || target == "*") return true
        val current = username.orEmpty().trim().removePrefix("@").lowercase()
        return current.isNotBlank() && current == target
    }
}

@IgnoreExtraProperties
data class CloudAppInstallation(
    @DocumentId val id: String = "",
    val username: String = "",
    val versionCode: Long = 1L,
    val versionName: String = "1.0",
    val deviceModel: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudAppUpdateRecord(
    @DocumentId val id: String = "",
    val versionCode: Long = 1L,
    val versionName: String = "1.0",
    val apkDownloadUrl: String = "",
    val releaseNotes: String = "",
    @get:PropertyName("isForceUpdate") val isForceUpdate: Boolean = false,
    @get:PropertyName("isUpdateEnabled") val isUpdateEnabled: Boolean = false,
    val targetUsername: String = "",
    val updatedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toConfig(): CloudAppUpdateConfig = CloudAppUpdateConfig(
        id = id,
        latestVersionCode = versionCode,
        latestVersionName = versionName,
        apkDownloadUrl = apkDownloadUrl,
        releaseNotes = releaseNotes,
        isForceUpdate = isForceUpdate,
        isUpdateEnabled = isUpdateEnabled,
        updatedBy = updatedBy,
        targetUsername = targetUsername,
        updatedAt = updatedAt ?: createdAt
    )
}

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
    ): Boolean {
        val dbId = resolveDatabaseId() ?: return false
        return withContext(Dispatchers.IO) {
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
                restHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
                    resp.isSuccessful
                }
            } catch (_: Throwable) {
                false
            }
        }
    }

    private fun isPermissionDeniedError(t: Throwable): Boolean {
        val firestoreEx = generateSequence(t) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        return firestoreEx?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
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
            val restOk = !isPermissionDeniedError(e) && syncViaFirestoreRestApi(
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
            if (!restOk) {
                handleFirestoreError(e, OperationType.WRITE, fileRef.path)
                throw e
            }
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
            val restOk = !isPermissionDeniedError(e) && syncViaFirestoreRestApi(
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
            if (!restOk) {
                handleFirestoreError(e, OperationType.WRITE, docRef.path)
                throw e
            }
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
            val restOk = !isPermissionDeniedError(e) && syncViaFirestoreRestApi(
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
            if (!restOk) {
                handleFirestoreError(e, OperationType.CREATE, loginRef.path)
                throw e
            }
        }

        try {
            mailRef.set(mailPayload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val restOk = !isPermissionDeniedError(e) && syncViaFirestoreRestApi(
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
            if (!restOk) {
                handleFirestoreError(e, OperationType.CREATE, mailRef.path)
                throw e
            }
        }

        mailRef.id
    }

    /**
     * Initializes the `announcements` collection with a default welcome document (`welcome_krs_bullion`)
     * if it does not exist yet so the Admin can immediately view and edit `/announcements` in Firebase Console.
     */
    suspend fun bootstrapInitialAnnouncementIfNeeded(): Result<Unit> = runCatching {
        val docRef = db.collection("announcements").document("welcome_krs_bullion")
        val snap = runCatching { docRef.get().await() }.getOrNull()
        if (snap == null || !snap.exists()) {
            val payload = mapOf(
                "authorId" to "admin_km_jashwanth",
                "title" to "KRS Gold & Silver Official Live Desk",
                "message" to "Live 1-second MCX Bullion broadcast active. Official announcements are controlled by K.M. Jashwanth.",
                "priority" to "high",
                "isActive" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val sdkResult = runCatching { docRef.set(payload).await() }
            if (sdkResult.isFailure) {
                val err = sdkResult.exceptionOrNull()
                if (err != null && !isPermissionDeniedError(err)) {
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
                }
            }
        }
    }

    /**
     * Real-time stream of active official announcements published by the Admin in the database,
     * sorted with the newest / most recently updated announcement first.
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
                    ).sortedByDescending { item ->
                        val updatedMs = item.updatedAt?.toDate()?.time ?: 0L
                        val createdMs = item.createdAt?.toDate()?.time ?: 0L
                        maxOf(updatedMs, createdMs)
                    }
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

    /**
     * One-shot fetch of active official announcements sorted newest/latest first.
     */
    suspend fun getActiveAnnouncementsOnce(): Result<List<CloudAnnouncement>> = runCatching {
        val path = "announcements"
        try {
            val snap = db.collection(path)
                .whereEqualTo("isActive", true)
                .get()
                .await()
            snap.toObjects(
                CloudAnnouncement::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).sortedByDescending { item ->
                val updatedMs = item.updatedAt?.toDate()?.time ?: 0L
                val createdMs = item.createdAt?.toDate()?.time ?: 0L
                maxOf(updatedMs, createdMs)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            throw e
        }
    }

    private fun parseFlexibleLong(raw: Any?): Long? = when (raw) {
        is Long -> raw
        is Int -> raw.toLong()
        is Double -> raw.toLong()
        is Float -> raw.toLong()
        is Number -> raw.toLong()
        is String -> raw.trim().toLongOrNull() ?: raw.trim().toDoubleOrNull()?.toLong()
        else -> null
    }

    private fun parseFlexibleBool(raw: Any?): Boolean? = when (raw) {
        is Boolean -> raw
        is Number -> raw.toLong() != 0L
        is String -> when (raw.trim().lowercase()) {
            "true", "1", "yes", "on", "enabled" -> true
            "false", "0", "no", "off", "disabled" -> false
            else -> null
        }
        else -> null
    }

    private fun parseUpdateRecordSnapshot(snap: DocumentSnapshot): CloudAppUpdateRecord? {
        if (!snap.exists()) return null
        val data = snap.data ?: return null
        val versionCode = parseFlexibleLong(data["versionCode"])
            ?: parseFlexibleLong(data["latestVersionCode"])
            ?: 1L
        val versionName = (data["versionName"] as? String)?.trim()?.takeIf { it.isNotBlank() }
            ?: (data["latestVersionName"] as? String)?.trim()?.takeIf { it.isNotBlank() }
            ?: "1.0"
        val url = (data["apkDownloadUrl"] as? String)?.trim()
            ?: (data["downloadUrl"] as? String)?.trim()
            ?: (data["apkUrl"] as? String)?.trim()
            ?: ""
        val notes = (data["releaseNotes"] as? String)?.trim()
            ?: (data["notes"] as? String)?.trim()
            ?: ""
        val force = parseFlexibleBool(data["isForceUpdate"]) ?: false
        val enabled = if (data.containsKey("isUpdateEnabled")) {
            parseFlexibleBool(data["isUpdateEnabled"]) ?: false
        } else {
            versionCode > 1L && url.isNotBlank()
        }
        val updatedBy = (data["updatedBy"] as? String)?.trim() ?: ""
        val targetUser = (data["targetUsername"] as? String)?.trim() ?: ""
        val createdAt = data["createdAt"] as? Timestamp
        val updatedAt = data["updatedAt"] as? Timestamp

        return CloudAppUpdateRecord(
            id = snap.id,
            versionCode = versionCode,
            versionName = versionName,
            apkDownloadUrl = url,
            releaseNotes = notes,
            isForceUpdate = force,
            isUpdateEnabled = enabled,
            targetUsername = targetUser,
            updatedBy = updatedBy,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun parseAppUpdateSnapshot(snap: DocumentSnapshot): CloudAppUpdateConfig? {
        if (!snap.exists()) return null
        val data = snap.data ?: return null
        val versionCode = parseFlexibleLong(data["latestVersionCode"])
            ?: parseFlexibleLong(data["versionCode"])
            ?: 1L
        val versionName = (data["latestVersionName"] as? String)?.trim()?.takeIf { it.isNotBlank() }
            ?: (data["versionName"] as? String)?.trim()?.takeIf { it.isNotBlank() }
            ?: "1.0"
        val url = (data["apkDownloadUrl"] as? String)?.trim()
            ?: (data["downloadUrl"] as? String)?.trim()
            ?: (data["apkUrl"] as? String)?.trim()
            ?: ""
        val notes = (data["releaseNotes"] as? String)?.trim()
            ?: (data["notes"] as? String)?.trim()
            ?: ""
        val force = parseFlexibleBool(data["isForceUpdate"]) ?: false
        val enabled = parseFlexibleBool(data["isUpdateEnabled"]) ?: false
        val updatedBy = (data["updatedBy"] as? String)?.trim() ?: ""
        val targetUser = (data["targetUsername"] as? String)?.trim() ?: ""
        val updatedAt = data["updatedAt"] as? Timestamp

        return CloudAppUpdateConfig(
            id = snap.id,
            latestVersionCode = versionCode,
            latestVersionName = versionName,
            apkDownloadUrl = url,
            releaseNotes = notes,
            isForceUpdate = force,
            isUpdateEnabled = enabled,
            updatedBy = updatedBy,
            targetUsername = targetUser,
            updatedAt = updatedAt
        )
    }

    private fun extractRestFieldString(fields: JSONObject, key: String): String? {
        val obj = fields.optJSONObject(key) ?: return null
        return if (obj.has("stringValue")) obj.optString("stringValue") else null
    }

    private fun extractRestFieldLong(fields: JSONObject, key: String): Long? {
        val obj = fields.optJSONObject(key) ?: return null
        return when {
            obj.has("integerValue") -> obj.optString("integerValue").toLongOrNull()
            obj.has("doubleValue") -> obj.optDouble("doubleValue").toLong()
            obj.has("stringValue") -> obj.optString("stringValue").trim().toLongOrNull()
            else -> null
        }
    }

    private fun extractRestFieldBool(fields: JSONObject, key: String): Boolean? {
        val obj = fields.optJSONObject(key) ?: return null
        return when {
            obj.has("booleanValue") -> obj.optBoolean("booleanValue")
            obj.has("stringValue") -> parseFlexibleBool(obj.optString("stringValue"))
            obj.has("integerValue") -> obj.optString("integerValue").toLongOrNull()?.let { it != 0L }
            else -> null
        }
    }

    private fun parseRestUpdateFields(docId: String, fields: JSONObject): CloudAppUpdateRecord {
        val versionCode = extractRestFieldLong(fields, "versionCode")
            ?: extractRestFieldLong(fields, "latestVersionCode")
            ?: 1L
        val versionName = extractRestFieldString(fields, "versionName")?.takeIf { it.isNotBlank() }
            ?: extractRestFieldString(fields, "latestVersionName")?.takeIf { it.isNotBlank() }
            ?: "1.0"
        val url = extractRestFieldString(fields, "apkDownloadUrl")?.trim()
            ?: extractRestFieldString(fields, "downloadUrl")?.trim()
            ?: extractRestFieldString(fields, "apkUrl")?.trim()
            ?: ""
        val notes = extractRestFieldString(fields, "releaseNotes")?.trim()
            ?: extractRestFieldString(fields, "notes")?.trim()
            ?: ""
        val force = extractRestFieldBool(fields, "isForceUpdate") ?: false
        val enabled = extractRestFieldBool(fields, "isUpdateEnabled")
            ?: (versionCode > 1L && url.isNotBlank())
        val updatedBy = extractRestFieldString(fields, "updatedBy")?.trim() ?: ""
        val targetUser = extractRestFieldString(fields, "targetUsername")?.trim() ?: ""

        return CloudAppUpdateRecord(
            id = docId,
            versionCode = versionCode,
            versionName = versionName,
            apkDownloadUrl = url,
            releaseNotes = notes,
            isForceUpdate = force,
            isUpdateEnabled = enabled,
            targetUsername = targetUser,
            updatedBy = updatedBy
        )
    }

    /**
     * Direct HTTPS REST GET fallback for `/app_config/app_update` so every user receives database
     * updates even on devices where Firestore gRPC snapshots or offline cache are stale.
     */
    suspend fun fetchAppUpdateConfigViaRestApi(): CloudAppUpdateConfig? {
        val dbId = resolveDatabaseId() ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://firestore.googleapis.com/v1/projects/$CLOUD_PROJECT_ID/databases/$dbId/documents/app_config/app_update"
                val req = Request.Builder()
                    .url(url)
                    .get()
                    .header("Accept", "application/json")
                    .build()
                restHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val bodyStr = resp.body?.string().orEmpty()
                    if (bodyStr.isBlank()) return@withContext null
                    val root = JSONObject(bodyStr)
                    val fields = root.optJSONObject("fields") ?: return@withContext null

                    CloudAppUpdateConfig(
                        id = "app_update",
                        latestVersionCode = extractRestFieldLong(fields, "latestVersionCode")
                            ?: extractRestFieldLong(fields, "versionCode")
                            ?: 1L,
                        latestVersionName = extractRestFieldString(fields, "latestVersionName")?.takeIf { it.isNotBlank() }
                            ?: extractRestFieldString(fields, "versionName")?.takeIf { it.isNotBlank() }
                            ?: "1.0",
                        apkDownloadUrl = extractRestFieldString(fields, "apkDownloadUrl")?.trim()
                            ?: extractRestFieldString(fields, "downloadUrl")?.trim()
                            ?: extractRestFieldString(fields, "apkUrl")?.trim()
                            ?: "",
                        releaseNotes = extractRestFieldString(fields, "releaseNotes")?.trim()
                            ?: extractRestFieldString(fields, "notes")?.trim()
                            ?: "",
                        isForceUpdate = extractRestFieldBool(fields, "isForceUpdate") ?: false,
                        isUpdateEnabled = extractRestFieldBool(fields, "isUpdateEnabled") ?: false,
                        updatedBy = extractRestFieldString(fields, "updatedBy")?.trim() ?: "",
                        targetUsername = extractRestFieldString(fields, "targetUsername")?.trim() ?: ""
                    )
                }
            } catch (_: Throwable) {
                null
            }
        }
    }

    /**
     * Direct HTTPS REST GET fallback for the `/updates` collection so every update stored in
     * Cloud Firestore `/updates` is guaranteed to reach every user.
     */
    suspend fun fetchUpdatesCollectionViaRestApi(): List<CloudAppUpdateRecord> {
        val dbId = resolveDatabaseId() ?: return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://firestore.googleapis.com/v1/projects/$CLOUD_PROJECT_ID/databases/$dbId/documents/updates?pageSize=50"
                val req = Request.Builder()
                    .url(url)
                    .get()
                    .header("Accept", "application/json")
                    .build()
                restHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext emptyList()
                    val bodyStr = resp.body?.string().orEmpty()
                    if (bodyStr.isBlank()) return@withContext emptyList()
                    val root = JSONObject(bodyStr)
                    val docs = root.optJSONArray("documents") ?: return@withContext emptyList()
                    val list = mutableListOf<CloudAppUpdateRecord>()
                    for (i in 0 until docs.length()) {
                        val docObj = docs.optJSONObject(i) ?: continue
                        val fullName = docObj.optString("name", "")
                        val docId = fullName.substringAfterLast('/').ifBlank { "update_$i" }
                        val fields = docObj.optJSONObject("fields") ?: continue
                        list.add(parseRestUpdateFields(docId, fields))
                    }
                    list.sortedWith(
                        compareByDescending<CloudAppUpdateRecord> { it.versionCode }
                            .thenByDescending { it.updatedAt?.toDate()?.time ?: 0L }
                    )
                }
            } catch (_: Throwable) {
                emptyList()
            }
        }
    }

    private fun selectBestUpdateConfig(candidates: List<CloudAppUpdateConfig?>): CloudAppUpdateConfig? {
        val nonNull = candidates.filterNotNull()
        if (nonNull.isEmpty()) return null
        val enabledCandidates = nonNull.filter { it.isUpdateEnabled }
        val pool = enabledCandidates.ifEmpty { nonNull }
        return pool.maxWithOrNull(
            compareBy<CloudAppUpdateConfig> { it.latestVersionCode }
                .thenBy { it.updatedAt?.toDate()?.time ?: 0L }
                .thenBy { if (it.apkDownloadUrl.isNotBlank()) 1 else 0 }
        )
    }

    /**
     * Initializes both `/app_config/app_update` and the dedicated `/updates` collection (`v1_0_initial`)
     * with safe disabled defaults if missing so the Admin immediately sees the `updates` option in Firebase Console.
     */
    suspend fun bootstrapInitialAppUpdateConfigIfNeeded(): Result<Unit> = runCatching {
        val docRef = db.collection("app_config").document("app_update")
        val snap = runCatching { docRef.get().await() }.getOrNull()
        if (snap == null || !snap.exists()) {
            val defaultNotes = "KRS Gold & Silver Live MCX v1.0 is up to date. Add new releases here or via Admin settings to push OTA updates to all users."
            val payload = mapOf(
                "latestVersionCode" to 1L,
                "latestVersionName" to "1.0",
                "apkDownloadUrl" to "",
                "releaseNotes" to defaultNotes,
                "isForceUpdate" to false,
                "isUpdateEnabled" to false,
                "updatedBy" to "admin_km_jashwanth",
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val sdkResult = runCatching { docRef.set(payload).await() }
            if (sdkResult.isFailure) {
                val err = sdkResult.exceptionOrNull()
                if (err != null && !isPermissionDeniedError(err)) {
                    syncViaFirestoreRestApi(
                        collection = "app_config",
                        documentId = "app_update",
                        fieldsMap = mapOf(
                            "latestVersionCode" to 1,
                            "latestVersionName" to "1.0",
                            "apkDownloadUrl" to "",
                            "releaseNotes" to defaultNotes,
                            "isForceUpdate" to false,
                            "isUpdateEnabled" to false,
                            "updatedBy" to "admin_km_jashwanth",
                            "updatedAt" to "__SERVER_TIMESTAMP__"
                        )
                    )
                }
            }
        }

        val initialUpdateRef = db.collection("updates").document("v1_0_initial")
        val initialUpdateSnap = runCatching { initialUpdateRef.get().await() }.getOrNull()
        if (initialUpdateSnap == null || !initialUpdateSnap.exists()) {
            val initialNotes = "Initial KRS Gold & Silver Live MCX v1.0 release baseline in the /updates database collection."
            val entryPayload = mapOf(
                "versionCode" to 1L,
                "versionName" to "1.0",
                "apkDownloadUrl" to "",
                "releaseNotes" to initialNotes,
                "isForceUpdate" to false,
                "isUpdateEnabled" to false,
                "targetUsername" to "",
                "updatedBy" to "admin_km_jashwanth",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val sdkResult = runCatching { initialUpdateRef.set(entryPayload).await() }
            if (sdkResult.isFailure) {
                val err = sdkResult.exceptionOrNull()
                if (err != null && !isPermissionDeniedError(err)) {
                    syncViaFirestoreRestApi(
                        collection = "updates",
                        documentId = "v1_0_initial",
                        fieldsMap = mapOf(
                            "versionCode" to 1,
                            "versionName" to "1.0",
                            "apkDownloadUrl" to "",
                            "releaseNotes" to initialNotes,
                            "isForceUpdate" to false,
                            "isUpdateEnabled" to false,
                            "targetUsername" to "",
                            "updatedBy" to "admin_km_jashwanth",
                            "createdAt" to "__SERVER_TIMESTAMP__",
                            "updatedAt" to "__SERVER_TIMESTAMP__"
                        )
                    )
                }
            }
        }
    }

    /**
     * Fetches all release entries stored in `/updates` sorted newest-first.
     */
    suspend fun listUpdatesHistory(): Result<List<CloudAppUpdateRecord>> = runCatching {
        val path = "updates"
        var lastError: Exception? = null
        val sdkList = try {
            val snap = db.collection(path).get().await()
            snap.documents.mapNotNull { parseUpdateRecordSnapshot(it) }
                .sortedWith(
                    compareByDescending<CloudAppUpdateRecord> { it.versionCode }
                        .thenByDescending { it.updatedAt?.toDate()?.time ?: it.createdAt?.toDate()?.time ?: 0L }
                )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastError = e
            emptyList()
        }

        val restList = fetchUpdatesCollectionViaRestApi()
        if (restList.isNotEmpty()) {
            // Merge preferring SDK timestamps where IDs match while including any newer REST docs
            val byId = LinkedHashMap<String, CloudAppUpdateRecord>()
            restList.forEach { byId[it.id] = it }
            sdkList.forEach { sdkItem ->
                val existing = byId[sdkItem.id]
                if (existing == null || sdkItem.versionCode >= existing.versionCode) {
                    byId[sdkItem.id] = sdkItem
                }
            }
            byId.values.sortedWith(
                compareByDescending<CloudAppUpdateRecord> { it.versionCode }
                    .thenByDescending { it.updatedAt?.toDate()?.time ?: it.createdAt?.toDate()?.time ?: 0L }
            )
        } else if (sdkList.isNotEmpty()) {
            sdkList
        } else if (lastError != null) {
            handleFirestoreError(lastError, OperationType.LIST, path)
            throw lastError
        } else {
            emptyList()
        }
    }

    /**
     * Real-time stream of all update releases stored in `/updates`.
     */
    fun observeUpdatesHistory(): Flow<List<CloudAppUpdateRecord>> = flow {
        val path = "updates"
        val initialRest = fetchUpdatesCollectionViaRestApi()
        if (initialRest.isNotEmpty()) {
            emit(initialRest)
        }

        var attempt = 0
        while (kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.isActive != false) {
            try {
                emitAll(
                    db.collection(path)
                        .snapshots()
                        .map { snap ->
                            snap.documents.mapNotNull { parseUpdateRecordSnapshot(it) }
                                .sortedWith(
                                    compareByDescending<CloudAppUpdateRecord> { it.versionCode }
                                        .thenByDescending { it.updatedAt?.toDate()?.time ?: it.createdAt?.toDate()?.time ?: 0L }
                                )
                        }
                )
                break
            } catch (e: CancellationException) {
                throw e
            } catch (error: Throwable) {
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                }
                val restFallback = fetchUpdatesCollectionViaRestApi()
                if (restFallback.isNotEmpty()) {
                    emit(restFallback)
                }
                attempt++
                val backoffMs = minOf(30_000L, 4_000L * attempt)
                kotlinx.coroutines.delay(backoffMs)
            }
        }
    }

    /**
     * Fetches the active remote app update config across BOTH `/updates` and `/app_config/app_update`
     * (via Firestore SDK + REST API fallback), selecting the newest enabled version.
     */
    suspend fun getAppUpdateConfig(): Result<CloudAppUpdateConfig?> = runCatching {
        val docRef = db.collection("app_config").document("app_update")
        var lastError: Exception? = null
        val sdkLegacy = try {
            parseAppUpdateSnapshot(docRef.get().await())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastError = e
            null
        }

        val sdkUpdates = try {
            db.collection("updates").get().await()
                .documents.mapNotNull { parseUpdateRecordSnapshot(it)?.toConfig() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (lastError == null) lastError = e
            emptyList()
        }

        val restLegacy = fetchAppUpdateConfigViaRestApi()
        val restUpdates = fetchUpdatesCollectionViaRestApi().map { it.toConfig() }

        val best = selectBestUpdateConfig(listOf(sdkLegacy, restLegacy) + sdkUpdates + restUpdates)
        if (best != null) {
            best
        } else if (lastError != null && sdkLegacy == null && sdkUpdates.isEmpty()) {
            handleFirestoreError(lastError, OperationType.GET, docRef.path)
            throw lastError
        } else {
            null
        }
    }

    /**
     * Real-time stream combining `/updates` collection AND `/app_config/app_update` with immediate
     * REST fallback and automatic retry so all users receive new updates stored in the database.
     */
    fun observeAppUpdateConfig(): Flow<CloudAppUpdateConfig?> = flow {
        val path = "updates"
        val initialRestUpdates = fetchUpdatesCollectionViaRestApi().map { it.toConfig() }
        val initialRestLegacy = fetchAppUpdateConfigViaRestApi()
        val initialBest = selectBestUpdateConfig(initialRestUpdates + listOf(initialRestLegacy))
        if (initialBest != null) {
            emit(initialBest)
        }

        var attempt = 0
        while (kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.isActive != false) {
            try {
                val updatesFlow = db.collection("updates")
                    .snapshots()
                    .map { snap -> snap.documents.mapNotNull { parseUpdateRecordSnapshot(it)?.toConfig() } }
                val legacyFlow = db.collection("app_config")
                    .document("app_update")
                    .snapshots()
                    .map { snap -> parseAppUpdateSnapshot(snap) }

                emitAll(
                    combine(updatesFlow, legacyFlow) { updatesList, legacyCfg ->
                        selectBestUpdateConfig(updatesList + listOf(legacyCfg))
                    }
                )
                break
            } catch (e: CancellationException) {
                throw e
            } catch (error: Throwable) {
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null) {
                    handleFirestoreError(exceptionToLog, OperationType.GET, path)
                }
                val restUpdates = fetchUpdatesCollectionViaRestApi().map { it.toConfig() }
                val restLegacy = fetchAppUpdateConfigViaRestApi()
                val restBest = selectBestUpdateConfig(restUpdates + listOf(restLegacy))
                if (restBest != null) {
                    emit(restBest)
                }
                attempt++
                val backoffMs = minOf(30_000L, 4_000L * attempt)
                kotlinx.coroutines.delay(backoffMs)
            }
        }
    }

    /**
     * Records the current user's installed app build into `/app_installations/{accountId}` so
     * Admin can verify update adoption across all individual users in Cloud Firestore.
     */
    suspend fun reportAppInstallation(
        username: String,
        versionCode: Long,
        versionName: String,
        deviceModel: String
    ): Result<Unit> = runCatching {
        val docId = resolveAccountId(username)
        val cleanUser = username.trim().removePrefix("@").ifBlank { docId }.take(60)
        val cleanVersionName = versionName.trim().ifBlank { "1.0" }.take(40)
        val cleanModel = deviceModel.trim().ifBlank { "Android Device" }.take(100)
        val cleanCode = versionCode.coerceIn(1L, 1_000_000L)

        val payload = mapOf(
            "username" to cleanUser,
            "versionCode" to cleanCode,
            "versionName" to cleanVersionName,
            "deviceModel" to cleanModel,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            db.collection("app_installations").document(docId).set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val restOk = !isPermissionDeniedError(e) && syncViaFirestoreRestApi(
                collection = "app_installations",
                documentId = docId,
                fieldsMap = mapOf(
                    "username" to cleanUser,
                    "versionCode" to cleanCode,
                    "versionName" to cleanVersionName,
                    "deviceModel" to cleanModel,
                    "updatedAt" to "__SERVER_TIMESTAMP__"
                )
            )
            if (!restOk) {
                throw e
            }
        }
    }

    // ==================== ADMIN-ONLY DATABASE OPERATIONS ====================

    private suspend fun ensureAdminDocIfEligible() {
        val user = auth.currentUser ?: return
        val email = user.email?.trim()?.lowercase().orEmpty()
        if (email !in ADMIN_EMAILS) return
        try {
            val adminRef = db.collection("admins").document(user.uid)
            val snap = adminRef.get().await()
            if (!snap.exists()) {
                adminRef.set(
                    mapOf(
                        "adminId" to user.uid,
                        "email" to email,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (_: Throwable) {}
    }

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
        ensureAdminDocIfEligible()
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
     * Publishes a new app update into BOTH the dedicated `/updates/{updateId}` collection (so all updates
     * and changelogs are permanently stored under `updates` in Cloud Firestore) AND `/app_config/app_update`
     * (Strictly restricted to Admin in firestore.rules).
     */
    suspend fun publishAppUpdateAsAdmin(
        latestVersionCode: Long,
        latestVersionName: String,
        apkDownloadUrl: String,
        releaseNotes: String,
        isForceUpdate: Boolean,
        isUpdateEnabled: Boolean,
        targetUsername: String = ""
    ): Result<Unit> = runCatching {
        val adminUid = auth.currentUser?.uid
            ?: throw IllegalStateException("Admin must be signed in with Google before publishing app updates.")
        ensureAdminDocIfEligible()
        val configDocRef = db.collection("app_config").document("app_update")
        val cleanVersionCode = latestVersionCode.coerceIn(1L, 1_000_000L)
        val cleanVersionName = latestVersionName.trim().ifBlank { "1.0" }.take(40)
        val cleanUrl = apkDownloadUrl.trim().take(1000)
        val cleanNotes = releaseNotes.trim().ifBlank { "Performance improvements and live bullion updates." }.take(2000)
        val cleanTarget = targetUsername.trim().removePrefix("@").take(60)
        val safeVerSlug = cleanVersionName.lowercase().replace(Regex("[^a-z0-9_\\-]"), "_").ifBlank { "rel" }
        val updateDocId = "v${safeVerSlug}_b${cleanVersionCode}".take(80)
        val updatesDocRef = db.collection("updates").document(updateDocId)

        val updateRecordPayload = mapOf(
            "versionCode" to cleanVersionCode,
            "versionName" to cleanVersionName,
            "apkDownloadUrl" to cleanUrl,
            "releaseNotes" to cleanNotes,
            "isForceUpdate" to isForceUpdate,
            "isUpdateEnabled" to isUpdateEnabled,
            "targetUsername" to cleanTarget,
            "updatedBy" to adminUid.take(128),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val configPayload = mapOf(
            "latestVersionCode" to cleanVersionCode,
            "latestVersionName" to cleanVersionName,
            "apkDownloadUrl" to cleanUrl,
            "releaseNotes" to cleanNotes,
            "isForceUpdate" to isForceUpdate,
            "isUpdateEnabled" to isUpdateEnabled,
            "updatedBy" to adminUid.take(128),
            "targetUsername" to cleanTarget,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            updatesDocRef.set(updateRecordPayload).await()
            configDocRef.set(configPayload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, updatesDocRef.path)
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

    /**
     * Lists all per-user app installations & versions (Strictly restricted to Admin in firestore.rules).
     */
    suspend fun listAppInstallationsForAdmin(): Result<List<CloudAppInstallation>> = runCatching {
        val path = "app_installations"
        try {
            val snap = db.collection(path).get().await()
            snap.toObjects(CloudAppInstallation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.updatedAt?.toDate()?.time ?: 0L }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            throw e
        }
    }
}
