package com.example.notetaker.asr

import com.example.notetaker.data.db.AppDatabase
import com.example.notetaker.data.db.Segment
import com.example.notetaker.recording.LiveStatus
import com.example.notetaker.recording.RecordingController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Menjalankan [StreamingAsr] di luar thread perekaman. Audio masuk lewat antrean tak terbatas sehingga
 * perekaman TIDAK PERNAH menunggu mesin (PRD §8.2). Jika mesin tertinggal, antrean dikejar kemudian.
 */
class LiveTranscriber(
    private val modelDir: File,
    private val recordingId: Long,
    private val db: AppDatabase,
    private val scope: CoroutineScope,
) {
    private val queue = Channel<ShortArray>(Channel.UNLIMITED)
    private val queuedSamples = AtomicLong(0)
    private var job: Job? = null

    fun start() {
        job = scope.launch(Dispatchers.Default) { run() }
    }

    /** Dipanggil dari thread perekaman. Menyalin buffer dan segera kembali. */
    fun feed(buffer: ShortArray, n: Int) {
        queuedSamples.addAndGet(n.toLong())
        queue.trySend(buffer.copyOf(n))
    }

    /** Tutup antrean, tunggu sisa diproses (maks. [timeoutMs]). Audio yang tersisa tetap aman di berkas WAV. */
    suspend fun finish(timeoutMs: Long = 120_000) {
        queue.close()
        val done = withTimeoutOrNull(timeoutMs) { job?.join() }
        if (done == null) job?.cancel()
    }

    private suspend fun run() {
        RecordingController.update { it.copy(liveStatus = LiveStatus.LOADING) }
        val asr = try {
            StreamingAsr(modelDir)
        } catch (t: Throwable) { // termasuk UnsatisfiedLinkError bila pustaka native gagal dimuat
            RecordingController.update {
                it.copy(liveStatus = LiveStatus.ERROR, liveError = t.message ?: t.javaClass.simpleName)
            }
            for (ignored in queue) queuedSamples.addAndGet(-ignored.size.toLong())
            return
        }
        RecordingController.update { it.copy(liveStatus = LiveStatus.ACTIVE, liveError = null) }

        try {
            var lastLagUpdate = 0L
            for (chunk in queue) {
                queuedSamples.addAndGet(-chunk.size.toLong())
                handle(asr.accept(chunk, chunk.size))

                val now = System.nanoTime() / 1_000_000
                if (now - lastLagUpdate >= 500) {
                    lastLagUpdate = now
                    val lag = queuedSamples.get() * 1000L / StreamingAsr.SAMPLE_RATE
                    RecordingController.update { it.copy(liveLagMs = lag) }
                }
            }
            handle(asr.finish())
        } catch (t: Throwable) {
            RecordingController.update {
                it.copy(liveStatus = LiveStatus.ERROR, liveError = t.message ?: t.javaClass.simpleName)
            }
        } finally {
            asr.close()
            RecordingController.update { it.copy(liveLagMs = 0, livePartial = "") }
        }
    }

    private suspend fun handle(events: List<AsrEvent>) {
        for (e in events) {
            when (e) {
                is AsrEvent.Partial -> RecordingController.update { it.copy(livePartial = e.text) }
                is AsrEvent.Final -> {
                    db.segmentDao().insert(
                        Segment(recordingId = recordingId, startMs = e.startMs, endMs = e.endMs, text = e.text),
                    )
                    RecordingController.addLiveLine(e.text)
                }
            }
        }
    }
}
