package com.myothuonion.languagetalk.model

/**
 * Keeps identity-card artwork deterministic, instant, and independent from image-generation quota.
 * Gemini only supplies language data; the app owns every visual decision.
 */
internal fun decorateKoreanNameCandidates(
    originalName: String,
    candidates: List<KoreanNameCandidate>
): List<KoreanNameCandidate> {
    val animals = listOf("tiger", "fox", "crane", "dragon", "wolf", "deer", "falcon", "lion", "rabbit", "turtle")
    val palettes = listOf(
        "#5CE1E6" to "#5B4BFF",
        "#FF7A8A" to "#FFB84D",
        "#6EA8FF" to "#C36BFF",
        "#5FE0A5" to "#146B8C",
        "#F0C85A" to "#8A4FFF"
    )
    val patterns = listOf("soft aurora halo", "thin prism rays", "floating starlight", "quiet moon arcs", "silk light trails")
    val layouts = listOf("orbit", "crest", "diagonal")
    val seed = originalName.hashCode() and Int.MAX_VALUE

    return candidates.take(3).mapIndexed { index, item ->
        val animal = animals[(seed + index) % animals.size]
        val palette = palettes[(seed + index) % palettes.size]
        val pattern = patterns[(seed + index) % patterns.size]
        val layout = layouts[index]
        val identity = animalIdentity(animal)
        val vibe = identity.first
        val symbolism = identity.second
        val motto = identity.third
        item.copy(
            animal = animal,
            animalSymbolism = symbolism,
            primaryColor = palette.first,
            secondaryColor = palette.second,
            lightPattern = pattern,
            layoutStyle = layout,
            vibe = vibe,
            motto = motto,
            externalImagePrompt = buildString {
                append("Premium minimalist vertical 4:5 Korean identity card for the exact Hangul name '${item.hangul}'. ")
                append("Use a refined geometric $animal emblem, $pattern, a $layout composition, and only ${palette.first} plus ${palette.second} on a deep charcoal background. ")
                append("Mood: $vibe. Keep '${item.hangul}' large, exact, centered and perfectly legible. ")
                append("No person, no photorealistic animal, no mockup, no watermark, no extra Hangul, generous negative space, luxury editorial typography, 4K.")
            }
        )
    }
}

