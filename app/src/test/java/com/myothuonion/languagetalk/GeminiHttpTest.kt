package com.myothuonion.languagetalk

import com.myothuonion.languagetalk.data.MessageEntity
import com.myothuonion.languagetalk.network.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Base64

class GeminiHttpTest {
    private lateinit var server: MockWebServer
    private lateinit var client: GeminiClient
    @Before fun setup() { server = MockWebServer(); server.start(); client = GeminiClient(OkHttpClient(), server.url("/v1beta/")) }
    @After fun close() { server.shutdown() }
    private fun enqueue(body: String, code: Int = 200) = server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body))
    private fun output(content: JsonArray) = buildJsonObject {
        put("status", "completed")
        put("steps", buildJsonArray {
            add(buildJsonObject { put("type", "thought"); put("signature", "private-reasoning-signature") })
            add(buildJsonObject { put("type", "model_output"); put("content", content) })
        })
    }.toString()
    private fun replyResponse() = output(buildJsonArray { add(buildJsonObject {
        put("type", "text"); put("text", "{\"reply\":\"안녕하세요.\",\"assessment\":\"NONE\",\"heardText\":\"\"}")
    }) })

    @Test fun catalogPaginationKeepsKeysOutOfUrlsAndFindsModelsAfterFirstPage() = runBlocking<Unit> {
        enqueue("""{"models":[{"name":"models/gemini-2.5-flash"}],"nextPageToken":"page/+2"}""")
        enqueue("""{"models":[{"name":"models/gemini-3.8-flash","supportedGenerationMethods":["generateContent"]}]}""")
        val models = client.listModelCatalog("test-key-only")
        assertEquals(2, models.size)
        val first = server.takeRequest(); val second = server.takeRequest()
        assertEquals("test-key-only", first.getHeader("x-goog-api-key"))
        assertNull(first.requestUrl!!.queryParameter("key"))
        assertEquals("page/+2", second.requestUrl!!.queryParameter("pageToken"))
        assertEquals(listOf(GeminiModels.TEXT), GeminiModels.candidates("gemini-2.5-flash", GeminiTask.TEXT, models))
    }

    @Test fun repeatedCatalogPageTokenFailsInsteadOfLooping() = runBlocking<Unit> {
        repeat(2) { enqueue("""{"models":[],"nextPageToken":"same"}""") }
        assertTrue(runCatching { client.listModels("test-key-only") }.exceptionOrNull() is AiApiException)
        assertEquals(2, server.requestCount)
    }

    @Test fun tutorUsesInteractionsWithCurrentAudioAndBoundedLocalHistory() = runBlocking<Unit> {
        enqueue(replyResponse())
        val reply = client.tutorReply("test-key-only", "models/gemini-3.8-flash", "Stay on the learner's topic.",
            listOf(MessageEntity(chatId = 1, role = "USER", content = "I work nights.")), "Continue.", AudioPayload(byteArrayOf(1, 2, 3), "audio/mp4"))
        assertEquals("안녕하세요.", reply.reply)
        val request = server.takeRequest()
        assertEquals("/v1beta/interactions", request.path)
        assertEquals("test-key-only", request.getHeader("x-goog-api-key"))
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals(GeminiModels.TEXT, body["model"]!!.jsonPrimitive.content)
        assertFalse(body["store"]!!.jsonPrimitive.boolean)
        assertEquals("Stay on the learner's topic.", body["system_instruction"]!!.jsonPrimitive.content)
        val input = body["input"]!!.jsonArray
        assertTrue(input.first().jsonObject["text"]!!.jsonPrimitive.content.contains("I work nights."))
        val audio = input.first { it.jsonObject["type"]!!.jsonPrimitive.content == "audio" }.jsonObject
        assertEquals("audio/m4a", audio["mime_type"]!!.jsonPrimitive.content)
        assertArrayEquals(byteArrayOf(1, 2, 3), Base64.getDecoder().decode(audio["data"]!!.jsonPrimitive.content))
        assertEquals("application/json", body["response_format"]!!.jsonObject["mime_type"]!!.jsonPrimitive.content)
        assertEquals("object", body["response_format"]!!.jsonObject["schema"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test fun speechStyleIsMetadataAndOnlyTheActualSentenceIsSpoken() = runBlocking<Unit> {
        val bytes = byteArrayOf(82, 73, 70, 70, 1, 2, 3)
        enqueue(output(buildJsonArray { add(buildJsonObject { put("type", "audio"); put("mime_type", "audio/wav"); put("data", Base64.getEncoder().encodeToString(bytes)) }) }))
        val audio = client.synthesize("test-key-only", GeminiModels.SPEECH, "안녕하세요.", "Charon", "Warm and slow.")
        assertEquals("audio/wav", audio.mimeType); assertArrayEquals(bytes, audio.bytes)
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        val text = body["input"]!!.jsonArray.single().jsonObject["content"]!!.jsonArray.single().jsonObject
        assertEquals("안녕하세요.", text["text"]!!.jsonPrimitive.content)
        assertEquals("Warm and slow.", text["annotations"]!!.jsonArray.single().jsonObject["style"]!!.jsonPrimitive.content)
        assertEquals("Charon", body["generation_config"]!!.jsonObject["speech_config"]!!.jsonArray.single().jsonObject["voice"]!!.jsonPrimitive.content)
        assertEquals("audio/wav", body["response_format"]!!.jsonObject["mime_type"]!!.jsonPrimitive.content)
        assertNull(body["response_format"]!!.jsonObject["delivery"])
    }

    @Test fun modernQuotaFailureRoutesToAvailableModernModelNeverToLegacy() = runBlocking<Unit> {
        enqueue("""{"error":{"message":"Quota exhausted"}}""", 429)
        enqueue(replyResponse())
        val candidates = GeminiModels.candidates("gemini-2.5-flash", GeminiTask.TEXT,
            listOf(GeminiModel(GeminiModels.TEXT), GeminiModel("gemini-3.5-flash-lite"), GeminiModel("gemini-2.5-flash")))
        val result = routeGemini("Text", candidates) { client.tutorReply("test-key-only", it, "Tutor", emptyList(), "Hello") }
        assertEquals("gemini-3.5-flash-lite", result.model)
        val requests = List(2) { Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["model"]!!.jsonPrimitive.content }
        assertEquals(listOf(GeminiModels.TEXT, "gemini-3.5-flash-lite"), requests)
    }

    @Test fun ttsQuotaFailureIsNotReportedAsSuccessfulVoice() = runBlocking<Unit> {
        enqueue("""{"error":{"message":"Speech daily quota exhausted"}}""", 429)
        val failure = runCatching { client.synthesize("test-key-only", GeminiModels.SPEECH, "Hello", "Kore", "Clear") }.exceptionOrNull() as AiApiException
        assertEquals(429, failure.statusCode)
        val check = GeminiKeyCheck(61, GeminiModels.TEXT, null, GeminiModels.LIVE, listOf(failure.message!!))
        assertFalse(check.ready); assertTrue(check.summary.contains("Speech daily quota exhausted"))
    }

    @Test fun missingAudioCannotPassTheSpeechCheck() = runBlocking<Unit> {
        enqueue(replyResponse())
        val failure = runCatching { client.synthesize("test-key-only", GeminiModels.SPEECH, "Hello", "Kore", "Clear") }.exceptionOrNull()
        assertTrue(failure is AiApiException)
        assertTrue(failure!!.message!!.contains("no audio"))
    }

    @Test fun importedUtf8TextIsAContentBlockInsteadOfAnUnsupportedDocumentMime() = runBlocking<Unit> {
        enqueue(output(buildJsonArray { add(buildJsonObject { put("type", "text"); put("text", "Saved summary") }) }))
        assertEquals("Saved summary", client.summarizeSource("test-key-only", GeminiModels.TEXT, "note.txt", "text/plain", "ညဆိုင်း".toByteArray()))
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("ညဆိုင်း", body["input"]!!.jsonArray.first().jsonObject["text"]!!.jsonPrimitive.content)
    }

    @Test fun failedInteractionPreservesProviderErrorCode() {
        val failure = runCatching { GeminiInteractions.output(Json.parseToJsonElement("""{"status":"failed","error":{"code":429,"message":"Quota exhausted"}}""").jsonObject) }.exceptionOrNull() as AiApiException
        assertEquals(429, failure.statusCode)
    }
}
