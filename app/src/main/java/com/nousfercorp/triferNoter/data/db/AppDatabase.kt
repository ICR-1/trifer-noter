package com.nousfercorp.triferNoter.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Recording::class,
        Chunk::class,
        Segment::class,
        SegmentFts::class,
        Bookmark::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun chunkDao(): ChunkDao
    abstract fun segmentDao(): SegmentDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "trifer_noter.db").build()
    }
}