internal fun knownKoreanNameResult(originalName: String): KoreanNameResult? {
    val normalized = originalName.lowercase().replace(Regex("[\\s._-]"), "")
    if (normalized != "မျိုးမင်းသူ" && normalized != "myominthu") return null

    val candidates = listOf(
        KoreanNameCandidate(
            hangul = "묘민뚜",
            romanization = "Myo Min-ttu",
            myanmarPronunciation = "မျိုးမင်းတူ — တင်းသံ",
            soundNotes = "뚜 ရဲ့ ㄸ က လေမထွက်ဘဲ တင်းတင်းထွက်တဲ့ ‘တူ’ သံပါ။ မင်းကြိုက်တဲ့ အသံနဲ့ အနီးဆုံးရွေးချယ်မှု။",
            naturalnessScore = 94,
            vibe = "",
            hanjaInspiredMeaning = "ဖန်တီးထားသော identity interpretation — ထူးခြားမှု၊ ဉာဏ်ရည်နဲ့ မလျော့သောစိတ်အား။ Hangul အသံရေးပုံရဲ့ တိုက်ရိုက်အဓိပ္ပာယ်မဟုတ်ပါ။",
            animal = "",
            animalSymbolism = "",
            primaryColor = "",
            secondaryColor = "",
            lightPattern = "",
            layoutStyle = "",
            motto = "",
            externalImagePrompt = ""
        ),
        KoreanNameCandidate(
            hangul = "묘민투",
            romanization = "Myo Min-tu",
            myanmarPronunciation = "မျိုးမင်းထူ — လေထွက်သံ",
            soundNotes = "투 ရဲ့ ㅌ က လေထွက်များတဲ့ ‘ထူ’ သံဖြစ်လို့ romanized THU ကို Korean မှာ ရေးရာတွင် အသုံးဝင်တယ်။",
            naturalnessScore = 88,
            vibe = "",
            hanjaInspiredMeaning = "ဖန်တီးထားသော identity interpretation — တက်ကြွမှု၊ ရှေ့သို့တိုးမှုနဲ့ ပြတ်သားသောစွမ်းအင်။ တရားဝင် Hanja အဓိပ္ပာယ်မဟုတ်ပါ။",
            animal = "",
            animalSymbolism = "",
            primaryColor = "",
            secondaryColor = "",
            lightPattern = "",
            layoutStyle = "",
            motto = "",
            externalImagePrompt = ""
        ),
        KoreanNameCandidate(
            hangul = "묘민두",
            romanization = "Myo Min-du",
            myanmarPronunciation = "မျိုးမင်းဒူ — ပျော့သံ",
            soundNotes = "두 ရဲ့ ㄷ က စကားလုံးအစမှာ ‘တ/ဒ’ ကြားပျော့သံဖြစ်ပြီး 뚜 ထက်တင်းအားနည်း၊ 투 ထက်လေထွက်နည်းတယ်။",
            naturalnessScore = 80,
            vibe = "",
            hanjaInspiredMeaning = "ဖန်တီးထားသော identity interpretation — တည်ငြိမ်မှု၊ လူမှုနားလည်မှုနဲ့ နူးညံ့သောခိုင်မာမှု။ တိုက်ရိုက်ဘာသာပြန်ချက်မဟုတ်ပါ။",
            animal = "",
            animalSymbolism = "",
            primaryColor = "",
            secondaryColor = "",
            lightPattern = "",
            layoutStyle = "",
            motto = "",
            externalImagePrompt = ""
        )
    )
    return KoreanNameResult(originalName, decorateKoreanNameCandidates(originalName, candidates), activeModel = "Verified local pronunciation")
}

private fun animalIdentity(animal: String): Triple<String, String, String> = when (animal) {
    "tiger" -> Triple("quiet courage", "ကျား — သတ္တိနဲ့ ကာကွယ်ပေးနိုင်မှု", "Calm strength. Clear purpose.")
    "fox" -> Triple("clever elegance", "မြေခွေး — လျင်မြန်သောအတွေးနဲ့ လိုက်လျောညီထွေမှု", "Move with intelligence.")
    "crane" -> Triple("graceful clarity", "ကြိုးကြာ — တည်ငြိမ်မှုနဲ့ ရေရှည်ကောင်းမွန်ခြင်း", "Grace above noise.")
    "dragon" -> Triple("modern power", "နဂါး — တိုးတက်မှုနဲ့ အားကောင်းသောရည်မှန်းချက်", "Shape your own horizon.")
    "wolf" -> Triple("loyal focus", "ဝံပုလွေ — သစ္စာရှိမှုနဲ့ အာရုံစူးစိုက်မှု", "Stay true. Move forward.")
    "deer" -> Triple("gentle confidence", "သမင် — နူးညံ့မှုနဲ့ သတိရှိသောယုံကြည်ချက်", "Soft presence. Strong direction.")
    "falcon" -> Triple("sharp ambition", "သိမ်းငှက် — မြင်ကွင်းကျယ်နဲ့ တိကျသောဆုံးဖြတ်ချက်", "See farther. Rise cleanly.")
    "lion" -> Triple("warm authority", "ခြင်္သေ့ — ဦးဆောင်မှုနဲ့ နွေးထွေးသောခိုင်မာမှု", "Lead without noise.")
    "rabbit" -> Triple("bright agility", "ယုန် — လျင်မြန်မှုနဲ့ အသစ်စတင်နိုင်ခြင်း", "Light steps. New paths.")
    else -> Triple("steady longevity", "လိပ် — စိတ်ရှည်မှုနဲ့ တည်တံ့သောတိုးတက်မှု", "Slow is smooth. Smooth is strong.")
}
