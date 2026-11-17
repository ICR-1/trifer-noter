package com.nousfercorp.triferNoter.util

/** HH:MM:SS untuk timer rekam. */
fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

/** Durasi singkat untuk daftar catatan, mis. "1 j 5 mnt", "12 mnt 3 dtk", "42 dtk". */
fun formatDurationShort(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return when {
        h > 0 -> "$h j $m mnt"
        m > 0 -> "$m mnt $s dtk"
        else -> "$s dtk"
    }
}
