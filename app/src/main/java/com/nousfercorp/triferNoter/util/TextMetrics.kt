package com.nousfercorp.triferNoter.util

/** Metrik teks sederhana untuk benchmark/validasi (WER per kata). */
object TextMetrics {
    private val NON_WORD = Regex("[^\\p{L}\\p{N}\\s]")

    fun normalize(text: String): List<String> =
        text.lowercase().replace(NON_WORD, " ").split(Regex("\\s+")).filter { it.isNotEmpty() }

    /** (jumlah kesalahan, panjang acuan) memakai jarak Levenshtein tingkat kata. */
    fun wordErrors(ref: List<String>, hyp: List<String>): Pair<Int, Int> {
        val n = ref.size
        val m = hyp.size
        var prev = IntArray(m + 1) { it }
        for (i in 1..n) {
            val cur = IntArray(m + 1)
            cur[0] = i
            for (j in 1..m) {
                val cost = if (ref[i - 1] == hyp[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            prev = cur
        }
        return prev[m] to n
    }

    /** WER 0..n (0,10 = 10%). Mengembalikan 0 bila acuan kosong dan hipotesis kosong. */
    fun wer(reference: String, hypothesis: String): Double {
        val (errors, len) = wordErrors(normalize(reference), normalize(hypothesis))
        return if (len == 0) (if (errors == 0) 0.0 else 1.0) else errors.toDouble() / len
    }
}
