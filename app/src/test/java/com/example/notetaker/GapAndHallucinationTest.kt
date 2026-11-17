package com.example.notetaker

import com.example.notetaker.asr.ChunkPlanner
import com.example.notetaker.asr.HallucinationFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HallucinationFilterTest {
    @Test fun `frasa penutup video dibuang`() {
        assertEquals("harus selalu", HallucinationFilter.clean("Terima kasih telah menonton harus selalu"))
        assertEquals("", HallucinationFilter.clean("Terima kasih telah menonton."))
        assertEquals("", HallucinationFilter.clean("Sampai jumpa di video selanjutnya."))
        assertEquals("", HallucinationFilter.clean("Jangan lupa subscribe dan like video ini."))
        assertEquals("", HallucinationFilter.clean("Subtitle by Amara.org"))
    }

    @Test fun `kalimat sah yang memuat terima kasih tidak dibuang`() {
        assertEquals("Terima kasih atas bantuannya.", HallucinationFilter.clean("Terima kasih atas bantuannya."))
        assertEquals("Terima kasih.", HallucinationFilter.clean("Terima kasih."))
        assertEquals("Kami ucapkan terima kasih kepada semua peserta rapat.", HallucinationFilter.clean("Kami ucapkan terima kasih kepada semua peserta rapat."))
    }

    @Test fun `teks biasa tidak berubah`() {
        val t = "Di sisi lain, kondisi yang dingin dan bersalju tergolong normal."
        assertEquals(t, HallucinationFilter.clean(t))
    }
}

class GapFinderTest {
    private val fps = 50

    /** Topeng: true = ada suara. Susun dari pasangan (detik, bersuara?). */
    private fun mask(vararg parts: Pair<Float, Boolean>): BooleanArray {
        val out = ArrayList<Boolean>()
        for ((sec, v) in parts) repeat((sec * fps).toInt()) { out += v }
        return out.toBooleanArray()
    }

    private fun sec(f: Int) = f.toFloat() / fps

    @Test fun `tidak ada celah bila semua suara tercakup`() {
        val m = mask(10f to true)
        val gaps = ChunkPlanner.findGaps(m, 0, m.size, listOf(0L..10_000L))
        assertTrue(gaps.isEmpty())
    }

    @Test fun `celah di awal ditemukan`() {
        // suara 0-10 dtk, segmen hanya menutup 3-10 dtk: 0-3 dtk terlewat
        val m = mask(10f to true)
        val gaps = ChunkPlanner.findGaps(m, 0, m.size, listOf(3_000L..10_000L))
        assertEquals(1, gaps.size)
        assertTrue("mulai ${sec(gaps[0].startFrame)}", sec(gaps[0].startFrame) <= 0.01f)
        assertTrue("akhir ${sec(gaps[0].endFrame)}", sec(gaps[0].endFrame) in 2.7f..3.4f)
    }

    @Test fun `celah di tengah dan di akhir ditemukan terpisah`() {
        val m = mask(30f to true)
        val covered = listOf(0L..5_000L, 12_000L..20_000L)
        val gaps = ChunkPlanner.findGaps(m, 0, m.size, covered)
        assertEquals(2, gaps.size)
        assertTrue(sec(gaps[0].startFrame) in 4.4f..5.4f && sec(gaps[0].endFrame) in 11.6f..12.4f)
        assertTrue(sec(gaps[1].startFrame) in 19.4f..20.4f && sec(gaps[1].endFrame) >= 29.9f)
    }

    @Test fun `celah pendek di bawah ambang diabaikan`() {
        val m = mask(10f to true)
        // hanya 1,0 dtk tak tercakup (<1,5 dtk)
        val gaps = ChunkPlanner.findGaps(m, 0, m.size, listOf(0L..4_300L, 5_700L..10_000L), toleranceSec = 0.0f)
        assertTrue(gaps.isEmpty())
    }

    @Test fun `senyap tidak dianggap celah`() {
        val m = mask(2f to true, 20f to false, 2f to true)
        val gaps = ChunkPlanner.findGaps(m, 0, m.size, listOf(0L..2_000L, 22_000L..24_000L))
        assertTrue(gaps.isEmpty())
    }

    @Test fun `toleransi menjaga ujung segmen yang sedikit meleset`() {
        val m = mask(10f to true)
        // segmen selesai 0,2 dtk lebih awal dari akhir suara: tidak dianggap celah
        val gaps = ChunkPlanner.findGaps(m, 0, m.size, listOf(0L..9_800L))
        assertTrue(gaps.isEmpty())
    }
}
