package com.example.notetaker.data

import androidx.room.withTransaction
import com.example.notetaker.data.db.AppDatabase
import com.example.notetaker.data.db.Bookmark
import com.example.notetaker.data.db.Recording
import com.example.notetaker.data.db.Segment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class RecordingRepository(private val db: AppDatabase) {
    fun observeAll(): Flow<List<Recording>> = db.recordingDao().observeAll()

    fun observe(id: Long): Flow<Recording?> = db.recordingDao().observeById(id)

    fun observeSegments(id: Long): Flow<List<Segment>> = db.segmentDao().observeByRecording(id)

    fun observeBookmarks(id: Long): Flow<List<Bookmark>> = db.bookmarkDao().observeByRecording(id)

    /**
     * Ganti transkrip otomatis dengan hasil baru (mis. Rapikan) dalam satu transaksi. Segmen yang sudah diedit
     * pengguna dipertahankan, dan segmen baru yang tumpang tindih dengannya dibuang.
     */
    suspend fun replaceTranscript(recordingId: Long, fresh: List<Segment>) = db.withTransaction {
        val dao = db.segmentDao()
        val edited = dao.getAll(recordingId).filter { it.edited }
        dao.deleteAuto(recordingId)
        dao.insertAll(fresh.filter { f -> edited.none { e -> f.startMs < e.endMs && f.endMs > e.startMs } })
    }

    suspend fun editSegment(segmentId: Long, text: String) {
        val clean = text.trim()
        if (clean.isNotEmpty()) db.segmentDao().updateText(segmentId, clean)
    }

    /** Hapus catatan: baris database (segmen, penanda, chunk ikut terhapus lewat CASCADE) dan berkas audio. */
    suspend fun delete(recording: Recording) = withContext(Dispatchers.IO) {
        recording.audioPath?.let { runCatching { File(it).delete() } }
        db.recordingDao().delete(recording)
    }
}
