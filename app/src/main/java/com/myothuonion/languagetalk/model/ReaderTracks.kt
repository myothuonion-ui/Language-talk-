package com.myothuonion.languagetalk.model

import com.myothuonion.languagetalk.util.PdfTextLayer

data class TrackMarker(val number: Int, val region: PageRect? = null)

object ReaderTracks {
    private val number = Regex("(?i)(?:track|트랙)\\s*[:#.-]?\\s*([0-9]{1,4})(?![0-9])")
    fun numberFromName(name: String): Int? = number.find(name)?.groupValues?.get(1)?.toIntOrNull()
        ?: Regex("(?<![0-9])([0-9]{1,3})(?![0-9])").findAll(name.substringBeforeLast('.')).toList().singleOrNull()?.groupValues?.get(1)?.toIntOrNull()
    fun detect(layer: PdfTextLayer): List<TrackMarker> {
        val found = mutableListOf<TrackMarker>()
        layer.words.groupBy { it.line }.values.forEach { words ->
            val text = words.joinToString(" ") { it.text }
            number.findAll(text).forEach { match ->
                var offset = 0
                val selected = words.filter { word ->
                    val start = offset; offset += word.text.length + 1
                    start <= match.range.last && offset - 2 >= match.range.first
                }
                val n = match.groupValues[1].toIntOrNull() ?: return@forEach
                if (n in 1..9999) found += TrackMarker(n, selected.takeIf { it.isNotEmpty() }?.let {
                    PageRect(it.minOf { it.left }, it.minOf { it.top }, it.maxOf { it.right }, it.maxOf { it.bottom })
                })
            }
        }
        number.findAll(layer.text).forEach { match ->
            val n = match.groupValues[1].toIntOrNull()
            if (n != null && n in 1..9999 && found.none { it.number == n }) found += TrackMarker(n)
        }
        return found.distinctBy { it.number }
    }
}
