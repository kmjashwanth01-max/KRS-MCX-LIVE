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

        // Open Charts tab and verify new 1s live charts dashboard replaced old chart views
        composeTestRule.onNodeWithTag("tab_charts").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("live_1s_charts_dashboard").assertIsDisplayed()
        composeTestRule.onNodeWithTag("live_chart_silver_1kg").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("live_chart_gold_999").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("live_chart_mcx_gold").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("advanced_technical_chart_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("historical_chart_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("all_contracts_list_card").assertDoesNotExist()

        // Open JASHX AI tab and verify Live Voice console renders cleanly WITHOUT voice changer UI
        composeTestRule.onNodeWithTag("tab_jashx_ai").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("jashx_live_voice_console").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("jashx_live_voice_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("voice_persona_kore").assertDoesNotExist()
        composeTestRule.onNodeWithTag("voice_persona_fenrir").assertDoesNotExist()

        // Verify JASHX AI Logo bitmap loads cleanly at 512x512
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val logoOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(ctx.resources, R.drawable.ic_jashx_ai_logo, logoOpts)
        assertTrue("JASHX AI logo must be valid size", logoOpts.outWidth in 256..1024 && logoOpts.outHeight in 256..1024)

        // Open Tools tab and verify Silver-Only widget preview and pin button exist and Unit Converter is removed
        composeTestRule.onNodeWithTag("tab_tools").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("unit_converter_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("silver_only_widget_preview").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("pin_silver_widget_button").performScrollTo().assertIsDisplayed()

        // Verify Settings modal hides Admin delete update controls from regular users
        composeTestRule.onNodeWithTag("tools_settings_card").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("admin_app_update_publisher_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("btn_delete_active_update").assertDoesNotExist()
    }

    @Test
    fun silverWidget_formatsOpenAndClosedStatesProperly() {
        val sampleFeed = VijayBullionFeed(
            silverBarRate1kg = 189440.0,
            mcxSilverBid = 185085.0,
            mcxSilverAsk = 185139.0,
            mcxSilverLtp = 185100.0,
            spotSilverBid = 52.01
        )
        val sampleDirections = com.example.widget.WidgetTickDirections(
            silver1kg = com.example.model.PriceTickDirection.UP,
            mcxSilver = com.example.model.PriceTickDirection.DOWN,
            spotSilverCent = com.example.model.PriceTickDirection.UP
        )

        val openDisplay = SilverAppWidgetProvider.formatSilverRatesForWidget(
            feed = sampleFeed,
            marketOpen = true,
            physicalOpen = true,
            directions = sampleDirections
        )
        assertTrue(openDisplay.mcxSilverPrice.contains("1,85,100") || openDisplay.mcxSilverPrice.contains("185,100"))
        assertTrue(openDisplay.physicalSilver1kgPrice.contains("1,89,440") || openDisplay.physicalSilver1kgPrice.contains("189,440"))
        assertEquals("$52.01", openDisplay.silverCentPrice)
        assertTrue(openDisplay.silverCentSub.contains("▲"))
        assertEquals(com.example.model.PriceTickDirection.DOWN, openDisplay.mcxDirection)
        assertEquals(com.example.model.PriceTickDirection.UP, openDisplay.physicalDirection)
        assertEquals(com.example.model.PriceTickDirection.UP, openDisplay.centDirection)

        val closedDisplay = SilverAppWidgetProvider.formatSilverRatesForWidget(
            feed = sampleFeed,
            marketOpen = false,
            physicalOpen = false,
            directions = sampleDirections,
            reopenNote = "Reopens Today 9:00 AM"
        )
        assertEquals("CLOSED", closedDisplay.mcxSilverPrice)
        assertEquals("CLOSED", closedDisplay.physicalSilver1kgPrice)
        assertEquals("CLOSED", closedDisplay.silverCentPrice)
        assertTrue("Silver Cent price must hide $ value when closed", "$" !in closedDisplay.silverCentPrice)
        assertTrue("MCX Silver price must hide ₹ value when closed", "₹" !in closedDisplay.mcxSilverPrice)
        assertEquals("Reopens Today 9:00 AM", closedDisplay.physicalSilver1kgSub)
        assertTrue(closedDisplay.summaryText.contains("Reopens"))
    }
}
