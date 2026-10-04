package com.myothuonion.languagetalk.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min

@Serializable
data class PdfWord(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float,
    val sentence: String, val line: Int)
@Serializable
data class PdfTextLayer(val text: String = "", val words: List<PdfWord> = emptyList(), val ocr: Boolean = true)
data class ReadablePdfPage(val bitmap: Bitmap, val layer: PdfTextLayer)

class PdfPageReader(context: Context) {
    private val cache = File(context.cacheDir, "reader-text").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }
    @RequiresApi(35)
    private fun nativeText(page: PdfRenderer.Page): String = page.textContents.joinToString("\n") { it.text }
    suspend fun render(file: File, id: String, index: Int): ReadablePdfPage = withContext(Dispatchers.IO) {
        var native = ""
        val bitmap = PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
            require(index in 0 until renderer.pageCount)
            renderer.openPage(index).use { page ->
                require(page.width > 0 && page.height > 0)
                if (Build.VERSION.SDK_INT >= 35) native = runCatching { nativeText(page) }.getOrDefault("")
                val scale = min(1440f / page.width, 2400f / page.height)
                val output = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1),
                    (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                output.eraseColor(android.graphics.Color.WHITE)
                page.render(output, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                output
            }
        }
        try {
            val saved = File(cache, id + "-" + index + ".json")
            val layer = if (saved.isFile) runCatching { json.decodeFromString<PdfTextLayer>(saved.readText()) }.getOrNull() else null
            if (layer != null) return@withContext ReadablePdfPage(bitmap, layer)
            val korean = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            val latin = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                val useLatin = native.isNotBlank() && native.none { it in '\uAC00'..'\uD7AF' }
                val chosen = if (useLatin) latin else korean
                var recognized = suspendCancellableCoroutine<com.google.mlkit.vision.text.Text> { continuation ->
                    chosen.process(InputImage.fromBitmap(bitmap, 0)).addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                        .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
                }
                if (recognized.text.isBlank() && !useLatin) recognized = suspendCancellableCoroutine { continuation ->
                    latin.process(InputImage.fromBitmap(bitmap, 0)).addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                        .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
                }
                var number = 0
                val words = recognized.textBlocks.flatMap { block -> block.lines.flatMap { line ->
                    val lineNumber = number++
                    line.elements.mapNotNull { word ->
                        word.boundingBox?.let { rect -> PdfWord(word.text,
                            rect.left.toFloat() / bitmap.width, rect.top.toFloat() / bitmap.height,
                            rect.right.toFloat() / bitmap.width, rect.bottom.toFloat() / bitmap.height, line.text, lineNumber) }
                    }
                } }
                val result = PdfTextLayer(native.ifBlank { recognized.text }, words, native.isBlank())
                saved.writeText(json.encodeToString(result))
                cache.listFiles()?.sortedByDescending { it.lastModified() }?.drop(500)?.forEach { it.delete() }
                ReadablePdfPage(bitmap, result)
            } finally { korean.close(); latin.close() }
        } catch (failure: Throwable) {
            if (failure is CancellationException) { bitmap.recycle(); throw failure }
            // A recognition failure must never prevent the original PDF from being read.
            ReadablePdfPage(bitmap, PdfTextLayer(native, emptyList(), native.isBlank()))
        }
    }
}
