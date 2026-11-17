package com.example.notetaker.importer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class JobKind {
    /** Mentranskripsi berkas audio yang diimpor. */
    TRANSCRIBE,

    /** Merapikan transkrip yang sudah ada dengan Whisper. */
    REFINE,
}

data class JobState(val kind: JobKind, val fraction: Float)

/** Jembatan status antara layanan latar (impor, rapikan) dan UI: kemajuan per rekaman. */
object ImportController {
    private val _progress = MutableStateFlow<Map<Long, JobState>>(emptyMap())
    val progress: StateFlow<Map<Long, JobState>> = _progress.asStateFlow()

    fun isActive(id: Long): Boolean = _progress.value.containsKey(id)

    fun hasAny(): Boolean = _progress.value.isNotEmpty()

    fun set(id: Long, fraction: Float, kind: JobKind = JobKind.TRANSCRIBE) =
        _progress.update { it + (id to JobState(kind, fraction.coerceIn(0f, 1f))) }

    fun clear(id: Long) = _progress.update { it - id }
}
