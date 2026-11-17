package com.example.notetaker.recording

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class Phase { IDLE, RECORDING, PAUSED, FINALIZING }

/** Status mesin transkripsi live. */
enum class LiveStatus { OFF_NO_MODEL, LOADING, ACTIVE, ERROR }

data class RecState(
    val phase: Phase = Phase.IDLE,
    val recordingId: Long = 0,
    val elapsedMs: Long = 0,
    /** Level suara 0..1 untuk visualisasi. */
    val level: Float = 0f,
    val bookmarkCount: Int = 0,
    /** True bila jeda terjadi karena sistem (mis. panggilan masuk), bukan pengguna. */
    val pausedBySystem: Boolean = false,
    val nearLimit: Boolean = false,
    val error: String? = null,
    /** Id rekaman yang baru selesai; dipakai UI untuk berpindah layar. */
    val lastFinishedId: Long? = null,

    // --- transkrip live ---
    val liveStatus: LiveStatus = LiveStatus.OFF_NO_MODEL,
    /** Ucapan yang sudah final (terbaru di akhir), dibatasi agar memori tidak membengkak. */
    val liveLines: List<String> = emptyList(),
    /** Teks sementara untuk ucapan yang sedang berjalan. */
    val livePartial: String = "",
    /** Audio yang menunggu diproses mesin. Besar berarti mesin tertinggal ("mengejar…"). */
    val liveLagMs: Long = 0,
    val liveError: String? = null,
)

/** Jembatan status antara [RecordingService] dan UI. */
object RecordingController {
    const val MAX_LIVE_LINES = 300

    private val _state = MutableStateFlow(RecState())
    val state: StateFlow<RecState> = _state.asStateFlow()

    fun update(block: (RecState) -> RecState) = _state.update(block)

    fun reset(finishedId: Long? = null, error: String? = null) {
        _state.value = RecState(lastFinishedId = finishedId, error = error)
    }

    fun consumeFinished() = _state.update { it.copy(lastFinishedId = null) }

    fun consumeError() = _state.update { it.copy(error = null) }

    fun addLiveLine(text: String) = _state.update {
        it.copy(liveLines = (it.liveLines + text).takeLast(MAX_LIVE_LINES), livePartial = "")
    }
}
