package com.nousfercorp.triferNoter.ui.home

import androidx.annotation.StringRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nousfercorp.triferNoter.R
import com.nousfercorp.triferNoter.asr.ModelState
import com.nousfercorp.triferNoter.data.db.Recording
import com.nousfercorp.triferNoter.data.db.RecordingStatus
import com.nousfercorp.triferNoter.importer.ImportController
import com.nousfercorp.triferNoter.importer.JobKind
import com.nousfercorp.triferNoter.importer.JobState
import com.nousfercorp.triferNoter.recording.Phase
import com.nousfercorp.triferNoter.recording.RecordingController
import com.nousfercorp.triferNoter.ui.theme.TriferTheme
import com.nousfercorp.triferNoter.util.formatDurationShort
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM, HH.mm", Locale.forLanguageTag("id-ID")).withZone(ZoneId.systemDefault())
private val monthFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("id-ID")).withZone(ZoneId.systemDefault())

/** Pembungkus berstatus: mengambil data dari ViewModel dan pengendali, lalu menampilkan [HomeContent]. */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    micDenied: Boolean,
    onRecordClick: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRecording: (Long) -> Unit,
    modelState: ModelState,
    onDownloadModel: () -> Unit,
    onImportClick: () -> Unit,
    onOpenAbout: () -> Unit,
    onCancelImport: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val recordings by viewModel.recordings.collectAsState()
    val jobs by ImportController.progress.collectAsState()
    val rec by RecordingController.state.collectAsState()
    val isRecording = rec.phase != Phase.IDLE
    var toDelete by remember { mutableStateOf<Recording?>(null) }

    HomeContent(
        recordings = recordings,
        jobs = jobs,
        isRecording = isRecording,
        activeRecordingId = rec.recordingId.takeIf { isRecording },
        micDenied = micDenied,
        modelState = modelState,
        onRecordClick = onRecordClick,
        onOpenSettings = onOpenSettings,
        onOpenRecording = onOpenRecording,
        onDownloadModel = onDownloadModel,
        onImportClick = onImportClick,
        onOpenAbout = onOpenAbout,
        onCancelImport = onCancelImport,
        onRequestDelete = { toDelete = it },
        modifier = modifier,
    )

    toDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(target)
                    toDelete = null
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Tampilan Beranda tanpa ketergantungan pada ViewModel, sehingga bisa dirender dengan data contoh. */
@Composable
fun HomeContent(
    recordings: List<Recording>,
    jobs: Map<Long, JobState>,
    isRecording: Boolean,
    activeRecordingId: Long?,
    micDenied: Boolean,
    modelState: ModelState,
    onRecordClick: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRecording: (Long) -> Unit,
    onDownloadModel: () -> Unit,
    onImportClick: () -> Unit,
    onOpenAbout: () -> Unit,
    onCancelImport: (Long) -> Unit,
    onRequestDelete: (Recording) -> Unit,
    modifier: Modifier = Modifier,
    nowMs: Long = System.currentTimeMillis(),
) {
    val extra = TriferTheme.extra
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            // Saat daftar kosong, tombol "Mulai merekam" di tengah sudah cukup; jangan tampilkan dua tombol sama.
            if (recordings.isNotEmpty() || isRecording) RecordFab(isRecording = isRecording, onClick = onRecordClick)
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 104.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") {
                HomeHeader(
                    count = recordings.size,
                    totalMs = recordings.sumOf { it.durationMs },
                    onImportClick = onImportClick,
                    onOpenAbout = onOpenAbout,
                )
            }
            if (micDenied) {
                item(key = "mic") {
                    NoticeBanner(
                        illustration = R.drawable.empty_no_mic,
                        text = stringResource(R.string.home_mic_denied),
                        container = MaterialTheme.colorScheme.errorContainer,
                        content = MaterialTheme.colorScheme.onErrorContainer,
                        actionLabel = stringResource(R.string.home_open_settings),
                        onAction = onOpenSettings,
                    )
                }
            }
            if (modelState !is ModelState.Ready) {
                item(key = "model") { ModelBanner(modelState, onDownloadModel) }
            }
            if (recordings.isEmpty()) {
                item(key = "empty") { EmptyState(onRecordClick, onImportClick) }
            } else {
                val groups = groupByDate(recordings, nowMs)
                for ((labelRes, labelText, list) in groups) {
                    item(key = "g-$labelText") {
                        Text(
                            text = labelRes?.let { stringResource(it) } ?: labelText,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 10.dp),
                        )
                    }
                    items(list, key = { it.id }) { r ->
                        // Rekaman yang sedang berjalan tidak boleh dihapus.
                        val active = isRecording && activeRecordingId == r.id
                        val job = jobs[r.id]
                        RecordingCard(
                            rec = r,
                            deletable = !active && job == null,
                            progress = job?.fraction,
                            progressLabel = if (job?.kind == JobKind.REFINE) R.string.refine_progress else R.string.import_progress,
                            onClick = { onOpenRecording(r.id) },
                            onDelete = { onRequestDelete(r) },
                            onCancel = { onCancelImport(r.id) },
                        )
                    }
                }
            }
        }
    }
    // extra dipakai oleh komponen di bawah melalui TriferTheme; referensi ini menjaga impor tetap jelas.
    @Suppress("UNUSED_EXPRESSION") extra
}

