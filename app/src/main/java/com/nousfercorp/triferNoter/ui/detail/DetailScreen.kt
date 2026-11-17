package com.nousfercorp.triferNoter.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nousfercorp.triferNoter.R
import com.nousfercorp.triferNoter.asr.ModelState
import com.nousfercorp.triferNoter.asr.WhisperSupport
import com.nousfercorp.triferNoter.importer.JobKind
import com.nousfercorp.triferNoter.recording.Phase
import com.nousfercorp.triferNoter.recording.RecordingController
import com.nousfercorp.triferNoter.refine.RefineService
import com.nousfercorp.triferNoter.data.db.Recording
import com.nousfercorp.triferNoter.data.db.RecordingStatus
import com.nousfercorp.triferNoter.data.db.Segment
import com.nousfercorp.triferNoter.importer.ImportController
import com.nousfercorp.triferNoter.importer.ImportService
import com.nousfercorp.triferNoter.ui.theme.TriferTheme
import com.nousfercorp.triferNoter.util.formatClock
import com.nousfercorp.triferNoter.util.formatDurationShort

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onBack: () -> Unit,
    whisperState: ModelState,
    onDownloadWhisper: () -> Unit,
    onRefine: () -> Unit,
    onSendToAi: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rec by viewModel.recording.collectAsState()
    val segments by viewModel.segments.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Segment?>(null) }
    val current = rec
    val importProgress by ImportController.progress.collectAsState()
    val context = LocalContext.current
    val importing = current != null && importProgress.containsKey(current.id)
    val recState by RecordingController.state.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(current?.title ?: "", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_kembali), contentDescription = stringResource(R.string.detail_back))
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }, enabled = current != null && !importing) {
                        Icon(painterResource(R.drawable.ic_hapus), contentDescription = stringResource(R.string.action_delete))
                    }
                },
            )
        },
    ) { inner ->
        if (current == null) return@Scaffold
        val player = rememberAudioPlayer(current.audioPath, current.durationMs)

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { InfoCard(current) }

            importProgress[current.id]?.let { job ->
                val p = job.fraction
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(
                                    if (job.kind == JobKind.REFINE) R.string.refine_progress else R.string.import_progress,
                                    (p * 100).toInt(),
                                ),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                            TextButton(onClick = {
                                if (job.kind == JobKind.REFINE) RefineService.cancel(context, current.id)
                                else ImportService.cancel(context, current.id)
                            }) {
                                Text(stringResource(R.string.import_cancel))
                            }
                        }
                    }
                }
            }

            val canRefine = importProgress[current.id] == null &&
                !ImportController.hasAny() &&
                recState.phase == Phase.IDLE &&
                current.audioPath != null &&
                (current.status == RecordingStatus.SELESAI.name || current.status == RecordingStatus.TERHENTI.name)
            if (canRefine) {
                item {
                    RefineCard(
                        supported = WhisperSupport.isSupported,
                        state = whisperState,
                        durationMs = current.durationMs,
                        onDownload = onDownloadWhisper,
                        onRefine = onRefine,
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FilledIconButton(onClick = { player.toggle() }, modifier = Modifier.size(48.dp)) {
                                Icon(
                                    painterResource(if (player.isPlaying) R.drawable.ic_jeda else R.drawable.ic_putar),
                                    contentDescription = stringResource(if (player.isPlaying) R.string.record_pause else R.string.detail_play),
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                val total = maxOf(player.durationMs, 1L)
                                Slider(
                                    value = player.positionMs.toFloat().coerceIn(0f, total.toFloat()),
                                    onValueChange = { player.seekTo(it.toLong()) },
                                    valueRange = 0f..total.toFloat(),
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(formatClock(player.positionMs), style = MaterialTheme.typography.labelSmall)
                                    Text(formatClock(player.durationMs), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        if (player.error) {
                            Text(
                                stringResource(R.string.detail_audio_missing),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            if (bookmarks.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.detail_bookmarks), style = MaterialTheme.typography.titleSmall)
                    LazyRow(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(bookmarks, key = { it.id }) { b ->
                            AssistChip(onClick = { player.seekTo(b.timeMs) }, label = { Text(formatClock(b.timeMs)) })
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.detail_transcript), style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(onClick = onSendToAi, enabled = segments.isNotEmpty()) {
                            Icon(painterResource(R.drawable.ic_kirim_ke_ai), contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.detail_send_ai))
                        }
                    }
                }
            }

            if (segments.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.detail_no_transcript),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(segments, key = { it.id }) { s ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { player.seekTo(s.startMs) },
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column {
                            Text(
                                formatClock(s.startMs),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (s.edited) {
                                Text(
                                    stringResource(R.string.detail_edited),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(s.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { editing = s }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                painterResource(R.drawable.ic_edit),
                                contentDescription = stringResource(R.string.detail_edit),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { seg ->
        var draft by remember(seg.id) { mutableStateOf(seg.text) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.detail_edit_title)) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = draft.isNotBlank(),
                    onClick = {
                        viewModel.editSegment(seg.id, draft)
                        editing = null
                    },
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onDone = onBack)
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun RefineCard(
    supported: Boolean,
    state: ModelState,
    durationMs: Long,
    onDownload: () -> Unit,
    onRefine: () -> Unit,
) {
    if (!supported) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.refine_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.refine_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (state) {
                is ModelState.Ready -> {
                    val minutes = ((durationMs / 60_000f) * 0.65f).toInt().coerceAtLeast(1)
                    Text(
                        stringResource(R.string.refine_estimate, minutes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onRefine) { Text(stringResource(R.string.refine_start)) }
                }
                is ModelState.Downloading -> {
                    Text(
                        stringResource(R.string.refine_model_downloading, (state.fraction * 100).toInt()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth())
                }
                is ModelState.Failed -> {
                    Text(stringResource(R.string.model_failed, state.message), color = MaterialTheme.colorScheme.error)
                    Button(onClick = onDownload) { Text(stringResource(R.string.model_retry)) }
                }
                else -> {
                    Text(stringResource(R.string.refine_model_needed), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onDownload) { Text(stringResource(R.string.refine_model_download)) }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(rec: Recording) {
    val extra = TriferTheme.extra
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(rec.title, style = MaterialTheme.typography.titleMedium)
            Text(
                "${formatDurationShort(rec.durationMs)} · ${statusText(rec.status)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            rec.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = extra.onProcessingContainer)
            }
        }
    }
}

@Composable
private fun statusText(status: String): String = stringResource(
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
