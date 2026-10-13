package com.myothuonion.languagetalk

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myothuonion.languagetalk.data.SettingsStore
import com.myothuonion.languagetalk.network.AudioPayload
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReplayScreensTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun pairedRecordingsAreAccessibleWithSpeakerAndSpeedControls() {
        val app = compose.activity.application as LanguageTalkApplication
        val settings = SettingsStore(app)
        val previous = runBlocking { settings.settings.first() }
        val title = "နားထောင်ပြီး လိုက်ပြောခြင်း"
        val session = runBlocking {
            settings.update { it.copy(recordPractice = true) }
            val id = app.replays.begin("SHADOW", UUID.randomUUID().toString(), title)
            val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open("gemini-greeting.wav").use { it.readBytes() }
            app.replays.save(id, "AI", "안녕하세요. 저는 민수예요.", AudioPayload(bytes, "audio/wav"))
            app.replays.save(id, "USER", "안녕하세요.", AudioPayload(bytes, "audio/wav"))
            id
        }
        try {
            compose.waitUntil(15000) { compose.onAllNodesWithText("Replays", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Replays", useUnmergedTree = true).performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
            capture("replays")
            compose.onNodeWithText(title).performClick()
            compose.onNodeWithTag("replay-filter-BOTH").assertIsSelected()
            compose.onNodeWithTag("replay-play").assertIsEnabled()
            compose.onNodeWithTag("replay-speed-0.75").performClick().assertIsSelected()
            compose.onNodeWithTag("replay-mark-A").performClick()
            compose.onNodeWithTag("replay-mark-B").assertIsEnabled()
            capture("replay-paired")
            compose.onNodeWithTag("replay-filter-USER").performClick().assertIsSelected()
            compose.onNodeWithTag("replays-screen").performScrollToNode(hasTestTag("replay-clip-USER"))
            compose.onNodeWithTag("replay-clip-USER").assertExists()
            compose.onNodeWithTag("replay-clip-AI").assertDoesNotExist()
            capture("replay-user")
        } finally { runBlocking { app.replays.delete(session); settings.update { previous } } }
    }
}
