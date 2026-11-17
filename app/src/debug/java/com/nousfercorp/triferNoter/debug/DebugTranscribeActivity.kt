package com.nousfercorp.triferNoter.debug

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import com.nousfercorp.triferNoter.NoterApp
import com.nousfercorp.triferNoter.asr.AsrEvent
import com.nousfercorp.triferNoter.asr.EndpointPolicy
import com.nousfercorp.triferNoter.asr.StreamingAsr
import com.nousfercorp.triferNoter.importer.ImportService
import com.nousfercorp.triferNoter.util.TextMetrics
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Alat uji (debug saja). Memakai [StreamingAsr] persis seperti layanan rekam, tetapi dari berkas WAV.
 *
 * Berkas di `files/debug/`: `in.wav` (PCM16 mono 16 kHz) dan opsional `ref.txt` (teks acuan).
 * Jalankan: `adb shell am start -n com.nousfercorp.triferNoter/.debug.DebugTranscribeActivity`
 * Hasil: logcat tag `BENCH` dan `files/debug/result.txt`.
 */
class DebugTranscribeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Uji impor: `--es import <nama berkas di files/debug>` memicu ImportService seperti pemilih berkas.
        intent.getStringExtra("import")?.let { name ->
            val f = File(File(filesDir, "debug"), name)
            ImportService.enqueue(this, android.net.Uri.fromFile(f))
            finish()
            return
        }
        // Uji Rapikan: `--es refine <id>` memicu RefineService seperti tombol "Rapikan sekarang".
        intent.getStringExtra("refine")?.toLongOrNull()?.let { id ->
            val mode = intent.getStringExtra("mode")?.let { runCatching { com.nousfercorp.triferNoter.refine.PromptMode.valueOf(it) }.getOrNull() }
            com.nousfercorp.triferNoter.refine.RefineService.enqueue(this, id, mode)
            finish()
            return
        }
        // Uji deteksi aplikasi AI: `--es aiapps 1` menulis files/debug/aiapps.txt (memakai konteks dan manifest asli).
        if (intent.getStringExtra("aiapps") != null) {
            val (ai, others) = com.nousfercorp.triferNoter.ai.AiApps.detect(this, emptySet())
            File(File(filesDir, "debug").apply { mkdirs() }, "aiapps.txt").writeText(
                "AI (${ai.size}):\n" + ai.joinToString("\n") { "  ${it.packageName}|${it.label}|ikon=${it.icon != null}" } +
                    "\nLAIN (${others.size}):\n" + others.joinToString("\n") { "  ${it.packageName}|${it.label}" },
            )
            finish()
            return
        }
        // Uji Kirim ke AI: `--es aiexport <idCatatan> [--es aiwords N] [--es aitemplate lecture|meeting|actions|none]`.
        intent.getStringExtra("aiexport")?.toLongOrNull()?.let { id ->
            val container = (application as NoterApp).container
            val words = intent.getStringExtra("aiwords")?.toIntOrNull()
            val template = com.nousfercorp.triferNoter.ai.AiTemplate.fromId(
                when (intent.getStringExtra("aitemplate")) { "lecture" -> "kuliah"; "meeting" -> "rapat"; "actions" -> "aksi"; "none" -> "polos"; else -> "kuliah" },
            )
            runBlocking(Dispatchers.IO) {
                val rec = container.database.recordingDao().getById(id)
                val segs = container.database.segmentDao().getAll(id)
                if (rec != null) {
                    val dir = File(filesDir, "debug").apply { mkdirs() }
                    val parts = com.nousfercorp.triferNoter.ai.AiExportBuilder.build(
                        rec.title, rec.createdAt, rec.durationMs, segs,
                        com.nousfercorp.triferNoter.ai.AiExportOptions(template, "", false, words),
                    )
                    parts.forEach { File(dir, "ai_${id}_${it.index}.txt").writeText(it.text, Charsets.UTF_8) }
                }
            }
            finish()
            return
        }
        // Uji ubah teks: `--es edit <idSegmen>@<teks dengan_garis_bawah_sebagai_spasi>` memanggil jalur yang sama
        // dengan tombol Simpan di dialog ubah teks.
        intent.getStringExtra("edit")?.let { spec ->
            val (segId, text) = spec.split("@", limit = 2)
            val container = (application as NoterApp).container
            runBlocking(Dispatchers.IO) { container.recordings.editSegment(segId.toLong(), text.replace('_', ' ')) }
            finish()
            return
        }
        // Bersihkan catatan uji: `--es purge judul1,judul2` menghapus catatan (dan berkas audionya) berjudul itu.
        intent.getStringExtra("purge")?.let { csv ->
            val titles = csv.split(",").map { it.trim() }.toSet()
            val container = (application as NoterApp).container
            runBlocking(Dispatchers.IO) {
                container.database.recordingDao().observeAll().first()
                    .filter { it.title in titles }
                    .forEach { container.recordings.delete(it) }
            }
            finish()
            return
        }
        setContent { Text("Uji transkripsi berjalan, lihat logcat (tag BENCH)…") }
        val wavName = intent.getStringExtra("wav") ?: "in.wav"
        val d = EndpointPolicy.DEFAULT
        val policy = EndpointPolicy(
            basePauseSec = intent.getStringExtra("base")?.toFloatOrNull() ?: d.basePauseSec,
            midAfterSec = intent.getStringExtra("midAfter")?.toFloatOrNull() ?: d.midAfterSec,
            midPauseSec = intent.getStringExtra("mid")?.toFloatOrNull() ?: d.midPauseSec,
            longAfterSec = intent.getStringExtra("longAfter")?.toFloatOrNull() ?: d.longAfterSec,
            longPauseSec = intent.getStringExtra("long")?.toFloatOrNull() ?: d.longPauseSec,
            urgentAfterSec = intent.getStringExtra("urgentAfter")?.toFloatOrNull() ?: d.urgentAfterSec,
            urgentPauseSec = intent.getStringExtra("urgent")?.toFloatOrNull() ?: d.urgentPauseSec,
            hardMaxSec = intent.getStringExtra("hard")?.toFloatOrNull() ?: d.hardMaxSec,
        )
        val skipOff = intent.getStringExtra("skipOff")?.toFloatOrNull() ?: StreamingAsr.DEFAULT_SKIP_OFFSET_SEC
        val refName = intent.getStringExtra("ref") ?: "ref.txt"
        Thread { run(wavName, policy, skipOff, refName) }.start()
    }

    private fun run(wavName: String, policy: EndpointPolicy, skipOff: Float, refName: String) {
        val dir = File(filesDir, "debug")
        val out = File(dir, "result.txt")
        fun log(msg: String) {
            Log.i(TAG, msg)
            out.appendText(msg + "\n")
        }
        out.delete()
        try {
            val modelStore = (application as NoterApp).container.modelStore
            check(modelStore.isInstalled()) { "model belum terpasang di ${modelStore.dir}" }
            val wav = File(dir, wavName)
            val ref = File(dir, refName).takeIf { it.exists() }?.readText()

            val samples = readPcm16(wav)
            val audioSec = samples.size / 16000.0
            log("BENCH_START file=${wav.name} audio=%.1fs policy=$policy skipOff=$skipOff".format(audioSec))

            val t0 = System.nanoTime()
            val asr = StreamingAsr(modelStore.dir, policy = policy, overlapSkipOffsetSec = skipOff)
            asr.debugLog = { log("BENCH_DBG " + it) }
            val loadSec = (System.nanoTime() - t0) / 1e9
            val finals = ArrayList<AsrEvent.Final>()
            var partials = 0

            val p0 = System.nanoTime()
            val chunk = ShortArray(1600) // 100 ms, seperti pembacaan AudioRecord di layanan
            var pos = 0
            while (pos < samples.size) {
                val n = minOf(chunk.size, samples.size - pos)
                System.arraycopy(samples, pos, chunk, 0, n)
                for (e in asr.accept(chunk, n)) {
                    when (e) {
                        is AsrEvent.Final -> finals += e
                        is AsrEvent.Partial -> partials++
                    }
                }
                pos += n
            }
            finals += asr.finish().filterIsInstance<AsrEvent.Final>()
            val procSec = (System.nanoTime() - p0) / 1e9
            asr.close()

            val text = finals.joinToString(" ") { it.text }
            log("BENCH_RESULT load=%.1fs proc=%.1fs rtf=%.3f finals=%d partialUpdates=%d".format(
                loadSec, procSec, procSec / audioSec, finals.size, partials,
            ))
            if (ref != null) {
                val (errs, len) = TextMetrics.wordErrors(TextMetrics.normalize(ref), TextMetrics.normalize(text))
                log("BENCH_WER wer=%.1f%% errors=%d refWords=%d hypWords=%d".format(
                    100.0 * errs / len, errs, len, TextMetrics.normalize(text).size,
                ))
            }
            finals.forEach { log("BENCH_SEG %6.1f-%6.1f | %s".format(it.startMs / 1000.0, it.endMs / 1000.0, it.text)) }
            log("BENCH_DONE")
        } catch (t: Throwable) {
            log("BENCH_ERROR ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun readPcm16(f: File): ShortArray {
        RandomAccessFile(f, "r").use { raf ->
            val len = (raf.length() - 44).toInt()
            val bytes = ByteArray(len)
            raf.seek(44)
            raf.readFully(bytes)
            val s = ShortArray(len / 2)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(s)
            return s
        }
    }

    private companion object {
        const val TAG = "BENCH"
    }
}
