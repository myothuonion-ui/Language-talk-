package com.myothuonion.languagetalk.network

import com.myothuonion.languagetalk.model.AiModelRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

data class AiRouted<T>(val value: T, val selected: AiModelRef, val usedFallback: Boolean)

/** Bounded attempts. A rejected credential is skipped for the rest of this request. */
class AiRouter(private val attemptMs: Long = 22_000, private val totalMs: Long = 75_000) {
    suspend fun <T> run(candidates: List<AiModelRef>, call: suspend (AiModelRef) -> T): AiRouted<T> {
        if (candidates.isEmpty()) throw AiApiException("AI & APIs မှာ ဒီလုပ်ဆောင်ချက်အတွက် API key ထည့်ပြီး model ရွေးပါ။")
        val skippedProfiles = mutableSetOf<String>()
        val tried = mutableListOf<String>()
        return withTimeoutOrNull(totalMs) {
                for (ref in candidates.take(12)) {
                    if (ref.profileId in skippedProfiles) continue
                    tried += "${ref.profileId}: ${ref.model}"
                    try {
                        val result = withTimeoutOrNull(attemptMs) { Value(call(ref)) }
                        if (result != null) return@withTimeoutOrNull AiRouted(result.value, ref, ref != candidates.first())
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        val api = failure as? AiApiException
                        if (api?.statusCode in setOf(401, 403) || api?.providerWide == true) skippedProfiles += ref.profileId
                        if (api == null && failure !is IOException) throw failure
                    }
                }
                throw AiApiException("အသုံးပြုနိုင်တဲ့ AI မရသေးပါ။ API access / quota နဲ့ Internet ကိုစစ်ပါ။\nTried: ${tried.joinToString(", ")}")
        } ?: throw AiApiException("AI အချိန်မီမဖြေပါ။ ထပ်စမ်းပါ။", 504)
    }
    private data class Value<T>(val value: T)
}
