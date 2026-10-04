package com.myothuonion.languagetalk

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PracticeScreensTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { output ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }

    @Test fun homeReviewContextAndSavedVoiceSettingsAreAccessible() {
        compose.waitUntil(10000) { compose.onAllNodesWithText("My Books").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("My Books").assertIsDisplayed()
        capture("home")
        compose.onNodeWithText("Practice", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("start-today").assertIsDisplayed()
        capture("practice")
        compose.onNodeWithText("Me", useUnmergedTree = true).performClick()
        compose.onNodeWithText("My Context").performClick()
        compose.onNodeWithText("Save profile").assertIsDisplayed()
        capture("context")
        compose.activity.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag("learning-profile").performScrollToNode(hasText("Settings"))
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithTag("simple-settings").assertExists()
        compose.onNodeWithText("အသံနဲ့ သင်ယူမှု").assertIsDisplayed()
        capture("api-settings")
        compose.onNodeWithTag("simple-settings").performScrollToNode(hasText("Advanced settings"))
        compose.onNodeWithText("Advanced settings").performClick()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("My default voice preset"))
        compose.onNodeWithText("My default voice preset").assertIsDisplayed()
        capture("voice-settings")
    }
}
