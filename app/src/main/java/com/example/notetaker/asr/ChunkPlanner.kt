package com.example.notetaker.asr

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.sqrt

/** Rentang frame [start, end) dari satu potongan audio untuk Whisper. */
data class ChunkRange(val startFrame: Int, val endFrame: Int)

/**
 * Memotong audio panjang menjadi potongan ≤ [maxChunkSec] untuk Whisper. Whisper kehilangan banyak teks bila
 * diberi aliran panjang satu kali jalan (docs/benchmark.md §4), jadi potongan dibuat di jeda bicara. Bila
 * tidak ada jeda, dipotong di titik berenergi terendah. Potongan yang nyaris hening dilewati karena Whisper
 * cenderung berhalusinasi pada senyap.
 */
object ChunkPlanner {
    const val FRAME_MS = 20
    const val FRAMES_PER_SEC = 1000 / FRAME_MS

    /** Di bawah ini dianggap senyap total, apa pun tingkat bicaranya. */
    private const val ABS_FLOOR_DB = -62f

    /** Untuk mencari JEDA: frame bersuara bila lebih keras dari (tingkat bicara lokal - ini) dB. Ketat. */
    private const val PAUSE_DROP_DB = 16f

    /** Untuk menjawab "adakah suara di sini?" (celah, penyaring halusinasi). Longgar. */
    const val PRESENCE_DROP_DB = 24f

    // Tingkat bicara dihitung LOKAL (jendela ±2,5 dtk), bukan global: ujaran yang bersebelahan bisa berbeda
    // volume puluhan dB, dan ambang global menganggap ujaran pelan sebagai senyap (terukur: 71 dari 236 dtk
    // ucapan terlewat, sehingga potongan itu dilewati perencana dan segmennya dibuang penyaring).
    private const val BLOCK_FRAMES = 25 // 0,5 dtk
    private const val WINDOW_BLOCKS = 5
    private const val LEVEL_PERCENTILE = 0.9f

    /** Blok yang tingkat bicara lokalnya di bawah ini dianggap tanpa ucapan sama sekali. */
    private const val MIN_LEVEL_DB = -52f

    fun plan(
        frameDb: FloatArray,
        maxChunkSec: Float = 27f,
        minChunkSec: Float = 4f,
        minPauseSec: Float = 0.25f,
        minSpeechSec: Float = 0.4f,
        marginSec: Float = 0.2f,
    ): List<ChunkRange> {
        if (frameDb.isEmpty()) return emptyList()
        val speech = classify(frameDb, PAUSE_DROP_DB)
        val maxF = (maxChunkSec * FRAMES_PER_SEC).toInt()
        val minF = (minChunkSec * FRAMES_PER_SEC).toInt()
        val pauseF = (minPauseSec * FRAMES_PER_SEC).toInt().coerceAtLeast(1)
        val marginF = (marginSec * FRAMES_PER_SEC).toInt()
        val n = frameDb.size

        val out = ArrayList<ChunkRange>()
        var pos = 0
        while (pos < n) {
            // Lewati senyap di awal.
            var s = pos
            while (s < n && !speech[s]) s++
            if (s >= n) break
            val start = (s - marginF).coerceAtLeast(pos)

            // Sisa audio muat dalam satu potongan.
            val end: Int
            if (n - start <= maxF) {
                var e = n
                while (e > start && !speech[e - 1]) e--
                end = (e + marginF).coerceAtMost(n)
            } else {
                end = chooseCut(frameDb, speech, start + minF, start + maxF, pauseF)
            }

            if (countSpeech(speech, start, end) >= minSpeechSec * FRAMES_PER_SEC) {
                out += ChunkRange(start, end)
            }
            pos = end
        }
        return out
    }

    /** Titik potong di [lo, hi]: tengah jeda terakhir yang cukup panjang, atau frame berenergi terendah. */
    private fun chooseCut(db: FloatArray, speech: BooleanArray, lo: Int, hi: Int, pauseF: Int): Int {
        val hiC = hi.coerceAtMost(db.size)
        val loC = lo.coerceAtMost(hiC)
        var best = -1
        var i = loC
        while (i < hiC) {
            if (!speech[i]) {
                var j = i
                while (j < hiC && !speech[j]) j++
                if (j - i >= pauseF) best = (i + j) / 2 // simpan yang terakhir (potongan terpanjang)
                i = j
            } else {
                i++
            }
        }
        if (best >= 0) return best
        // Tanpa jeda: pilih frame paling pelan di 4 detik terakhir jendela.
        val from = (hiC - 4 * FRAMES_PER_SEC).coerceAtLeast(loC)
        var minIdx = hiC - 1
        var minDb = Float.MAX_VALUE
        for (k in from until hiC) {
            if (db[k] < minDb) {
                minDb = db[k]
                minIdx = k
            }
        }
        return minIdx.coerceIn(loC, hiC).coerceAtLeast(1)
    }

