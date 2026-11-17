package com.example.notetaker.asr

/**
 * Whisper dilatih dengan banyak video berbahasa Indonesia, sehingga pada derau/awal potongan sering
 * "menghalusinasi" frasa penutup video yang tidak pernah diucapkan. Hanya frasa yang sudah dikenal yang
 * dibuang, supaya kalimat sah seperti "terima kasih atas bantuannya" tidak ikut hilang.
 */
object HallucinationFilter {
    private val PATTERNS = listOf(
        Regex("""\bterima\s+kasih\s+(telah|sudah)\s+(menonton|mendengarkan|melihat)(\s+video(\s+ini)?)?\b[.!,]?""", RegexOption.IGNORE_CASE),
        Regex("""\bsampai\s+jumpa\s+(di|pada|lagi\s+di)\s+(video|episode|channel|vidio)[^.!?]*[.!?]?""", RegexOption.IGNORE_CASE),
        Regex("""\b(jangan\s+lupa|silakan|silahkan)\s+(untuk\s+)?(like|subscribe|berlangganan|subscribe)[^.!?]*[.!?]?""", RegexOption.IGNORE_CASE),
        // Kredit subtitle selalu di akhir; buang sampai akhir teks (nama situs memuat titik).
        Regex("""\b(subtitle|subtitles)\s+(by|oleh)\b.*$""", RegexOption.IGNORE_CASE),
        Regex("""\bamara\.org\b""", RegexOption.IGNORE_CASE),
    )
    private val SPACES = Regex("""\s+""")

    /** Mengembalikan teks tanpa frasa halusinasi yang dikenal; kosong bila tidak ada isi tersisa. */
    fun clean(text: String): String {
        var t = text
        for (p in PATTERNS) t = p.replace(t, " ")
        t = SPACES.replace(t, " ").trim()
        // Sisa hanya tanda baca = tidak ada isi.
        return if (t.any { it.isLetterOrDigit() }) t else ""
    }
}
