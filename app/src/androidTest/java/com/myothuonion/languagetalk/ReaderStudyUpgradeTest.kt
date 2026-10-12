package com.myothuonion.languagetalk

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.myothuonion.languagetalk.data.*
import com.myothuonion.languagetalk.model.*
import com.myothuonion.languagetalk.ui.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReaderStudyUpgradeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as LanguageTalkApplication
    private fun pdf(): File {
        val file = File(app.cacheDir, "reader-study-test.pdf")
        PdfDocument().use { doc ->
            repeat(3) { index ->
                val page = doc.startPage(PdfDocument.PageInfo.Builder(600, 800, index + 1).create())
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawText("Hello reader " + (index + 1), 50f, 100f, Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 32f; color = Color.BLACK })
                doc.finishPage(page)
            }
            file.outputStream().use { doc.writeTo(it) }
        }
        return file
    }
    @Test fun annotationsAndTeachingSurviveDatabaseReopenAndBackupRestore() = runBlocking<Unit> {
        val name = "reader-study-durable.db"
        app.deleteDatabase(name)
        val annotation = ReaderAnnotation("durable-note", "pdf-test", "sha", "Test PDF", 1, "안녕하세요.", kind = "NOTE",
            note = "မြန်မာမှတ်စု", rectangles = PageTeachingCodec.json.encodeToString(listOf(PageRect(.1f, .2f, .4f, .25f))))
        val blocks = listOf(SourceBlock("line-0", "안녕하세요."))
        val teaching = PageTeaching("နုတ်ဆက်စကား", sentences = listOf(TeachingSentence("line-0", "안녕하세요.", "မင်္ဂလာပါ")))
        val explanation = PageExplanation("cache-test", "pdf-test", "sha", 1, "source-hash", PageTeachingCodec.json.encodeToString(teaching), PageTeachingCodec.json.encodeToString(blocks))
        var db = AppDatabase.open(app, name)
        try {
            db.dao().saveAnnotation(annotation); db.dao().savePageExplanation(explanation)
            db.close(); db = AppDatabase.open(app, name)
            assertEquals(annotation, db.dao().allAnnotations().single())
            assertEquals(teaching, db.dao().explanation("cache-test")!!.value())
            val backup = BackupStore(db, SettingsStore(app), File(app.cacheDir, "reader-backup-test"))
            val bytes = backup.export()
            db.dao().deleteAnnotation(annotation.id)
            backup.restore(bytes)
            assertEquals(annotation, db.dao().allAnnotations().single())
            assertEquals(explanation, BackupCodec.decode(bytes).pageExplanations.single())
        } finally { db.close(); app.deleteDatabase(name) }
    }
    @Test fun swipeNavigationAndPersistentSelectionToolsWorkOnTheRenderedPdf() {
        val old = runBlocking { app.learningStore.export() }
        val source = pdf()
        val book = runBlocking { app.reader.initialize(); app.reader.import(Uri.fromFile(source)) }
        val vm = ViewModelProvider(compose.activity)[ReaderViewModel::class.java]
        compose.activity.runOnUiThread { vm.open(book.id) }
        compose.activity.runOnUiThread { compose.activity.setContent { LanguageTalkTheme { PdfReaderScreen(vm, {}) } } }
        try {
            compose.waitUntil(30000) { vm.ui.value.pageData != null }
            compose.onNodeWithTag("reader-pager").performTouchInput { swipeLeft() }
            compose.waitUntil(30000) { vm.ui.value.page == 1 && vm.ui.value.pageData != null }
            val word = vm.ui.value.pageData!!.layer.words.first()
            compose.activity.runOnUiThread { vm.lookup(word.text, word.sentence, listOf(PageRect(word.left, word.top, word.right, word.bottom)), translate = false) }
            compose.waitUntil(10000) { compose.onAllNodesWithTag("add-highlight").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("add-highlight").performClick()
            compose.waitUntil(10000) { runBlocking { AppDatabase.get(app).dao().allAnnotations().any { it.bookId == book.id && it.page == 1 } } }
            compose.activity.runOnUiThread { vm.close(); vm.open(book.id) }
            compose.waitUntil(30000) { vm.ui.value.page == 1 && vm.ui.value.pageData != null }
            assertEquals(1, runBlocking { app.learningStore.export().books.first { it.id == book.id }.page })
            assertTrue(runBlocking { AppDatabase.get(app).dao().allAnnotations().first { it.bookId == book.id }.regions().isNotEmpty() })
        } finally {
            compose.activity.runOnUiThread { vm.close() }
            runBlocking { AppDatabase.get(app).dao().allAnnotations().filter { it.bookId == book.id }.forEach { AppDatabase.get(app).dao().deleteAnnotation(it.id) }; app.learningStore.update { old } }
            source.delete()
        }
    }
    @Test fun explicitAppearanceSurvivesRelaunchAndPortableBackup() = runBlocking<Unit> {
        val store = SettingsStore(app)
        val old = store.settings.first()
        try {
            store.update { it.copy(appearance = "DARK", darkTheme = true) }
            assertEquals("DARK", SettingsStore(app).settings.first().appearance)
            val bytes = app.repository.backup.export()
            store.update { it.copy(appearance = "LIGHT") }
            app.repository.backup.restore(bytes)
            assertEquals("DARK", store.settings.first().appearance)
            assertFalse(bytes.decodeToString().contains("geminiApiKey"))
        } finally { store.update { old } }
    }
}
