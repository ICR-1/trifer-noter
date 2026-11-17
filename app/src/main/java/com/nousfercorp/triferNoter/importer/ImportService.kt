package com.nousfercorp.triferNoter.importer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import android.os.StatFs
import android.provider.OpenableColumns
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.nousfercorp.triferNoter.MainActivity
import com.nousfercorp.triferNoter.NoterApp
import com.nousfercorp.triferNoter.R
import com.nousfercorp.triferNoter.asr.AsrEvent
import com.nousfercorp.triferNoter.asr.StreamingAsr
import com.nousfercorp.triferNoter.audio.AudioFileDecoder
import com.nousfercorp.triferNoter.audio.UnsupportedAudioException
import com.nousfercorp.triferNoter.audio.WavWriter
import com.nousfercorp.triferNoter.data.db.Recording
import com.nousfercorp.triferNoter.data.db.RecordingStatus
import com.nousfercorp.triferNoter.data.db.Segment
import com.nousfercorp.triferNoter.recording.RecordingLimits
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Mengimpor berkas audio: dekode ke 16 kHz mono, simpan sebagai WAV (untuk diputar ulang), lalu transkripsi
 * dengan mesin live yang sama. Berjalan sebagai foreground service (dataSync) agar selamat bila aplikasi ditutup.
 * Beberapa berkas diproses berurutan.
 */
class ImportService : Service() {

    private data class ImportJob(val uri: Uri, val recordingId: Long)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val jobs = Channel<ImportJob>(Channel.UNLIMITED)
    private val pending = AtomicInteger(0)
    private val cancelled = ConcurrentHashMap.newKeySet<Long>()
    private var workerStarted = false
    private var wakeLock: PowerManager.WakeLock? = null

