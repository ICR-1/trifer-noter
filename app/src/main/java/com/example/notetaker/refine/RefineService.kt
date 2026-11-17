package com.example.notetaker.refine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.notetaker.MainActivity
import com.example.notetaker.NoterApp
import com.example.notetaker.R
import com.example.notetaker.asr.ChunkPlanner
import com.example.notetaker.asr.HallucinationFilter
import com.example.notetaker.asr.WhisperEngine
import com.example.notetaker.asr.WhisperSupport
import com.example.notetaker.data.db.RecordingStatus
import com.example.notetaker.data.db.Segment
import com.example.notetaker.importer.ImportController
import com.example.notetaker.importer.JobKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Fitur "Rapikan": memproses ulang audio tersimpan dengan Whisper medium (lebih akurat, bertanda baca) dan
 * mengganti transkrip live. Audio dipotong di jeda ([ChunkPlanner]) karena Whisper kehilangan teks bila diberi
 * aliran panjang. Berjalan sebagai foreground service (dataSync), beberapa catatan diproses berurutan.
 */
class RefineService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val queue = Channel<Long>(Channel.UNLIMITED)
    private val pending = AtomicInteger(0)
    private val cancelled = ConcurrentHashMap.newKeySet<Long>()
    private val promptModes = ConcurrentHashMap<Long, PromptMode>()
    private var workerStarted = false
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile private var engine: WhisperEngine? = null

    private val container get() = (application as NoterApp).container
    private val db get() = container.database

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_REFINE -> {
                val id = intent.getLongExtra(EXTRA_ID, 0L)
                intent.getStringExtra(EXTRA_PROMPT_MODE)?.let { m ->
                    runCatching { PromptMode.valueOf(m) }.getOrNull()?.let { promptModes[id] = it }
                }
                if (id <= 0) {
                    if (pending.get() == 0) stopSelf()
                    return START_NOT_STICKY
                }
                pending.incrementAndGet()
                createChannel()
                ServiceCompat.startForeground(
                    this, NOTIF_ID, buildNotification(getString(R.string.refine_notif_preparing), -1, 0L),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
                acquireWakeLock()
                ImportController.set(id, 0f, JobKind.REFINE)
                queue.trySend(id)
                startWorker()
            }
            ACTION_CANCEL -> {
                val id = intent.getLongExtra(EXTRA_ID, 0L)
                cancelled.add(id)
                engine?.abort() // hentikan potongan yang sedang diproses
            }
            else -> if (pending.get() == 0) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startWorker() {
        if (workerStarted) return
        workerStarted = true
        scope.launch {
            for (id in queue) {
                try {
                    process(id)
                } finally {
                    if (pending.decrementAndGet() <= 0) {
                        releaseWakeLock()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- proses satu catatan

    private suspend fun process(id: Long) {
        val dao = db.recordingDao()
        val rec = dao.getById(id) ?: run { ImportController.clear(id); return }
        val prevStatus = rec.status
        val ctx = currentCoroutineContext()
        var error: String? = null
        var abortedByUser = false
        var replaced = false

        try {
            check(WhisperSupport.isSupported) { getString(R.string.refine_error_unsupported) }
            val store = container.whisperModel
            check(store.isInstalled()) { getString(R.string.refine_error_no_model) }
            val audio = rec.audioPath?.let(::File)?.takeIf { it.exists() }
                ?: error(getString(R.string.refine_error_no_audio))

            dao.update(rec.copy(status = RecordingStatus.MERAPIKAN.name, error = null))
            ImportController.set(id, 0f, JobKind.REFINE)

            val frameDb = withContext(Dispatchers.IO) { ChunkPlanner.frameDbFromWav(audio) }
            val mask = ChunkPlanner.speechMask(frameDb)
            val chunks = ChunkPlanner.plan(frameDb, maxChunkSec = MAX_CHUNK_SEC)
            val whisper = withContext(Dispatchers.IO) { WhisperEngine(store.modelFile.path) }
            engine = whisper

            val mode = promptModes[id] ?: DEFAULT_PROMPT_MODE
            val live = if (mode == PromptMode.NONE) emptyList() else {
                db.segmentDao().getAll(id).map { LiveSeg(it.startMs, it.endMs, it.text) }
            }
            Log.i(TAG, "mode petunjuk=$mode, segmen live=${live.size}")

            val fresh = ArrayList<Segment>()
            var lastNotify = 0L
            RandomAccessFile(audio, "r").use { raf ->
                for ((i, c) in chunks.withIndex()) {
                    if (!ctx.isActive || id in cancelled) {
                        abortedByUser = true
                        break
                    }
                    val startSample = ChunkPlanner.framesToSamples(c.startFrame)
                    val n = ChunkPlanner.framesToSamples(c.endFrame) - startSample
                    val samples = withContext(Dispatchers.IO) { readFloat(raf, startSample, n) }

                    // Whisper kadang melewatkan kalimat (awal, tengah, atau akhir) pada jendela berisi beberapa
                    // kalimat. Setelah satu potongan diproses, cari suara yang belum punya segmen (dari energi
                    // audio) dan proses ulang celahnya sebagai potongan kecil.
                    val covered = ArrayList<LongRange>()
                    val chunkMs = startSample * 1000L / 16_000
                    val prompt = RefinePrompt.build(live, chunkMs, chunkMs + n * 1000L / 16_000, mode)
                    runRange(raf, whisper, id, mask, startSample, startSample + n, covered, fresh, "chunk ${i + 1}/${chunks.size}", prompt)
                    val tried = HashSet<Pair<Int, Int>>()
                    for (pass in 1..MAX_GAP_PASSES) {
                        if (id in cancelled) break
                        val gaps = ChunkPlanner.findGaps(mask, c.startFrame, c.endFrame, covered)
                            .filter { tried.add(it.startFrame to it.endFrame) }
                        if (gaps.isEmpty()) break
                        for (g in gaps) {
                            if (id in cancelled) break
                            val gs = ChunkPlanner.framesToSamples(g.startFrame)
                            val ge = ChunkPlanner.framesToSamples(g.endFrame)
                            val gMs = gs * 1000L / 16_000
                            val gPrompt = RefinePrompt.build(live, gMs, ge * 1000L / 16_000, mode)
                            runRange(raf, whisper, id, mask, gs, ge, covered, fresh, "celah ${g.startFrame * 0.02f}s", gPrompt)
                        }
                    }
                    if (id in cancelled) abortedByUser = true
                    if (abortedByUser) break
                    val p = (i + 1).toFloat() / chunks.size
                    ImportController.set(id, p, JobKind.REFINE)
                    val now = System.nanoTime() / 1_000_000
                    if (now - lastNotify >= 1_000) {
                        lastNotify = now
                        notify(buildNotification(rec.title, (p * 100).toInt(), id))
                    }
                }
            }

            if (!abortedByUser) {
                if (fresh.isEmpty()) {
                    error = getString(R.string.refine_error_empty) // jangan hapus transkrip lama
                } else {
                    container.recordings.replaceTranscript(id, fresh)
                    replaced = true
                }
            }
        } catch (e: CancellationException) {
            abortedByUser = true
        } catch (t: Throwable) {
            error = t.message ?: t.javaClass.simpleName
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                engine?.let { runCatching { it.close() } }
                engine = null
                dao.getById(id)?.let { r ->
                    // Berhasil: SELESAI. Dibatalkan/gagal: kembali ke status semula, transkrip lama tetap utuh.
                    val status = if (replaced) RecordingStatus.SELESAI.name else prevStatus
                    dao.update(r.copy(status = status, error = if (replaced) null else error))
                }
                cancelled.remove(id)
                ImportController.clear(id)
            }
        }
    }

    /**
     * Transkripsi rentang sampel [fromSample, toSample) dan tambahkan segmen yang lolos penyaringan ke [fresh].
     * Mengembalikan jumlah segmen yang diterima.
     */
    private suspend fun runRange(
        raf: RandomAccessFile,
        whisper: WhisperEngine,
        id: Long,
        mask: BooleanArray,
        fromSample: Int,
        toSample: Int,
        covered: MutableList<LongRange>,
        fresh: MutableList<Segment>,
        label: String,
        prompt: String = "",
    ): Int {
        val len = toSample - fromSample
        if (len <= 0) return 0
        val part = withContext(Dispatchers.IO) { readFloat(raf, fromSample, len) }
        val segs = withContext(Dispatchers.Default) { whisper.transcribe(part, len, 0, prompt) }
        val baseMs = fromSample * 1000L / 16_000
        Log.i(TAG, "$label ${baseMs / 1000.0}s +${len / 16000.0}s -> ${segs.size} segmen")
        var accepted = 0
        for (s in segs) {
            val absStart = baseMs + s.startMs
            val absEnd = baseMs + s.endMs
            val voiced = ChunkPlanner.countSpeechFrames(
                mask, (absStart / ChunkPlanner.FRAME_MS).toInt(), (absEnd / ChunkPlanner.FRAME_MS).toInt() + 1,
            )
            val text = HallucinationFilter.clean(s.text)
            Log.i(TAG, "  seg %.1f-%.1f suara=%d | %s".format(absStart / 1000.0, absEnd / 1000.0, voiced, s.text))
            // Tanpa suara di rentang waktunya = halusinasi pada senyap/derau. Titik tengah yang sudah tercakup =
            // duplikat dari potongan sebelumnya.
            if (text.isBlank() || voiced < MIN_VOICED_FRAMES) continue
            val mid = (absStart + absEnd) / 2
            if (covered.any { mid in it }) continue
            fresh += Segment(recordingId = id, startMs = absStart, endMs = absEnd, text = text)
            covered += absStart..absEnd
            accepted++
        }
        return accepted
    }

    private fun readFloat(raf: RandomAccessFile, startSample: Int, n: Int): FloatArray {
        val bytes = ByteArray(n * 2)
        raf.seek(44L + startSample * 2L)
        raf.readFully(bytes)
        val sb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return FloatArray(n) { sb.get() / 32768f }
    }

    // ---------------------------------------------------------------- notifikasi & wake lock

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.refine_channel_name), NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) },
            )
        }
    }

    private fun notify(n: Notification) = getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n)

    private fun buildNotification(title: String, percent: Int, recordingId: Long): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_transcribing)
            .setContentTitle(getString(R.string.refine_notif_title))
            .setContentText(title)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setProgress(100, percent.coerceAtLeast(0), percent < 0)
        if (recordingId > 0) {
            val cancel = PendingIntent.getService(
                this, recordingId.toInt() + 100_000,
                Intent(this, RefineService::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_ID, recordingId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            b.addAction(0, getString(R.string.import_cancel), cancel)
        }
        return b.build()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "trifer:refine")
            .apply { acquire(6L * 60 * 60 * 1000) }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    override fun onDestroy() {
        releaseWakeLock()
        engine?.abort()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "refine"
        private const val NOTIF_ID = 1003
        /** Potongan maksimum untuk Whisper. Lebih pendek = lebih jarang berhenti dini. */
        private const val MAX_CHUNK_SEC = 20f
        private const val MAX_GAP_PASSES = 2

        /** Segmen dengan kurang dari 0,2 dtk suara di rentang waktunya dibuang. */
        private const val MIN_VOICED_FRAMES = 10

        private const val TAG = "Refine"

        const val ACTION_REFINE = "com.example.notetaker.action.REFINE"
        const val ACTION_CANCEL = "com.example.notetaker.action.REFINE_CANCEL"
        const val EXTRA_ID = "recording_id"
        const val EXTRA_PROMPT_MODE = "prompt_mode"

        /** Dipilih lewat pengukuran, lihat docs/benchmark.md §6f. */
        val DEFAULT_PROMPT_MODE = PromptMode.NONE

        fun enqueue(context: Context, recordingId: Long, promptMode: PromptMode? = null) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, RefineService::class.java)
                    .setAction(ACTION_REFINE)
                    .putExtra(EXTRA_ID, recordingId)
                    .apply { promptMode?.let { putExtra(EXTRA_PROMPT_MODE, it.name) } },
            )
        }

        fun cancel(context: Context, recordingId: Long) {
            context.startService(
                Intent(context, RefineService::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_ID, recordingId),
            )
        }
    }
}
