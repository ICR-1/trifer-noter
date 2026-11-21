package com.nousfercorp.triferNoter

import com.nousfercorp.triferNoter.refine.LiveSpan
import com.nousfercorp.triferNoter.refine.SegmentGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentGateTest {
    // ---------------------------------------------------------------- frasa penutup baku
    @Test fun `frasa penutup baku dikenali`() {
        for (t in listOf("Terima kasih.", "terima kasih banyak", "Terimakasih", "Ya, terima kasih.", "Makasih ya", "Thank you.", "Sampai jumpa lagi", "oke terima kasih semuanya")) {
            assertTrue("seharusnya baku: $t", SegmentGate.isStockPhrase(t))
        }
    }

    @Test fun `kalimat sah yang memuat terima kasih bukan frasa baku`() {
        for (t in listOf("Terima kasih atas bantuannya Pak Budi dan tim", "Kita lanjutkan rapat hari ini", "Saya ucapkan terima kasih kepada seluruh peserta yang hadir", "bedah anggaran")) {
            assertFalse("bukan baku: $t", SegmentGate.isStockPhrase(t))
        }
    }

    // ---------------------------------------------------------------- keputusan terima/tolak
    @Test fun `terima kasih yang tidak didengar mesin live ditolak`() {
        // pola nyata: Whisper mengeluarkan "Terima kasih." berkali-kali padahal live tidak mendengarnya
        assertFalse(SegmentGate.accept("Terima kasih.", voicedFrames = 100, liveAvailable = true, liveSupport = false))
        assertFalse(SegmentGate.accept("Thank you.", voicedFrames = 100, liveAvailable = true, liveSupport = false))
    }

    @Test fun `terima kasih yang didengar mesin live diterima`() {
        assertTrue(SegmentGate.accept("Terima kasih.", voicedFrames = 45, liveAvailable = true, liveSupport = true))
    }

    @Test fun `tanpa transkrip live tidak ada pembanding jadi tidak ditolak`() {
        assertTrue(SegmentGate.accept("Terima kasih.", voicedFrames = 80, liveAvailable = false, liveSupport = false))
    }

    @Test fun `kalimat biasa tidak pernah ditolak oleh aturan frasa penutup`() {
        assertTrue(SegmentGate.accept("kita lanjutkan ke agenda berikutnya", 60, liveAvailable = true, liveSupport = false))
        assertTrue(SegmentGate.accept("Terima kasih atas bantuannya Pak Budi", 60, liveAvailable = true, liveSupport = false))
        assertTrue(SegmentGate.accept("iya", 12, liveAvailable = true, liveSupport = false))
    }

    @Test fun `segmen kosong atau tanpa suara tetap ditolak`() {
        assertFalse(SegmentGate.accept("   ", 100, liveAvailable = true, liveSupport = true))
        assertFalse(SegmentGate.accept("kita lanjutkan", 5, liveAvailable = true, liveSupport = true))
    }

    @Test fun `dukungan live dicocokkan menurut waktu dan kata kunci`() {
        val live = listOf(LiveSpan(10_000, 12_000, "terima kasih semuanya"))
        assertTrue(SegmentGate.liveSupports("Terima kasih.", 11_000, 12_500, live))
        assertTrue(SegmentGate.liveSupports("Terima kasih.", 14_000, 15_000, live)) // dalam toleransi 2,5 dtk
        assertFalse(SegmentGate.liveSupports("Terima kasih.", 30_000, 31_000, live)) // jauh dari waktunya
        assertFalse(SegmentGate.liveSupports("Terima kasih.", 11_000, 12_000, listOf(LiveSpan(10_000, 12_000, "kita mulai rapatnya"))))
        assertFalse(SegmentGate.liveSupports("Terima kasih.", 11_000, 12_000, emptyList()))
    }

    // ---------------------------------------------------------------- kalimat penutup yang menempel pada ucapan
    @Test fun `kalimat penutup palsu di depan ucapan nyata dibuang`() {
        assertEquals(
            "Bukankah untuk Harman?",
            SegmentGate.stripUnsupportedStock("Terima kasih. Bukankah untuk Harman?", liveAvailable = true, liveSupport = false),
        )
    }

    @Test fun `kalimat penutup yang didengar mesin live dipertahankan`() {
        val t = "Terima kasih. Sini ada contohnya. Terantakan gak?"
        assertEquals(t, SegmentGate.stripUnsupportedStock(t, liveAvailable = true, liveSupport = true))
    }

    @Test fun `tanpa transkrip live teks tidak diubah`() {
        val t = "Terima kasih. Bukankah untuk Harman?"
        assertEquals(t, SegmentGate.stripUnsupportedStock(t, liveAvailable = false, liveSupport = false))
    }

    @Test fun `kalimat yang hanya memuat terima kasih di tengah tidak disentuh`() {
        val t = "Masuk barisnya, lalu terima kasih. Baik, kita lanjut."
        assertEquals(t, SegmentGate.stripUnsupportedStock(t, liveAvailable = true, liveSupport = false))
        val one = "Mungkin terima kasih untuk Harman."
        assertEquals(one, SegmentGate.stripUnsupportedStock(one, liveAvailable = true, liveSupport = false))
    }
}
