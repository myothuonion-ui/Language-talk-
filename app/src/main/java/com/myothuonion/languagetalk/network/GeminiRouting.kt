package com.myothuonion.languagetalk.network

import com.myothuonion.languagetalk.data.isGeminiFallbackEligible
import kotlinx.coroutines.CancellationException

data class GeminiResult<T>(val value: T, val model: String)

suspend fun <T> routeGemini(task: String, candidates: List<String>, call: suspend (String) -> T): GeminiResult<T> {
    require(candidates.isNotEmpty()) { "ဒီ key အတွက် $task model မရှိပါ။ Settings မှာ Test ကိုနှိပ်ပြီး text နဲ့ voice access ကိုစစ်ပါ။" }
    val failures = mutableListOf<Pair<String, Throwable>>()
    for (model in candidates.distinct()) {
        try { return GeminiResult(call(model), model) }
        catch (failure: Throwable) {
            if (failure is CancellationException) throw failure
            if (!isGeminiFallbackEligible(failure)) throw failure
            failures += model to failure
        }
    }
    // A later unavailable endpoint must not hide the original quota/setup failure.
    val important = failures.firstOrNull { (it.second as? AiApiException)?.statusCode == 429 }
        ?: failures.firstOrNull { (it.second as? AiApiException)?.statusCode != 404 } ?: failures.first()
    val error = important.second
    throw AiApiException("$task မအောင်မြင်ပါ (${important.first}). ${error.message}\nTried: ${failures.joinToString { it.first }}", (error as? AiApiException)?.statusCode)
}

data class GeminiKeyCheck(val modelCount: Int, val textModel: String?, val speechModel: String?, val liveModel: String?, val issues: List<String>) {
    val ready: Boolean get() = textModel != null && speechModel != null
    val summary: String get() = if (ready) "Ready · Text ✓ · Voice ✓\nLive connection ကိုစတင်ချိန်တွင်စစ်မည်"
        else "Key accepted · Voice not ready\n${issues.joinToString("\n")}"
}
