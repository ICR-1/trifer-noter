package com.example.notetaker.ai

import com.example.notetaker.data.db.Segment
import com.example.notetaker.util.formatClock
import com.example.notetaker.util.formatDurationShort
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Template prompt untuk chat AI. [id] disimpan di preferensi, jangan diubah. */
enum class AiTemplate(val id: String) {
    LECTURE("kuliah"),
    MEETING("rapat"),
    ACTIONS("aksi"),
    CUSTOM("kustom"),
    NONE("polos"),
    ;

    companion object {
        fun fromId(id: String?): AiTemplate = entries.firstOrNull { it.id == id } ?: LECTURE
    }
}

data class AiExportOptions(
    val template: AiTemplate,
    val customPrompt: String = "",
    val withTimestamps: Boolean = false,
    /** Maksimum kata transkrip per bagian; null berarti semua dalam satu bagian. */
    val maxWordsPerPart: Int? = DEFAULT_MAX_WORDS,
) {
    companion object {
        /** ±6.000 kata ≈ 9–12 ribu token Bahasa Indonesia, aman untuk hampir semua chat AI. */
        const val DEFAULT_MAX_WORDS = 6_000
    }
}

data class AiPart(val index: Int, val total: Int, val text: String, val words: Int)

/**
 * Menyusun teks siap-tempel ke chat AI dari transkrip. Transkrip panjang dipecah di batas segmen (tidak pernah di
 * tengah kalimat). Untuk beberapa bagian, bagian awal dan tengah meminta AI menunggu, dan bagian terakhir memuat
 * tugasnya.
 */
object AiExportBuilder {
    private val DATE_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM yyyy, HH.mm", Locale.forLanguageTag("id-ID"))

    private const val DEFAULT_CUSTOM = "Rangkum transkrip berikut dalam Bahasa Indonesia."

    private const val CAVEAT =
        "Catatan: transkrip dibuat otomatis dari rekaman suara sehingga mungkin ada salah dengar, terutama pada nama " +
            "orang dan istilah. Jangan mengarang informasi yang tidak ada di transkrip; bila tidak jelas, tulis " +
            "\"tidak jelas\"."

    private val LECTURE_PROMPT = """
        Kamu adalah asisten belajar. Dari transkrip kuliah/presentasi berikut, buat dalam Bahasa Indonesia:
        1. Ringkasan singkat (5-8 kalimat).
        2. Poin-poin utama dalam bentuk daftar.
        3. Istilah penting beserta penjelasan singkatnya.
        4. Kesimpulan.
        5. Lima pertanyaan latihan beserta jawabannya.
        $CAVEAT
    """.trimIndent()

    private val MEETING_PROMPT = """
        Kamu adalah notulis profesional. Dari transkrip rapat berikut, buat notulen dalam Bahasa Indonesia:
        1. Topik/agenda yang dibahas.
        2. Ringkasan pembahasan per topik.
        3. Keputusan yang diambil.
        4. Action items: tugas, penanggung jawab (bila disebut), dan tenggat (bila disebut), dalam bentuk tabel.
        5. Pertanyaan atau isu yang masih terbuka.
        $CAVEAT
    """.trimIndent()

    private val ACTIONS_PROMPT = """
        Dari transkrip berikut, ambil HANYA action items (tugas yang harus dikerjakan) dalam Bahasa Indonesia, dalam
        bentuk daftar: tugas, penanggung jawab (bila disebut), tenggat (bila disebut). Jika tidak ada, tulis
        "Tidak ada action item".
        $CAVEAT
    """.trimIndent()

    /** Isi prompt untuk [template]. Kosong bila [AiTemplate.NONE]. Placeholder {judul} {tanggal} {durasi} diganti. */
    fun promptFor(template: AiTemplate, custom: String, title: String, date: String, duration: String): String {
        val raw = when (template) {
            AiTemplate.LECTURE -> LECTURE_PROMPT
            AiTemplate.MEETING -> MEETING_PROMPT
            AiTemplate.ACTIONS -> ACTIONS_PROMPT
            AiTemplate.CUSTOM -> custom.trim().ifBlank { DEFAULT_CUSTOM }
            AiTemplate.NONE -> ""
        }
        return raw.replace("{judul}", title).replace("{tanggal}", date).replace("{durasi}", duration)
    }

