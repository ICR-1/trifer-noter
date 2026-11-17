package com.example.notetaker.asr

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ModelState {
    data object Missing : ModelState
    data class Downloading(val doneBytes: Long, val totalBytes: Long) : ModelState {
        val fraction: Float get() = if (totalBytes <= 0) 0f else (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
    }

    data object Ready : ModelState
    data class Failed(val message: String) : ModelState
}

/**
 * Penyimpanan model transkripsi di memori internal aplikasi. Model tidak dibundel di APK (ukuran),
 * jadi diunduh sekali dari Hugging Face (dapat dilanjutkan), atau disalin manual untuk pemakaian offline.
 * Model: sherpa-onnx streaming zipformer multibahasa PengChengStarling (Apache-2.0).
 */
class ModelStore(context: Context) {
    val dir = File(context.filesDir, "models/sherpa-multi")

    private val _state = MutableStateFlow<ModelState>(if (isInstalled()) ModelState.Ready else ModelState.Missing)
    val state: StateFlow<ModelState> = _state.asStateFlow()
    private val mutex = Mutex()

    fun isInstalled(): Boolean = REQUIRED.all { File(dir, it.name).let { f -> f.exists() && f.length() > 0 } }

    fun refresh() {
        if (_state.value !is ModelState.Downloading) {
            _state.value = if (isInstalled()) ModelState.Ready else ModelState.Missing
        }
    }

    /** Unduh semua berkas yang belum ada. Aman dipanggil ulang; melanjutkan berkas `.part` bila ada. */
    suspend fun download() = withContext(Dispatchers.IO) {
        if (!mutex.tryLock()) return@withContext
        try {
            dir.mkdirs()
            val total = REQUIRED.sumOf { it.approxBytes }
            var done = REQUIRED.filter { File(dir, it.name).exists() }.sumOf { it.approxBytes }
            _state.value = ModelState.Downloading(done, total)

            for (f in REQUIRED) {
                val target = File(dir, f.name)
                if (target.exists() && target.length() > 0) continue
                val part = File(dir, f.name + ".part")
                downloadOne(f.name, part) { delta ->
                    done += delta
                    _state.value = ModelState.Downloading(done.coerceAtMost(total), total)
                }
                if (!part.renameTo(target)) error("Gagal menyimpan ${f.name}")
            }
            _state.value = ModelState.Ready
        } catch (e: Exception) {
            _state.value = ModelState.Failed(e.message ?: e.javaClass.simpleName)
        } finally {
            mutex.unlock()
        }
    }

    private fun downloadOne(name: String, part: File, onBytes: (Long) -> Unit) =
        Downloader.download(BASE_URL + name, part, name, onBytes)

    private data class Item(val name: String, val approxBytes: Long)

    companion object {
        const val TOKENS = "tokens.txt"
        const val ENCODER = "encoder-epoch-75-avg-11-chunk-16-left-128.int8.onnx"
        const val DECODER = "decoder-epoch-75-avg-11-chunk-16-left-128.onnx"
        const val JOINER = "joiner-epoch-75-avg-11-chunk-16-left-128.int8.onnx"

        const val BASE_URL =
            "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-ar_en_id_ja_ru_th_vi_zh-2025-02-10/resolve/main/"

        /** Ukuran perkiraan (untuk bilah progres dan teks "±340 MB"). */
        private val REQUIRED = listOf(
            Item(TOKENS, 200_000L),
            Item(DECODER, 33_800_000L),
            Item(ENCODER, 296_600_000L),
            Item(JOINER, 8_300_000L),
        )
        val APPROX_TOTAL_BYTES: Long = REQUIRED.sumOf { it.approxBytes }
    }
}
