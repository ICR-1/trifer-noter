package com.nousfercorp.triferNoter.recording

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.StatFs
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.nousfercorp.triferNoter.MainActivity
import com.nousfercorp.triferNoter.NoterApp
import com.nousfercorp.triferNoter.R
import com.nousfercorp.triferNoter.asr.LiveTranscriber
import com.nousfercorp.triferNoter.audio.WavWriter
import com.nousfercorp.triferNoter.data.db.Bookmark
import com.nousfercorp.triferNoter.data.db.Recording
import com.nousfercorp.triferNoter.data.db.RecordingStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Perekaman audio di foreground service (tipe `microphone`). Audio ditulis ke WAV 16 kHz mono
 * di penyimpanan privat aplikasi. Pipeline transkripsi (M2b) akan membaca dari aliran yang sama.
 */
class RecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    @Volatile private var stopRequested = false
    @Volatile private var pauseRequested = false
    @Volatile private var pausedBySystem = false

    private var recordingId = 0L
    private var transcriber: LiveTranscriber? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var focusRequest: AudioFocusRequest? = null
    @Volatile private var elapsedMs = 0L
    @Volatile private var paused = false

    private val db get() = (application as NoterApp).container.database

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_PAUSE -> {
                pausedBySystem = false
                pauseRequested = true
            }
            ACTION_RESUME -> {
                pausedBySystem = false
                pauseRequested = false
            }
            ACTION_STOP -> stopRequested = true
            ACTION_BOOKMARK -> addBookmark()
            else -> if (!started) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (started) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            RecordingController.reset(error = getString(R.string.rec_error_permission))
            stopSelf()
            return
        }
        started = true
        createChannel()
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            buildNotification(Phase.RECORDING, 0, false),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
        scope.launch(Dispatchers.IO) { runRecording() }
    }

    // ---------------------------------------------------------------- perekaman

    private suspend fun runRecording() {
        var writer: WavWriter? = null
        var recorder: AudioRecord? = null
        var error: String? = null
        try {
            val free = StatFs(filesDir.absolutePath).availableBytes
            if (free < RecordingLimits.MIN_FREE_BYTES) {
                error = getString(R.string.rec_error_storage)
                return
            }

            val dir = File(filesDir, "recordings").apply { mkdirs() }
            val now = System.currentTimeMillis()
            val title = "Rekaman " + SimpleDateFormat("d MMM yyyy HH.mm", Locale.forLanguageTag("id-ID")).format(Date(now))
            recordingId = db.recordingDao().insert(Recording(title = title, createdAt = now))
            val file = File(dir, "$recordingId.wav")
            db.recordingDao().getById(recordingId)?.let {
                db.recordingDao().update(it.copy(audioPath = file.absolutePath))
            }

            writer = WavWriter(file)
            recorder = createRecorder() ?: run {
                error = getString(R.string.rec_error_mic)
                return
            }

            requestFocus()
            acquireWakeLock()
            recorder.startRecording()
            RecordingController.update {
                RecState(phase = Phase.RECORDING, recordingId = recordingId)
            }

            // Transkrip live berjalan di thread terpisah; perekaman tidak pernah menunggunya.
            val store = (application as NoterApp).container.modelStore
            if (store.isInstalled()) {
                transcriber = LiveTranscriber(store.dir, recordingId, db, scope).also { it.start() }
            } else {
                RecordingController.update { it.copy(liveStatus = LiveStatus.OFF_NO_MODEL) }
            }

            val buffer = ShortArray(SAMPLE_RATE / 10) // 100 ms
            var lastUi = 0L
            var lastPatch = 0L
            var warned = false

            while (scope.isActive && !stopRequested) {
                // Terapkan permintaan jeda/lanjut.
                if (pauseRequested && !paused) {
                    recorder.stop()
                    paused = true
                    publish(Phase.PAUSED, level = 0f, force = true)
                } else if (!pauseRequested && paused) {
                    recorder.startRecording()
                    paused = false
                    publish(Phase.RECORDING, level = 0f, force = true)
                }
                if (paused) {
                    delay(100)
                    continue
                }

                val n = recorder.read(buffer, 0, buffer.size)
                if (n < 0) {
                    error = getString(R.string.rec_error_read)
                    break
                }
                if (n == 0) continue
                writer.write(buffer, n)
                transcriber?.feed(buffer, n)
                elapsedMs = writer.durationMs

                val tick = System.nanoTime() / 1_000_000
                if (tick - lastPatch >= HEADER_PATCH_MS) {
                    writer.patchHeader()
                    lastPatch = tick
                }
                if (tick - lastUi >= UI_UPDATE_MS) {
                    var peak = 0
                    for (i in 0 until n) peak = maxOf(peak, abs(buffer[i].toInt()))
                    publish(Phase.RECORDING, level = peak / 32768f)
                    lastUi = tick
                }
                if (!warned && RecordingLimits.shouldWarn(elapsedMs)) {
                    warned = true
                    RecordingController.update { it.copy(nearLimit = true) }
                    vibrate()
                    notify(buildNotification(Phase.RECORDING, elapsedMs, true))
                }
                if (RecordingLimits.shouldStop(elapsedMs)) break
            }
        } catch (e: SecurityException) {
            error = getString(R.string.rec_error_permission)
        } catch (e: Exception) {
            error = e.message ?: getString(R.string.rec_error_read)
        } finally {
            finish(recorder, writer, error)
        }
    }

    private fun createRecorder(): AudioRecord? {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) return null
        val size = maxOf(minBuf * 2, SAMPLE_RATE * 2) // ≥ 1 detik
        @Suppress("MissingPermission") // izin dicek di startRecording()
        val rec = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(size)
            .build()
        return if (rec.state == AudioRecord.STATE_INITIALIZED) rec else {
            rec.release()
            null
        }
    }

    private suspend fun finish(recorder: AudioRecord?, writer: WavWriter?, error: String?) {
        withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
            try {
                if (!paused) recorder?.stop()
            } catch (_: IllegalStateException) {
            }
            recorder?.release()
            val duration = writer?.durationMs ?: 0L
            try {
                writer?.close()
            } catch (_: Exception) {
            }
            abandonFocus()
            releaseWakeLock()

            val id = recordingId
            val live = transcriber
            if (live != null && id > 0) {
                // Selesaikan sisa teks yang masih diproses sebelum menutup catatan.
                RecordingController.update { it.copy(phase = Phase.FINALIZING, livePartial = "") }
                db.recordingDao().getById(id)?.let {
                    db.recordingDao().update(it.copy(durationMs = duration, status = RecordingStatus.FINALISASI.name))
                }
                live.finish()
                transcriber = null
            }
            if (id > 0) {
                db.recordingDao().getById(id)?.let { rec ->
                    val status = if (error != null && duration == 0L) RecordingStatus.GAGAL else RecordingStatus.SELESAI
                    db.recordingDao().update(rec.copy(durationMs = duration, status = status.name, error = error))
                }
            }
            RecordingController.reset(finishedId = id.takeIf { it > 0 && duration > 0 }, error = error)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun addBookmark() {
        val id = recordingId
        if (id <= 0) return
        val at = elapsedMs
        scope.launch(Dispatchers.IO) {
            db.bookmarkDao().insert(Bookmark(recordingId = id, timeMs = at))
            RecordingController.update { it.copy(bookmarkCount = it.bookmarkCount + 1) }
        }
    }

    // ---------------------------------------------------------------- status & notifikasi

    private fun publish(phase: Phase, level: Float, force: Boolean = false) {
        RecordingController.update {
            it.copy(
                phase = phase,
                elapsedMs = elapsedMs,
                level = level,
                pausedBySystem = phase == Phase.PAUSED && pausedBySystem,
            )
        }
        // Notifikasi memakai chronometer sistem, jadi hanya diperbarui saat status berubah.
        if (force) {
            notify(buildNotification(phase, elapsedMs, RecordingController.state.value.nearLimit))
        }
    }

    private fun notify(n: Notification) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n)
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.rec_channel_name), NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) },
            )
        }
    }

    private fun action(code: Int, action: String): PendingIntent = PendingIntent.getService(
        this, code, Intent(this, RecordingService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun buildNotification(phase: Phase, elapsed: Long, nearLimit: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val recording = phase == Phase.RECORDING
        val b = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_recording)
            .setContentTitle(getString(if (recording) R.string.rec_notif_recording else R.string.rec_notif_paused))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (recording) {
            b.setUsesChronometer(true).setWhen(System.currentTimeMillis() - elapsed)
            b.addAction(0, getString(R.string.rec_pause), action(1, ACTION_PAUSE))
        } else {
            b.setUsesChronometer(false).setShowWhen(false)
            b.addAction(0, getString(R.string.rec_resume), action(2, ACTION_RESUME))
        }
        b.addAction(0, getString(R.string.rec_stop), action(3, ACTION_STOP))
        if (nearLimit) {
            b.setContentText(getString(R.string.rec_notif_near_limit, RecordingLimits.remainingMs(elapsed) / 60_000))
        }
        return b.build()
    }

    private fun vibrate() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
        }
        v?.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    // ---------------------------------------------------------------- audio focus & wake lock

    private fun requestFocus() {
        val am = getSystemService(AudioManager::class.java)
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener { change ->
                // Panggilan masuk atau aplikasi lain mengambil fokus: jeda otomatis, pengguna melanjutkan sendiri.
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    pausedBySystem = true
                    pauseRequested = true
                }
            }
            .build()
        focusRequest = req
        am.requestAudioFocus(req)
    }

    private fun abandonFocus() {
        focusRequest?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "trifer:recording").apply {
            acquire(RecordingLimits.MAX_MS + 15 * 60_000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val SAMPLE_RATE = WavWriter.SAMPLE_RATE
        private const val CHANNEL_ID = "recording"
        private const val NOTIF_ID = 1001
        private const val UI_UPDATE_MS = 200L
        private const val HEADER_PATCH_MS = 10_000L

        const val ACTION_START = "com.nousfercorp.triferNoter.action.START"
        const val ACTION_PAUSE = "com.nousfercorp.triferNoter.action.PAUSE"
        const val ACTION_RESUME = "com.nousfercorp.triferNoter.action.RESUME"
        const val ACTION_STOP = "com.nousfercorp.triferNoter.action.STOP"
        const val ACTION_BOOKMARK = "com.nousfercorp.triferNoter.action.BOOKMARK"

        fun send(context: Context, action: String) {
            val i = Intent(context, RecordingService::class.java).setAction(action)
            if (action == ACTION_START) ContextCompat.startForegroundService(context, i) else context.startService(i)
        }
    }
}
