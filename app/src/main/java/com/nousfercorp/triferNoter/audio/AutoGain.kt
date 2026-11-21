package com.nousfercorp.triferNoter.audio

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Penguat suara otomatis untuk rekaman yang terlalu lirih (rapat ramai, pembicara jauh dari HP).
 *
 * Prinsip:
 *  - Hanya **menaikkan** suara lirih sampai tingkat target. Suara yang sudah cukup keras tidak diubah.
 *  - Gain hanya naik saat ada ucapan (di atas lantai noise + [gateDb]); saat hening gain ditahan, sehingga
 *    noise ruangan tidak ikut dipompa naik.
 *  - Gain naik pelan ([riseDbPerSec]) dan turun cepat ([fallDbPerSec]), supaya tidak terdengar "memompa".
 *  - Pembatas lunak mencegah suara pecah pada lonjakan mendadak.
 *
 * Dipakai di jalur rekam saja (WAV dan mesin transkripsi mendapat audio yang sama). Impor file tidak diproses.
 * Tidak aman untuk dipakai dari beberapa thread sekaligus.
 */
class AutoGain(
    private val sampleRate: Int = 16_000,
    /** Tingkat RMS ucapan yang dituju, dalam dBFS. */
    private val targetDb: Float = -22f,
    /** Penguatan maksimum. Di atas ini, noise lebih menonjol daripada manfaatnya. */
    private val maxGainDb: Float = 30f,
    /** Seberapa jauh di atas lantai noise sebuah blok dianggap ucapan. */
    private val gateDb: Float = 9f,
    private val riseDbPerSec: Float = 8f,
    private val fallDbPerSec: Float = 100f,
) {
    private val blockSize = sampleRate / 100 // 10 ms
    private val blocksPerSec = 100f

    private var noiseDb = INITIAL_NOISE_DB
    private var speechDb = Float.NaN
    private var gainDb = 0f

    /** Gain yang sedang berlaku, dalam dB. Untuk uji dan diagnostik. */
    val currentGainDb: Float get() = gainDb

    fun reset() {
        noiseDb = INITIAL_NOISE_DB
        speechDb = Float.NaN
        gainDb = 0f
    }

    /** Proses [n] sampel di tempat. Sisa yang kurang dari satu blok 10 ms ikut diproses sebagai blok pendek. */
    fun process(buf: ShortArray, n: Int = buf.size) {
        var i = 0
        while (i < n) {
            val len = minOf(blockSize, n - i)
            processBlock(buf, i, len)
            i += len
        }
    }

    private fun processBlock(buf: ShortArray, off: Int, len: Int) {
        var acc = 0.0
        for (k in off until off + len) {
            val v = buf[k] / 32768.0
            acc += v * v
        }
        val db = 20f * log10(sqrt(acc / len).toFloat() + 1e-6f)

        // Lantai noise: turun cepat ke yang terendah, naik sangat pelan (±0,5 dB/dtk).
        noiseDb = if (db < noiseDb) db else noiseDb + NOISE_RISE_DB_PER_SEC / blocksPerSec
        noiseDb = noiseDb.coerceIn(MIN_NOISE_DB, 0f)

        val active = db > maxOf(noiseDb + gateDb, ABS_GATE_DB)
        if (active) {
            speechDb = when {
                speechDb.isNaN() -> db
                db > speechDb -> speechDb + (db - speechDb) * ENV_ATTACK
                else -> speechDb + (db - speechDb) * ENV_RELEASE
            }
        }

        val start = gainDb
        if (!speechDb.isNaN()) {
            val desired = (targetDb - speechDb).coerceIn(0f, maxGainDb)
            if (desired < gainDb) {
                gainDb -= minOf(gainDb - desired, fallDbPerSec / blocksPerSec)
            } else if (active && desired > gainDb) {
                gainDb += minOf(desired - gainDb, riseDbPerSec / blocksPerSec)
            }
        }
        val end = gainDb

        if (start == 0f && end == 0f) {
            // Tanpa penguatan: hanya batasi lonjakan, sampel normal tidak disentuh.
            limitOnly(buf, off, len)
            return
        }
        val g0 = dbToLin(start)
        val g1 = dbToLin(end)
        for (k in 0 until len) {
            val g = g0 + (g1 - g0) * (k + 1) / len
            buf[off + k] = toShort(softLimit(buf[off + k] / 32768f * g))
        }
    }

    private fun limitOnly(buf: ShortArray, off: Int, len: Int) {
        for (k in off until off + len) {
            val x = buf[k] / 32768f
            if (abs(x) > KNEE) buf[k] = toShort(softLimit(x))
        }
    }

    private fun softLimit(x: Float): Float {
        val a = abs(x)
        if (a <= KNEE) return x
        val y = KNEE + (1f - KNEE) * tanh((a - KNEE) / (1f - KNEE))
        return if (x < 0) -y else y
    }

    private fun toShort(x: Float): Short = (x * 32767f).toInt().coerceIn(-32768, 32767).toShort()

    private fun dbToLin(db: Float): Float = 10.0.pow(db / 20.0).toFloat()

    private companion object {
        const val INITIAL_NOISE_DB = -50f
        const val MIN_NOISE_DB = -90f
        /** Di bawah ini tidak pernah dianggap ucapan, apa pun lantai noisenya. */
        const val ABS_GATE_DB = -68f
        const val NOISE_RISE_DB_PER_SEC = 0.5f
        const val ENV_ATTACK = 0.3f
        const val ENV_RELEASE = 0.01f
        const val KNEE = 0.7f
    }
}
