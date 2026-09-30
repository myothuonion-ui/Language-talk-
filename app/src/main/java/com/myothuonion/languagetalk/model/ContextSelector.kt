package com.myothuonion.languagetalk.model

import com.myothuonion.languagetalk.data.MemoryEntity
import com.myothuonion.languagetalk.data.KnowledgeSourceEntity

object ContextSelector {
    private fun tokens(text: String) = text.lowercase().split(Regex("[\\s,.;:!?/]+"))
        .filter { it.length >= 2 }.toSet()

    fun memories(items: List<MemoryEntity>, chatId: Long, query: String): List<MemoryEntity> {
        val words = tokens(query)
        return items.filter { it.enabled && (it.scopeChatId == null || it.scopeChatId == chatId) }
            .sortedWith(compareByDescending<MemoryEntity> {
                (if (it.scopeChatId == chatId) 100 else 0) +
                    (if (it.category == "Personal" || it.category == "Profile") 20 else 0) +
                    words.count { word -> (it.title + " " + it.content).lowercase().contains(word) }
            }.thenByDescending { it.updatedAt }).take(12)
    }

    fun sources(items: List<KnowledgeSourceEntity>, query: String): List<KnowledgeSourceEntity> {
        val words = tokens(query)
        return items.filter { it.enabled && words.any { word -> (it.name + " " + it.summary).lowercase().contains(word) } }
            .sortedByDescending { source -> words.count { (source.name + " " + source.summary).lowercase().contains(it) } }
            .take(3)
    }

    fun explicitFact(heard: String, fact: String, evidence: String, mode: PracticeMode): String? {
        if (mode == PracticeMode.ROLEPLAY || fact.isBlank() || evidence.isBlank()) return null
        val request = listOf("remember", "မှတ်ထား", "기억해", "기억해 주세요").any { heard.lowercase().contains(it) }
        return evidence.trim().take(500).takeIf { request && heard.contains(evidence.trim()) }
    }
}
