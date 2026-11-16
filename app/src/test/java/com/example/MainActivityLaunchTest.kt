package com.example

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.VijayBullionFeed
import com.example.util.UserAccountManager
import com.example.widget.SilverAppWidgetProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainActivityLaunchTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainActivity_launchesCleanly_onColdStart() {
        composeTestRule.waitForIdle()
        val activity = composeTestRule.activity
        assertNotNull(activity)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val krsLogoOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(context.resources, R.drawable.ic_krs_app_logo, krsLogoOpts)
        assertTrue("ic_krs_app_logo width=${krsLogoOpts.outWidth}", krsLogoOpts.outWidth in 1..2048)

        val jashxOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(context.resources, R.drawable.ic_jashx_ai_logo, jashxOpts)
        assertTrue("ic_jashx_ai_logo width=${jashxOpts.outWidth}", jashxOpts.outWidth in 1..2048)
    }

    @Test
    fun mainActivity_rendersDashboardNoticesAndSilverWidgetAfterSignIn() {
        UserAccountManager.createAccount(
            username = "preview_tester",
            name = "Preview Tester",
            phone = "9876543210",
            city = "Coimbatore",
            role = "Bullion Trader",
            pin4Digit = "1234",
            enableLockImmediately = false,
            enableBiometric = false
        )
        UserAccountManager.completeOnboarding()
        UserAccountManager.unlockApp()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("app_brand_header").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_announcements").assertIsDisplayed()

        // Open Notices tab and verify Announcements view renders without in-app publish UI
        composeTestRule.onNodeWithTag("tab_announcements").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("announcements_tab_view").assertIsDisplayed()
        composeTestRule.onNodeWithTag("admin_publish_announcement_card").assertDoesNotExist()

        // Open Tools tab and verify Silver-Only widget preview and pin button exist and Unit Converter is removed
        composeTestRule.onNodeWithTag("tab_tools").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("unit_converter_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("silver_only_widget_preview").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("pin_silver_widget_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun silverWidget_formatsOpenAndClosedStatesProperly() {
        val sampleFeed = VijayBullionFeed(
            silverBarRate1kg = 189440.0,
            mcxSilverBid = 185085.0,
            mcxSilverAsk = 185139.0,
            mcxSilverLtp = 185100.0
        )

        val openDisplay = SilverAppWidgetProvider.formatSilverRatesForWidget(
            feed = sampleFeed,
            marketOpen = true,
            physicalOpen = true
        )
        assertTrue(openDisplay.mcxSilverPrice.contains("1,85,100") || openDisplay.mcxSilverPrice.contains("185,100"))
        assertTrue(openDisplay.physicalSilver1kgPrice.contains("1,89,440") || openDisplay.physicalSilver1kgPrice.contains("189,440"))

        val closedDisplay = SilverAppWidgetProvider.formatSilverRatesForWidget(
            feed = sampleFeed,
            marketOpen = false,
            physicalOpen = false
        )
        assertEquals("Closed", closedDisplay.physicalSilver1kgPrice)
        assertEquals("Market Closed", closedDisplay.physicalSilver1kgSub)
    }
}
