package com.nousfercorp.triferNoter.debug

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nousfercorp.triferNoter.asr.ModelState
import com.nousfercorp.triferNoter.data.db.Recording
import com.nousfercorp.triferNoter.data.db.RecordingStatus
import com.nousfercorp.triferNoter.importer.JobKind
import com.nousfercorp.triferNoter.importer.JobState
import com.nousfercorp.triferNoter.ui.home.HomeContent
import com.nousfercorp.triferNoter.ui.theme.TriferNoterTheme
import java.io.File

/**
 * Galeri tampilan (hanya build debug): merender layar dengan DATA CONTOH, tanpa menyentuh database atau rekaman
 * asli. Dipakai untuk menilai UI lewat tangkapan layar.
 *
 * `adb shell am start -n <paket>/.debug.UiGalleryActivity --es screen home --es dark 1`
 * screen: home | home_empty | home_model | home_download | record | record_paused | record_wait | about | detail | send
 */
class UiGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val screen = intent.getStringExtra("screen") ?: "home"
        val dark = intent.getStringExtra("dark") == "1"
        setContent {
            TriferNoterTheme(darkTheme = dark) { Gallery(screen) }
        }
        // `--es shot <nama>`: simpan jendela aplikasi ke files/debug/<nama>.png lewat PixelCopy. Hanya jendela
        // aplikasi ini yang disalin, jadi notifikasi, bayangan notifikasi, pop-up, dan bilah sistem TIDAK ikut.
        intent.getStringExtra("shot")?.let { name ->
            window.decorView.postDelayed({ capture(name) }, 1800)
        }
    }

    private fun capture(name: String) {
        val dir = File(filesDir, "debug").apply { mkdirs() }
        val v = window.decorView
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        PixelCopy.request(window, bmp, { result ->
            if (result == PixelCopy.SUCCESS) File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            File(dir, "$name.done").writeText(result.toString())
        }, Handler(Looper.getMainLooper()))
    }
}

private const val HOUR = 3_600_000L

@Composable
private fun Gallery(screen: String) {
    if (screen.startsWith("record")) {
        RecordGallery(screen)
        return
    }
    if (screen == "detail" || screen == "send") {
        DbGallery(screen)
        return
    }
    if (screen == "about") {
        com.nousfercorp.triferNoter.ui.about.AboutScreen(onBack = {}, modifier = Modifier.fillMaxSize())
        return
    }
    val now = System.currentTimeMillis()
    val sample = listOf(
        Recording(1, "Rapat koordinasi mingguan", now - 2 * HOUR, 5_401_000, RecordingStatus.SELESAI.name),
        Recording(2, "Kuliah Metodologi Penelitian", now - 5 * HOUR, 2_880_000, RecordingStatus.MERAPIKAN.name),
        Recording(3, "Seminar proposal tesis", now - 26 * HOUR, 1_153_000, RecordingStatus.MENTRANSKRIP.name),
        Recording(4, "Wawancara narasumber", now - 3 * 24 * HOUR, 612_000, RecordingStatus.GAGAL.name),
        Recording(5, "Presentasi proposal", now - 10 * 24 * HOUR, 3_420_000, RecordingStatus.SELESAI.name),
        Recording(6, "Diskusi kelompok", now - 40 * 24 * HOUR, 960_000, RecordingStatus.TERHENTI.name),
    )
    val empty = screen == "home_empty"
    val model: ModelState = when (screen) {
        "home_model" -> ModelState.Missing
        "home_download" -> ModelState.Downloading(120_000_000, 340_000_000)
        else -> ModelState.Ready
    }
    HomeContent(
        recordings = if (empty || screen == "home_model" || screen == "home_download") (if (empty) emptyList() else sample.take(3)) else sample,
        jobs = if (empty) emptyMap() else mapOf(2L to JobState(JobKind.REFINE, 0.42f)),
        isRecording = false,
        activeRecordingId = null,
        micDenied = false,
        modelState = if (empty) ModelState.Ready else model,
        onRecordClick = {}, onOpenSettings = {}, onOpenRecording = {}, onDownloadModel = {},
        onImportClick = {}, onOpenAbout = {}, onCancelImport = {}, onRequestDelete = {},
        modifier = Modifier.fillMaxSize(),
        nowMs = now,
    )
}

