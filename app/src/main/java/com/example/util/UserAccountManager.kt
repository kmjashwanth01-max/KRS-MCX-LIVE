package com.example.util

import android.content.Context
import android.content.SharedPreferences
import com.example.data.repository.AdminCloudDatabaseRepository
import com.example.model.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class UserProfile(
    val id: String = UUID.randomUUID().toString(),
    val username: String = "",
    val name: String,
    val email: String,
    val phone: String,
    val city: String,
    val role: String,
    val pin4Digit: String = "1234",
    val isAppLockEnabled: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val isDarkMode: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    val initials: String
        get() {
            val src = name.ifBlank { username }
            val parts = src.trim().split(" ").filter { it.isNotBlank() }
            return when {
                parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
                parts.isNotEmpty() -> parts[0].take(2).uppercase()
                else -> "KR"
            }
        }

    val textFileName: String
        get() {
            val clean = username.trim().removePrefix("@").lowercase()
                .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                .ifBlank { "trader" }
            return "$clean.txt"
        }

    fun toTextFileContent(): String {
        return AdminCloudDatabaseRepository.buildUserTextFileContent(
            userId = id,
            username = username,
            name = name,
            pin4Digit = pin4Digit,
            city = city,
            role = role,
            email = email,
            phone = phone,
            isDarkMode = isDarkMode,
            isAppLockEnabled = isAppLockEnabled,
            isBiometricEnabled = isBiometricEnabled
        )
    }
}

/**
 * Persistent Account & Synced Database Manager.
 * - Saves each user in their own dedicated plain-text file (`user_accounts_files/<username>.txt`) on disk
 *   AND in `/user_files/{username}` + `/user_accounts/{username}` in Cloud Firestore (`AdminCloudDatabaseRepository`).
 * - Unlinking or signing out of an account on the device NEVER deletes the user's `.txt` file or database record,
 *   so users can always sign back in with their Username and Password / 4-Digit PIN.
 * - Checks whether an account already exists before creating a new account ("Account Already Exists - Sign In" popup).
 * - Verifies Username and Password / PIN against the synced database on sign-in (Access Granted vs Access Denied).
 */
object UserAccountManager {

    private const val PREFS_NAME = "krs_bullion_individual_account_prefs"
    private const val DB_PREFS_NAME = "krs_bullion_synced_database_prefs"
    private const val SYNCED_DB_FILENAME = "krs_synced_accounts_db.json"
    private const val USER_TEXT_FILES_DIR = "user_accounts_files"

    private const val KEY_MY_PROFILE_JSON = "my_profile_json"
    private const val KEY_ALL_ACCOUNTS_JSON = "all_registered_accounts_json"
    private const val KEY_SYNCED_DATABASE_ACCOUNTS_JSON = "synced_database_accounts_json"
    private const val KEY_TOTAL_ACCOUNTS_CREATED_COUNT = "total_accounts_created_count"
    private const val KEY_TOTAL_SIGN_IN_COUNT = "total_sign_in_count"
    private const val KEY_DARK_MODE_PREF = "user_dark_mode_pref"
    private const val KEY_ONBOARDING_DONE = "onboarding_done_flag"
    private const val KEY_MY_CHAT_JSON = "my_isolated_chat_json"

    private var prefs: SharedPreferences? = null
    private var dbPrefs: SharedPreferences? = null
    private var appContext: Context? = null
    private val dbScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    private val _allAccounts = MutableStateFlow<List<UserProfile>>(emptyList())
    val allAccounts: StateFlow<List<UserProfile>> = _allAccounts.asStateFlow()

    private val _totalAccountsCreatedCount = MutableStateFlow(1)
    val totalAccountsCreatedCount: StateFlow<Int> = _totalAccountsCreatedCount.asStateFlow()

    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _isAppLocked = MutableStateFlow(false)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private val _requiresOnboarding = MutableStateFlow(false)
    val requiresOnboarding: StateFlow<Boolean> = _requiresOnboarding.asStateFlow()

    init {
        // Fallback default for JVM unit tests
        val testUser = UserProfile(
            id = "user_device_owner",
            username = "jashwanth",
            name = "K.M. Jashwanth",
            email = "kmjashwanth01@gmail.com",
            phone = "+91 98422 12345",
            city = "Coimbatore, Tamil Nadu",
            role = "Bullion Administrator",
            pin4Digit = "1234",
            isAppLockEnabled = false,
            isBiometricEnabled = false,
            isDarkMode = true
        )
        _currentUser.value = testUser
        _allAccounts.value = listOf(testUser)
        _totalAccountsCreatedCount.value = 1
        _isDarkMode.value = true
    }

    fun init(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx
        prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        dbPrefs = appCtx.getSharedPreferences(DB_PREFS_NAME, Context.MODE_PRIVATE)
        loadAccountsAndPreferences()

        val p = prefs
        if (p != null) {
            val onboardingDone = p.getBoolean(KEY_ONBOARDING_DONE, false)
            _requiresOnboarding.value = !onboardingDone || (_currentUser.value == null)
        }

        val user = _currentUser.value
        if (user != null && user.isAppLockEnabled) {
            _isAppLocked.value = true
        }

        syncAllAccountsToCloudNow()
    }