    /** Topeng bersuara per frame (true = ada suara), dengan ambang relatif terhadap tingkat bicara berkas. */
    fun speechMask(frameDb: FloatArray, dropDb: Float = PRESENCE_DROP_DB): BooleanArray =
        if (frameDb.isEmpty()) BooleanArray(0) else classify(frameDb, dropDb)

    /**
     * Cari ucapan yang belum tercakup transkrip. Whisper kadang melewatkan kalimat di awal, tengah, atau akhir
     * sebuah potongan; energi audio menunjukkan di mana masih ada suara tanpa segmen.
     *
     * @param coveredMs rentang waktu (ms, absolut) yang sudah punya segmen
     * @return rentang frame (sudah diberi bantalan) yang perlu diproses ulang
     */
    fun findGaps(
        mask: BooleanArray,
        fromFrame: Int,
        toFrame: Int,
        coveredMs: List<LongRange>,
        minSpeechSec: Float = 1.5f,
        mergeSec: Float = 0.5f,
        toleranceSec: Float = 0.3f,
        padSec: Float = 0.3f,
    ): List<ChunkRange> {
        val to = toFrame.coerceAtMost(mask.size)
        val tolMs = (toleranceSec * 1000).toLong()
        val minFrames = (minSpeechSec * FRAMES_PER_SEC).toInt()
        val mergeFrames = (mergeSec * FRAMES_PER_SEC).toInt()
        val padFrames = (padSec * FRAMES_PER_SEC).toInt()

        fun uncoveredSpeech(f: Int): Boolean {
            if (!mask[f]) return false
            val ms = f.toLong() * FRAME_MS
            return coveredMs.none { ms >= it.first - tolMs && ms <= it.last + tolMs }
        }

        val out = ArrayList<ChunkRange>()
        var f = fromFrame.coerceAtLeast(0)
        while (f < to) {
            if (!uncoveredSpeech(f)) {
                f++
                continue
            }
            val runStart = f
            var lastHit = f
            var count = 0
            var g = f
            while (g < to && g - lastHit <= mergeFrames) {
                if (uncoveredSpeech(g)) {
                    lastHit = g
                    count++
                }
                g++
            }
            if (count >= minFrames) {
                out += ChunkRange((runStart - padFrames).coerceAtLeast(fromFrame), (lastHit + padFrames).coerceAtMost(to))
            }
            f = lastHit + 1
        }
        return out
    }

    /** Jumlah frame bersuara pada rentang [from, to). */
    fun countSpeechFrames(mask: BooleanArray, from: Int, to: Int): Int = countSpeech(mask, from.coerceAtLeast(0), to)

    private fun classify(db: FloatArray, dropDb: Float): BooleanArray {
        val nBlocks = (db.size + BLOCK_FRAMES - 1) / BLOCK_FRAMES
        val levels = FloatArray(nBlocks)
        for (b in 0 until nBlocks) {
            val lo = (b - WINDOW_BLOCKS).coerceAtLeast(0) * BLOCK_FRAMES
            val hi = minOf(db.size, (b + WINDOW_BLOCKS + 1) * BLOCK_FRAMES)
            val w = db.copyOfRange(lo, hi)
            w.sort()
            levels[b] = w[((w.size - 1) * LEVEL_PERCENTILE).toInt()]
        }
        return BooleanArray(db.size) { i ->
            val level = levels[i / BLOCK_FRAMES]
            level > MIN_LEVEL_DB && db[i] > maxOf(level - dropDb, ABS_FLOOR_DB)
        }
    }

    private fun countSpeech(speech: BooleanArray, from: Int, to: Int): Int {
        var c = 0
        for (i in from until to.coerceAtMost(speech.size)) if (speech[i]) c++
        return c
    }

    /** Energi (dB) tiap frame 20 ms dari berkas WAV PCM16 mono 16 kHz (header 44 byte). */
    fun frameDbFromWav(file: File): FloatArray {
        RandomAccessFile(file, "r").use { raf ->
            val total = ((raf.length() - 44) / 2).coerceAtLeast(0)
            val frameSamples = 16_000 * FRAME_MS / 1000
            val frames = (total / frameSamples).toInt()
            val out = FloatArray(frames)
            val buf = ByteArray(frameSamples * 2 * 50) // 1 detik per baca
            raf.seek(44)
            var f = 0
            while (f < frames) {
                val want = minOf(50, frames - f) * frameSamples * 2
                raf.readFully(buf, 0, want)
                val sb = ByteBuffer.wrap(buf, 0, want).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                var k = 0
                while (k < want / 2 / frameSamples) {
                    var acc = 0.0
                    for (i in 0 until frameSamples) {
                        val v = sb.get().toDouble() / 32768.0
                        acc += v * v
                    }
                    out[f++] = (20.0 * log10(sqrt(acc / frameSamples) + 1e-6)).toFloat()
                    k++
                }
            }
            return out
        }
    }

    fun framesToSamples(frame: Int): Int = frame * (16_000 * FRAME_MS / 1000)
}