/** Layar rekam dengan keadaan contoh: record (merekam), record_paused (dijeda), record_wait (menunggu suara). */
@Composable
private fun RecordGallery(screen: String) {
    val levels = List(48) { i -> (0.18f + 0.55f * kotlin.math.abs(kotlin.math.sin(i * 0.55f)) * (0.5f + 0.5f * kotlin.math.sin(i * 0.17f)).coerceIn(0f, 1f)) }
    val lines = listOf(
        "Baik, kita mulai rapat koordinasi minggu ini dengan agenda pertama.",
        "Untuk anggaran kuartal depan, tim keuangan sudah menyiapkan dua skenario.",
        "Skenario pertama mengasumsikan pertumbuhan stabil di semua wilayah.",
    )
    val base = com.nousfercorp.triferNoter.recording.RecState(
        phase = com.nousfercorp.triferNoter.recording.Phase.RECORDING,
        elapsedMs = 1_523_000, level = 0.4f, bookmarkCount = 2,
        liveStatus = com.nousfercorp.triferNoter.recording.LiveStatus.ACTIVE,
        liveLines = lines, livePartial = "dan skenario kedua lebih konservatif karena",
    )
    val state = when (screen) {
        "record_paused" -> base.copy(phase = com.nousfercorp.triferNoter.recording.Phase.PAUSED, livePartial = "")
        "record_wait" -> base.copy(elapsedMs = 4_000, bookmarkCount = 0, liveLines = emptyList(), livePartial = "")
        else -> base
    }
    com.nousfercorp.triferNoter.ui.record.RecordContent(
        state = state, levels = if (screen == "record_wait") emptyList() else levels,
        onBookmark = {}, onPauseResume = {}, onStop = {}, modifier = Modifier.fillMaxSize(),
    )
}

/** Detail dan Kirim ke AI: memakai database SEMENTARA di memori berisi data contoh (tidak menyentuh data asli). */
@Composable
private fun DbGallery(screen: String) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val vmState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<com.nousfercorp.triferNoter.ui.detail.DetailViewModel?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val db = androidx.room.Room.inMemoryDatabaseBuilder(ctx.applicationContext, com.nousfercorp.triferNoter.data.db.AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val wav = File(ctx.cacheDir, "contoh.wav")
        if (!wav.exists()) {
            val data = ByteArray(16_000 * 2 * 2)
            val h = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            h.put("RIFF".toByteArray()).putInt(36 + data.size).put("WAVE".toByteArray()).put("fmt ".toByteArray())
            h.putInt(16).putShort(1).putShort(1).putInt(16_000).putInt(32_000).putShort(2).putShort(16)
            h.put("data".toByteArray()).putInt(data.size)
            wav.writeBytes(h.array() + data)
        }
        val now = System.currentTimeMillis()
        val id = db.recordingDao().insert(
            Recording(title = "Rapat koordinasi mingguan", createdAt = now - 2 * HOUR, durationMs = 5_401_000,
                status = RecordingStatus.SELESAI.name, audioPath = wav.absolutePath),
        )
        val lines = listOf(
            0L to "Baik, selamat pagi semuanya. Kita mulai rapat koordinasi minggu ini dengan agenda pertama.",
            9_000L to "Untuk anggaran kuartal depan, tim keuangan sudah menyiapkan dua skenario.",
            21_000L to "Skenario pertama mengasumsikan pertumbuhan stabil di semua wilayah.",
            34_000L to "Skenario kedua lebih konservatif karena ada ketidakpastian pasar.",
            47_000L to "Mohon masukan dari tiap divisi sebelum hari Jumat.",
            63_000L to "Baik Pak, dari sisi operasional kami butuh tambahan dua orang.",
            78_000L to "Itu akan kita bahas di agenda kedua soal rekrutmen.",
            92_000L to "Untuk jadwal peluncuran, target kita tetap akhir bulan depan.",
        )
        db.segmentDao().insertAll(lines.mapIndexed { i, (t, text) ->
            com.nousfercorp.triferNoter.data.db.Segment(recordingId = id, startMs = t, endMs = t + 8_000, text = text, edited = i == 2)
        })
        db.bookmarkDao().insert(com.nousfercorp.triferNoter.data.db.Bookmark(recordingId = id, timeMs = 312_000))
        db.bookmarkDao().insert(com.nousfercorp.triferNoter.data.db.Bookmark(recordingId = id, timeMs = 1_240_000))
        vmState.value = com.nousfercorp.triferNoter.ui.detail.DetailViewModel(com.nousfercorp.triferNoter.data.RecordingRepository(db), id)
    }
    vmState.value?.let { v ->
        if (screen == "detail") {
            com.nousfercorp.triferNoter.ui.detail.DetailScreen(
                viewModel = v, onBack = {}, whisperState = ModelState.Ready, onDownloadWhisper = {}, onRefine = {},
                onSendToAi = {}, modifier = Modifier.fillMaxSize(),
            )
        } else {
            com.nousfercorp.triferNoter.ui.send.SendToAiScreen(
                viewModel = v, prefs = com.nousfercorp.triferNoter.ai.AiPrefs(ctx), onBack = {}, modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
