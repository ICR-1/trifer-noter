package com.nousfercorp.triferNoter.refine

/**
 * Gerbang terakhir untuk segmen keluaran Whisper pada fitur Rapikan.
 *
 * Whisper dilatih dengan banyak video, sehingga ia sering "mendengar" frasa penutup yang tidak pernah diucapkan,
 * paling sering "Terima kasih." Terukur pada rekaman rapat nyata 19 menit: Whisper mengeluarkan 8 segmen
 * "Terima kasih." (beberapa berurutan, satu di detik terakhir), padahal mesin live tidak mendengar kata itu
 * sekali pun. Sinyal lain terbukti tidak bisa diandalkan: `noSpeechProb` Whisper dihitung per jendela 30 detik
 * (selalu 0 karena jendelanya berisi ucapan), dan topeng energi tidak memisahkan noise ruangan nyata dari ucapan
 * (obrolan latar juga naik-turun seperti ucapan).
 *
 * Frasa itu juga kalimat sah di akhir rapat, jadi tidak bisa dibuang hanya dari teksnya. Aturannya: bila hanya
 * itu isi segmen, ia diterima hanya jika transkrip live (mesin lain yang jarang berhalusinasi) juga mendengar
 * frasa yang sama di sekitar waktunya. Bila tidak ada transkrip live sama sekali (mis. model live belum
 * terpasang saat merekam), tidak ada pembanding, sehingga segmen tidak ditolak.
 */
object SegmentGate {
    /** Minimal frame bersuara (20 ms per frame) di dalam rentang segmen. */
    const val MIN_VOICED_FRAMES = 10

    /** Toleransi waktu saat mencocokkan dengan transkrip live. */
    const val LIVE_TOLERANCE_MS = 2_500L

    private val STOCK = Regex(
        """^(?:(?:ya|oke|baik|ok)\s+)?(?:terima\s*kasih|makasih|thank\s+you|thanks|sampai\s+jumpa(?:\s+lagi)?|selamat\s+tinggal|bye)(?:\s+(?:banyak|ya|semuanya|atas\s+perhatiannya))?$""",
        RegexOption.IGNORE_CASE,
    )
    private val NON_WORD = Regex("""[^\p{L}\p{N}\s]""")
    private val SPACES = Regex("""\s+""")

    /** Kata kunci tiap keluarga frasa penutup, untuk mencocokkan dengan transkrip live. */
    private val KEYS = listOf(
        Regex("""\b(terima\s*kasih|makasih)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(thank|thanks)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bjumpa\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(selamat\s+tinggal|bye)\b""", RegexOption.IGNORE_CASE),
    )

    private fun norm(text: String): String = SPACES.replace(NON_WORD.replace(text, " "), " ").trim()

    /** Segmen yang isinya hanya frasa penutup baku (mis. "Terima kasih."). */
    fun isStockPhrase(text: String): Boolean = STOCK.matches(norm(text))

    /** Apakah ada segmen live di sekitar [startMs, endMs] yang memuat kata kunci frasa penutup dari [text]. */
    fun liveSupports(text: String, startMs: Long, endMs: Long, live: List<LiveSpan>): Boolean {
        val t = norm(text)
        val key = KEYS.firstOrNull { it.containsMatchIn(t) } ?: return false
        return live.any {
            it.endMs >= startMs - LIVE_TOLERANCE_MS && it.startMs <= endMs + LIVE_TOLERANCE_MS &&
                key.containsMatchIn(norm(it.text))
        }
    }

    private val SENTENCE_SPLIT = Regex("""(?<=[.!?])\s+""")

    /**
     * Buang kalimat penutup baku yang menempel di depan/dalam ucapan nyata ("Terima kasih. Bukankah untuk Harman?")
     * bila transkrip live tidak mendengarnya. Kalimat lain dibiarkan utuh. Tanpa pembanding live, teks tidak diubah.
     */
    fun stripUnsupportedStock(text: String, liveAvailable: Boolean, liveSupport: Boolean): String {
        if (!liveAvailable || liveSupport) return text
        val sentences = SENTENCE_SPLIT.split(text.trim())
        if (sentences.size < 2) return text
        val kept = sentences.filterNot { isStockPhrase(it) }
        return if (kept.size == sentences.size) text else kept.joinToString(" ")
    }

    /**
     * @param liveAvailable ada transkrip live untuk catatan ini (tanpa itu frasa penutup tidak bisa diverifikasi)
     * @param liveSupport transkrip live mendengar frasa yang sama di sekitar waktu segmen
     */
    fun accept(text: String, voicedFrames: Int, liveAvailable: Boolean, liveSupport: Boolean): Boolean {
        if (text.isBlank()) return false
        if (voicedFrames < MIN_VOICED_FRAMES) return false
        if (liveAvailable && !liveSupport && isStockPhrase(text)) return false
        return true
    }
}

/** Rentang waktu dan teks sebuah segmen transkrip live. */
data class LiveSpan(val startMs: Long, val endMs: Long, val text: String)
