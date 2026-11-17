package com.example.notetaker.recording

import com.example.notetaker.audio.WavWriter
import com.example.notetaker.data.db.AppDatabase
import com.example.notetaker.data.db.RecordingStatus
import com.example.notetaker.importer.ImportController
import java.io.File

/**
 * Dijalankan saat aplikasi mulai. Rekaman yang masih berstatus MEREKAM (atau impor MENTRANSKRIP) padahal tidak ada service aktif
 * berarti proses sempat dimatikan sistem. Header WAV diperbaiki dan statusnya menjadi TERHENTI.
 */
object RecordingRecovery {
    suspend fun run(db: AppDatabase) {
        if (RecordingController.state.value.phase != Phase.IDLE) return
        val dao = db.recordingDao()
        val stale = dao.getByStatus(RecordingStatus.MEREKAM.name) +
            if (ImportController.hasAny()) emptyList() else dao.getByStatus(RecordingStatus.MENTRANSKRIP.name)
        // Rapikan terputus: penggantian transkrip bersifat atomik, jadi transkrip lama masih utuh.
        if (!ImportController.hasAny()) {
            dao.getByStatus(RecordingStatus.MERAPIKAN.name).forEach {
                dao.update(it.copy(status = RecordingStatus.SELESAI.name))
            }
        }
        stale.forEach { rec ->
            val duration = rec.audioPath?.let { WavWriter.repair(File(it)) }
            dao.update(
                rec.copy(
                    durationMs = duration ?: rec.durationMs,
                    status = if (duration != null && duration > 0) RecordingStatus.TERHENTI.name else RecordingStatus.GAGAL.name,
                ),
            )
        }
    }
}
