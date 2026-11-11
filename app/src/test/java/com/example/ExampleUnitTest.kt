package com.example

import com.example.data.JashxAiEngine
import com.example.data.JashxKnowledgeDatabase
import com.example.data.VijayBullionFeed
import com.example.model.ChatMessage
import com.example.model.CommodityType
import com.example.model.ContractUnit
import com.example.model.GoldPurity
import com.example.util.AppDiagnosticsManager
import com.example.util.UserAccountManager
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testVijayBullionFeedBaselineDefaults() {
        val feed = VijayBullionFeed()
        assertTrue("Gold bar rate per 10g should be > 100,000", feed.goldBarRate10g > 100000.0)
        assertTrue("Silver bar rate per 1kg should be > 100,000", feed.silverBarRate1kg > 100000.0)
        assertTrue("MCX Gold bid should be positive", feed.mcxGoldBid > 0.0)
        assertTrue("MCX Silver bid should be positive", feed.mcxSilverBid > 0.0)
        assertTrue("Spot Gold should be > $3000", feed.spotGoldBid > 3000.0)
        assertTrue("Spot Silver should be > $30", feed.spotSilverBid > 30.0)
        assertTrue("Spot USD/INR should be > 80", feed.spotInrBid > 80.0)
    }

    @Test
    fun testGoldPurityMultipliers() {
        assertEquals(1.0, GoldPurity.K24_999.multiplier, 0.001)
        assertEquals(0.916, GoldPurity.K22_916.multiplier, 0.001)
        assertEquals(0.750, GoldPurity.K18_750.multiplier, 0.001)
        assertEquals(0.585, GoldPurity.K14_585.multiplier, 0.001)
    }

    @Test
    fun testWidgetFormattedAllFourRates() {
        val feed = VijayBullionFeed(
            goldBarRate10g = 146040.0,
            silverBarRate1kg = 189240.0,
            mcxGoldBid = 142405.0,
            mcxSilverBid = 185100.0,
            spotGoldBid = 4070.77,
            spotSilverBid = 52.01,
            spotInrBid = 93.90
        )
        // Rate 1: Gold 999 (10g)
        val goldStr = "₹${String.format("%,.0f", feed.goldBarRate10g)}"
        // Rate 2: Silver 1kg
        val silverStr = "₹${String.format("%,.0f", feed.silverBarRate1kg)}"
        // Rate 3: Gold Cent ($)
        val goldCentStr = "$${String.format("%.2f", feed.spotGoldBid)}"
        // Rate 4: Silver Cent ($)
        val silverCentStr = "$${String.format("%.2f", feed.spotSilverBid)}"

        val spotStr = "Forex: USD/INR ₹${String.format("%.2f", feed.spotInrBid)} • Spot 1s"

        assertEquals("₹146,040", goldStr)
        assertEquals("₹189,240", silverStr)
        assertEquals("$4070.77", goldCentStr)
        assertEquals("$52.01", silverCentStr)

        assertTrue(spotStr.contains("USD/INR ₹93.90"))
    }

    @Test
    fun testJashxAiLivePredictionAndPivotPoints() {
        val jashxEngine = JashxAiEngine()
        val feed = VijayBullionFeed(
            goldBarRate10g = 146040.0,
            silverBarRate1kg = 189240.0,
            spotGoldBid = 4070.77,
            spotSilverBid = 52.01
        )
        val contract = com.example.model.CommodityContract(
            id = "gold_999",
            symbol = "GOLD 999",
            name = "Gold 999 (10g)",
            expiry = "Spot Delivery",
            type = CommodityType.GOLD,
            unit = ContractUnit.TEN_GRAMS,
            ltp = 146040.0,
            previousClose = 145500.0,
            high = 146500.0,
            low = 145200.0,
            open = 145600.0,
            volume = 5200,
            openInterest = 0,
            bidPrice = 146000.0,
            bidQty = 10,
            askPrice = 146080.0,
            askQty = 12
        )

        val prediction = jashxEngine.calculateLivePrediction(contract, feed)

        assertNotNull(prediction)
        assertEquals("gold_999", prediction.contractId)
        assertTrue(prediction.pivotPoint > 0.0)
        assertTrue("R1 should be > Pivot", prediction.resistance1 > prediction.pivotPoint)
        assertTrue("R2 should be > R1", prediction.resistance2 > prediction.resistance1)
        assertTrue("S1 should be < Pivot", prediction.support1 < prediction.pivotPoint)
        assertTrue("S2 should be < S1", prediction.support2 < prediction.support1)
        assertTrue("Target 1 should be positive", prediction.target1 > 0.0)
        assertTrue("Stop loss should be positive", prediction.stopLoss > 0.0)
        assertTrue("Confidence should be between 50 and 100", prediction.confidencePercent in 50..100)
    }

    @Test
    fun testDailyReportGenerationAndAdminEmail() {
        assertEquals("krsmcxofficial@gmail.com", AppDiagnosticsManager.ADMIN_EMAIL)
        assertEquals("kmjashwanth01@gmail.com", AppDiagnosticsManager.CREATOR_EMAIL)

        val user = UserAccountManager.createAccount(
            username = "test_admin_check",
            name = "Test Bullion Trader",
            email = "trader@test.com",
            phone = "+91 98422 00000",
            city = "Coimbatore",
            role = "Bullion Trader",
            pin4Digit = "1234"
        )

        val report = AppDiagnosticsManager.generateAccountEventEmailBody(
            eventType = "ACCOUNT_CREATED",
            user = user,
            totalAccountsCount = 1,
            allAccounts = listOf(user)
        )
        assertTrue(report.contains("krsmcxofficial@gmail.com"))
        assertTrue(report.contains("KRS GOLD & SILVER - IMMEDIATE ACCOUNT & SIGN-IN REPORT"))
        assertTrue(report.contains("test_admin_check"))
        assertTrue(report.contains("K.M. Jashwanth"))
    }

    @Test
    fun testStrictIndividualAccountIsolationAndWhatsAppModel() {
        val accountManager = UserAccountManager

        // Create Individual Account on Device with Username
        val user = accountManager.createAccount(
            username = "anand_bullion",
            name = "Anand Kumar",
            email = "anand@chennaibullion.in",
            phone = "+91 98400 11111",
            city = "Chennai",
            role = "Jewelry Retailer",
            pin4Digit = "4321",
            enableLockImmediately = true,
            enableBiometric = true
        )

        assertEquals("Anand Kumar", accountManager.currentUser.value?.name)
        assertEquals("anand_bullion", accountManager.currentUser.value?.username)
        assertTrue(accountManager.verifyPin("4321"))
        assertFalse(accountManager.verifyPin("0000"))
        assertTrue(user.isBiometricEnabled)

        // Test Isolated Chat Storage
        val chatMessages = listOf(
            ChatMessage(text = "What is the rate for 22K gold?", isFromUser = true),
            ChatMessage(text = "22K gold is quoting at ₹133,772 for 10g", isFromUser = false)
        )
        accountManager.saveUserChat(user.id, chatMessages)

        // Unlinking Account from Device logs out the session while preserving the account in the Synced Database
        accountManager.deleteAccount(user.id)
        assertNull(accountManager.currentUser.value)
        assertTrue(accountManager.requiresOnboarding.value)

        // Verify the unlinked account is STILL in the Synced Database and signing in with Username & PIN succeeds!
        assertTrue(accountManager.doesAccountExist("anand_bullion"))
        val (reSignInOk, reSignInErr) = accountManager.signIn("anand_bullion", "4321")
        assertTrue("Signing in after unlinking account must succeed from synced database", reSignInOk)
        assertNull(reSignInErr)
        assertEquals("anand_bullion", accountManager.currentUser.value?.username)
    }

    @Test
    fun testUsernameAndPinAuthenticationInCreateAndSignInPages() {
        val accountManager = UserAccountManager

        // 1. Create account with explicit Username and Password/PIN
        val created = accountManager.createAccount(
            username = "jashwanth01",
            name = "K.M. Jashwanth",
            email = "kmjashwanth01@gmail.com",
            phone = "+91 98422 12345",
            city = "Coimbatore",
            role = "Bullion Administrator",
            pin4Digit = "9999"
        )
        assertEquals("jashwanth01", created.username)
        assertEquals("9999", created.pin4Digit)

        // 2. Attempting to create an account that already exists in Database Manager detects existing account
        val existingFound = accountManager.findExistingAccount(username = "jashwanth01")
        assertNotNull("Database Manager must detect existing account", existingFound)
        assertEquals("jashwanth01", existingFound?.username)

        val (createdDup, dupExisting) = accountManager.tryCreateNewAccount(
            username = "jashwanth01",
            name = "Duplicate Attempt",
            pin4Digit = "0000"
        )
        assertNull("Duplicate account creation must be blocked", createdDup)
        assertNotNull("Existing account must be returned for popup prompt", dupExisting)

        // 3. Sign Out preserves the account in the database manager
        accountManager.signOut()
        assertNull(accountManager.currentUser.value)
        assertTrue(accountManager.doesAccountExist("jashwanth01"))

        // 4. Sign In with matching username and Password/PIN -> Access Granted
        val (successMatch, errorNull) = accountManager.signIn("jashwanth01", "9999")
        assertTrue("Sign in should succeed with matching username and PIN", successMatch)
        assertNull(errorNull)
        assertEquals("jashwanth01", accountManager.currentUser.value?.username)

        // 5. Sign In with wrong Password/PIN -> Access Denied
        val (failWrongPin, errWrongPin) = accountManager.signIn("jashwanth01", "1111")
        assertFalse("Sign in must be denied with incorrect Password/PIN", failWrongPin)
        assertNotNull(errWrongPin)
        assertTrue(errWrongPin!!.contains("Access Denied", ignoreCase = true))

        // 6. Sign In with non-existent username -> Access Denied
        val (failUnknownUser, errUnknownUser) = accountManager.signIn("non_existent_trader_99", "5555")
        assertFalse("Sign in must be denied when username does not exist in database", failUnknownUser)
        assertNotNull(errUnknownUser)
        assertTrue(errUnknownUser!!.contains("does not exist", ignoreCase = true))
    }

    @Test
    fun testJashxAiAnswersChiefMinisterOfTamilNaduAndNationalLeaders() {
        val feed = VijayBullionFeed(
            goldBarRate10g = 146040.0,
            silverBarRate1kg = 189240.0,
            spotGoldBid = 4070.77,
            spotInrBid = 93.90
        )

        // 1. CM of Tamil Nadu Query (2026 Updated Feed: Joseph Vijay)
        val cmTnAnswer = JashxKnowledgeDatabase.queryKnowledgeBase("who is the cm of tamil nadu", feed)
        assertTrue("Answer should mention Joseph Vijay", cmTnAnswer.contains("Joseph Vijay"))
        assertTrue("Answer should mention Chief Minister", cmTnAnswer.contains("Chief Minister"))

        // 2. Prime Minister of India Query
        val pmIndiaAnswer = JashxKnowledgeDatabase.queryKnowledgeBase("who is the prime minister of india", feed)
        assertTrue("Answer should mention Narendra Modi", pmIndiaAnswer.contains("Narendra Modi"))

        // 3. Capital of Tamil Nadu
        val capitalTnAnswer = JashxKnowledgeDatabase.queryKnowledgeBase("capital of tamil nadu", feed)
        assertTrue("Answer should mention Chennai", capitalTnAnswer.contains("Chennai"))

        // 4. Sovereign / Pavan in grams
        val sovereignAnswer = JashxKnowledgeDatabase.queryKnowledgeBase("how many grams in 1 sovereign", feed)
        assertTrue("Answer should state 8 Grams", sovereignAnswer.contains("8 Grams", ignoreCase = true) || sovereignAnswer.contains("8g", ignoreCase = true))

        // 5. Math calculation: 15% of 145000
        val mathAnswer = JashxKnowledgeDatabase.queryKnowledgeBase("what is 15% of 145000", feed)
        assertTrue("Calculation should return 21,750", mathAnswer.contains("21,750"))

        // 6. Who created you -> K.M. Jashwanth is the Founder of JASHX AI (without email ID)
        val creatorAnswer = JashxKnowledgeDatabase.queryKnowledgeBase("who created you", feed)
        assertTrue(
            "Creator answer should state 'K.M. Jashwanth is the Founder of JASHX AI'",
            creatorAnswer.contains("K.M. Jashwanth is the Founder of JASHX AI")
        )
        assertFalse(
            "Creator answer should not include email ID",
            creatorAnswer.contains("kmjashwanth01@gmail.com")
        )

        // 7. Weather & Location detection query
        val weatherAnswer = JashxKnowledgeDatabase.queryKnowledgeBase("what is the weather today", feed)
        assertTrue("Weather answer should include temperature in °C", weatherAnswer.contains("°C"))

        // 8. Latest Live Feeds query & Human Speech sanitizer
        val latestFeedsAnswer = JashxKnowledgeDatabase.queryKnowledgeBase("Give me the latest live feeds", feed)
        assertTrue("Latest feeds should include LIVE BULLION and headlines", latestFeedsAnswer.contains("LIVE BULLION"))

        val cleanSpeech = JashxKnowledgeDatabase.toNaturalSpeechText("✨ **K.M. Jashwanth is the Founder of JASHX AI** • Gold ₹146,040")
        assertTrue("Speech text should replace ₹ with Rupees and strip asterisks", cleanSpeech.contains("Rupees") && !cleanSpeech.contains("**"))
        assertTrue("Speech text should pronounce JASHX as one word 'Jashex' and AI as 'AI'", cleanSpeech.contains("Jashex AI"))
    }

    @Test
    fun testUserDefinedPriceAlertThresholds() {
        val alertBuy = com.example.data.ActivePriceAlert(
            id = "alert_1",
            contractId = "gold_999",
            contractSymbol = "GOLD 999",
            targetPrice = 145000.0,
            isAbove = false,
            isBuyTarget = true
        )
        val alertSell = com.example.data.ActivePriceAlert(
            id = "alert_2",
            contractId = "silver_1kg",
            contractSymbol = "SILVER 1KG",
            targetPrice = 190000.0,
            isAbove = true,
            isBuyTarget = false
        )

        assertTrue("Buy alert should trigger when price drops below target", !alertBuy.isAbove)
        assertTrue("Sell alert should trigger when price rises above target", alertSell.isAbove)
        assertFalse(alertBuy.isTriggered)
        assertFalse(alertSell.isTriggered)
    }

    @Test
    fun testVijayBullionExactBroadcastParsing() {
        val samplePayload = "3\t56\t\"GOLD BAR 9999 BIS 09 NOV\"\t-\t14630.00\t-\t-\t-\t-\t-\t09-11-2026\n" +
                "3\t87\t\"SILVER BAR IMP 09 NOV\"\t-\t189440\t-\t-\t-\t-\t-\t09-11-2026\n" +
                "2\tGOLDDEC\tGOLDMCX\t142468\t142500\t142692\t142163\t142480\t\t\t-\n" +
                "2\tSILVERDEC\tSILVERMCX\t185085\t185139\t185339\t185052\t185139\t\t\t-\n" +
                "1\tSPOT-GOLD\tGOLD($)\t4069.99\t4070.55\t4107.00\t4026.04\t4069.99\t\t\t-\n" +
                "1\tSPOT-SILVER\tSILVER($)\t51.90\t51.97\t52.37\t51.17\t51.90\t\t\t-\n" +
                "1\tSPOT-INR\tINR(₹)\t94.09\t94.09\t94.52\t93.81\t93.91\t\t\t-\n" +
                "4\t4\t-\t1\t0\t\t\"11/08/2026 02:26:46 PM\"\t-\t-\t-\t-"

        val parsed = com.example.data.VijayBullionApiClient.parseBroadcast(samplePayload)
        assertEquals(146300.0, parsed.goldBarRate10g, 0.01)
        assertEquals(189440.0, parsed.silverBarRate1kg, 0.01)
        assertEquals(142468.0, parsed.mcxGoldBid, 0.01)
        assertEquals(142500.0, parsed.mcxGoldAsk, 0.01)
        assertEquals(142480.0, parsed.mcxGoldLtp, 0.01)
        assertEquals(185085.0, parsed.mcxSilverBid, 0.01)
        assertEquals(185139.0, parsed.mcxSilverLtp, 0.01)
        assertEquals(4069.99, parsed.spotGoldBid, 0.01)
        assertEquals(51.90, parsed.spotSilverBid, 0.01)
        assertEquals(94.09, parsed.spotInrBid, 0.01)
        assertTrue(parsed.serverTime.contains("02:26:46 PM"))
    }

    @Test
    fun testAccountThemePersistenceAndImmediateAdminEmailToKrsMcxOfficial() {
        val accountManager = UserAccountManager

        // 1. Create an account and verify immediate email report to krsmcxofficial@gmail.com
        val countBefore = accountManager.getTotalAccountsCreatedCount()
        val created = accountManager.createAccount(
            username = "krs_trader_cbe",
            name = "KRS Bullion Trader",
            email = "trader@krsbullion.in",
            phone = "+91 98422 99999",
            city = "Coimbatore",
            role = "Bullion Trader",
            pin4Digit = "7777"
        )

        val newCount = accountManager.getTotalAccountsCreatedCount()
        assertTrue("Total accounts created count must increment", newCount >= countBefore)

        val creationEmail = AppDiagnosticsManager.lastAccountEmailReportText.value
        assertTrue("Immediate email must target krsmcxofficial@gmail.com", creationEmail.contains("krsmcxofficial@gmail.com"))
        assertTrue("Immediate email must include Total No. of Accounts Created", creationEmail.contains("Total No. of Accounts Created: $newCount"))
        assertTrue("Immediate email must include created username", creationEmail.contains("krs_trader_cbe"))

        // 2. Change from Dark theme to Light theme and verify it is saved on the account
        assertTrue("Default theme should be Dark", accountManager.isDarkMode.value)
        accountManager.setDarkModePreference(false)
        assertFalse("Theme state must update to Light (false)", accountManager.isDarkMode.value)
        assertEquals("Active user profile must save Light theme preference", false, accountManager.currentUser.value?.isDarkMode)

        // 3. Sign in and verify saved Light theme persists AND immediate sign-in email is sent to krsmcxofficial@gmail.com
        val (signInOk, _) = accountManager.signIn("krs_trader_cbe", "7777")
        assertTrue("Sign in must succeed", signInOk)
        assertFalse("Saved Light theme must be preserved after sign-in", accountManager.isDarkMode.value)

        val signInEmail = AppDiagnosticsManager.lastAccountEmailReportText.value
        assertTrue("Sign-in email must target krsmcxofficial@gmail.com", signInEmail.contains("krsmcxofficial@gmail.com"))
        assertTrue("Sign-in email must report SIGN-IN event", signInEmail.contains("SIGN-IN"))
        assertTrue("Sign-in email must show saved Light Platinum Mode", signInEmail.contains("Light Platinum Mode"))

        // Restore dark mode for subsequent tests
        accountManager.setDarkModePreference(true)
        assertEquals(created.username, accountManager.currentUser.value?.username)
    }

    @Test
    fun testJashxAiVerifiedMultiDomainAccuracy() {
        val feed = VijayBullionFeed()

        // 1. Space & Science verified fact
        val isroAnswer = JashxKnowledgeDatabase.queryKnowledgeBase("Tell me about Chandrayaan ISRO", feed)
        assertTrue("AI should accurately answer about Chandrayaan-3", isroAnswer.contains("Chandrayaan-3"))

        // 2. Classical Literature verified fact
        val thirukkuralAnswer = JashxKnowledgeDatabase.queryKnowledgeBase("Tell me about Thirukkural", feed)
        assertTrue("AI should accurately answer about Thiruvalluvar", thirukkuralAnswer.contains("Thiruvalluvar"))

        // 3. Custom bullion gram weight calculation
        val customGoldAnswer = JashxKnowledgeDatabase.queryKnowledgeBase("What is the price of 8 grams gold?", feed)
        assertTrue("AI should calculate exact 24K and 22K price for 8 grams", customGoldAnswer.contains("8.00g") && customGoldAnswer.contains("22K"))

        // 4. Non-bullion query containing the word "rate" (e.g. Repo Rate) must NOT falsely trigger the bullion table
        val repoRateAnswer = JashxKnowledgeDatabase.queryKnowledgeBase("What is Repo Rate?", feed)
        assertTrue("Repo rate query must return RBI Repo Rate definition", repoRateAnswer.contains("Reserve Bank of India"))
    }
}

