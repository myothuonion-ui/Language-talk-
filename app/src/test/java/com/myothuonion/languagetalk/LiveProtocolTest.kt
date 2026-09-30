package com.myothuonion.languagetalk

import com.myothuonion.languagetalk.model.LiveSessionConfig
import com.myothuonion.languagetalk.network.LiveProtocol
import com.myothuonion.languagetalk.util.pcm16Wave
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class LiveProtocolTest {
    private val config = LiveSessionConfig("unused-test-key", listOf("test-model"), "Tutor", "Charon", silenceMs = 2500)
    @Test fun selectedVoiceIsInTheRawWebSocketGenerationConfiguration() {
        val setup = LiveProtocol.setup(config, "test-model")["setup"]!!.jsonObject
        assertNull(setup["responseModalities"])
        val generation = setup["generationConfig"]!!.jsonObject
        assertEquals("AUDIO", generation["responseModalities"]!!.jsonArray.single().jsonPrimitive.content)
        assertEquals("Charon", generation["speechConfig"]!!.jsonObject["voiceConfig"]!!.jsonObject["prebuiltVoiceConfig"]!!.jsonObject["voiceName"]!!.jsonPrimitive.content)
    }
    @Test fun answerPauseAndInterruptionAreConfigured() {
        val input = LiveProtocol.setup(config, "test-model")["setup"]!!.jsonObject["realtimeInputConfig"]!!.jsonObject
        assertEquals(2500, input["automaticActivityDetection"]!!.jsonObject["silenceDurationMs"]!!.jsonPrimitive.int)
        assertEquals("START_OF_ACTIVITY_INTERRUPTS", input["activityHandling"]!!.jsonPrimitive.content)
    }
    @Test fun reconnectIncludesTheLastResumptionHandle() {
        val setup = LiveProtocol.setup(config, "test-model", "resume-handle")["setup"]!!.jsonObject
        assertEquals("resume-handle", setup["sessionResumption"]!!.jsonObject["handle"]!!.jsonPrimitive.content)
        assertFalse(setup.toString().contains("unused-test-key"))
    }
    @Test fun transcriptFragmentsPreserveWordBoundariesAndCumulativeUpdates() {
        assertEquals("다시 설명해 주세요.", LiveProtocol.mergeTranscript("다시 ", "설명해 주세요."))
        assertEquals("다시 설명해 주세요.", LiveProtocol.mergeTranscript("다시 ", "다시 설명해 주세요."))
    }
    @Test fun restoredTurnsRemainConversationContentRatherThanSystemInstructions() {
        val content = LiveProtocol.continuation(listOf("user" to "hello", "model" to "안녕하세요."), "continue")["clientContent"]!!.jsonObject
        assertEquals(3, content["turns"]!!.jsonArray.size); assertTrue(content["turnComplete"]!!.jsonPrimitive.boolean)
    }
    @Test fun savedRecordingHasAValidPcmWaveHeader() {
        val wav = pcm16Wave(ByteArray(3200))
        assertEquals("RIFF", wav.copyOfRange(0, 4).decodeToString()); assertEquals("WAVE", wav.copyOfRange(8, 12).decodeToString())
        assertEquals(3244, wav.size)
    }
}
