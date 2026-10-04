package com.myothuonion.languagetalk

import com.myothuonion.languagetalk.model.*
import com.myothuonion.languagetalk.network.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class AiRoutingTest {
    private val refs = listOf(AiModelRef("gemini", "first"), AiModelRef("gemini", "second"), AiModelRef("nvidia", "third"))

    @Test fun modelFailureTriesSameKeyBeforeTheNextProvider() = runTest {
        val seen = mutableListOf<AiModelRef>()
        val result = AiRouter().run(refs) { ref ->
            seen += ref
            if (ref.model == "first") throw AiApiException("missing model", 404)
            "ok"
        }
        assertEquals(refs.take(2), seen)
        assertEquals(refs[1], result.selected)
        assertTrue(result.usedFallback)
    }

    @Test fun invalidKeyAndProviderQuotaSkipTheRemainingModelsOfThatKey() = runTest {
        for (failure in listOf(AiApiException("secret echoed", 403), AiApiException("daily limit", 429, true))) {
            val seen = mutableListOf<AiModelRef>()
            val result = AiRouter().run(refs) { ref -> seen += ref; if (ref.profileId == "gemini") throw failure else "ok" }
            assertEquals(listOf(refs[0], refs[2]), seen)
            assertEquals("nvidia", result.selected.profileId)
        }
    }

    @Test fun modelRateLimitCanUseAnotherModelUnderTheSameKey() = runTest {
        val result = AiRouter().run(refs) { if (it.model == "first") throw AiApiException("rate", 429) else "ok" }
        assertEquals("second", result.selected.model)
    }

    @Test fun cancellationNeverTriggersAnotherApiCall() = runTest {
        var count = 0
        try { AiRouter().run(refs) { count++; throw CancellationException("User stopped") } }
        catch (_: CancellationException) { }
        assertEquals(1, count)
    }

    @Test fun slowModelIsBoundedAndFallsBack() = runTest {
        val result = AiRouter(attemptMs = 20, totalMs = 80).run(refs) {
            if (it.model == "first") delay(100)
            "ok"
        }
        assertEquals("second", result.selected.model)
    }

    @Test fun onlyEnabledReadyAndAuthorizedProfilesJoinAutoFallback() {
        val config = AiConfiguration()
        val allKeys: (String) -> Boolean = { true }
        val defaults = AiPlans.candidates(config, AiTask.TRANSLATE, allKeys)
        assertEquals("gemini-3.5-flash-lite", defaults.first().model)
        assertEquals(setOf("gemini", "nvidia"), defaults.map { it.profileId }.toSet())
        val selected = AiPlans.candidates(config, AiTask.BOOK, allKeys,
            AiRoutePrefs(AiMode.CUSTOM, AiModelRef("claude", "my-model")))
        assertEquals(AiModelRef("claude", "my-model"), selected.first())
        assertFalse(selected.any { it.profileId == "openai" })
    }

    @Test fun customModelAndProviderOrderArePreserved() {
        val config = AiConfiguration(profiles = builtInAiProfiles().map { it.copy(useAsFallback = true) })
        val route = AiRoutePrefs(AiMode.CUSTOM, AiModelRef("nvidia", "custom/primary"),
            modelBackups = listOf("custom/backup"), fallbacks = listOf(AiModelRef("claude", "custom/reviewer"), AiModelRef("gemini")))
        val refs = AiPlans.candidates(config, AiTask.CONVERSATION, { true }, route)
        assertEquals(listOf("custom/primary", "custom/backup"), refs.take(2).map { it.model })
        assertEquals(listOf("nvidia", "claude", "gemini", "openai", "deepseek"), refs.map { it.profileId }.distinct())
    }

    @Test fun textOnlyModelsCannotBeSpeechOrLiveFallbacksAndReviewNeedsAnotherProfile() {
        val config = AiConfiguration()
        assertEquals(setOf("gemini"), AiPlans.candidates(config, AiTask.SPEECH, { true }).map { it.profileId }.toSet())
        assertFalse(config.profiles.first { it.id == "nvidia" }.supports(AiTask.TRANSCRIBE))
        assertFalse(config.profiles.first { it.id == "claude" }.supports(AiTask.LIVE))
        assertNull(AiPlans.reviewer(config, AiTask.BOOK, "gemini", { it == "gemini" }, AiRoutePrefs(AiMode.REVIEW)))
    }

    @Test fun koreanSpeechExcludesMyanmarAndEnglishExplanations() {
        val text = VoiceText.korean("မြန်မာစာ ရှင်းပြချက်။\n물 주세요.\nEnglish explanation.\nရေတောင်းတာပါ။")
        assertEquals("물 주세요.", text)
        assertEquals("", VoiceText.korean("မြန်မာရှင်းပြချက် only"))
        assertEquals("ㅏ ㅓ ㅗ", VoiceText.korean("ㅏ ㅓ ㅗ"))
    }

    @Test fun nvidiaHostedModelsUseTheNvidiaCredentialAndNoUniversalThinkingOptions() = runTest {
        MockWebServer().use { server ->
            server.enqueue(chatReply("""{"reply":"물 주세요.","assessment":"NONE","answerPattern":"[အရာ] 주세요."}"""))
            val profile = builtInAiProfiles().first { it.id == "nvidia" }.copy(baseUrl = server.url("/v1/").toString())
            val result = ProviderClient().tutor(profile, "nvidia-test-only", "z-ai/glm-5.3-flash", "Tutor", emptyList(), "Water please")
            assertEquals("[အရာ] 주세요.", result.answerPattern)
            val request = server.takeRequest()
            assertEquals("/v1/chat/completions", request.path)
            assertEquals("Bearer nvidia-test-only", request.getHeader("Authorization"))
            val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals("z-ai/glm-5.3-flash", body["model"]?.jsonPrimitive?.content)
            assertFalse(body.containsKey("chat_template_kwargs"))
        }
    }

    @Test fun claudeUsesNativeMessagesAuthAndParsesOnlyTextContent() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(buildJsonObject {
                put("content", buildJsonArray {
                    add(buildJsonObject { put("type", "thinking"); put("thinking", "private") })
                    add(buildJsonObject { put("type", "text"); put("text", """{"reply":"안녕하세요.","assessment":"NONE"}""") })
                })
            }.toString()))
            val profile = builtInAiProfiles().first { it.id == "claude" }.copy(baseUrl = server.url("/v1/").toString())
            val result = ProviderClient().tutor(profile, "claude-test-only", profile.textModel, "Korean tutor", emptyList(), "Hello")
            assertEquals("안녕하세요.", result.reply)
            val request = server.takeRequest()
            assertEquals("/v1/messages", request.path)
            assertEquals("claude-test-only", request.getHeader("x-api-key"))
            assertEquals("2023-06-01", request.getHeader("anthropic-version"))
            assertNull(request.getHeader("Authorization"))
            val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertTrue(body.containsKey("system"))
            assertEquals("user", body["messages"]?.jsonArray?.first()?.jsonObject?.get("role")?.jsonPrimitive?.content)
        }
    }

    @Test fun openAiUsesCompletionTokenFieldAndErrorsDoNotEchoCredentials() = runTest {
        MockWebServer().use { server ->
            val profile = builtInAiProfiles().first { it.id == "openai" }.copy(baseUrl = server.url("/v1/").toString())
            server.enqueue(chatReply("""{"reply":"안녕하세요.","assessment":"NONE"}"""))
            ProviderClient().tutor(profile, "key-test-only", profile.textModel, "Tutor", emptyList(), "Hello")
            val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            assertTrue(body.containsKey("max_completion_tokens"))
            assertFalse(body.containsKey("max_tokens"))
            server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"key-test-only was rejected"}}"""))
            val failure = runCatching { ProviderClient().tutor(profile, "key-test-only", profile.textModel, "Tutor", emptyList(), "Hello") }.exceptionOrNull()
            assertTrue(failure is AiApiException)
            assertFalse(failure!!.message.orEmpty().contains("key-test-only"))
        }
    }

    @Test fun badOrEmptyStructuredOutputTriggersTheNextModel() = runTest {
        MockWebServer().use { server ->
            server.enqueue(chatReply(""))
            server.enqueue(chatReply("""{"reply":"안녕하세요.","assessment":"NONE"}"""))
            val profile = builtInAiProfiles().first { it.id == "nvidia" }.copy(baseUrl = server.url("/v1/").toString())
            val result = AiRouter().run(listOf(AiModelRef("nvidia", "a"), AiModelRef("nvidia", "b"))) {
                ProviderClient().tutor(profile, "test-only", it.model, "Tutor", emptyList(), "Hello")
            }
            assertEquals("b", result.selected.model)
        }
    }

    @Test fun endpointValidationRejectsCredentialsQueriesAndBuiltinEndpointReplacement() {
        val custom = AiProfile("custom", "Custom", AiProviderKind.CUSTOM, "https://example.com/v1/", textModel = "my-model")
        validateAiProfile(custom)
        listOf("http://example.com/v1/", "https://user:pass@example.com/v1/", "https://example.com/v1/?key=x").forEach {
            assertTrue(runCatching { validateAiProfile(custom.copy(baseUrl = it)) }.isFailure)
        }
        assertTrue(runCatching { validateAiProfile(builtInAiProfiles().first().copy(baseUrl = "https://example.com/v1/")) }.isFailure)
    }

    private fun chatReply(content: String) = MockResponse().setBody(buildJsonObject {
        put("choices", buildJsonArray { add(buildJsonObject { put("message", buildJsonObject { put("content", content) }) }) })
    }.toString())
}
