package com.myothuonion.languagetalk

import android.Manifest
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myothuonion.languagetalk.data.ChatEntity
import com.myothuonion.languagetalk.data.LearningProgressEntity
import com.myothuonion.languagetalk.network.LivePhase
import com.myothuonion.languagetalk.network.LiveState
import com.myothuonion.languagetalk.ui.LanguageTalkTheme
import com.myothuonion.languagetalk.ui.LiveChatScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LiveErrorScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun retryRemainsVisibleAndClickableOnAShortScreen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, Manifest.permission.RECORD_AUDIO)
        var starts = 0
        var stops = 0
        val chat = ChatEntity(id = 1, title = "နေ့စဉ်စကားပြော", language = "KOREAN", topic = "Daily greetings", level = "Beginner",
            tutorRole = "TEACHER", correctionMode = "AFTER_REPLY", customPrompt = "", voiceName = "Kore", voiceStyle = "Clear", brainMode = "GEMINI_ONLY")
        compose.setContent {
            LanguageTalkTheme {
                Box(Modifier.width(320.dp).height(500.dp)) {
                    LiveChatScreen(chat, LiveState(phase = LivePhase.ERROR, fallbackUsed = true,
                        error = "Gemini voice မအောင်မြင်ပါ။ Audio delivery mode is not supported.\nTried: gemini-3.8-flash-tts, gemini-3.8-flash-lite-tts"),
                        LearningProgressEntity(1, "Daily greetings", stage = "REPEAT", targetSentence = "안녕하세요. 잘 부탁드립니다."),
                        onStart = { starts++ }, onToggleMic = {}, onStop = { stops++ }, onBack = {}, onCustomize = {})
                }
            }
        }
        compose.onNodeWithText("ပြန်ချိတ်မယ်").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(2, starts); assertEquals(1, stops) }
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(directory, "live-error-retry.png").outputStream().use { output ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }
}