    fun wordCount(segments: List<Segment>): Int = segments.sumOf { countWords(it.text) }

    /** Perkiraan jumlah bagian untuk [options]; berguna untuk pratinjau sebelum menyusun teks. */
    fun estimateParts(segments: List<Segment>, options: AiExportOptions): Int =
        split(usable(segments), options.maxWordsPerPart).size.coerceAtLeast(1)

    fun build(
        title: String,
        createdAtMs: Long,
        durationMs: Long,
        segments: List<Segment>,
        options: AiExportOptions,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<AiPart> {
        val date = DATE_FORMAT.format(Instant.ofEpochMilli(createdAtMs).atZone(zone))
        val duration = formatDurationShort(durationMs)
        val meta = "Judul: ${title.trim()}\nTanggal: $date\nDurasi: $duration"
        val prompt = promptFor(options.template, options.customPrompt, title.trim(), date, duration)

        val groups = split(usable(segments), options.maxWordsPerPart)
        if (groups.isEmpty()) {
            return listOf(AiPart(1, 1, "$meta\n\n(Tidak ada transkrip.)", 0))
        }
        val n = groups.size

        return groups.mapIndexed { i, group ->
            val idx = i + 1
            val lines = group.joinToString("\n") { line(it, options.withTimestamps) }
            val words = group.sumOf { countWords(it.text) }
            val text = when {
                n == 1 -> buildString {
                    if (prompt.isNotEmpty()) append(prompt).append("\n\n")
                    append(meta).append("\n\n")
                    if (prompt.isNotEmpty()) append("--- TRANSKRIP ---\n")
                    append(lines)
                }
                idx == 1 -> buildString {
                    append("Saya akan mengirim satu transkrip panjang dalam $n bagian. ")
                    append("Jangan merangkum atau menjawab dulu. Setelah tiap bagian, balas singkat ")
                    append("\"Bagian X diterima.\" saja. Tugasnya saya berikan setelah bagian terakhir.\n\n")
                    append(meta).append("\n\n")
                    append("--- TRANSKRIP (Bagian $idx dari $n) ---\n").append(lines)
                }
                idx < n -> "Bagian $idx dari $n. Balas hanya \"Bagian $idx diterima.\"\n\n" +
                    "--- TRANSKRIP (Bagian $idx dari $n) ---\n$lines"
                else -> buildString {
                    append("Bagian $idx dari $n (terakhir).\n\n")
                    append("--- TRANSKRIP (Bagian $idx dari $n) ---\n").append(lines).append("\n\n")
                    append("--- SELESAI ---\n")
                    if (prompt.isNotEmpty()) {
                        append("Semua $n bagian transkrip sudah dikirim. Sekarang kerjakan tugas ini berdasarkan SELURUH ")
                        append("transkrip:\n").append(prompt)
                    } else {
                        append("Semua $n bagian transkrip sudah dikirim.")
                    }
                }
            }
            AiPart(idx, n, text, words)
        }
    }

    private fun usable(segments: List<Segment>) = segments.filter { it.text.isNotBlank() }.sortedBy { it.startMs }

    /** Pecah di batas segmen. Satu segmen tidak pernah dibelah. */
    private fun split(segments: List<Segment>, maxWords: Int?): List<List<Segment>> {
        if (segments.isEmpty()) return emptyList()
        if (maxWords == null || maxWords <= 0) return listOf(segments)
        val out = ArrayList<List<Segment>>()
        var cur = ArrayList<Segment>()
        var words = 0
        for (s in segments) {
            val w = countWords(s.text)
            if (cur.isNotEmpty() && words + w > maxWords) {
                out += cur
                cur = ArrayList()
                words = 0
            }
            cur += s
            words += w
        }
        if (cur.isNotEmpty()) out += cur
        return out
    }

    private fun line(s: Segment, withTimestamps: Boolean): String =
        if (withTimestamps) "[${formatClock(s.startMs)}] ${s.text.trim()}" else s.text.trim()

    private fun countWords(text: String): Int = text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
}
