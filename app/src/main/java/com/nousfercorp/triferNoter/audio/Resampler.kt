package com.nousfercorp.triferNoter.audio

/**
 * Resampler streaming dengan rata-rata area (box filter). Cukup baik untuk ucapan: menurunkan 44,1/48 kHz
 * ke 16 kHz tanpa aliasing berarti, dan menaikkan 8 kHz ke 16 kHz (zero-order hold).
 */
class Resampler(srcRate: Int, dstRate: Int) {
    private val ratio = srcRate.toDouble() / dstRate // sampel sumber per sampel keluaran
    private var acc = 0.0
    private var remaining = ratio

    /** Ukuran buffer keluaran yang aman untuk [n] sampel masukan. */
    fun maxOutputFor(n: Int): Int = (n / ratio).toInt() + 2

    /** Proses [n] sampel dari [input]; tulis hasil ke [out]. Mengembalikan jumlah sampel keluaran. */
    fun process(input: FloatArray, n: Int, out: FloatArray): Int {
        var o = 0
        for (i in 0 until n) {
            var mass = 1.0
            val x = input[i].toDouble()
            while (mass > 1e-12) {
                val take = if (mass < remaining) mass else remaining
                acc += x * take
                mass -= take
                remaining -= take
                if (remaining <= 1e-9) {
                    out[o++] = (acc / ratio).toFloat()
                    acc = 0.0
                    remaining = ratio
                }
            }
        }
        return o
    }
}
