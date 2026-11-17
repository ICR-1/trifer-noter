package com.example.notetaker

import com.example.notetaker.asr.ChunkPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChunkPlannerTest {
    private val fps = 50 // frame 20 ms

    /** Susun garis waktu: pasangan (detik, bersuara?). Bersuara = -30 dB, senyap = -70 dB. */
    private fun timeline(vararg parts: Pair<Float, Boolean>): FloatArray {
        val out = ArrayList<Float>()
        for ((sec, voiced) in parts) repeat((sec * fps).toInt()) { out += if (voiced) -30f else -70f }
        return out.toFloatArray()
    }

    private fun sec(frame: Int) = frame.toFloat() / fps

    @Test fun `audio pendek menjadi satu potongan tanpa senyap di tepi`() {
        val db = timeline(2f to false, 10f to true, 3f to false)
        val chunks = ChunkPlanner.plan(db)
        assertEquals(1, chunks.size)
        assertTrue("mulai ${sec(chunks[0].startFrame)}", sec(chunks[0].startFrame) in 1.7f..2.0f)
        assertTrue("akhir ${sec(chunks[0].endFrame)}", sec(chunks[0].endFrame) in 12.0f..12.3f)
    }

    @Test fun `audio panjang dipotong di jeda dan tiap potongan tidak melebihi batas`() {
        val db = timeline(
            12f to true, 0.5f to false, 12f to true, 0.5f to false, 12f to true, 0.5f to false, 12f to true,
        )
        val chunks = ChunkPlanner.plan(db, maxChunkSec = 27f)
        assertTrue("jumlah ${chunks.size}", chunks.size >= 2)
        for (c in chunks) assertTrue("panjang ${sec(c.endFrame - c.startFrame)}", sec(c.endFrame - c.startFrame) <= 27.01f)
        // Titik potong jatuh di tengah jeda (±24,5 dtk atau ±12,25 dtk), bukan di tengah kata.
        val firstEnd = sec(chunks[0].endFrame)
        val inPause = listOf(12.25f, 24.75f, 37.25f).any { kotlin.math.abs(it - firstEnd) < 0.3f }
        assertTrue("potong di $firstEnd", inPause)
    }

    @Test fun `memilih jeda terakhir dalam jendela agar potongan sepanjang mungkin`() {
        val db = timeline(10f to true, 0.5f to false, 10f to true, 0.5f to false, 10f to true)
        val chunks = ChunkPlanner.plan(db, maxChunkSec = 27f)
        // Jeda pertama di 10,25 dtk dan kedua di 20,75 dtk; keduanya di dalam 27 dtk, pilih yang kedua.
        assertTrue("akhir ${sec(chunks[0].endFrame)}", kotlin.math.abs(sec(chunks[0].endFrame) - 20.75f) < 0.3f)
    }

    @Test fun `bicara kontinu tanpa jeda tetap dipotong di bawah batas`() {
        val db = timeline(70f to true)
        val chunks = ChunkPlanner.plan(db, maxChunkSec = 27f)
        assertTrue("jumlah ${chunks.size}", chunks.size >= 3)
        for (c in chunks) assertTrue(sec(c.endFrame - c.startFrame) <= 27.01f)
        // Tidak ada audio yang hilang di antara potongan.
        for (i in 1 until chunks.size) assertEquals(chunks[i - 1].endFrame, chunks[i].startFrame)
    }

    @Test fun `ujaran pelan di sebelah ujaran keras tetap dianggap suara`() {
        // keras -25 dB, jeda 0,8 dtk, pelan -40 dB (selisih 15 dB seperti rekaman nyata)
        val db = timeline(8f to true, 0.8f to false, 8f to true).also {
            val start = ((8f + 0.8f) * fps).toInt()
            for (i in start until it.size) it[i] = -40f
            for (i in 0 until (8f * fps).toInt()) it[i] = -25f
        }
        val mask = ChunkPlanner.speechMask(db)
        val quietFrom = ((8f + 0.8f + 2.6f) * fps).toInt() // di luar jangkauan jendela ujaran keras
        val voiced = ChunkPlanner.countSpeechFrames(mask, quietFrom, db.size)
        val total = db.size - quietFrom
        assertTrue("terdeteksi $voiced dari $total", voiced >= total * 0.9)
        // dan perencana tidak melewati ujaran pelan itu
        val chunks = ChunkPlanner.plan(db)
        assertTrue("akhir ${sec(chunks.last().endFrame)}", sec(chunks.last().endFrame) >= 16.5f)
    }

    @Test fun `senyap total tidak menghasilkan potongan`() {
        assertTrue(ChunkPlanner.plan(timeline(60f to false)).isEmpty())
    }

    @Test fun `suara sangat singkat dilewati`() {
        val db = timeline(20f to false, 0.2f to true, 20f to false)
        assertTrue(ChunkPlanner.plan(db).isEmpty())
    }

    @Test fun `potongan tidak tumpang tindih`() {
        val db = timeline(15f to true, 0.4f to false, 15f to true, 0.4f to false, 15f to true)
        val chunks = ChunkPlanner.plan(db)
        for (i in 1 until chunks.size) assertTrue(chunks[i].startFrame >= chunks[i - 1].endFrame)
    }
}
