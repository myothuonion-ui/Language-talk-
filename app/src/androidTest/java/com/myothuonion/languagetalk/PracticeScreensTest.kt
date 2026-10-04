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
        compose.onNodeWithText("My Books · Korean Conversations").assertIsDisplayed()
        capture("home")
        compose.onNodeWithText("Review", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Review & recordings").assertIsDisplayed()
        capture("review")
        compose.onNodeWithText("My Context", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Save profile").assertIsDisplayed()
        capture("context")
        compose.onNodeWithText("Settings", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("API keys"))
        compose.onNodeWithText("Test").assertIsDisplayed()
        capture("api-settings")
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("My default voice preset"))
        compose.onNodeWithText("My default voice preset").assertIsDisplayed()
        capture("voice-settings")
    }
}
