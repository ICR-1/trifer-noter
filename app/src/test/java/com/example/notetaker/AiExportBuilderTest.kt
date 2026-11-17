package com.example.notetaker

import com.example.notetaker.ai.AiExportBuilder
import com.example.notetaker.ai.AiExportOptions
import com.example.notetaker.ai.AiTemplate
import com.example.notetaker.data.db.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class AiExportBuilderTest {
    private val created = 1_794_935_932_585L

    private fun seg(startMs: Long, text: String) =
        Segment(id = startMs, recordingId = 1, startMs = startMs, endMs = startMs + 3000, text = text)

    /** Segmen dengan tepat [words] kata. */
    private fun segWords(i: Int, words: Int) = seg(i * 4000L, List(words) { "kata$i" }.joinToString(" "))

    private fun build(segs: List<Segment>, o: AiExportOptions, title: String = "Rapat") =
        AiExportBuilder.build(title, created, 90_000, segs, o, ZoneOffset.UTC)

    @Test fun `satu bagian memuat prompt metadata dan transkrip`() {
        val parts = build(listOf(seg(0, "Halo semua."), seg(4000, "Kita mulai.")), AiExportOptions(AiTemplate.MEETING))
        assertEquals(1, parts.size)
        val t = parts[0].text
        assertTrue(t.contains("notulis"))
        assertTrue(t.contains("Judul: Rapat"))
        assertTrue(t.contains("--- TRANSKRIP ---"))
        assertTrue(t.contains("Halo semua.\nKita mulai."))
        assertTrue(t.indexOf("notulis") < t.indexOf("Judul:"))
    }

    @Test fun `tanpa prompt hanya metadata dan transkrip`() {
        val t = build(listOf(seg(0, "Halo.")), AiExportOptions(AiTemplate.NONE))[0].text
        assertFalse(t.contains("--- TRANSKRIP ---"))
        assertTrue(t.startsWith("Judul: Rapat"))
        assertTrue(t.endsWith("Halo."))
    }

    @Test fun `timestamp bisa dinyalakan`() {
        val on = build(listOf(seg(65_000, "Halo.")), AiExportOptions(AiTemplate.NONE, withTimestamps = true))[0].text
        assertTrue(on.contains("[00:01:05] Halo."))
        val off = build(listOf(seg(65_000, "Halo.")), AiExportOptions(AiTemplate.NONE, withTimestamps = false))[0].text
        assertFalse(off.contains("[00:"))
    }

    @Test fun `prompt kustom dengan placeholder diganti`() {
        val o = AiExportOptions(AiTemplate.CUSTOM, customPrompt = "Buat ringkasan untuk {judul} ({durasi}).")
        val t = build(listOf(seg(0, "Halo.")), o, title = "Kuliah Fisika")[0].text
        assertTrue(t.contains("Buat ringkasan untuk Kuliah Fisika (1 mnt 30 dtk)."))
        assertFalse(t.contains("{judul}"))
    }

    @Test fun `prompt kustom kosong memakai bawaan`() {
        val t = build(listOf(seg(0, "Halo.")), AiExportOptions(AiTemplate.CUSTOM, customPrompt = "  "))[0].text
        assertTrue(t.contains("Rangkum transkrip berikut"))
    }

    @Test fun `transkrip panjang dipecah di batas segmen dan tiap bagian tidak melebihi batas`() {
        val segs = List(30) { segWords(it, 100) } // 3000 kata
        val parts = build(segs, AiExportOptions(AiTemplate.LECTURE, maxWordsPerPart = 1000))
        assertEquals(3, parts.size)
        for (p in parts) assertTrue("kata ${p.words}", p.words <= 1000)
        assertEquals(3000, parts.sumOf { it.words })
        assertEquals(listOf(1, 2, 3), parts.map { it.index })
        assertTrue(parts.all { it.total == 3 })
        // tidak ada segmen yang terbelah/hilang: semua penanda kata muncul tepat 100 kali
        val all = parts.joinToString("\n") { it.text }
        for (i in 0 until 30) assertEquals(100, Regex("kata$i\\b").findAll(all).count())
    }

    @Test fun `instruksi bagian awal tengah dan akhir`() {
        val segs = List(30) { segWords(it, 100) }
        val parts = build(segs, AiExportOptions(AiTemplate.MEETING, maxWordsPerPart = 1000))
        val first = parts.first().text
        val mid = parts[1].text
        val last = parts.last().text
        assertTrue(first.contains("dalam 3 bagian"))
        assertTrue(first.contains("Jangan merangkum"))
        assertFalse(first.contains("notulis")) // tugas belum diberikan
        assertTrue(mid.contains("Bagian 2 dari 3. Balas hanya \"Bagian 2 diterima.\""))
        assertFalse(mid.contains("notulis"))
        assertTrue(last.contains("(terakhir)"))
        assertTrue(last.contains("--- SELESAI ---"))
        assertTrue(last.contains("notulis"))
        assertTrue(last.contains("SELURUH transkrip"))
    }

    @Test fun `satu bagian saja bila batas null`() {
        val segs = List(30) { segWords(it, 400) } // 12.000 kata
        val parts = build(segs, AiExportOptions(AiTemplate.LECTURE, maxWordsPerPart = null))
        assertEquals(1, parts.size)
        assertEquals(12_000, parts[0].words)
    }

    @Test fun `segmen yang lebih panjang dari batas tetap utuh dalam satu bagian`() {
        val parts = build(listOf(segWords(0, 50), segWords(1, 5000), segWords(2, 50)), AiExportOptions(AiTemplate.NONE, maxWordsPerPart = 100))
        assertEquals(3, parts.size)
        assertEquals(5000, parts[1].words)
    }

    @Test fun `transkrip kosong menghasilkan keterangan`() {
        val parts = build(emptyList(), AiExportOptions(AiTemplate.LECTURE))
        assertEquals(1, parts.size)
        assertTrue(parts[0].text.contains("(Tidak ada transkrip.)"))
    }

    @Test fun `estimasi jumlah bagian sama dengan hasil build`() {
        val segs = List(45) { segWords(it, 100) }
        val o = AiExportOptions(AiTemplate.LECTURE, maxWordsPerPart = 1000)
        assertEquals(build(segs, o).size, AiExportBuilder.estimateParts(segs, o))
    }

    @Test fun `urutan segmen mengikuti waktu`() {
        val t = build(listOf(seg(8000, "Kedua."), seg(0, "Pertama.")), AiExportOptions(AiTemplate.NONE))[0].text
        assertTrue(t.indexOf("Pertama.") < t.indexOf("Kedua."))
    }

    @Test fun `id template dapat dipulihkan dari preferensi`() {
        for (t in AiTemplate.entries) assertEquals(t, AiTemplate.fromId(t.id))
        assertEquals(AiTemplate.LECTURE, AiTemplate.fromId("tidak-dikenal"))
        assertEquals(AiTemplate.LECTURE, AiTemplate.fromId(null))
    }

    @Test fun `semua prompt bawaan memperingatkan kemungkinan salah dengar`() {
        for (t in listOf(AiTemplate.LECTURE, AiTemplate.MEETING, AiTemplate.ACTIONS)) {
            val p = AiExportBuilder.promptFor(t, "", "J", "T", "D")
            assertTrue("$t", p.contains("salah dengar"))
            assertTrue("$t", p.contains("Bahasa Indonesia"))
        }
    }
}
