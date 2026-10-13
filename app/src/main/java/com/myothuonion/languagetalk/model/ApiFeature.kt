package com.myothuonion.languagetalk.model

enum class ApiFeature(val label: String, val task: AiTask) {
    TEXT("စာပြန်မှု", AiTask.CONVERSATION), PDF("PDF ရှင်းပြမှု", AiTask.READER),
    SPEECH("အသံဖတ်ပြမှု", AiTask.SPEECH), TRANSCRIBE("အသံမှ စာဖမ်းမှု", AiTask.TRANSCRIBE),
    LIVE("Live စကားပြော", AiTask.LIVE)
}
