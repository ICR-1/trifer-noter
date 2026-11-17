package com.nousfercorp.triferNoter.asr

/**
 * Aturan kapan sebuah ucapan ditutup. Model streaming dilatih dengan ucapan pendek, jadi aliran panjang harus
 * dipotong, tetapi pemotongan buta tiap N detik merusak kata di titik potong ("Subianto" menjadi "Bianto").
 * Solusinya: potong di JEDA nyata (diukur dari energi audio oleh [PauseDetector]), dan makin panjang ucapan,
 * makin pendek jeda yang cukup untuk memotong. Potongan paksa hanya sebagai jaring pengaman.
 */
data class EndpointPolicy(
    /** Jeda yang menutup ucapan pendek (kalimat selesai). */
    val basePauseSec: Float = 0.9f,
    /** Setelah ucapan selama ini, jeda yang lebih pendek sudah cukup. */
    val midAfterSec: Float = 6f,
    val midPauseSec: Float = 0.5f,
    val longAfterSec: Float = 9f,
    val longPauseSec: Float = 0.3f,
    val urgentAfterSec: Float = 12f,
    val urgentPauseSec: Float = 0.25f,
    /**
     * Batas keras: potong walau tanpa jeda. Sengaja pendek karena model streaming ini "mogok" (berhenti
     * mengeluarkan teks) pada ucapan kontinu yang panjang: terukur ±4,7 dtk teks hilang pada stream ±17 dtk.
     */
    val hardMaxSec: Float = 14f,
) {
    /** Jeda minimum (detik) yang diperlukan untuk menutup ucapan sepanjang [speechSec]. */
    fun requiredPauseSec(speechSec: Float): Float = when {
        speechSec >= urgentAfterSec -> urgentPauseSec
        speechSec >= longAfterSec -> longPauseSec
        speechSec >= midAfterSec -> midPauseSec
        else -> basePauseSec
    }

    /** True bila ucapan sepanjang [speechSec] dengan jeda [pauseSec] harus ditutup sekarang. */
    fun shouldCut(speechSec: Float, pauseSec: Float): Boolean =
        speechSec >= hardMaxSec || (pauseSec > 0f && pauseSec >= requiredPauseSec(speechSec))

    companion object {
        val DEFAULT = EndpointPolicy()
    }
}
