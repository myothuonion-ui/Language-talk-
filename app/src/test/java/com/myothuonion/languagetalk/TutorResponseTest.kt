package com.myothuonion.languagetalk

import com.myothuonion.languagetalk.network.AiApiException
import com.myothuonion.languagetalk.network.GeminiClient
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test

class TutorResponseTest {
    private val client = GeminiClient(OkHttpClient())
    @Test(expected = AiApiException::class) fun malformedJsonIsRejectedInsteadOfSpeakingIt() {
        client.parseTutorReply("This is not structured lesson data")
    }
    @Test(expected = AiApiException::class) fun emptyReplyCannotBecomeAValidLesson() {
        client.parseTutorReply("{\"reply\":\"\",\"assessment\":\"NONE\"}")
    }
    @Test fun validAssessmentAndNextPhraseArePreserved() {
        val reply = client.parseTutorReply("{\"reply\":\"좋아요.\",\"assessment\":\"PASSED\",\"nextTargetSentence\":\"감사합니다.\",\"heardText\":\"안녕하세요.\"}")
        assertEquals("PASSED", reply.assessment); assertEquals("감사합니다.", reply.nextTargetSentence)
        assertEquals("안녕하세요.", reply.heardText)
    }
}
