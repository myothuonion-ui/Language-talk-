package com.myothuonion.languagetalk

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myothuonion.languagetalk.data.BackupCodec
import com.myothuonion.languagetalk.model.*
import com.myothuonion.languagetalk.util.PdfPageReader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class ReaderAndLearningTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as LanguageTalkApplication
    private fun capture(name: String) {
        compose.waitForIdle()
        val directory = File(app.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        // A modal sheet uses its own window; capture the displayed page and sheet together.
        val screenshot = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(directory, name + ".png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
    }
    @Test fun scannedPdfCanBeImportedSelectedTranslatedFromCacheAndSavedWithoutLeavingReader() {
        val source = File(app.cacheDir, "QA scan.pdf")
        val image = Bitmap.createBitmap(840, 840, Bitmap.Config.ARGB_8888)
        Canvas(image).apply {
            drawColor(Color.WHITE)
            drawText("Hello reader", 90f, 160f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 62f })
        }
        val pdf = PdfDocument()
        try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(840, 840, 1).create())
            page.canvas.drawBitmap(image, 0f, 0f, null)
            pdf.finishPage(page)
            source.outputStream().use { pdf.writeTo(it) }
        } finally { pdf.close() }
        image.recycle()
        val saved = runBlocking { app.learningStore.export() }
        try {
            val book = runBlocking { app.reader.initialize(); app.reader.import(Uri.fromFile(source)) }
            val duplicate = runBlocking { app.reader.import(Uri.fromFile(source)) }
            assertEquals(book.id, duplicate.id)
            val page = runBlocking { PdfPageReader(app).render(app.reader.file(book), book.id, 0) }
            val word = page.layer.words.firstOrNull { it.text.equals("Hello", true) }
            assertNotNull("Bundled OCR must recognize scanned English text offline", word)
            word!!
            val key = MessageDigest.getInstance("SHA-256").digest((word.text + "|" + word.sentence).toByteArray())
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            runBlocking { app.learningStore.update { it.copy(translations = listOf(
                ReaderTranslation(key, word.text, "hello", "မင်္ဂလာပါ", "နုတ်ဆက်စကား", word.sentence))) } }
            compose.waitUntil(15000) { compose.onAllNodesWithTag("bookshelf").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("bookshelf").performScrollToNode(hasTestTag("read-" + book.id))
            compose.onNodeWithTag("read-" + book.id).performClick()
            compose.waitUntil(30000) { compose.onAllNodesWithContentDescription("PDF page 1").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("PDF page 1").performTouchInput {
                longClick(Offset(width * (word.left + word.right) / 2, height * (word.top + word.bottom) / 2), 1100)
            }
            compose.waitUntil(15000) { compose.onAllNodesWithText("မင်္ဂလာပါ").fetchSemanticsNodes().isNotEmpty() }
            compose.mainClock.advanceTimeBy(1000)
            compose.waitForIdle()
            compose.onNodeWithText("မင်္ဂလာပါ").assertIsDisplayed()
            compose.onNodeWithTag("pdf-reader").assertExists()
            capture("reader-translate")
            compose.onNodeWithText("သိမ်းမယ်").performScrollTo().performClick()
            compose.waitUntil(10000) { runBlocking { app.learningStore.export().cards.any { it.kind == "WORD" && it.bookId == book.id } } }
            val card = runBlocking { app.learningStore.export().cards.first { it.kind == "WORD" && it.bookId == book.id } }
            assertEquals(0, card.page)
            assertEquals(word.sentence, card.context)
            assertEquals("မင်္ဂလာပါ", card.meaning)
            page.bitmap.recycle()
        } finally {
            compose.activity.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
            runBlocking { app.learningStore.update { saved } }
            source.delete()
        }
    }
    @Test fun curriculumLearningAndReaderReferencesSurvivePortableBackup() = runBlocking<Unit> {
        val old = app.learningStore.export()
        try {
            val curriculum = app.learningStore.curriculum
            assertEquals(48, curriculum.size)
            assertEquals((0..5).toSet(), curriculum.map { it.level }.toSet())
            assertTrue(curriculum.filter { it.level == 5 }.all { CoachEngine.task(it, 6).hint.isBlank() })
            val learning = LearningState(level = 2, activeUnit = "course-2-1",
                units = listOf(UnitProgress("course-2-1", 3, false, 5, 3, 100)),
                cards = listOf(ReviewCard("word-test", "먹다", "말해 보세요.", "Use 먹다", "먹었어요", "စားခဲ့တယ်", bookId = "ttmik-beginner", page = 16, kind = "WORD")),
                books = listOf(ReaderBook("ttmik-beginner", "Beginner", assetId = "ttmik-beginner", pageCount = 332, page = 16, bookmarks = listOf(16))),
                readerTheme = "SEPIA", studySeconds = mapOf("2026-10-04" to 120))
            app.learningStore.update { learning }
            val bytes = app.repository.backup.export()
            assertEquals(learning, BackupCodec.decode(bytes).learning)
            assertFalse(bytes.decodeToString().contains("geminiApiKey"))
            app.learningStore.update { LearningState() }
            app.repository.backup.restore(bytes)
            assertEquals(learning, app.learningStore.export())
            val bad = learning.copy(books = listOf(ReaderBook("bad", "bad", "../../secret", pageCount = 1)))
            assertTrue(runCatching { app.learningStore.validateRestore(bad) }.isFailure)
        } finally { app.learningStore.update { old } }
    }
}
