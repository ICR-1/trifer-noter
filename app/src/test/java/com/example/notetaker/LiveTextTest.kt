package com.example.notetaker

import com.example.notetaker.asr.LiveText
import com.example.notetaker.util.TextMetrics
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveTextTest {
    @Test fun `huruf besar diubah menjadi kalimat biasa`() {
        assertEquals(
            "Di sisi lain kondisi yang dingin",
            LiveText.clean("DI SISI LAIN KONDISI YANG DINGIN"),
        )
    }

    @Test fun `aksara asing di awal segmen dibuang`() {
        assertEquals("Luar mendampingi dirinya", LiveText.clean("デンで LUAR MENDAMPINGI DIRINYA"))
        assertEquals("Dengan dua anak anak", LiveText.clean("ดูฝาก DENGAN DUA ANAK ANAK"))
    }

    @Test fun `teks kosong atau hanya aksara asing menghasilkan string kosong`() {
        assertEquals("", LiveText.clean(""))
        assertEquals("", LiveText.clean("   "))
        assertEquals("", LiveText.clean("デンで ดูฝาก"))
    }

    @Test fun `spasi berlebih dirapikan dan tanda hubung dipertahankan`() {
        assertEquals("Orang-orang telah mengenal", LiveText.clean("  ORANG-ORANG   TELAH  MENGENAL "))
    }
}

class TextMetricsTest {
    @Test fun `wer nol untuk teks sama walau beda huruf besar dan tanda baca`() {
        assertEquals(0.0, TextMetrics.wer("Di sisi lain, kondisi dingin.", "di sisi lain kondisi dingin"), 1e-9)
    }

    @Test fun `wer menghitung substitusi, sisipan, dan hapusan`() {
        // acuan 4 kata, 1 substitusi
        assertEquals(0.25, TextMetrics.wer("a b c d", "a x c d"), 1e-9)
        // 1 kata hilang
        assertEquals(0.25, TextMetrics.wer("a b c d", "a b c"), 1e-9)
        // 1 kata tambahan
        assertEquals(0.25, TextMetrics.wer("a b c d", "a b c d e"), 1e-9)
    }

    @Test fun `wer untuk acuan kosong`() {
        assertEquals(0.0, TextMetrics.wer("", ""), 1e-9)
        assertEquals(1.0, TextMetrics.wer("", "ada teks"), 1e-9)
    }
}
