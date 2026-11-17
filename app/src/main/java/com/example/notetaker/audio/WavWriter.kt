package com.example.notetaker.audio

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Penulis WAV PCM 16-bit mono. Header diperbarui berkala lewat [patchHeader], sehingga bila proses
 * dimatikan sistem, file tetap bisa dibuka (atau diperbaiki dengan [repair]).
 */
class WavWriter(private val file: File, private val sampleRate: Int = SAMPLE_RATE) : Closeable {
    private val raf = RandomAccessFile(file, "rw")
    private var dataBytes = 0L
    private var scratch = ByteArray(0)

    init {
        raf.setLength(0)
        raf.write(header(sampleRate, 0))
    }

    /** Durasi audio yang sudah ditulis. */
    val durationMs: Long get() = bytesToMs(dataBytes, sampleRate)

    fun write(samples: ShortArray, count: Int) {
        val need = count * 2
        if (scratch.size < need) scratch = ByteArray(need)
        ByteBuffer.wrap(scratch, 0, need).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(samples, 0, count)
        raf.write(scratch, 0, need)
        dataBytes += need
    }

    /** Tulis ulang panjang data di header tanpa menggeser posisi tulis. */
    fun patchHeader() {
        val pos = raf.filePointer
        raf.seek(0)
        raf.write(header(sampleRate, dataBytes))
        raf.seek(pos)
    }

    override fun close() {
        patchHeader()
        raf.close()
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val HEADER_SIZE = 44
        private const val BYTES_PER_SAMPLE = 2

        fun bytesToMs(bytes: Long, sampleRate: Int = SAMPLE_RATE): Long =
            bytes * 1000L / (sampleRate.toLong() * BYTES_PER_SAMPLE)

        fun header(sampleRate: Int, dataLen: Long): ByteArray {
            val byteRate = sampleRate * BYTES_PER_SAMPLE
            return ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray(Charsets.US_ASCII))
                putInt((36 + dataLen).coerceAtMost(0xFFFFFFFFL).toInt())
                put("WAVE".toByteArray(Charsets.US_ASCII))
                put("fmt ".toByteArray(Charsets.US_ASCII))
                putInt(16)
                putShort(1) // PCM
                putShort(1) // mono
                putInt(sampleRate)
                putInt(byteRate)
                putShort(BYTES_PER_SAMPLE.toShort()) // block align
                putShort(16) // bits per sample
                put("data".toByteArray(Charsets.US_ASCII))
                putInt(dataLen.coerceAtMost(0xFFFFFFFFL).toInt())
            }.array()
        }

        /**
         * Perbaiki header WAV yang tidak sempat ditutup (mis. proses dimatikan). Mengembalikan durasi (ms),
         * atau null bila berkas tidak valid.
         */
        fun repair(file: File, sampleRate: Int = SAMPLE_RATE): Long? {
            if (!file.exists() || file.length() < HEADER_SIZE) return null
            val dataLen = file.length() - HEADER_SIZE
            RandomAccessFile(file, "rw").use { it.write(header(sampleRate, dataLen)) }
            return bytesToMs(dataLen, sampleRate)
        }
    }
}
