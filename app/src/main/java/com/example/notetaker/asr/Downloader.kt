package com.example.notetaker.asr

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Unduhan HTTP yang dapat dilanjutkan (berkas `.part` + header Range). */
object Downloader {
    /**
     * Unduh [url] ke [part]. Bila [part] sudah ada, lanjutkan dari ukurannya. [onBytes] dipanggil tiap blok.
     * Melempar [IllegalStateException] bila server gagal atau unduhan tidak lengkap.
     */
    fun download(url: String, part: File, label: String, onBytes: (Long) -> Unit) {
        val existing = if (part.exists()) part.length() else 0L
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
        }
        try {
            val code = conn.responseCode
            val resume = code == HttpURLConnection.HTTP_PARTIAL
            if (code != HttpURLConnection.HTTP_OK && !resume) error("Server menjawab HTTP $code untuk $label")
            val expected = conn.contentLengthLong.takeIf { it > 0 }?.let { if (resume) it + existing else it }
            if (!resume && existing > 0) part.delete()
            FileOutputStream(part, resume).use { out ->
                conn.inputStream.use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        onBytes(n.toLong())
                    }
                }
            }
            if (expected != null && part.length() != expected) {
                error("Unduhan $label tidak lengkap (${part.length()} dari $expected byte)")
            }
        } finally {
            conn.disconnect()
        }
    }
}
