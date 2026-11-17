package com.example.notetaker.asr

import java.io.File

/** Satu segmen hasil Whisper. Waktu dalam milidetik relatif ke awal potongan audio yang diproses. */
data class WhisperSegment(val startMs: Long, val endMs: Long, val noSpeechProb: Float, val text: String)

/** Apakah CPU perangkat ini aman menjalankan build whisper.cpp kita (dikompilasi dengan -march=armv8.6-a). */
object WhisperSupport {
    private val REQUIRED = listOf("i8mm", "bf16", "asimddp")

    val isSupported: Boolean by lazy {
        runCatching {
            val features = File("/proc/cpuinfo").useLines { lines ->
                lines.firstOrNull { it.startsWith("Features") }.orEmpty()
            }.split(' ', '\t', ':').filter { it.isNotEmpty() }.toSet()
            REQUIRED.all { it in features }
        }.getOrDefault(false)
    }
}

/**
 * Pembungkus whisper.cpp lewat JNI. Satu instance memuat model sekali dan dipakai untuk banyak potongan audio.
 * Tidak thread-safe; panggil dari satu thread.
 */
class WhisperEngine(modelPath: String, private val threads: Int = 4) : AutoCloseable {
    private var ptr: Long

    init {
        check(WhisperSupport.isSupported) { "CPU perangkat ini tidak mendukung Rapikan (butuh i8mm/bf16)" }
        System.loadLibrary("trifer_whisper")
        nativeAbort(false)
        ptr = nativeInit(modelPath)
        check(ptr != 0L) { "Gagal memuat model Whisper" }
    }

    /**
     * Transkripsi potongan audio PCM float 16 kHz (idealnya ≤ 30 dtk). [audioCtx] 0 = penuh; nilai lebih kecil
     * mempercepat potongan pendek tanpa menurunkan akurasi (lihat docs/benchmark.md).
     */
    fun transcribe(samples: FloatArray, n: Int, audioCtx: Int = 0, prompt: String = ""): List<WhisperSegment> {
        val lines = nativeTranscribe(ptr, samples, n, threads, audioCtx, prompt) ?: return emptyList()
        return lines.mapNotNull(::parse)
    }

    /** Minta pemrosesan berjalan dihentikan secepatnya. */
    fun abort() = nativeAbort(true)

    override fun close() {
        if (ptr != 0L) {
            nativeFree(ptr)
            ptr = 0
        }
    }

    private fun parse(line: String): WhisperSegment? {
        val parts = line.split('|', limit = 4)
        if (parts.size < 4) return null
        return WhisperSegment(
            startMs = parts[0].toLongOrNull() ?: return null,
            endMs = parts[1].toLongOrNull() ?: return null,
            noSpeechProb = parts[2].toFloatOrNull() ?: 0f,
            text = parts[3].trim(),
        )
    }

    private external fun nativeInit(modelPath: String): Long
    private external fun nativeFree(ptr: Long)
    private external fun nativeAbort(abort: Boolean)
    private external fun nativeTranscribe(
        ptr: Long, samples: FloatArray, nSamples: Int, threads: Int, audioCtx: Int, prompt: String,
    ): Array<String>?
}
