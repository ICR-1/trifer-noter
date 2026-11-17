package com.example.notetaker.asr

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Model Whisper medium terkuantisasi q8_0 (MIT, whisper.cpp) untuk fitur Rapikan. q8_0 dipilih karena
 * 3x lebih cepat daripada q5 di CPU ini (docs/benchmark.md §3). Diunduh sekali, dapat dilanjutkan.
 */
class WhisperModelStore(context: Context) {
    private val dir = File(context.filesDir, "models/whisper-medium")
    val modelFile = File(dir, FILE_NAME)

    private val _state = MutableStateFlow<ModelState>(if (isInstalled()) ModelState.Ready else ModelState.Missing)
    val state: StateFlow<ModelState> = _state.asStateFlow()
    private val mutex = Mutex()

    fun isInstalled(): Boolean = modelFile.exists() && modelFile.length() >= MIN_VALID_BYTES

    fun refresh() {
        if (_state.value !is ModelState.Downloading) {
            _state.value = if (isInstalled()) ModelState.Ready else ModelState.Missing
        }
    }

    suspend fun download() = withContext(Dispatchers.IO) {
        if (!mutex.tryLock()) return@withContext
        try {
            dir.mkdirs()
            val part = File(dir, "$FILE_NAME.part")
            var done = if (part.exists()) part.length() else 0L
            _state.value = ModelState.Downloading(done, APPROX_BYTES)
            Downloader.download(URL, part, FILE_NAME) { delta ->
                done += delta
                _state.value = ModelState.Downloading(done.coerceAtMost(APPROX_BYTES), APPROX_BYTES)
            }
            if (!part.renameTo(modelFile)) error("Gagal menyimpan $FILE_NAME")
            _state.value = ModelState.Ready
        } catch (e: Exception) {
            _state.value = ModelState.Failed(e.message ?: e.javaClass.simpleName)
        } finally {
            mutex.unlock()
        }
    }

    companion object {
        const val FILE_NAME = "ggml-medium-q8_0.bin"
        const val URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$FILE_NAME"
        const val APPROX_BYTES = 823_400_000L

        /** Berkas lebih kecil dari ini dianggap tidak utuh. */
        private const val MIN_VALID_BYTES = 800_000_000L
    }
}
