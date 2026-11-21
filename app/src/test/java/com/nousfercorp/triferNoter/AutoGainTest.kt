package com.nousfercorp.triferNoter

import com.nousfercorp.triferNoter.audio.AutoGain
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class AutoGainTest {
    private val sr = 16_000

    /** Sinyal mirip ucapan: beberapa nada termodulasi ±4 Hz, dengan jeda. [rmsDb] = tingkat RMS saat bicara. */
    private fun speechLike(seconds: Int, rmsDb: Float, pauseEvery: Int = 3): ShortArray {
        val n = seconds * sr
        val a = 10.0.pow(rmsDb / 20.0)
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / sr
            val inPause = (t.toInt() % pauseEvery) == pauseEvery - 1 && (t - t.toInt()) > 0.3
            val env = if (inPause) 0.0 else 0.5 * (1 + sin(2 * PI * 4 * t))
            val s = sin(2 * PI * 220 * t) + 0.6 * sin(2 * PI * 1100 * t) + 0.3 * sin(2 * PI * 2700 * t)
            // Normalisasi kasar supaya RMS ≈ a saat env = 1 (daya rata-rata s = (1 + .36 + .09)/2 = .725)
            val v = a * env * s / sqrt(0.725 * 0.375)
            out[i] = (v * 32767).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    private fun noise(seconds: Int, rmsDb: Float, seed: Long = 7): ShortArray {
        val r = Random(seed)
        val a = 10.0.pow(rmsDb / 20.0)
        return ShortArray(seconds * sr) { (r.nextGaussian() * a * 32767).toInt().coerceIn(-32768, 32767).toShort() }
    }

    private fun rmsDb(x: ShortArray, from: Int, to: Int): Float {
        var acc = 0.0
        for (i in from until to) { val v = x[i] / 32768.0; acc += v * v }
        return (20 * log10(sqrt(acc / (to - from)) + 1e-9)).toFloat()
    }

    /** RMS hanya dari sampel yang tidak diam, supaya jeda tidak menurunkan angka. */
    private fun activeRmsDb(x: ShortArray, from: Int, to: Int): Float {
        var acc = 0.0; var n = 0
        for (i in from until to) { if (abs(x[i].toInt()) > 40) { val v = x[i] / 32768.0; acc += v * v; n++ } }
        return (20 * log10(sqrt(acc / n.coerceAtLeast(1)) + 1e-9)).toFloat()
    }

    private fun run(x: ShortArray, chunk: Int = 1600): ShortArray {
        val g = AutoGain()
        val y = x.copyOf()
        var i = 0
        while (i < y.size) {
            val n = minOf(chunk, y.size - i)
            val tmp = y.copyOfRange(i, i + n)
            g.process(tmp, n)
            System.arraycopy(tmp, 0, y, i, n)
            i += n
        }
        return y
    }

    @Test fun `suara lirih dinaikkan mendekati target`() {
        val x = speechLike(20, rmsDb = -50f)
        val y = run(x)
        val before = activeRmsDb(x, 15 * sr, 20 * sr)
        val after = activeRmsDb(y, 15 * sr, 20 * sr)
        assertTrue("sebelum $before sesudah $after", after > before + 15f)
        assertTrue("terlalu keras: $after", after < -14f)
    }

    @Test fun `suara yang sudah cukup keras tidak diubah`() {
        val x = speechLike(10, rmsDb = -20f)
        val g = AutoGain()
        val y = x.copyOf()
        g.process(y)
        assertEquals(0f, g.currentGainDb, 0.5f)
        // di bawah lutut pembatas, sampel identik
        var maxDiff = 0
        for (i in x.indices) if (abs(x[i].toInt()) < 20_000) maxDiff = maxOf(maxDiff, abs(x[i] - y[i]))
        assertTrue("selisih $maxDiff", maxDiff <= 1)
    }

    @Test fun `tidak pernah pecah pada lonjakan keras setelah bagian lirih`() {
        val quiet = speechLike(8, rmsDb = -52f)
        val loud = ShortArray(sr) { if ((it / 40) % 2 == 0) 32767 else -32768 } // gelombang kotak penuh
        val x = quiet + loud
        val y = run(x)
        for (v in y) assertTrue("keluar jangkauan: $v", v in -32768..32767)
        // pembatas lunak: puncak tidak menempel di batas keras terus-menerus
        val clipped = y.count { abs(it.toInt()) >= 32767 }
        assertTrue("terlalu banyak sampel menempel di batas: $clipped", clipped < loud.size / 2)
    }

    @Test fun `noise saat hening tidak dipompa naik`() {
        val x = noise(10, rmsDb = -75f)
        val g = AutoGain()
        val y = x.copyOf()
        g.process(y)
        assertEquals("gain harus tetap 0 dB di noise murni", 0f, g.currentGainDb, 0.01f)
        assertEquals(rmsDb(x, 0, x.size), rmsDb(y, 0, y.size), 0.5f)
    }

    @Test fun `gain ditahan saat hening, tidak naik terus`() {
        val talk = speechLike(10, rmsDb = -50f)
        val g = AutoGain()
        val a = talk.copyOf(); g.process(a)
        val gainAfterTalk = g.currentGainDb
        val silence = noise(8, rmsDb = -80f)
        g.process(silence)
        assertEquals(gainAfterTalk, g.currentGainDb, 0.01f)
        assertTrue("gain sempat naik: $gainAfterTalk", gainAfterTalk > 5f)
    }

    @Test fun `gain tidak melebihi batas maksimum`() {
        val x = speechLike(40, rmsDb = -70f)
        val g = AutoGain()
        g.process(x.copyOf())
        assertTrue("gain ${g.currentGainDb}", g.currentGainDb <= 30.01f)
    }

    @Test fun `gain turun cepat saat suara mendadak keras`() {
        val g = AutoGain()
        g.process(speechLike(12, rmsDb = -50f))
        val high = g.currentGainDb
        assertTrue(high > 10f)
        val loud = speechLike(1, rmsDb = -15f)
        val tmp = loud.copyOf(sr / 5) // 200 ms
        g.process(tmp)
        assertTrue("gain masih ${g.currentGainDb} dari $high", g.currentGainDb < high - 8f)
    }

    @Test fun `hasil sama untuk potongan 100 ms dan satu panggilan`() {
        val x = speechLike(8, rmsDb = -48f)
        val a = x.copyOf(); AutoGain().process(a)
        val b = run(x, chunk = 1600)
        assertArrayEquals(a, b)
    }
}
