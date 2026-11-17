package com.nousfercorp.triferNoter

import com.nousfercorp.triferNoter.audio.Resampler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class ResamplerTest {
    private fun run(src: Int, dst: Int, input: FloatArray, block: Int = 1000): FloatArray {
        val r = Resampler(src, dst)
        val out = ArrayList<Float>()
        var pos = 0
        while (pos < input.size) {
            val n = minOf(block, input.size - pos)
            val chunk = input.copyOfRange(pos, pos + n)
            val buf = FloatArray(r.maxOutputFor(n))
            val produced = r.process(chunk, n, buf)
            for (i in 0 until produced) out += buf[i]
            pos += n
        }
        return out.toFloatArray()
    }

    @Test fun `48 kHz ke 16 kHz menghasilkan sepertiga sampel dan menjaga nilai konstan`() {
        val out = run(48_000, 16_000, FloatArray(48_000) { 0.5f })
        assertEquals(16_000, out.size)
        assertTrue(out.all { abs(it - 0.5f) < 1e-4f })
    }

    @Test fun `44,1 kHz ke 16 kHz panjangnya sesuai rasio`() {
        val out = run(44_100, 16_000, FloatArray(44_100) { 0.25f })
        assertTrue("panjang ${out.size}", abs(out.size - 16_000) <= 1)
        assertTrue(out.all { abs(it - 0.25f) < 1e-3f })
    }

    @Test fun `8 kHz ke 16 kHz menggandakan sampel`() {
        val out = run(8_000, 16_000, floatArrayOf(1f, -1f, 0.5f))
        assertEquals(listOf(1f, 1f, -1f, -1f, 0.5f, 0.5f), out.toList())
    }

    @Test fun `hasil sama untuk ukuran blok berbeda (streaming konsisten)`() {
        val x = FloatArray(10_000) { sin(2 * PI * 440 * it / 44_100).toFloat() }
        val a = run(44_100, 16_000, x, block = 333)
        val b = run(44_100, 16_000, x, block = 4096)
        assertEquals(a.size, b.size)
        for (i in a.indices) assertEquals(a[i], b[i], 1e-5f)
    }

    @Test fun `nada rendah dipertahankan amplitudonya setelah downsample`() {
        val x = FloatArray(48_000) { (0.8 * sin(2 * PI * 300 * it / 48_000)).toFloat() }
        val out = run(48_000, 16_000, x)
        val peak = out.drop(100).maxOf { abs(it) }
        assertTrue("puncak $peak", peak in 0.7f..0.85f)
    }
}
