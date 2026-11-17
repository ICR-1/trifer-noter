package com.example.notetaker.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.IOException
import java.nio.ByteOrder

/** Berkas tidak berisi audio atau formatnya tidak didukung perangkat. */
class UnsupportedAudioException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Mendekode berkas audio apa pun yang didukung Android (mp3, m4a/aac, ogg/opus, wav, flac, atau trek audio
 * di video) menjadi PCM16 mono 16 kHz, dikirim bertahap lewat [decode] agar memori tetap kecil.
 */
class AudioFileDecoder(private val context: Context) {

    data class Info(val durationMs: Long, val sampleRate: Int, val channels: Int, val mime: String)

    /** Baca metadata tanpa mendekode. [Info.durationMs] bernilai -1 bila tidak diketahui. */
    fun probe(uri: Uri): Info {
        val extractor = openExtractor(uri)
        try {
            val format = extractor.getTrackFormat(audioTrack(extractor))
            return Info(
                durationMs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) / 1000 else -1,
                sampleRate = format.intOrDefault(MediaFormat.KEY_SAMPLE_RATE, 0),
                channels = format.intOrDefault(MediaFormat.KEY_CHANNEL_COUNT, 0),
                mime = format.getString(MediaFormat.KEY_MIME) ?: "",
            )
        } finally {
            extractor.release()
        }
    }

    /**
     * Dekode seluruh berkas. [sink] menerima blok PCM16 mono 16 kHz. [onProgress] menerima 0..1 bila durasi
     * diketahui. Berhenti lebih awal bila [isActive] mengembalikan false.
     */
    suspend fun decode(
        uri: Uri,
        isActive: () -> Boolean,
        onProgress: (Float) -> Unit,
        sink: suspend (ShortArray, Int) -> Unit,
    ) {
        val extractor = openExtractor(uri)
        var codec: MediaCodec? = null
        try {
            val trackIndex = audioTrack(extractor)
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: throw UnsupportedAudioException("Tipe audio tidak dikenal")
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else -1L

            codec = try {
                MediaCodec.createDecoderByType(mime).also {
                    it.configure(format, null, null, 0)
                    it.start()
                }
            } catch (e: Exception) {
                throw UnsupportedAudioException("Format audio tidak didukung ($mime)", e)
            }

            var srcRate = format.intOrDefault(MediaFormat.KEY_SAMPLE_RATE, 16_000)
            var channels = format.intOrDefault(MediaFormat.KEY_CHANNEL_COUNT, 1).coerceAtLeast(1)
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            var resampler = Resampler(srcRate, TARGET_RATE)

            val info = MediaCodec.BufferInfo()
            val outChunk = ShortArray(CHUNK_SAMPLES)
            var outFill = 0
            var monoBuf = FloatArray(0)
            var resBuf = FloatArray(0)
            var inputDone = false
            var outputDone = false
            var lastProgress = -1f

            while (!outputDone && isActive()) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        val newRate = f.intOrDefault(MediaFormat.KEY_SAMPLE_RATE, srcRate)
                        channels = f.intOrDefault(MediaFormat.KEY_CHANNEL_COUNT, channels).coerceAtLeast(1)
                        pcmEncoding = f.intOrDefault(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                        if (newRate != srcRate) {
                            srcRate = newRate
                            resampler = Resampler(srcRate, TARGET_RATE)
                        }
                    }
                    outIdx >= 0 -> {
                        if (info.size > 0) {
                            val buf = codec.getOutputBuffer(outIdx)!!
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            buf.order(ByteOrder.nativeOrder())

                            val frames: Int
                            if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                                val fb = buf.asFloatBuffer()
                                frames = fb.remaining() / channels
                                if (monoBuf.size < frames) monoBuf = FloatArray(frames)
                                for (i in 0 until frames) {
                                    var sum = 0f
                                    for (c in 0 until channels) sum += fb.get()
                                    monoBuf[i] = sum / channels
                                }
                            } else {
                                val sb = buf.asShortBuffer()
                                frames = sb.remaining() / channels
                                if (monoBuf.size < frames) monoBuf = FloatArray(frames)
                                for (i in 0 until frames) {
                                    var sum = 0
                                    for (c in 0 until channels) sum += sb.get()
                                    monoBuf[i] = sum / (32768f * channels)
                                }
                            }

                            val need = resampler.maxOutputFor(frames)
                            if (resBuf.size < need) resBuf = FloatArray(need)
                            val produced = resampler.process(monoBuf, frames, resBuf)
                            for (i in 0 until produced) {
                                val v = (resBuf[i] * 32768f).toInt().coerceIn(-32768, 32767)
                                outChunk[outFill++] = v.toShort()
                                if (outFill == outChunk.size) {
                                    sink(outChunk, outFill)
                                    outFill = 0
                                }
                            }

                            if (durationUs > 0) {
                                val p = (info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f)
                                if (p - lastProgress >= 0.005f) {
                                    lastProgress = p
                                    onProgress(p)
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
            if (outFill > 0 && isActive()) sink(outChunk, outFill)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    private fun openExtractor(uri: Uri): MediaExtractor {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
        } catch (e: Exception) {
            extractor.release()
            throw UnsupportedAudioException("Berkas tidak dapat dibuka sebagai audio", e)
        }
        return extractor
    }

    private fun audioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
            if (mime != null && mime.startsWith("audio/")) return i
        }
        throw UnsupportedAudioException("Berkas ini tidak berisi trek audio")
    }

    private fun MediaFormat.intOrDefault(key: String, default: Int): Int =
        if (containsKey(key)) getInteger(key) else default

    companion object {
        const val TARGET_RATE = 16_000
        private const val CHUNK_SAMPLES = 4_096 // ±256 ms
        private const val TIMEOUT_US = 10_000L
    }
}
