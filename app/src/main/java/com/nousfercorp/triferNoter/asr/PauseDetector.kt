package com.nousfercorp.triferNoter.asr

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Detektor jeda bicara berbasis energi audio (bukan token model). Token model streaming keluar terlambat
 * ±0,6–1 dtk, jadi "tidak ada token baru" tidak berarti pembicara sudah diam. Energi audio mencerminkan
 * keadaan sebenarnya.
 *
 * Sebuah frame 20 ms dianggap jeda bila energinya jauh di bawah tingkat bicara baru-baru ini (persentil 80
 * dalam ±3 detik terakhir), sehingga bekerja juga di ruangan yang agak bising, dan tidak perlu ambang mutlak.
 */
class PauseDetector(
    private val sampleRate: Int = 16_000,
    /** Berapa dB di bawah tingkat bicara agar sebuah frame dianggap jeda. */
    private val dropDb: Float = 12f,
) {
    private val frameSize = sampleRate / 50 // 20 ms
    private val levels = FloatArray(HISTORY_FRAMES)
    private var count = 0
    private var head = 0
    private var acc = 0.0
    private var accN = 0
    private var pauseFrames = 0
    private var framesSinceLevel = 0
    private var speechLevelDb = -100f

    private var peakFrames = 0

    /** Panjang jeda berjalan saat ini, dalam detik. 0 bila sedang ada suara. */
    val pauseSec: Float get() = pauseFrames * FRAME_SEC

    /**
     * Jeda terpanjang yang tercapai selama [push] terakhir (termasuk jeda yang baru saja berakhir). Dipakai untuk
     * keputusan pemotongan, karena pengecekan per blok 100 ms bisa melewatkan jeda pendek yang sudah usai.
     */
    val peakPauseSec: Float get() = peakFrames * FRAME_SEC

    fun reset() {
        peakFrames = 0
        count = 0
        head = 0
        acc = 0.0
        accN = 0
        pauseFrames = 0
        framesSinceLevel = 0
        speechLevelDb = -100f
    }

    fun push(samples: FloatArray, n: Int = samples.size) {
        peakFrames = pauseFrames
        for (i in 0 until n) {
            val v = samples[i]
            acc += v.toDouble() * v
            if (++accN == frameSize) {
                onFrame(20f * log10(sqrt(acc / accN).toFloat() + 1e-6f))
                acc = 0.0
                accN = 0
            }
        }
    }

    private fun onFrame(db: Float) {
        levels[head] = db
        head = (head + 1) % levels.size
        if (count < levels.size) count++
        if (++framesSinceLevel >= LEVEL_EVERY || count == MIN_FRAMES) {
            framesSinceLevel = 0
            speechLevelDb = percentile80()
        }
        val warm = count >= MIN_FRAMES && speechLevelDb > MIN_SPEECH_DB
        pauseFrames = if (warm && db < speechLevelDb - dropDb) pauseFrames + 1 else 0
        if (pauseFrames > peakFrames) peakFrames = pauseFrames
    }

    private fun percentile80(): Float {
        val a = levels.copyOf(count)
        a.sort()
        return a[((count - 1) * 0.8f).toInt()]
    }

    companion object {
        private const val FRAME_SEC = 0.02f
        private const val HISTORY_FRAMES = 150 // ±3 detik
        private const val MIN_FRAMES = 25 // 0,5 dtk sebelum penilaian dimulai
        private const val LEVEL_EVERY = 5

        /** Di bawah ini dianggap tidak ada bicara sama sekali (senyap total), jadi tidak ada "jeda" bermakna. */
        private const val MIN_SPEECH_DB = -50f
    }
}
