package com.example.notetaker

import com.example.notetaker.asr.EndpointPolicy
import com.example.notetaker.asr.PauseDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

class EndpointPolicyTest {
    private val p = EndpointPolicy.DEFAULT

    @Test fun `ucapan pendek butuh jeda panjang`() {
        assertEquals(0.9f, p.requiredPauseSec(3f), 1e-6f)
        assertFalse(p.shouldCut(speechSec = 3f, pauseSec = 0.6f))
        assertTrue(p.shouldCut(speechSec = 3f, pauseSec = 0.9f))
    }

    @Test fun `makin panjang ucapan makin pendek jeda yang cukup`() {
        assertEquals(0.9f, p.requiredPauseSec(5.9f), 1e-6f)
        assertEquals(0.5f, p.requiredPauseSec(6f), 1e-6f)
        assertEquals(0.5f, p.requiredPauseSec(8.9f), 1e-6f)
        assertEquals(0.3f, p.requiredPauseSec(9f), 1e-6f)
        assertEquals(0.3f, p.requiredPauseSec(11.9f), 1e-6f)
        assertEquals(0.25f, p.requiredPauseSec(12f), 1e-6f)
        assertEquals(0.25f, p.requiredPauseSec(13.9f), 1e-6f)
    }

    @Test fun `tanpa jeda tidak memotong sebelum batas keras`() {
        assertFalse(p.shouldCut(speechSec = 10f, pauseSec = 0f))
        assertFalse(p.shouldCut(speechSec = 13.9f, pauseSec = 0f))
    }

    @Test fun `batas keras memotong walau tanpa jeda`() {
        assertTrue(p.shouldCut(speechSec = 14f, pauseSec = 0f))
        assertTrue(p.shouldCut(speechSec = 45f, pauseSec = 0f))
    }

    @Test fun `jeda terpendek tetap di atas penutupan konsonan di dalam kata`() {
        // Penutupan huruf mati (mis. akhir "dianggap" sebelum "nya") bisa sampai ±150-200 ms.
        assertTrue(p.urgentPauseSec >= 0.25f)
    }

    @Test fun `urutan ambang masuk akal`() {
        assertTrue(p.urgentPauseSec <= p.longPauseSec && p.longPauseSec <= p.midPauseSec && p.midPauseSec <= p.basePauseSec)
        assertTrue(p.midAfterSec < p.longAfterSec && p.longAfterSec < p.urgentAfterSec && p.urgentAfterSec < p.hardMaxSec)
    }
}

class PauseDetectorTest {
    private val rnd = Random(1)

    /** Derau berenergi seperti bicara (amplitudo sekitar [amp]) selama [sec] detik. */
    private fun speech(sec: Float, amp: Float = 0.1f) =
        FloatArray((16_000 * sec).toInt()) { (amp * (rnd.nextGaussian() * 0.6 + sin(2 * PI * 200 * it / 16_000))).toFloat() }

    private fun gap(sec: Float) = FloatArray((16_000 * sec).toInt()) { (0.0008 * rnd.nextGaussian()).toFloat() }

    @Test fun `suara terus-menerus tidak dianggap jeda`() {
        val d = PauseDetector()
        d.push(speech(5f))
        assertEquals(0f, d.pauseSec, 1e-6f)
    }

    @Test fun `jeda 400 ms terdeteksi sekitar 400 ms`() {
        val d = PauseDetector()
        d.push(speech(3f))
        d.push(gap(0.4f))
        assertEquals(0.4f, d.pauseSec, 0.05f)
    }

    @Test fun `jeda berakhir saat bicara kembali`() {
        val d = PauseDetector()
        d.push(speech(3f))
        d.push(gap(0.5f))
        assertTrue(d.pauseSec > 0.3f)
        d.push(speech(0.2f))
        assertEquals(0f, d.pauseSec, 1e-6f)
    }

    @Test fun `jeda pendek yang sudah usai dalam satu blok tetap terlihat lewat peakPauseSec`() {
        val d = PauseDetector()
        d.push(speech(3f))
        // satu blok 100 ms: jeda 60 ms lalu bicara kembali, setelah jeda 300 ms terjadi di blok sebelumnya
        d.push(gap(0.3f))
        d.push(speech(0.1f))
        assertEquals(0f, d.pauseSec, 1e-6f)
        d.push(speech(0.02f)) // blok berikut tidak lagi mengandung jeda
        assertTrue(d.peakPauseSec < 0.05f)
        // blok yang memuat akhir jeda: peak mengingat jeda terpanjang
        val e = PauseDetector()
        e.push(speech(3f))
        val mixed = gap(0.3f) + speech(0.05f)
        e.push(mixed)
        assertTrue("peak ${e.peakPauseSec}", e.peakPauseSec > 0.25f)
        assertEquals(0f, e.pauseSec, 1e-6f)
    }

    @Test fun `senyap total sejak awal bukan jeda`() {
        val d = PauseDetector()
        d.push(gap(5f))
        assertEquals(0f, d.pauseSec, 1e-6f)
    }

    @Test fun `bekerja pada volume berbeda relatif terhadap tingkat bicara`() {
        val quiet = PauseDetector()
        quiet.push(speech(3f, amp = 0.03f))
        quiet.push(gap(0.4f))
        assertTrue("jeda pada rekaman pelan: ${quiet.pauseSec}", quiet.pauseSec > 0.3f)
    }
}
