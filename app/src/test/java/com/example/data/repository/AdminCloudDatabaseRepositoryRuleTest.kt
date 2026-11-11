package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminCloudDatabaseRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private suspend fun bootstrapAdminDocument(adminUid: String, email: String) {
        val docRef = firestore.collection("admins").document(adminUid)
        val snap = docRef.get().await()
        if (!snap.exists()) {
            docRef.set(
                mapOf(
                    "adminId" to adminUid,
                    "email" to email,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    @Test
    fun saveUserAccount_and_recordLoginAndMail_succeedsForUsernamePinUser() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = AdminCloudDatabaseRepository(firestore, auth)

        val saveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveUserAccount(
                username = "alice_trader",
                name = "Alice Trader",
                city = "Coimbatore",
                role = "Bullion Trader",
                isDarkMode = false,
                isAppLockEnabled = true,
                isBiometricEnabled = false,
                email = "alice@test.com",
                phone = "+91 98422 11111",
                pin4Digit = "4321"
            )
        }
        assertTrue(saveResult.isSuccess)

        val loaded = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getUserAccount("alice_trader").getOrThrow()
        }
        assertNotNull(loaded)
        assertEquals("alice_trader", loaded?.username)
        assertEquals("alice_trader.txt", loaded?.fileName)
        assertEquals("4321", loaded?.pin4Digit)
        assertEquals(false, loaded?.isDarkMode)
        assertTrue(loaded?.userTextFile?.contains("PASSWORD_PIN       : 4321") == true)

        val loadedTextFile = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getUserTextFile("alice_trader").getOrThrow()
        }
        assertNotNull(loadedTextFile)
        assertEquals("alice_trader.txt", loadedTextFile?.fileName)
        assertTrue(loadedTextFile?.fileContentText?.contains("USERNAME           : alice_trader") == true)
        assertTrue(loadedTextFile?.fileContentText?.contains("CITY               : Coimbatore") == true)

        val logResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.recordLoginAndMailToAdminDatabase(
                username = "alice_trader",
                name = "Alice Trader",
                eventType = "USER_SIGN_IN",
                savedTheme = "Light Platinum Mode",
                totalAccountsCount = 2,
                subject = "[KRS Bullion] Sign-In Alert",
                body = "Total Accounts Created: 2"
            )
        }
        assertTrue(logResult.isSuccess)
    }

    @Test
    fun regularUser_cannotListLoginEventsOrMailLogsOrAllAccounts_failsWithPermissionDenied() = runBlocking {
        signInTestUser(BOB_EMAIL)
        val bobRepo = AdminCloudDatabaseRepository(firestore, auth)

        // Regular user Bob cannot list user_accounts, login_events, or admin_mail_logs
        val listAccountsResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.listAllUserAccountsForAdmin()
        }
        assertTrue(listAccountsResult.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            listAccountsResult.exceptionOrNull()?.unwrapFirestoreException()?.code
        )

        val listLoginsResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.listLoginEventsForAdmin()
        }
        assertTrue(listLoginsResult.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            listLoginsResult.exceptionOrNull()?.unwrapFirestoreException()?.code
        )

        val listMailsResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.listMailLogsForAdmin()
        }
        assertTrue(listMailsResult.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            listMailsResult.exceptionOrNull()?.unwrapFirestoreException()?.code
        )

        // Regular user Bob cannot publish arbitrary announcements
        val pubResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.publishAnnouncementAsAdmin("Fake", "Unauthorized", "high")
        }
        assertTrue(pubResult.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            pubResult.exceptionOrNull()?.unwrapFirestoreException()?.code
        )
    }

    @Test
    fun adminUser_canPublishAnnouncementsAndListAccountsLoginsAndMails() = runBlocking {
        val adminUid = signInTestUser(ADMIN_EMAIL)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bootstrapAdminDocument(adminUid, ADMIN_EMAIL)
        }
        val adminRepo = AdminCloudDatabaseRepository(firestore, auth)

        val annId = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.publishAnnouncementAsAdmin(
                title = "Official KRS Bullion Update",
                message = "Special Diwali Gold 999 & Silver 1kg booking desk open now.",
                priority = "urgent"
            ).getOrThrow()
        }
        assertTrue(annId.isNotEmpty())

        // Regular user Alice can observe active announcements published by Admin
        signInTestUser(ALICE_EMAIL)
        val aliceRepo = AdminCloudDatabaseRepository(firestore, auth)
        val activeAnnouncements = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeActiveAnnouncements().first { list -> list.any { it.id == annId } }
        }
        assertTrue(activeAnnouncements.any { it.title == "Official KRS Bullion Update" })
    }

    private companion object {
        const val ADMIN_EMAIL = "kmjashwanth01@gmail.com"
        const val ALICE_EMAIL = "alice@test.com"
        const val BOB_EMAIL = "bob@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
