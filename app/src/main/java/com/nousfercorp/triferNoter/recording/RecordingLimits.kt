package com.nousfercorp.triferNoter.recording

/** Aturan batas durasi rekaman (PRD D3: maks. 3 jam, peringatan 10 menit sebelumnya). */
object RecordingLimits {
    const val MAX_MS: Long = 3L * 60 * 60 * 1000
    const val WARN_BEFORE_MS: Long = 10L * 60 * 1000
    const val WARN_AT_MS: Long = MAX_MS - WARN_BEFORE_MS

    /** Ruang kosong minimum sebelum merekam (PRD §8.1). */
    const val MIN_FREE_BYTES: Long = 500L * 1024 * 1024

    fun shouldWarn(elapsedMs: Long): Boolean = elapsedMs >= WARN_AT_MS

    fun shouldStop(elapsedMs: Long): Boolean = elapsedMs >= MAX_MS

    fun remainingMs(elapsedMs: Long): Long = (MAX_MS - elapsedMs).coerceAtLeast(0)
}
