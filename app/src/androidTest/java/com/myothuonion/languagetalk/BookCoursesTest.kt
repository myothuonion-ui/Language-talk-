package com.myothuonion.languagetalk

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myothuonion.languagetalk.data.BackupCodec
import com.myothuonion.languagetalk.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BookCoursesTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val application get() = compose.activity.application as LanguageTalkApplication
    private fun capture(name: String) {
        val directory = File(application.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun packagedBooksRenderAndOriginalAnswerAdvancesOnlyOneTurn() {
        runBlocking { application.bookStore.update { BookShelfState() } }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("open-books").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("open-books").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("continue-book").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Beginner · 40").assertIsDisplayed()
        compose.onNodeWithText("Intermediate · 30").assertIsDisplayed()
        capture("built-in-books")
        compose.onNodeWithTag("continue-book").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("book-answer").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("book-courses").performScrollToNode(hasTestTag("book-answer"))
        capture("book-dialogue")
        compose.onNodeWithTag("book-answer").performTextInput("반갑습니다. 저는 이지연이에요.")
        compose.onNodeWithTag("book-courses").performScrollToNode(hasText("အဖြေစစ်"))
        compose.onNodeWithText("အဖြေစစ်").performClick()
        compose.waitUntil(10000) {
            runBlocking { application.books.progress("ttmik-beginner").activity == 2 }
        }
        compose.onNodeWithTag("book-courses").performScrollToNode(hasTestTag("book-next"))
        compose.onNodeWithTag("book-next").assertIsEnabled().performClick()
        compose.waitUntil(10000) { runBlocking { application.books.progress("ttmik-beginner").section == 1 } }
        compose.onNodeWithTag("book-courses").performScrollToIndex(0)
        capture("book-vocabulary")
    }

    @Test fun sourcePdfAndBookStateSurviveBackupWithoutPersonalCredentials() = runBlocking<Unit> {
        val repository = application.books
        val courses = repository.courses()
        assertEquals(listOf(40, 30), courses.map { it.chapters.size })
        for (book in courses) {
            val file = repository.sourcePdf(book.id)
            PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use {
                assertEquals(book.pageCount, it.pageCount)
                it.openPage(16).use { page ->
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap.recycle()
                }
            }
        }
        val saved = BookProgress("ttmik-beginner", chapter = 1, section = 5, activity = 2,
            completedSections = listOf("ttmik-beginner/user-pdf-1/1/short"), heard = "저는 회사원이에요.", feedback = "မှန်ပါတယ်")
        application.bookStore.update { BookShelfState(progress = listOf(saved), explanations = listOf(BookExplanation("ttmik-beginner/user-pdf-1/1/grammar/myanmar", "မြန်မာရှင်းပြချက်"))) }
        val bytes = application.repository.backup.export()
        val backup = BackupCodec.decode(bytes)
        assertEquals(saved, backup.books.progress.single())
        assertFalse(bytes.decodeToString().contains("geminiApiKey"))
        application.bookStore.update { BookShelfState() }
        application.repository.backup.restore(bytes)
        assertEquals(saved, application.bookStore.export().progress.single())
        assertEquals("မြန်မာရှင်းပြချက်", application.bookStore.export().explanations.single().text)
        application.bookStore.update { BookShelfState() }
    }
}