    /**
     * Immediately pushes all registered local accounts, per-user `.txt` files (`user_files`),
     * `user_accounts`, `login_events`, `admin_mail_logs`, and `announcements` to Cloud Firestore.
     */
    fun syncAllAccountsToCloudNow() {
        val appCtx = appContext ?: return
        syncDatabaseFromStorage()
        dbScope.launch {
            try {
                val repo = AdminCloudDatabaseRepository(appCtx)
                repo.bootstrapInitialAnnouncementIfNeeded()

                val accountsToSync = _allAccounts.value.ifEmpty {
                    listOf(
                        UserProfile(
                            id = "jashwanth",
                            username = "jashwanth",
                            name = "K.M. Jashwanth",
                            email = "kmjashwanth01@gmail.com",
                            phone = "+91 98422 12345",
                            city = "Coimbatore",
                            role = "Bullion Administrator",
                            pin4Digit = "1234",
                            isAppLockEnabled = false,
                            isBiometricEnabled = false,
                            isDarkMode = _isDarkMode.value
                        )
                    )
                }

                writeIndividualUserTextFiles(accountsToSync)

                for (acc in accountsToSync) {
                    repo.saveUserAccount(
                        username = acc.username,
                        name = acc.name,
                        city = acc.city,
                        role = acc.role,
                        isDarkMode = acc.isDarkMode,
                        isAppLockEnabled = acc.isAppLockEnabled,
                        isBiometricEnabled = acc.isBiometricEnabled,
                        email = acc.email,
                        phone = acc.phone,
                        pin4Digit = acc.pin4Digit
                    )
                }

                val primaryUser = _currentUser.value ?: accountsToSync.first()
                val body = AppDiagnosticsManager.generateAccountEventEmailBody(
                    eventType = "DATABASE INITIALIZED & USER TEXT FILES SYNCED",
                    user = primaryUser,
                    totalAccountsCount = maxOf(1, _totalAccountsCreatedCount.value),
                    allAccounts = accountsToSync
                )
                repo.recordLoginAndMailToAdminDatabase(
                    username = primaryUser.username,
                    name = primaryUser.name,
                    eventType = "DATABASE INITIALIZED & USER TEXT FILES SYNCED",
                    savedTheme = if (primaryUser.isDarkMode) "Dark Titanium Mode" else "Light Platinum Mode",
                    totalAccountsCount = maxOf(1, _totalAccountsCreatedCount.value),
                    subject = "[KRS Bullion] Cloud Database & User Files Synced • Total Accounts: ${maxOf(1, _totalAccountsCreatedCount.value)}",
                    body = body
                )
            } catch (_: Throwable) {}
        }
    }

    /**
     * Parses a `UserProfile` directly from a plain-text `<username>.txt` file content.
     */
    fun parseUserFromTextFile(textContent: String, fallbackDark: Boolean = true): UserProfile? {
        if (textContent.isBlank()) return null
        val map = mutableMapOf<String, String>()
        for (line in textContent.lines()) {
            val idx = line.indexOf(':')
            if (idx > 0) {
                val key = line.substring(0, idx).trim().uppercase()
                val value = line.substring(idx + 1).trim()
                if (key.isNotBlank()) {
                    map[key] = value
                }
            }
        }
        val rawUsername = map["USERNAME"]?.removePrefix("@")?.trim().orEmpty()
        if (rawUsername.isBlank()) return null

        val rawId = map["USER_ID"]?.ifBlank { rawUsername } ?: rawUsername
        val rawName = map["FULL_NAME"]?.ifBlank { rawUsername } ?: rawUsername
        val rawPin = map["PASSWORD_PIN"]?.ifBlank { "1234" } ?: "1234"
        val rawCity = map["CITY"]?.ifBlank { "Coimbatore" } ?: "Coimbatore"
        val rawRole = map["ROLE"]?.ifBlank { "Bullion Trader" } ?: "Bullion Trader"
        val rawEmail = map["EMAIL"]?.let { if (it.equals("Not provided", ignoreCase = true)) "" else it } ?: ""
        val rawPhone = map["MOBILE_PHONE"]?.let { if (it.equals("Not provided", ignoreCase = true)) "" else it } ?: ""
        val isDark = map["IS_DARK_MODE"]?.toBooleanStrictOrNull()
            ?: map["THEME_MODE"]?.contains("Dark", ignoreCase = true)
            ?: fallbackDark
        val appLock = map["APP_LOCK_ENABLED"]?.toBooleanStrictOrNull() ?: false
        val biometric = map["BIOMETRIC_ENABLED"]?.toBooleanStrictOrNull() ?: false

        return UserProfile(
            id = rawId,
            username = rawUsername,
            name = rawName,
            email = rawEmail,
            phone = rawPhone,
            city = rawCity,
            role = rawRole,
            pin4Digit = rawPin,
            isAppLockEnabled = appLock,
            isBiometricEnabled = biometric,
            isDarkMode = isDark
        )
    }