@Composable
private fun HomeHeader(count: Int, totalMs: Long, onImportClick: () -> Unit, onOpenAbout: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (count > 0) {
                Text(
                    stringResource(R.string.home_summary, count, formatDurationShort(totalMs)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onImportClick) {
            Icon(
                painterResource(R.drawable.ic_impor),
                contentDescription = stringResource(R.string.import_action),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onOpenAbout) {
            Icon(
                painterResource(R.drawable.ic_info),
                contentDescription = stringResource(R.string.about_action),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecordFab(isRecording: Boolean, onClick: () -> Unit) {
    val extra = TriferTheme.extra
    // Titik rekam berkedip lembut saat sedang merekam, sebagai penanda hidup di setiap layar Beranda.
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulseAlpha",
    )
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = if (isRecording) extra.recording else MaterialTheme.colorScheme.primary,
        contentColor = if (isRecording) extra.onRecording else MaterialTheme.colorScheme.onPrimary,
        icon = {
            Icon(
                painterResource(R.drawable.ic_rekam),
                contentDescription = null,
                modifier = Modifier.alpha(if (isRecording) pulse else 1f),
            )
        },
        text = { Text(stringResource(if (isRecording) R.string.home_recording_now else R.string.home_record)) },
    )
}

@Composable
private fun EmptyState(onRecordClick: () -> Unit, onImportClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, start = 8.dp, end = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(R.drawable.empty_no_notes), contentDescription = null, modifier = Modifier.size(176.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRecordClick) {
            Icon(painterResource(R.drawable.ic_rekam), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.home_empty_cta))
        }
        TextButton(onClick = onImportClick) { Text(stringResource(R.string.home_empty_import)) }
    }
}

@Composable
private fun NoticeBanner(
    illustration: Int,
    text: String,
    container: Color,
    content: Color,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(illustration), contentDescription = null, modifier = Modifier.size(56.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(text, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onAction, contentPadding = PaddingValues(0.dp)) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun ModelBanner(state: ModelState, onDownload: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Image(painterResource(R.drawable.empty_no_model), contentDescription = null, modifier = Modifier.size(64.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.model_banner_title), style = MaterialTheme.typography.titleSmall)
                when (state) {
                    is ModelState.Downloading -> {
                        Text(
                            stringResource(R.string.model_downloading, (state.fraction * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        LinearProgressIndicator(
                            progress = { state.fraction },
                            modifier = Modifier.fillMaxWidth(),
                            trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        )
                    }
                    is ModelState.Failed -> {
                        Text(stringResource(R.string.model_failed, state.message), style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = onDownload) { Text(stringResource(R.string.model_retry)) }
                    }
                    else -> {
                        Text(stringResource(R.string.model_banner_body), style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = onDownload) {
                            Icon(painterResource(R.drawable.ic_unduh_model), contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.model_download))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingCard(
    rec: Recording,
    deletable: Boolean,
    progress: Float?,
    @StringRes progressLabel: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    val look = statusLook(rec.status)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(44.dp).background(look.container, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_rekam), contentDescription = null, tint = look.content, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(rec.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${dateFormat.format(Instant.ofEpochMilli(rec.createdAt))} · ${formatDurationShort(rec.durationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    StatusChip(statusLabel(rec.status), look)
                }
                if (progress != null) {
                    Text(
                        stringResource(progressLabel, (progress * 100).toInt()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
            if (progress != null) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.import_cancel)) }
            }
            if (deletable) {
                IconButton(onClick = onDelete) {
                    Icon(
                        painterResource(R.drawable.ic_hapus),
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, look: StatusLook) {
    Surface(shape = RoundedCornerShape(50), color = look.container) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = look.content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

private data class StatusLook(val container: Color, val content: Color)

@Composable
private fun statusLook(status: String): StatusLook {
    val extra = TriferTheme.extra
    val cs = MaterialTheme.colorScheme
    return when (runCatching { RecordingStatus.valueOf(status) }.getOrNull()) {
        RecordingStatus.SELESAI -> StatusLook(extra.successContainer, extra.onSuccessContainer)
        RecordingStatus.MEREKAM -> StatusLook(extra.recordingContainer, extra.onRecordingContainer)
        RecordingStatus.MENTRANSKRIP, RecordingStatus.FINALISASI, RecordingStatus.MERAPIKAN ->
            StatusLook(extra.processingContainer, extra.onProcessingContainer)
        RecordingStatus.TERHENTI -> StatusLook(cs.surfaceVariant, cs.onSurfaceVariant)
        RecordingStatus.GAGAL, null -> StatusLook(cs.errorContainer, cs.onErrorContainer)
    }
}

/** Kelompok tanggal: (id string bila berupa label tetap, teks bila berupa nama bulan, isi). */
private data class DateGroup(val labelRes: Int?, val labelText: String, val items: List<Recording>)

private fun groupByDate(list: List<Recording>, nowMs: Long): List<DateGroup> {
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    fun key(r: Recording): String {
        val d: LocalDate = Instant.ofEpochMilli(r.createdAt).atZone(zone).toLocalDate()
        val days = java.time.temporal.ChronoUnit.DAYS.between(d, today)
        return when {
            days <= 0 -> "today"
            days == 1L -> "yesterday"
            days <= 7 -> "week"
            else -> monthFormat.format(Instant.ofEpochMilli(r.createdAt)).replaceFirstChar { it.uppercase() }
        }
    }
    val out = LinkedHashMap<String, MutableList<Recording>>()
    for (r in list) out.getOrPut(key(r)) { mutableListOf() }.add(r)
    return out.map { (k, v) ->
        when (k) {
            "today" -> DateGroup(R.string.home_group_today, k, v)
            "yesterday" -> DateGroup(R.string.home_group_yesterday, k, v)
            "week" -> DateGroup(R.string.home_group_week, k, v)
            else -> DateGroup(null, k, v)
        }
    }
}

@Composable
private fun statusLabel(status: String): String = stringResource(
    when (runCatching { RecordingStatus.valueOf(status) }.getOrNull()) {
        RecordingStatus.MEREKAM -> R.string.status_recording
        RecordingStatus.MENTRANSKRIP -> R.string.status_transcribing
        RecordingStatus.FINALISASI -> R.string.status_finalizing
        RecordingStatus.SELESAI -> R.string.status_done
        RecordingStatus.MERAPIKAN -> R.string.status_refining
        RecordingStatus.TERHENTI -> R.string.status_stopped
        RecordingStatus.GAGAL, null -> R.string.status_failed
    },
)
