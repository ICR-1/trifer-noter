package com.example.notetaker.refine

/** Segmen transkrip live sebagai bahan petunjuk untuk Whisper. */
data class LiveSeg(val startMs: Long, val endMs: Long, val text: String)

/** Sumber teks yang diberikan ke Whisper sebagai `initial_prompt`. */
enum class PromptMode {
    /** Tanpa petunjuk. */
    NONE,

    /** Teks live sebelum potongan (konteks). */
    PREVIOUS,

    /** Teks live pada rentang potongan itu sendiri (membawa ejaan nama dan istilah). */
    OWN,

    /** Konteks sebelumnya + teks potongan itu sendiri. */
    BOTH,
}

/**
 * Menyusun `initial_prompt` Whisper dari transkrip live. Whisper meniru gaya petunjuknya (huruf kecil tanpa tanda
 * baca akan menghasilkan keluaran serupa), jadi tiap segmen live diberi huruf kapital di awal dan titik di akhir.
 */
object RefinePrompt {
    /** Batas aman: petunjuk Whisper dipotong ke ±224 token; ±1 token per 3-4 huruf Indonesia. */
    const val MAX_CHARS = 420

    fun build(live: List<LiveSeg>, chunkStartMs: Long, chunkEndMs: Long, mode: PromptMode): String {
        if (mode == PromptMode.NONE || live.isEmpty()) return ""
        val before = if (mode == PromptMode.PREVIOUS || mode == PromptMode.BOTH) {
            live.filter { it.endMs <= chunkStartMs }.takeLast(2)
        } else {
            emptyList()
        }
        val own = if (mode == PromptMode.OWN || mode == PromptMode.BOTH) {
            live.filter { it.endMs > chunkStartMs && it.startMs < chunkEndMs }
        } else {
            emptyList()
        }
        val text = (before + own).joinToString(" ") { sentence(it.text) }.trim()
        // Ambil bagian terakhir (paling dekat dengan audio yang akan didekode).
        return if (text.length <= MAX_CHARS) text else text.takeLast(MAX_CHARS).substringAfter(' ')
    }

    private fun sentence(raw: String): String {
        val t = raw.trim()
        if (t.isEmpty()) return ""
        val cap = t.replaceFirstChar { it.uppercaseChar() }
        return if (cap.last() in ".?!") cap else "$cap."
    }
}