    private val container get() = (application as NoterApp).container
    private val db get() = container.database

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_IMPORT -> {
                val uri = intent.data ?: run { stopIfIdle(); return START_NOT_STICKY }
                pending.incrementAndGet()
                createChannel()
                ServiceCompat.startForeground(
                    this, NOTIF_ID, buildNotification(getString(R.string.import_notif_preparing), -1, 0L),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
                acquireWakeLock()
                scope.launch(Dispatchers.IO) {
                    val id = createRow(uri)
                    jobs.send(ImportJob(uri, id))
                }
                startWorker()
            }
            ACTION_CANCEL -> cancelled.add(intent.getLongExtra(EXTRA_ID, 0L))
            else -> stopIfIdle()
        }
        return START_NOT_STICKY
    }

    private fun stopIfIdle() {
        if (pending.get() == 0) stopSelf()
    }

    private fun startWorker() {
        if (workerStarted) return
        workerStarted = true
        scope.launch {
            for (job in jobs) {
                try {
                    process(job)
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

    private suspend fun createRow(uri: Uri): Long {
        val name = displayName(uri)
        val title = name?.substringBeforeLast('.', name)?.ifBlank { null } ?: getString(R.string.import_default_title)
        return db.recordingDao().insert(
            Recording(
                title = title,
                createdAt = System.currentTimeMillis(),
                status = RecordingStatus.MENTRANSKRIP.name,
            ),
        ).also { ImportController.set(it, 0f) }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment

    // ---------------------------------------------------------------- proses satu berkas

    private suspend fun process(job: ImportJob) {
        val id = job.recordingId
        val dao = db.recordingDao()
        val rec = dao.getById(id) ?: run { ImportController.clear(id); return }
        if (id in cancelled) { // dibatalkan sebelum sempat mulai
            dao.delete(rec)
            cancelled.remove(id)
            ImportController.clear(id)
            return
        }

        val ctx = currentCoroutineContext()
        var writer: WavWriter? = null
        var asr: StreamingAsr? = null
        var error: String? = null
        var segmentCount = 0
        var lastNotify = 0L
        var samplesSincePatch = 0L

        suspend fun save(e: AsrEvent) {
            if (e is AsrEvent.Final) {
                db.segmentDao().insert(Segment(recordingId = id, startMs = e.startMs, endMs = e.endMs, text = e.text))
                segmentCount++
            }
        }

        try {
            val store = container.modelStore
            if (!store.isInstalled()) throw IllegalStateException(getString(R.string.import_error_no_model))

            val decoder = AudioFileDecoder(this)
            val info = decoder.probe(job.uri)
            if (info.durationMs > 0) {
                val need = info.durationMs * BYTES_PER_MS + RecordingLimits.MIN_FREE_BYTES
                if (StatFs(filesDir.absolutePath).availableBytes < need) {
                    throw IllegalStateException(getString(R.string.import_error_storage))
                }
                dao.getById(id)?.let { dao.update(it.copy(durationMs = info.durationMs)) }
            }

            val file = File(File(filesDir, "recordings").apply { mkdirs() }, "$id.wav")
            writer = WavWriter(file)
            dao.getById(id)?.let { dao.update(it.copy(audioPath = file.absolutePath)) }
            asr = withContext(Dispatchers.IO) { StreamingAsr(store.dir) }

            val w = writer
            val engine = asr
            decoder.decode(
                uri = job.uri,
                isActive = { ctx.isActive && id !in cancelled },
                onProgress = { p ->
                    ImportController.set(id, p)
                    val now = System.nanoTime() / 1_000_000
                    if (now - lastNotify >= 1_000) {
                        lastNotify = now
                        notify(buildNotification(rec.title, (p * 100).toInt(), id))
                    }
                },
            ) { buf, n ->
                w.write(buf, n)
                samplesSincePatch += n
                if (samplesSincePatch >= PATCH_EVERY_SAMPLES) {
                    w.patchHeader()
                    samplesSincePatch = 0
                }
                // Umpankan per 100 ms seperti rekaman langsung. Blok yang lebih besar membuat deteksi jeda pendek
                // terlewat (terukur: WER 10,7% vs 6,7%).
                var o = 0
                while (o < n) {
                    val m = minOf(ASR_BLOCK_SAMPLES, n - o)
                    val sub = if (o == 0 && m == n) buf else buf.copyOfRange(o, o + m)
                    for (e in engine.accept(sub, m)) save(e)
                    o += m
                }
            }

            if (id !in cancelled) for (e in engine.finish()) save(e)
        } catch (e: CancellationException) {
            cancelled.add(id)
        } catch (e: UnsupportedAudioException) {
            error = e.message
        } catch (t: Throwable) {
            error = t.message ?: t.javaClass.simpleName
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                val duration = writer?.durationMs ?: 0L
                runCatching { asr?.close() }
                runCatching { writer?.close() }
                val wasCancelled = id in cancelled
                db.recordingDao().getById(id)?.let { r ->
                    val status = when {
                        wasCancelled -> RecordingStatus.TERHENTI
                        error != null && segmentCount == 0 -> RecordingStatus.GAGAL
                        error != null -> RecordingStatus.TERHENTI
                        else -> RecordingStatus.SELESAI
                    }
                    val note = when {
                        error != null -> error
                        !wasCancelled && segmentCount == 0 -> getString(R.string.import_no_speech)
                        else -> null
                    }
                    db.recordingDao().update(
                        r.copy(durationMs = if (duration > 0) duration else r.durationMs, status = status.name, error = note),
                    )
                }
                cancelled.remove(id)
                ImportController.clear(id)
            }
        }
    }

    // ---------------------------------------------------------------- notifikasi & wake lock

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.import_channel_name), NotificationManager.IMPORTANCE_LOW)
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
            .setContentTitle(getString(R.string.import_notif_title))
            .setContentText(title)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setProgress(100, percent.coerceAtLeast(0), percent < 0)
        if (recordingId > 0) {
            val cancel = PendingIntent.getService(
                this, recordingId.toInt(),
                Intent(this, ImportService::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_ID, recordingId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            b.addAction(0, getString(R.string.import_cancel), cancel)
        }
        return b.build()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "trifer:import")
            .apply { acquire(6L * 60 * 60 * 1000) }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "import"
        private const val NOTIF_ID = 1002
        private const val BYTES_PER_MS = 32L // 16 kHz x 2 byte
        private const val ASR_BLOCK_SAMPLES = 1_600 // 100 ms
        private const val PATCH_EVERY_SAMPLES = 16_000L * 30

        const val ACTION_IMPORT = "com.nousfercorp.triferNoter.action.IMPORT"
        const val ACTION_CANCEL = "com.nousfercorp.triferNoter.action.IMPORT_CANCEL"
        const val EXTRA_ID = "recording_id"

        fun enqueue(context: Context, uri: Uri) {
            val i = Intent(context, ImportService::class.java)
                .setAction(ACTION_IMPORT)
                .setData(uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            i.clipData = ClipData.newRawUri("", uri)
            ContextCompat.startForegroundService(context, i)
        }

        fun cancel(context: Context, recordingId: Long) {
            context.startService(
                Intent(context, ImportService::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_ID, recordingId),
            )
        }
    }
}
