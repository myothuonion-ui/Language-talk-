package com.myothuonion.languagetalk.model

enum class AppLanguage(val label: String, val code: String) {
    KOREAN("한국어", "ko"),
    ENGLISH("English", "en"),
    MIXED("한국어 + English", "mixed")
}

enum class BrainMode(val label: String, val description: String) {
    GEMINI_ONLY("Gemini Only", "မြန်ဆန်ပြီး API key တစ်ခုတည်းသာလိုသည်"),
    HYBRID_AUTO("Hybrid Auto", "ခက်ခဲသောစာများကို Nemotron ဖြင့်စစ်မည်"),
    BEST_QUALITY("Best Quality", "Gemini နှင့် Nemotron နှစ်ခုလုံးဖြင့်စစ်မည်"),
    NVIDIA_BRAIN("NVIDIA Brain", "Nemotron စဉ်းစားပြီး Gemini အသံပြောမည်")
}

enum class TutorRole(val label: String) {
    TEACHER("ဆရာ/ဆရာမ"),
    FRIEND("သူငယ်ချင်း"),
    COWORKER("အလုပ်ဖော်"),
    MANAGER("မန်နေဂျာ"),
    INTERVIEWER("အင်တာဗျူးမေးသူ")
}

enum class CorrectionMode(val label: String) {
    INSTANT("ချက်ချင်းပြင်"),
    AFTER_REPLY("စကားပြီးမှပြင်"),
    OFF("မပြင်")
}

enum class MessageRole { USER, ASSISTANT, SYSTEM }

data class TutorConfig(
    val language: AppLanguage = AppLanguage.KOREAN,
    val level: String = "Beginner",
    val topic: String = "နေ့စဉ်စကားပြော",
    val role: TutorRole = TutorRole.TEACHER,
    val correctionMode: CorrectionMode = CorrectionMode.AFTER_REPLY,
    val customPrompt: String = "",
    val voiceName: String = "Kore",
    val voiceStyle: String = "နူးညံ့ပြီး ရှင်းလင်းသော ဆရာမအသံ",
    val brainMode: BrainMode = BrainMode.GEMINI_ONLY,
    val initialMemory: String = ""
)

data class LiveSessionConfig(
    val apiKey: String,
    val models: List<String>,
    val systemInstruction: String,
    val voiceName: String
)

data class GeminiRouteStatus(
    val task: String = "",
    val requestedModel: String = "",
    val activeModel: String = "",
    val usedFallback: Boolean = false
)

data class KoreanNameCandidate(
    val hangul: String,
    val romanization: String,
    val myanmarPronunciation: String,
    val soundNotes: String,
    val naturalnessScore: Int,
    val vibe: String,
    val hanjaInspiredMeaning: String,
    val animal: String,
    val animalSymbolism: String,
    val primaryColor: String,
    val secondaryColor: String,
    val lightPattern: String,
    val layoutStyle: String,
    val motto: String,
    val externalImagePrompt: String
)

data class KoreanNameResult(
    val originalName: String,
    val candidates: List<KoreanNameCandidate>,
    val activeModel: String = ""
)

data class TranslationResult(
    val detectedLanguage: String,
    val originalText: String,
    val myanmarMeaning: String,
    val naturalTranslation: String,
    val pronunciation: String,
    val wordBreakdown: String,
    val grammarNote: String,
    val activeModel: String = ""
)

data class TutorReply(
    val reply: String,
    val translation: String = "",
    val correction: String = "",
    val explanation: String = "",
    val followUpQuestion: String = ""
) {
    val spokenText: String
        get() = buildString {
            append(reply.trim())
            if (followUpQuestion.isNotBlank()) {
                append(" ")
                append(followUpQuestion.trim())
            }
        }
}

data class VoicePreset(
    val name: String,
    val voice: String,
    val style: String,
    val previewText: String
)

val DefaultVoicePresets = listOf(
    VoicePreset("Korean Female Teacher", "Kore", "Warm Korean female teacher. Speak clearly at a slightly slow pace.", "안녕하세요. 오늘도 한국어를 같이 공부해요."),
    VoicePreset("Korean Friendly", "Aoede", "Friendly native Korean speaker with natural conversational pacing.", "오늘 하루는 어땠어요? 편하게 이야기해 주세요."),
    VoicePreset("Korean Male", "Charon", "Calm Korean male tutor. Clear pronunciation with short pauses.", "천천히 말해도 괜찮아요. 제가 도와드릴게요."),
    VoicePreset("English Female Tutor", "Leda", "Encouraging English tutor. Clear neutral pronunciation.", "Let's practice English together, one step at a time."),
    VoicePreset("English Male Friend", "Puck", "Relaxed friendly English speaker with natural rhythm.", "Hey! What would you like to talk about today?"),
    VoicePreset("EPS Listening", "Kore", "Formal Korean EPS listening-test narrator. Precise, steady, and neutral.", "다음을 듣고 알맞은 것을 고르십시오.")
)
