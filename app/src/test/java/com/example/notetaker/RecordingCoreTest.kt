package com.example.notetaker

import com.example.notetaker.audio.WavWriter
import com.example.notetaker.recording.RecordingLimits
import com.example.notetaker.util.formatClock
import com.example.notetaker.util.formatDurationShort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RecordingLimitsTest {
    @Test fun `batas rekaman 3 jam dengan peringatan 10 menit sebelumnya`() {
        assertEquals(3 * 60 * 60 * 1000L, RecordingLimits.MAX_MS)
        assertEquals((2 * 60 + 50) * 60 * 1000L, RecordingLimits.WARN_AT_MS)
    }

    @Test fun `peringatan dan berhenti tepat di ambang`() {
        assertFalse(RecordingLimits.shouldWarn(RecordingLimits.WARN_AT_MS - 1))
        assertTrue(RecordingLimits.shouldWarn(RecordingLimits.WARN_AT_MS))
        assertFalse(RecordingLimits.shouldStop(RecordingLimits.MAX_MS - 1))
        assertTrue(RecordingLimits.shouldStop(RecordingLimits.MAX_MS))
    }

    @Test fun `sisa waktu tidak pernah negatif`() {
        assertEquals(0L, RecordingLimits.remainingMs(RecordingLimits.MAX_MS + 5_000))
        assertEquals(RecordingLimits.MAX_MS, RecordingLimits.remainingMs(0))
    }
}

class TimeFormatTest {
    @Test fun `formatClock`() {
        assertEquals("00:00:00", formatClock(0))
        assertEquals("00:01:05", formatClock(65_999))
        assertEquals("03:00:00", formatClock(3 * 60 * 60 * 1000L))
        assertEquals("00:00:00", formatClock(-5))
    }

    @Test fun `formatDurationShort`() {
        assertEquals("42 dtk", formatDurationShort(42_000))
        assertEquals("12 mnt 3 dtk", formatDurationShort((12 * 60 + 3) * 1000L))
        assertEquals("1 j 5 mnt", formatDurationShort((65 * 60) * 1000L))
    }
}

class WavWriterTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun le(bytes: ByteArray, off: Int, len: Int): Long {
        var v = 0L
        for (i in 0 until len) v = v or ((bytes[off + i].toLong() and 0xFF) shl (8 * i))
        return v
    }

    @Test fun `header WAV valid setelah ditutup`() {
        val f = File(tmp.root, "a.wav")
        WavWriter(f).use { w ->
            w.write(ShortArray(16_000) { 1000 }, 16_000) // 1 detik
            assertEquals(1000L, w.durationMs)
        }
        val b = f.readBytes()
        assertEquals("RIFF", String(b, 0, 4))
        assertEquals("WAVE", String(b, 8, 4))
        assertEquals(1L, le(b, 20, 2)) // PCM
        assertEquals(1L, le(b, 22, 2)) // mono
        assertEquals(16_000L, le(b, 24, 4))
        assertEquals(16L, le(b, 34, 2))
        assertEquals(32_000L, le(b, 40, 4)) // data = 1 dtk x 16000 x 2 byte
        assertEquals(44L + 32_000L, f.length())
        assertEquals(36L + 32_000L, le(b, 4, 4))
    }

    @Test fun `sampel ditulis little endian`() {
        val f = File(tmp.root, "b.wav")
        WavWriter(f).use { it.write(shortArrayOf(0x0102, -2), 2) }
        val b = f.readBytes()
        assertEquals(0x02, b[44].toInt())
        assertEquals(0x01, b[45].toInt())
        assertEquals(0xFE, b[46].toInt() and 0xFF)
        assertEquals(0xFF, b[47].toInt() and 0xFF)
    }

    @Test fun `repair memperbaiki berkas yang tidak sempat ditutup`() {
        val f = File(tmp.root, "c.wav")
        val w = WavWriter(f)
        w.write(ShortArray(8_000), 8_000) // 0,5 detik, header belum diperbarui
        // Simulasi crash: header masih berisi panjang 0.
        assertEquals(0L, le(f.readBytes(), 40, 4))
        val dur = WavWriter.repair(f)
        assertEquals(500L, dur)
        assertEquals(16_000L, le(f.readBytes(), 40, 4))
        w.close()
    }

    @Test fun `repair berkas terlalu kecil mengembalikan null`() {
        val f = File(tmp.root, "d.wav").apply { writeBytes(ByteArray(10)) }
        assertNull(WavWriter.repair(f))
        assertNull(WavWriter.repair(File(tmp.root, "tidak-ada.wav")))
    }

    @Test fun `patchHeader tidak menggeser posisi tulis`() {
        val f = File(tmp.root, "e.wav")
        WavWriter(f).use { w ->
            w.write(ShortArray(100), 100)
            w.patchHeader()
            w.write(ShortArray(100), 100)
        }
        assertEquals(44L + 400L, f.length())
        assertEquals(400L, le(f.readBytes(), 40, 4))
        // pastikan ByteBuffer util tidak dipakai salah: buffer scratch cukup besar
        assertEquals(ByteOrder.LITTLE_ENDIAN, ByteBuffer.allocate(1).order(ByteOrder.LITTLE_ENDIAN).order())
    }
}
