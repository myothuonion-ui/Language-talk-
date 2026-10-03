package com.myothuonion.languagetalk

import com.myothuonion.languagetalk.model.LiveSessionConfig
import com.myothuonion.languagetalk.network.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Export actual app request bodies for provider checks performed outside public CI. */
class ProviderContractTest {
    @Test fun providerFixturesComeFromTheAppClientsAndContainNoCredential() = runBlocking<Unit> {
        val server = MockWebServer()
        server.start()
        try {
            val client = GeminiClient(OkHttpClient(), server.url("/v1beta/"))
            server.enqueue(MockResponse().setBody("""{"status":"completed","steps":[{"type":"model_output","content":[{"type":"text","text":"{\"reply\":\"안녕하세요.\",\"assessment\":\"NONE\"}"}]}]}"""))
            client.tutorReply("unused-test-credential", GeminiModels.TEXT,
                "You are a concise Korean tutor. Introduce one short greeting and ask the learner to repeat. Return all required JSON fields. Assessment NONE, no memory facts.",
                emptyList(), "Start one greeting phrase, then wait for me.")
            val text = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            server.enqueue(MockResponse().setBody("""{"status":"completed","steps":[{"type":"model_output","content":[{"type":"audio","mime_type":"audio/wav","data":"UklGRg=="}]}]}"""))
            client.synthesize("unused-test-credential", GeminiModels.SPEECH, "안녕하세요. 잘 부탁드립니다.", "Kore", "Warm, slow and clear Korean tutor.")
            val speech = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            assertNull(speech["response_format"]!!.jsonObject["delivery"])
            val live = LiveProtocol.setup(LiveSessionConfig("unused-test-credential", listOf(GeminiModels.LIVE),
                "You are a concise Korean tutor. Say one greeting, then wait. On the opening call update_learning_progress once with assessment NONE and targetSentence 안녕하세요. 잘 부탁드립니다. Never pretend to be the learner.", "Kore"), GeminiModels.LIVE)
            val fixtures = buildJsonObject {
                put("text", text); put("speech", speech); put("live", live)
                put("text_models", buildJsonArray { GeminiModels.defaults(GeminiTask.TEXT).forEach { add(JsonPrimitive(it)) } })
            }
            assertFalse(fixtures.toString().contains("unused-test-credential"))
            val directory = File("build/reports/provider-contract").apply { mkdirs() }
            File(directory, "requests.json").writeText(fixtures.toString())
        } finally { server.shutdown() }
    }
}
