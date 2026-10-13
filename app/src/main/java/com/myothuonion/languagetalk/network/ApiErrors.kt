package com.myothuonion.languagetalk.network

/** Safe causes rather than provider bodies, which can contain keys or private source text. */
object ApiErrors {
    fun failure(provider: String, code: Int, body: String): AiApiException {
        val lower = body.lowercase()
        val quota = code == 429 && listOf("insufficient_quota", "billing", "credit", "daily", "perday", "per_day").any(lower::contains)
        val reason = when (code) {
            401 -> "API key မမှန်ပါ သို့မဟုတ် သက်တမ်းကုန်နေပါတယ်။"
            403 -> if (lower.contains("leaked")) "Provider က ဒီ key ကို leaked key အဖြစ် ပိတ်ထားပါတယ်။ Key အသစ်လိုပါတယ်။"
                else "ဒီ API / key အတွက် ခွင့်ပြုချက် သို့မဟုတ် key ကန့်သတ်မှုကို စစ်ပါ။"
            404 -> "ဒီ account မှာ ရွေးထားတဲ့ model / endpoint မရပါ။"
            429 -> if (quota) "Quota / credit ကုန်နေပါတယ်။" else "ခဏအတွင်း request များလွန်းပါတယ်။ ခဏနားပြီး စမ်းပါ။"
            400, 415, 422 -> "ဒီ model ရဲ့ request / file ပုံစံ မကိုက်ပါ။ အခြား model ကို ရွေးပါ။"
            500, 502, 503 -> "Provider က လောလောဆယ် အလုပ်များနေပါတယ်။ အခြား model နဲ့ စမ်းနိုင်ပါတယ်။"
            504 -> "API အချိန်ကျော်သွားပါတယ်။"
            else -> "API request မအောင်မြင်ပါ။"
        }
        return AiApiException("$provider · $reason ($code)", code, quota || code in setOf(401, 403))
    }
}
