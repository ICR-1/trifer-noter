package com.nousfercorp.triferNoter.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

/** Status siklus hidup sebuah rekaman (lihat PRD §7). */
enum class RecordingStatus {
    MEREKAM,
    /** Berkas audio yang diimpor sedang ditranskripsi. */
    MENTRANSKRIP,
    FINALISASI,
    SELESAI,
    MERAPIKAN,
    TERHENTI,
    GAGAL,
}

@Entity(tableName = "recording")
data class Recording(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val durationMs: Long = 0,
    val status: String = RecordingStatus.MEREKAM.name,
    val audioPath: String? = null,
    val language: String = "id",
    val error: String? = null,
)

@Entity(
    tableName = "chunk",
    foreignKeys = [
        ForeignKey(
            entity = Recording::class,
            parentColumns = ["id"],
            childColumns = ["recordingId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["recordingId", "chunkIndex"], unique = true)],
)
data class Chunk(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordingId: Long,
    val chunkIndex: Int,
    val startMs: Long,
    val endMs: Long,
    val filePath: String,
    /** BELUM, SELESAI, atau GAGAL. */
    val sttState: String = "BELUM",
)

@Entity(
    tableName = "segment",
    foreignKeys = [
        ForeignKey(
            entity = Recording::class,
            parentColumns = ["id"],
            childColumns = ["recordingId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recordingId")],
)
data class Segment(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    val recordingId: Long,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val edited: Boolean = false,
)

/** Indeks pencarian teks penuh untuk [Segment.text]. */
@Fts4(contentEntity = Segment::class)
@Entity(tableName = "segment_fts")
data class SegmentFts(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    val text: String,
)

@Entity(
    tableName = "bookmark",
    foreignKeys = [
        ForeignKey(
            entity = Recording::class,
            parentColumns = ["id"],
            childColumns = ["recordingId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recordingId")],
)
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordingId: Long,
    val timeMs: Long,
    val label: String = "",
)
