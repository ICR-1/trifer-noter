package com.nousfercorp.triferNoter.asr

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.File

sealed interface AsrEvent {
    /** Teks sementara untuk ucapan yang sedang berjalan (ditampilkan abu-abu). */
    data class Partial(val text: String) : AsrEvent

    /** Ucapan selesai; teks dikunci dan disimpan. */
    data class Final(val text: String, val startMs: Long, val endMs: Long) : AsrEvent
}

/**
 * Pembungkus sherpa-onnx streaming zipformer. Tidak thread-safe: panggil dari satu thread.
 *
 * Endpoint ditentukan sendiri oleh [EndpointPolicy] (bukan aturan bawaan sherpa) agar pemotongan terjadi di
 * jeda alami. Di tiap pemotongan dibuat stream BARU (reset() pada stream yang sama menghilangkan teks, lihat
 * docs/benchmark.md §6b), dan beberapa ratus milidetik audio terakhir diumpankan ulang sebagai bantalan supaya
 * awal kata berikutnya tidak terpotong.
 */
class StreamingAsr(
    modelDir: File,
    numThreads: Int = 4,
    private val policy: EndpointPolicy = EndpointPolicy.DEFAULT,
    /** Geser batas pembuangan token tumpang tindih (detik). Negatif = buang lebih sedikit. */
    private val overlapSkipOffsetSec: Float = DEFAULT_SKIP_OFFSET_SEC,
) : AutoCloseable {
    private val recognizer: OnlineRecognizer
    private var stream: OnlineStream

    private var totalSamples = 0L

    /** Posisi (sampel, pada garis waktu rekaman) tempat stream saat ini mulai menerima audio. */
    private var streamStartSamples = 0L
    private var lastPartial = ""

    /** Diagnostik (opsional): menerima baris log tiap titik potong. */
    var debugLog: ((String) -> Unit)? = null

    /** Token dengan timestamp di bawah ini (detik, relatif ke awal stream) milik bantalan tumpang tindih. */
    private var skipBeforeSec = 0f

    private val pause = PauseDetector(SAMPLE_RATE)

    // Cincin audio terakhir, dipakai sebagai bantalan stream baru.
    private val ring = FloatArray(RING_SAMPLES)
    private var ringFill = 0
    private var ringPos = 0

    init {
        val config = OnlineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = File(modelDir, ModelStore.ENCODER).path,
                    decoder = File(modelDir, ModelStore.DECODER).path,
                    joiner = File(modelDir, ModelStore.JOINER).path,
                ),
                tokens = File(modelDir, ModelStore.TOKENS).path,
                numThreads = numThreads,
                provider = "cpu",
            ),
            enableEndpoint = false, // endpoint ditangani oleh EndpointPolicy
            decodingMethod = "greedy_search",
        )
        recognizer = OnlineRecognizer(config = config)
        stream = recognizer.createStream()
    }

    /** Umpankan [n] sampel PCM16. Mengembalikan peristiwa yang terjadi (teks sementara dan/atau final). */
    fun accept(samples: ShortArray, n: Int): List<AsrEvent> {
        val floats = FloatArray(n) { samples[it] / 32768f }
        pushRing(floats)
        pause.push(floats, n)
        stream.acceptWaveform(floats, SAMPLE_RATE)
        totalSamples += n
        while (recognizer.isReady(stream)) recognizer.decode(stream)

        val events = ArrayList<AsrEvent>(2)
        val view = visible(recognizer.getResult(stream))
        val text = view.text
        val ts = view.ts
        val streamSec = (totalSamples - streamStartSamples).toFloat() / SAMPLE_RATE

        if (ts.isEmpty()) {
            // Belum ada ucapan di stream ini. Buang stream yang terlalu lama kosong agar tetap ringan.
            if (streamSec > MAX_EMPTY_SEC) restartStream(prerollSamples = 0)
        } else {
            // Panjang ucapan dihitung dari waktu nyata, bukan dari token terakhir (token keluar terlambat).
            val speechSec = streamSec - ts.first()
            if (policy.shouldCut(speechSec, pause.peakPauseSec)) {
                val hardCut = speechSec >= policy.hardMaxSec
                // Tambah bantalan senyap di ekor agar token terakhir yang masih tertahan keluar.
                stream.acceptWaveform(FloatArray(TAIL_PAD_SAMPLES), SAMPLE_RATE)
                while (recognizer.isReady(stream)) recognizer.decode(stream)
                val rawFlushed = recognizer.getResult(stream)
                debugLog?.invoke(
                    "CUT t=%.1fs speech=%.1fs pause=%.2fs skipBefore=%.2f streamStart=%.1fs tokens=%s".format(
                        elapsedMs() / 1000.0, speechSec, pause.peakPauseSec, skipBeforeSec, streamStartSamples / 16000.0,
                        rawFlushed.tokens.indices.joinToString(" ") { i ->
                            "%s@%.2f".format(rawFlushed.tokens[i].trim(), rawFlushed.timestamps.getOrElse(i) { -1f })
                        },
                    ),
                )
                val flushed = visible(rawFlushed)
                val finalText = flushed.text.ifEmpty { text }
                if (finalText.isNotEmpty()) {
                    events += finalEvent(finalText, flushed.ts.takeIf { it.isNotEmpty() } ?: ts)
                }
                // Stream baru dipanaskan dengan audio sebelumnya (token di wilayah itu dibuang) supaya kata
                // pertamanya tidak rusak. Potongan paksa (tanpa jeda) memakai bantalan yang sama.
                restartStream(prerollSamples = if (hardCut) HARD_CUT_PREROLL_SAMPLES else PAUSE_CUT_PREROLL_SAMPLES)
                return events
            }
        }
        if (text != lastPartial) {
            lastPartial = text
            events += AsrEvent.Partial(text)
        }
        return events
    }

    /** Selesaikan sisa audio dan kunci teks yang masih menggantung. */
    fun finish(): List<AsrEvent> {
        stream.acceptWaveform(FloatArray(TAIL_PAD_SAMPLES), SAMPLE_RATE)
        stream.inputFinished()
        while (recognizer.isReady(stream)) recognizer.decode(stream)
        val view = visible(recognizer.getResult(stream))
        lastPartial = ""
        if (view.text.isEmpty()) return emptyList()
        return listOf(finalEvent(view.text, view.ts))
    }

    private class View(val text: String, val ts: FloatArray)

    /**
     * Teks dan timestamp yang terlihat. Bila stream dipanaskan dengan audio tumpang tindih, token yang
     * timestamp-nya masih di wilayah itu milik ucapan sebelumnya, jadi dibuang agar tidak ada kata ganda.
     */
    private fun visible(r: com.k2fsa.sherpa.onnx.OnlineRecognizerResult): View {
        if (skipBeforeSec <= 0f) return View(LiveText.clean(r.text), r.timestamps)
        val n = minOf(r.tokens.size, r.timestamps.size)
        val sb = StringBuilder()
        val keep = ArrayList<Float>(n)
        for (i in 0 until n) {
            if (r.timestamps[i] >= skipBeforeSec) {
                sb.append(r.tokens[i])
                keep += r.timestamps[i]
            }
        }
        return View(LiveText.clean(sb.toString()), keep.toFloatArray())
    }

    private fun finalEvent(text: String, ts: FloatArray): AsrEvent.Final {
        val base = streamStartSamples * 1000L / SAMPLE_RATE
        val start = base + ((ts.firstOrNull() ?: 0f) * 1000).toLong()
        val end = base + (((ts.lastOrNull() ?: 0f) * 1000).toLong() + 250)
        return AsrEvent.Final(text, start, end.coerceAtMost(elapsedMs()).coerceAtLeast(start))
    }

    /** Buat stream baru. Bantalan: audio terakhir diumpankan ulang agar awal kata tidak hilang. */
    private fun restartStream(prerollSamples: Int) {
        stream.release()
        stream = recognizer.createStream()
        lastPartial = ""
        val pre = readRing(prerollSamples)
        streamStartSamples = totalSamples - pre.size
        // Token sebelum (panjang bantalan - margin) milik ucapan lama. Margin menjaga kata yang sudah mulai
        // diucapkan tepat di akhir bantalan.
        skipBeforeSec = if (pre.isEmpty()) 0f else (pre.size.toFloat() / SAMPLE_RATE + overlapSkipOffsetSec).coerceAtLeast(0f)
        if (pre.isNotEmpty()) stream.acceptWaveform(pre, SAMPLE_RATE)
    }

    private fun pushRing(x: FloatArray) {
        for (v in x) {
            ring[ringPos] = v
            ringPos = (ringPos + 1) % ring.size
            if (ringFill < ring.size) ringFill++
        }
    }

    private fun readRing(n: Int): FloatArray {
        val len = minOf(n, ringFill)
        val out = FloatArray(len)
        val start = (ringPos - len + ring.size) % ring.size
        for (i in 0 until len) out[i] = ring[(start + i) % ring.size]
        return out
    }

    fun elapsedMs(): Long = totalSamples * 1000L / SAMPLE_RATE

    override fun close() {
        runCatching { stream.release() }
        runCatching { recognizer.release() }
    }

    companion object {
        const val SAMPLE_RATE = 16_000

        /** Audio terakhir yang disimpan untuk memanaskan stream baru. */
        private const val RING_SAMPLES = (SAMPLE_RATE * 1.2).toInt()
        const val DEFAULT_PREROLL_SEC = 1.0f
        private const val PAUSE_CUT_PREROLL_SAMPLES = (SAMPLE_RATE * DEFAULT_PREROLL_SEC).toInt()
        private const val HARD_CUT_PREROLL_SAMPLES = (SAMPLE_RATE * DEFAULT_PREROLL_SEC).toInt()
        const val DEFAULT_SKIP_OFFSET_SEC = 0.1f
        private const val TAIL_PAD_SAMPLES = (SAMPLE_RATE * 0.4).toInt()
        private const val MAX_EMPTY_SEC = 10f
    }
}
