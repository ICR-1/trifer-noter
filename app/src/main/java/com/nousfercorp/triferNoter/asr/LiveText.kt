package com.nousfercorp.triferNoter.asr

import java.util.Locale

/**
 * Merapikan keluaran mentah model streaming: HURUF BESAR, tanpa tanda baca, dan kadang memuntahkan
 * aksara asing di awal segmen (temuan benchmark M2a). Bahasa dikunci Indonesia, jadi token non-Latin dibuang.
 */
object LiveText {
    private val LATIN = Regex("[A-Za-z0-9'\\-]+")
    private val SPACES = Regex("\\s+")

    fun clean(raw: String): String {
        val words = raw.trim().split(SPACES).filter { it.isNotEmpty() && LATIN.matches(it) }
        if (words.isEmpty()) return ""
        return words.joinToString(" ").lowercase(Locale.ROOT).replaceFirstChar { it.uppercaseChar() }
    }
}