    /**
     * Writes each user account into its own dedicated `<username>.txt` file inside `filesDir/user_accounts_files/`.
     */
    private fun writeIndividualUserTextFiles(accounts: List<UserProfile>) {
        val ctx = appContext ?: return
        try {
            val dir = File(ctx.filesDir, USER_TEXT_FILES_DIR)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            for (acc in accounts) {
                val file = File(dir, acc.textFileName)
                file.writeText(acc.toTextFileContent())
            }
        } catch (_: Exception) {}
    }

    /**
     * Reads all individual `<username>.txt` user files from `filesDir/user_accounts_files/`.
     */
    private fun readIndividualUserTextFiles(fallbackDark: Boolean): List<UserProfile> {
        val ctx = appContext ?: return emptyList()
        val list = mutableListOf<UserProfile>()
        try {
            val dir = File(ctx.filesDir, USER_TEXT_FILES_DIR)
            if (dir.exists() && dir.isDirectory) {
                val txtFiles = dir.listFiles { f -> f.isFile && f.name.endsWith(".txt", ignoreCase = true) }
                if (txtFiles != null) {
                    for (f in txtFiles) {
                        val parsed = parseUserFromTextFile(f.readText(), fallbackDark)
                        if (parsed != null) {
                            list.add(parsed)
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return list
    }

    /**
     * Checks both the local Synced Database (`.txt` files + JSON) and the Cloud Firestore Database
     * (`/user_accounts/{username}` and `/user_files/{username}`) for an existing account,
     * and if found in Cloud Firestore, imports it into the local Synced Database & writes `<username>.txt`.
     */
    suspend fun fetchAndSyncAccountFromCloudIfPresent(username: String): UserProfile? {
        val localMatch = findExistingAccount(username = username)
        if (localMatch != null) return localMatch

        val ctx = appContext ?: return null
        return try {
            val repo = AdminCloudDatabaseRepository(ctx)
            val cloudAcc = repo.getUserAccount(username).getOrNull()
            if (cloudAcc != null && cloudAcc.username.isNotBlank()) {
                val imported = UserProfile(
                    id = cloudAcc.userId.ifBlank { cloudAcc.username },
                    username = cloudAcc.username,
                    name = cloudAcc.name.ifBlank { cloudAcc.username },
                    email = cloudAcc.email,
                    phone = cloudAcc.phone,
                    city = cloudAcc.city.ifBlank { "Coimbatore" },
                    role = cloudAcc.role.ifBlank { "Bullion Trader" },
                    pin4Digit = cloudAcc.pin4Digit.ifBlank { "1234" },
                    isAppLockEnabled = cloudAcc.isAppLockEnabled,
                    isBiometricEnabled = cloudAcc.isBiometricEnabled,
                    isDarkMode = cloudAcc.isDarkMode
                )
                mergeAccountIntoMemoryAndDisk(imported)
                return imported
            }

            val cloudFile = repo.getUserTextFile(username).getOrNull()
            if (cloudFile != null) {
                val fromText = parseUserFromTextFile(cloudFile.fileContentText, _isDarkMode.value)
                    ?: UserProfile(
                        id = cloudFile.username,
                        username = cloudFile.username,
                        name = cloudFile.name.ifBlank { cloudFile.username },
                        email = cloudFile.email,
                        phone = cloudFile.phone,
                        city = cloudFile.city.ifBlank { "Coimbatore" },
                        role = cloudFile.role.ifBlank { "Bullion Trader" },
                        pin4Digit = cloudFile.pin4Digit.ifBlank { "1234" },
                        isDarkMode = cloudFile.themeMode.contains("Dark", ignoreCase = true)
                    )
                mergeAccountIntoMemoryAndDisk(fromText)
                return fromText
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    private fun parseUserJson(obj: JSONObject, fallbackDark: Boolean): UserProfile {
        return UserProfile(
            id = obj.optString("id", UUID.randomUUID().toString()),
            username = obj.optString("username", obj.optString("name", "trader")).trim().removePrefix("@"),
            name = obj.optString("name", "Trader"),
            email = obj.optString("email", ""),
            phone = obj.optString("phone", ""),
            city = obj.optString("city", "Coimbatore"),
            role = obj.optString("role", "Bullion Trader"),
            pin4Digit = obj.optString("pin4Digit", "1234"),
            isAppLockEnabled = obj.optBoolean("isAppLockEnabled", false),
            isBiometricEnabled = obj.optBoolean("isBiometricEnabled", false),
            isDarkMode = obj.optBoolean("isDarkMode", fallbackDark),
            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
        )
    }

    private fun toUserJson(user: UserProfile): JSONObject {
        return JSONObject().apply {
            put("id", user.id)
            put("fileName", user.textFileName)
            put("userTextFile", user.toTextFileContent())
            put("username", user.username.trim().removePrefix("@"))
            put("name", user.name)
            put("email", user.email)
            put("phone", user.phone)
            put("city", user.city)
            put("role", user.role)
            put("pin4Digit", user.pin4Digit)
            put("isAppLockEnabled", user.isAppLockEnabled)
            put("isBiometricEnabled", user.isBiometricEnabled)
            put("isDarkMode", user.isDarkMode)
            put("createdAt", user.createdAt)
        }
    }

    private fun parseAccountListFromJsonString(jsonStr: String?, fallbackDark: Boolean): List<UserProfile> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        val list = mutableListOf<UserProfile>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                list.add(parseUserJson(arr.getJSONObject(i), fallbackDark))
            }
        } catch (_: Exception) {}
        return list
    }

    private fun readSyncedDatabaseFile(fallbackDark: Boolean): List<UserProfile> {
        val ctx = appContext ?: return emptyList()
        return try {
            val file = File(ctx.filesDir, SYNCED_DB_FILENAME)
            if (file.exists()) {
                parseAccountListFromJsonString(file.readText(), fallbackDark)
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeSyncedDatabaseFile(accounts: List<UserProfile>) {
        val ctx = appContext ?: return
        try {
            val arr = JSONArray()
            for (u in accounts) {
                arr.put(toUserJson(u))
            }
            val jsonStr = arr.toString()
            File(ctx.filesDir, SYNCED_DB_FILENAME).writeText(jsonStr)
            dbPrefs?.edit()?.putString(KEY_SYNCED_DATABASE_ACCOUNTS_JSON, jsonStr)?.apply()
            writeIndividualUserTextFiles(accounts)
        } catch (_: Exception) {}
    }

    /**
     * Merges accounts from all persistent storage layers (individual per-user `.txt` files,
     * internal database file, dedicated database prefs, and session prefs) so unlinking an account never loses records.
     */
    fun syncDatabaseFromStorage() {
        val p = prefs ?: return
        val savedDarkPref = p.getBoolean(KEY_DARK_MODE_PREF, _isDarkMode.value)
        val mergedByUsername = linkedMapOf<String, UserProfile>()

        fun addAllToMerged(source: List<UserProfile>) {
            for (acc in source) {
                val key = acc.username.trim().removePrefix("@").lowercase()
                if (key.isNotBlank()) {
                    mergedByUsername[key] = acc
                }
            }
        }

        // 0. Read from individual per-user `<username>.txt` files
        addAllToMerged(readIndividualUserTextFiles(savedDarkPref))
        // 1. Read from internal database file
        addAllToMerged(readSyncedDatabaseFile(savedDarkPref))
        // 2. Read from dedicated database SharedPreferences
        addAllToMerged(parseAccountListFromJsonString(dbPrefs?.getString(KEY_SYNCED_DATABASE_ACCOUNTS_JSON, null), savedDarkPref))
        // 3. Read from main SharedPreferences
        addAllToMerged(parseAccountListFromJsonString(p.getString(KEY_ALL_ACCOUNTS_JSON, null), savedDarkPref))
        // 4. Read from in-memory _allAccounts
        addAllToMerged(_allAccounts.value)
        // 5. Read from active user if present
        _currentUser.value?.let { addAllToMerged(listOf(it)) }

        val finalAccounts = mergedByUsername.values.toList()
        _allAccounts.value = finalAccounts
        if (finalAccounts.isNotEmpty()) {
            writeSyncedDatabaseFile(finalAccounts)
        }
    }

    private fun mergeAccountIntoMemoryAndDisk(account: UserProfile) {
        val mutableAll = _allAccounts.value.toMutableList()
        val cleanUser = account.username.trim().removePrefix("@")
        val idx = mutableAll.indexOfFirst {
            it.id == account.id || it.username.trim().removePrefix("@").equals(cleanUser, ignoreCase = true)
        }
        if (idx >= 0) {
            mutableAll[idx] = account
        } else {
            mutableAll.add(account)
        }
        _allAccounts.value = mutableAll
        saveAllAccountsRegistryToDisk()
    }

    private fun loadAccountsAndPreferences() {
        val p = prefs ?: return
        val savedDarkPref = p.getBoolean(KEY_DARK_MODE_PREF, true)
        _isDarkMode.value = savedDarkPref

        val mergedByUsername = linkedMapOf<String, UserProfile>()
        fun addList(list: List<UserProfile>) {
            for (acc in list) {
                val key = acc.username.trim().removePrefix("@").lowercase()
                if (key.isNotBlank()) {
                    mergedByUsername[key] = acc
                }
            }
        }

        addList(readIndividualUserTextFiles(savedDarkPref))
        addList(readSyncedDatabaseFile(savedDarkPref))
        addList(parseAccountListFromJsonString(dbPrefs?.getString(KEY_SYNCED_DATABASE_ACCOUNTS_JSON, null), savedDarkPref))
        addList(parseAccountListFromJsonString(p.getString(KEY_ALL_ACCOUNTS_JSON, null), savedDarkPref))

        val activeJsonStr = p.getString(KEY_MY_PROFILE_JSON, null)
        var activeUser: UserProfile? = null
        if (!activeJsonStr.isNullOrBlank()) {
            try {
                activeUser = parseUserJson(JSONObject(activeJsonStr), savedDarkPref)
            } catch (_: Exception) {}
        }

        if (activeUser != null) {
            _currentUser.value = activeUser
            _isDarkMode.value = activeUser.isDarkMode
            val key = activeUser.username.trim().removePrefix("@").lowercase()
            if (key.isNotBlank()) {
                mergedByUsername[key] = activeUser
            }
        } else {
            _currentUser.value = null
        }

        val loadedList = mergedByUsername.values.toList()
        _allAccounts.value = loadedList
        if (loadedList.isNotEmpty()) {
            writeSyncedDatabaseFile(loadedList)
        }

        val storedCount = maxOf(
            p.getInt(KEY_TOTAL_ACCOUNTS_CREATED_COUNT, loadedList.size),
            dbPrefs?.getInt(KEY_TOTAL_ACCOUNTS_CREATED_COUNT, loadedList.size) ?: loadedList.size
        )
        _totalAccountsCreatedCount.value = maxOf(storedCount, loadedList.size)
    }

    private fun saveAllAccountsRegistryToDisk() {
        if (prefs == null && dbPrefs == null && appContext == null) return
        try {
            val arr = JSONArray()
            for (u in _allAccounts.value) {
                arr.put(toUserJson(u))
            }
            val jsonStr = arr.toString()

            prefs?.edit()
                ?.putString(KEY_ALL_ACCOUNTS_JSON, jsonStr)
                ?.putInt(KEY_TOTAL_ACCOUNTS_CREATED_COUNT, _totalAccountsCreatedCount.value)
                ?.apply()

            dbPrefs?.edit()
                ?.putString(KEY_SYNCED_DATABASE_ACCOUNTS_JSON, jsonStr)
                ?.putInt(KEY_TOTAL_ACCOUNTS_CREATED_COUNT, _totalAccountsCreatedCount.value)
                ?.apply()

            writeSyncedDatabaseFile(_allAccounts.value)
        } catch (_: Exception) {}
    }

    private fun persistStateToDisk(activeUser: UserProfile?) {
        _currentUser.value = activeUser

        if (activeUser != null) {
            _isDarkMode.value = activeUser.isDarkMode
            val mutableAll = _allAccounts.value.toMutableList()
            val cleanUser = activeUser.username.trim().removePrefix("@")
            val idx = mutableAll.indexOfFirst {
                it.id == activeUser.id || it.username.trim().removePrefix("@").equals(cleanUser, ignoreCase = true)
            }
            if (idx >= 0) {
                mutableAll[idx] = activeUser
            } else {
                mutableAll.add(activeUser)
            }
            _allAccounts.value = mutableAll
            if (_totalAccountsCreatedCount.value < mutableAll.size) {
                _totalAccountsCreatedCount.value = mutableAll.size
            }
        }

        saveAllAccountsRegistryToDisk()

        val p = prefs ?: return
        val editor = p.edit()
        editor.putBoolean(KEY_DARK_MODE_PREF, _isDarkMode.value)
        editor.putInt(KEY_TOTAL_ACCOUNTS_CREATED_COUNT, _totalAccountsCreatedCount.value)

        if (activeUser == null) {
            editor.remove(KEY_MY_PROFILE_JSON)
            editor.putBoolean(KEY_ONBOARDING_DONE, false)
        } else {
            editor.putString(KEY_MY_PROFILE_JSON, toUserJson(activeUser).toString())
            editor.putBoolean(KEY_ONBOARDING_DONE, true)
        }
        editor.apply()
    }

    /**
     * Checks whether an account already exists in the Synced App Database Manager by username or email.
     * Automatically syncs from persistent storage first so unlinked accounts are always found.
     */
    fun findExistingAccount(
        username: String,
        email: String = "",
        phone: String = ""
    ): UserProfile? {
        syncDatabaseFromStorage()

        val cleanUser = username.trim().removePrefix("@")
        val cleanEmail = email.trim()

        val candidates = (_allAccounts.value + listOfNotNull(_currentUser.value)).distinctBy {
            it.username.trim().removePrefix("@").lowercase()
        }

        if (cleanUser.isNotBlank()) {
            candidates.firstOrNull { acc ->
                acc.username.trim().removePrefix("@").equals(cleanUser, ignoreCase = true)
            }?.let { return it }
        }

        if (cleanEmail.isNotBlank()) {
            candidates.firstOrNull { acc ->
                acc.email.isNotBlank() && acc.email.trim().equals(cleanEmail, ignoreCase = true)
            }?.let { return it }
        }

        return null
    }

    fun doesAccountExist(
        username: String,
        email: String = "",
        phone: String = ""
    ): Boolean = findExistingAccount(username = username, email = email, phone = phone) != null

    /**
     * Attempts to create a brand-new account after checking if the account already exists in the Database Manager.
     * If the account already exists, returns Pair(null, existingAccount) so the caller can show a popup prompting Sign In.
     */
    fun tryCreateNewAccount(
        username: String,
        name: String,
        email: String = "",
        phone: String = "",
        city: String = "Coimbatore",
        role: String = "Bullion Trader",
        pin4Digit: String = "1234",
        enableLockImmediately: Boolean = true,
        enableBiometric: Boolean = false
    ): Pair<UserProfile?, UserProfile?> {
        val cleanUsername = username.trim().removePrefix("@").ifBlank {
            name.trim().lowercase().replace("\\s+".toRegex(), "_").ifBlank { "trader" }
        }
        val existing = findExistingAccount(
            username = cleanUsername,
            email = email,
            phone = phone
        )
        if (existing != null) {
            return Pair(null, existing)
        }
        val created = createAccount(
            username = cleanUsername,
            name = name,
            email = email,
            phone = phone,
            city = city,
            role = role,
            pin4Digit = pin4Digit,
            enableLockImmediately = enableLockImmediately,
            enableBiometric = enableBiometric
        )
        return Pair(created, null)
    }

    /**
     * Saves the user's Dark/Light theme change immediately to both their account profile and SharedPreferences.
     */
    fun setDarkModePreference(isDark: Boolean) {
        _isDarkMode.value = isDark
        val curr = _currentUser.value
        if (curr != null) {
            val updated = curr.copy(isDarkMode = isDark)
            persistStateToDisk(updated)
            val themeLabel = if (isDark) "Dark Titanium Mode" else "Light Platinum Mode"
            AppDiagnosticsManager.logGeneral(
                "THEME_SAVED",
                "Saved $themeLabel preference for @${updated.username}"
            )
            AppDiagnosticsManager.syncAccountChangeToCloudDatabase(appContext, updated)
        } else {
            prefs?.edit()?.putBoolean(KEY_DARK_MODE_PREF, isDark)?.apply()
        }
    }

    fun toggleDarkModePreference() {
        setDarkModePreference(!_isDarkMode.value)
    }

    fun getTotalAccountsCreatedCount(): Int = _totalAccountsCreatedCount.value

    /**
     * Register individual account with Username & Password/PIN, persist all accounts in the synced database manager,
     * and immediately email krsmcxofficial@gmail.com with the total number of accounts created.
     */
    fun createAccount(
        username: String,
        name: String,
        email: String = "",
        phone: String = "",
        city: String = "Coimbatore",
        role: String = "Bullion Trader",
        pin4Digit: String = "1234",
        enableLockImmediately: Boolean = true,
        enableBiometric: Boolean = false
    ): UserProfile {
        syncDatabaseFromStorage()

        val cleanPin = pin4Digit.trim().ifBlank { "1234" }
        val cleanUsername = username.trim().removePrefix("@").ifBlank {
            name.trim().lowercase().replace("\\s+".toRegex(), "_").ifBlank { "trader" }
        }
        val cleanName = name.trim().ifBlank { cleanUsername }

        val existing = _allAccounts.value.firstOrNull {
            it.username.trim().removePrefix("@").equals(cleanUsername, ignoreCase = true)
        }

        val newUser = UserProfile(
            id = existing?.id ?: UUID.randomUUID().toString(),
            username = cleanUsername,
            name = cleanName,
            email = email.trim(),
            phone = phone.trim(),
            city = if (city.isNotBlank()) city.trim() else "Coimbatore",
            role = role.trim().ifBlank { "Bullion Trader" },
            pin4Digit = cleanPin,
            isAppLockEnabled = enableLockImmediately,
            isBiometricEnabled = enableBiometric,
            isDarkMode = _isDarkMode.value
        )

        val isBrandNew = existing == null
        val mutableAll = _allAccounts.value.toMutableList()
        val idx = mutableAll.indexOfFirst {
            it.username.trim().removePrefix("@").equals(cleanUsername, ignoreCase = true)
        }
        if (idx >= 0) {
            mutableAll[idx] = newUser
        } else {
            mutableAll.add(newUser)
        }
        _allAccounts.value = mutableAll

        if (isBrandNew) {
            _totalAccountsCreatedCount.value = maxOf(_totalAccountsCreatedCount.value + 1, mutableAll.size)
        } else {
            _totalAccountsCreatedCount.value = maxOf(_totalAccountsCreatedCount.value, mutableAll.size)
        }

        persistStateToDisk(newUser)
        _requiresOnboarding.value = false
        _isAppLocked.value = false

        AppDiagnosticsManager.logGeneral(
            "ACCOUNT_CREATED",
            "Account created & synced to database: @${newUser.username} (${newUser.name}) • Total Accounts Created: ${_totalAccountsCreatedCount.value}"
        )

        // Immediately mail krsmcxofficial@gmail.com and sync to Admin Cloud Database
        AppDiagnosticsManager.dispatchImmediateAccountOrSignInEmail(
            context = appContext,
            eventType = "NEW ACCOUNT CREATED",
            user = newUser,
            totalAccountsCount = _totalAccountsCreatedCount.value,
            allAccounts = _allAccounts.value
        )

        return newUser
    }

    /**
     * Overloaded helper for backwards compatibility.
     */
    fun createAccount(
        name: String,
        email: String,
        phone: String,
        city: String,
        role: String,
        pin4Digit: String,
        enableLockImmediately: Boolean = true,
        enableBiometric: Boolean = false
    ): UserProfile {
        val defaultUsername = name.trim().lowercase().replace("\\s+".toRegex(), "_")
        return createAccount(
            username = defaultUsername,
            name = name,
            email = email,
            phone = phone,
            city = city,
            role = role,
            pin4Digit = pin4Digit,
            enableLockImmediately = enableLockImmediately,
            enableBiometric = enableBiometric
        )
    }

    /**
     * Sign In using Username and Password / 4-Digit PIN.
     * First syncs all accounts from the persistent database so even if the user unlinked their account
     * from the active session earlier, their account is found and verified accurately!
     */
    fun signIn(username: String, pin: String): Pair<Boolean, String?> {
        syncDatabaseFromStorage()

        val cleanUsername = username.trim().removePrefix("@")
        val cleanPin = pin.trim()

        if (cleanUsername.isBlank()) {
            return Pair(false, "Access Denied: Please enter your username.")
        }
        if (cleanPin.length < 4) {
            return Pair(false, "Access Denied: Please enter your valid Password / 4-digit PIN.")
        }

        val candidates = (_allAccounts.value + listOfNotNull(_currentUser.value)).distinctBy {
            it.username.trim().removePrefix("@").lowercase()
        }

        val matchedAccount = candidates.firstOrNull { acc ->
            cleanUsername.equals(acc.username.trim().removePrefix("@"), ignoreCase = true) ||
                cleanUsername.equals(acc.name.trim(), ignoreCase = true) ||
                (acc.email.isNotBlank() && cleanUsername.equals(acc.email.trim(), ignoreCase = true))
        }

        if (matchedAccount == null) {
            AppDiagnosticsManager.logGeneral(
                "AUTH_DENIED",
                "Sign-in denied: Account '@$cleanUsername' does not exist in database",
                isError = true
            )
            return Pair(
                false,
                "Access Denied: Account '@$cleanUsername' does not exist in our database. Please create an account first."
            )
        }

        if (cleanPin != matchedAccount.pin4Digit) {
            AppDiagnosticsManager.logGeneral(
                "AUTH_DENIED",
                "Sign-in denied: Invalid Password / PIN for @${matchedAccount.username}",
                isError = true
            )
            return Pair(
                false,
                "Access Denied: Incorrect Password / 4-digit PIN for @${matchedAccount.username}. Please verify your credentials."
            )
        }

        // Credentials verified against synced database -> Grant Access & restore user's saved theme/settings
        persistStateToDisk(matchedAccount)
        completeOnboarding()
        unlockApp()
        recordSignInAndMailAdmin(matchedAccount, "USER SIGN-IN ALERT")
        return Pair(true, null)
    }

    fun recordBiometricSignIn() {
        syncDatabaseFromStorage()
        val user = _currentUser.value ?: _allAccounts.value.lastOrNull() ?: return
        persistStateToDisk(user)
        completeOnboarding()
        unlockApp()
        recordSignInAndMailAdmin(user, "BIOMETRIC SIGN-IN ALERT")
    }

    private fun recordSignInAndMailAdmin(user: UserProfile, eventLabel: String) {
        val p = prefs
        val nextSignInCount = (p?.getInt(KEY_TOTAL_SIGN_IN_COUNT, 0) ?: 0) + 1
        p?.edit()?.putInt(KEY_TOTAL_SIGN_IN_COUNT, nextSignInCount)?.apply()

        AppDiagnosticsManager.logGeneral(
            "AUTH_SIGN_IN",
            "$eventLabel for @${user.username} (${user.name}) • Total Accounts Created: ${_totalAccountsCreatedCount.value}"
        )

        AppDiagnosticsManager.dispatchImmediateAccountOrSignInEmail(
            context = appContext,
            eventType = eventLabel,
            user = user,
            totalAccountsCount = _totalAccountsCreatedCount.value,
            allAccounts = _allAccounts.value
        )
    }

    fun onGoogleSignIn(uid: String, email: String?, displayName: String?) {
        syncDatabaseFromStorage()

        val cleanEmail = email?.trim().orEmpty()
        val cleanName = displayName?.trim()?.ifBlank { null }
            ?: cleanEmail.substringBefore("@").ifBlank { "Trader" }
        val cleanUsername = cleanEmail.substringBefore("@")
            .ifBlank { cleanName.lowercase().replace("\\s+".toRegex(), "_") }

        val existing = _allAccounts.value.firstOrNull {
            it.id == uid ||
                (cleanEmail.isNotBlank() && it.email.equals(cleanEmail, ignoreCase = true)) ||
                it.username.equals(cleanUsername, ignoreCase = true)
        }

        val updatedUser = if (existing != null) {
            existing.copy(
                id = uid,
                username = cleanUsername,
                name = cleanName,
                email = cleanEmail.ifBlank { existing.email }
            )
        } else {
            UserProfile(
                id = uid,
                username = cleanUsername,
                name = cleanName,
                email = cleanEmail,
                phone = "",
                city = "Coimbatore",
                role = "Bullion Trader",
                pin4Digit = "1234",
                isAppLockEnabled = false,
                isBiometricEnabled = false,
                isDarkMode = _isDarkMode.value
            )
        }

        val isNewAccount = existing == null
        val mutableAll = _allAccounts.value.toMutableList()
        val idx = mutableAll.indexOfFirst {
            it.id == uid || it.username.equals(cleanUsername, ignoreCase = true)
        }
        if (idx >= 0) {
            mutableAll[idx] = updatedUser
        } else {
            mutableAll.add(updatedUser)
        }
        _allAccounts.value = mutableAll

        if (isNewAccount) {
            _totalAccountsCreatedCount.value = maxOf(_totalAccountsCreatedCount.value + 1, mutableAll.size)
        } else {
            _totalAccountsCreatedCount.value = maxOf(_totalAccountsCreatedCount.value, mutableAll.size)
        }

        persistStateToDisk(updatedUser)
        completeOnboarding()
        unlockApp()
        recordSignInAndMailAdmin(
            updatedUser,
            if (isNewAccount) "GOOGLE ACCOUNT CREATED & SIGNED IN" else "GOOGLE SIGN-IN ALERT"
        )
    }

    fun completeOnboarding() {
        _requiresOnboarding.value = false
        prefs?.edit()?.putBoolean(KEY_ONBOARDING_DONE, true)?.apply()
    }

    fun updateProfile(
        username: String = "",
        name: String,
        phone: String,
        city: String,
        role: String
    ) {
        val curr = _currentUser.value ?: return
        val updated = curr.copy(
            username = if (username.isNotBlank()) username.trim().removePrefix("@") else curr.username,
            name = name.trim().ifBlank { curr.name },
            phone = phone.trim(),
            city = city.trim().ifBlank { curr.city },
            role = role.trim().ifBlank { curr.role }
        )
        persistStateToDisk(updated)
        AppDiagnosticsManager.logGeneral("ACCOUNT_UPDATED", "Profile changes saved for @${updated.username}")
        AppDiagnosticsManager.syncAccountChangeToCloudDatabase(appContext, updated)
    }

    fun setAppLockEnabled(enabled: Boolean, pin: String? = null) {
        val curr = _currentUser.value ?: return
        val finalPin = if (!pin.isNullOrBlank() && pin.trim().length >= 4) pin.trim() else curr.pin4Digit
        val updated = curr.copy(isAppLockEnabled = enabled, pin4Digit = finalPin)
        persistStateToDisk(updated)
        AppDiagnosticsManager.syncAccountChangeToCloudDatabase(appContext, updated)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        val curr = _currentUser.value ?: return
        val updated = curr.copy(isBiometricEnabled = enabled)
        persistStateToDisk(updated)
        AppDiagnosticsManager.syncAccountChangeToCloudDatabase(appContext, updated)
    }

    fun changePin(newPin: String): Boolean {
        if (newPin.trim().length < 4) return false
        val curr = _currentUser.value ?: return false
        val updated = curr.copy(pin4Digit = newPin.trim(), isAppLockEnabled = true)
        persistStateToDisk(updated)
        AppDiagnosticsManager.syncAccountChangeToCloudDatabase(appContext, updated)
        return true
    }

    fun verifyPin(inputPin: String): Boolean {
        val curr = _currentUser.value ?: return (inputPin.trim() == "1234")
        return curr.pin4Digit == inputPin.trim()
    }

    fun lockApp() {
        _isAppLocked.value = true
    }

    fun unlockApp() {
        _isAppLocked.value = false
    }

    /**
     * Unlinks / Signs out of the active session on the device while keeping all created accounts
     * safely synced in the database (_allAccounts, SharedPreferences, and internal DB file).
     */
    fun signOut() {
        saveAllAccountsRegistryToDisk()
        _currentUser.value = null
        prefs?.edit()
            ?.remove(KEY_MY_PROFILE_JSON)
            ?.putBoolean(KEY_ONBOARDING_DONE, false)
            ?.apply()
        _requiresOnboarding.value = true
        _isAppLocked.value = false
    }

    fun switchAccount(userId: String) {
        syncDatabaseFromStorage()
        val target = _allAccounts.value.firstOrNull { it.id == userId } ?: return
        persistStateToDisk(target)
        recordSignInAndMailAdmin(target, "ACCOUNT SWITCH / SIGN-IN ALERT")
    }

    /**
     * Unlinks the account from the active device session so the user returns to the Sign In screen,
     * while preserving the account in the synced database so they can sign back in with Username & PIN.
     */
    fun deleteAccount(userId: String): Boolean {
        signOut()
        return true
    }

    /**
     * Unlinks the active user session on the device and triggers the login/onboarding screen,
     * while preserving all registered accounts in the synced database.
     */
    fun resetAccountOnDevice() {
        signOut()
    }

    fun saveUserChat(userId: String, messages: List<ChatMessage>) {
        val p = prefs ?: return
        try {
            val array = JSONArray()
            for (m in messages.takeLast(30)) {
                val obj = JSONObject().apply {
                    put("text", m.text)
                    put("isFromUser", m.isFromUser)
                    put("timestampMillis", m.timestampMillis)
                }
                array.put(obj)
            }
            val jsonStr = array.toString()
            p.edit()
                .putString("${KEY_MY_CHAT_JSON}_$userId", jsonStr)
                .putString(KEY_MY_CHAT_JSON, jsonStr)
                .apply()
        } catch (_: Exception) {}
    }

    fun loadUserChat(userId: String): List<ChatMessage> {
        val p = prefs ?: return emptyList()
        val jsonStr = p.getString("${KEY_MY_CHAT_JSON}_$userId", null)
            ?: p.getString(KEY_MY_CHAT_JSON, null)
            ?: return emptyList()
        val list = mutableListOf<ChatMessage>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ChatMessage(
                        text = obj.getString("text"),
                        isFromUser = obj.getBoolean("isFromUser"),
                        timestampMillis = obj.optLong("timestampMillis", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }
}
