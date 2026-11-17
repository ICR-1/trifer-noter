package com.nousfercorp.triferNoter.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recording ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Recording>>

    @Query("SELECT * FROM recording WHERE id = :id")
    suspend fun getById(id: Long): Recording?

    @Query("SELECT * FROM recording WHERE id = :id")
    fun observeById(id: Long): Flow<Recording?>

    @Query("SELECT * FROM recording WHERE status = :status")
    suspend fun getByStatus(status: String): List<Recording>

    @Insert
    suspend fun insert(recording: Recording): Long

    @Update
    suspend fun update(recording: Recording)

    @Delete
    suspend fun delete(recording: Recording)
}

@Dao
interface ChunkDao {
    @Insert
    suspend fun insert(chunk: Chunk): Long

    @Query("SELECT * FROM chunk WHERE recordingId = :recordingId ORDER BY chunkIndex")
    suspend fun getByRecording(recordingId: Long): List<Chunk>

    @Query("SELECT * FROM chunk WHERE recordingId = :recordingId AND sttState = 'BELUM' ORDER BY chunkIndex")
    suspend fun getPending(recordingId: Long): List<Chunk>

    @Query("UPDATE chunk SET sttState = :state WHERE id = :id")
    suspend fun setSttState(id: Long, state: String)
}

@Dao
interface SegmentDao {
    @Insert
    suspend fun insert(segment: Segment): Long

    @Update
    suspend fun update(segment: Segment)

    @Insert
    suspend fun insertAll(segments: List<Segment>)

    @Query("SELECT * FROM segment WHERE recordingId = :recordingId ORDER BY startMs")
    suspend fun getAll(recordingId: Long): List<Segment>

    /** Simpan hasil edit pengguna. Segmen yang diedit dipertahankan saat transkrip diganti (mis. Rapikan). */
    @Query("UPDATE segment SET text = :text, edited = 1 WHERE id = :id")
    suspend fun updateText(id: Long, text: String)

    /** Hapus transkrip otomatis; segmen yang sudah diedit pengguna dipertahankan. */
    @Query("DELETE FROM segment WHERE recordingId = :recordingId AND edited = 0")
    suspend fun deleteAuto(recordingId: Long)

    @Query("SELECT * FROM segment WHERE recordingId = :recordingId ORDER BY startMs")
    fun observeByRecording(recordingId: Long): Flow<List<Segment>>

    /** Cari kata di seluruh transkrip lewat indeks FTS. */
    @Query(
        """
        SELECT segment.* FROM segment
        JOIN segment_fts ON segment.id = segment_fts.rowid
        WHERE segment_fts MATCH :query
        ORDER BY segment.recordingId DESC, segment.startMs
        """,
    )
    fun search(query: String): Flow<List<Segment>>
}

@Dao
interface BookmarkDao {
    @Insert
    suspend fun insert(bookmark: Bookmark): Long

    @Query("SELECT * FROM bookmark WHERE recordingId = :recordingId ORDER BY timeMs")
    fun observeByRecording(recordingId: Long): Flow<List<Bookmark>>

    @Delete
    suspend fun delete(bookmark: Bookmark)
}
