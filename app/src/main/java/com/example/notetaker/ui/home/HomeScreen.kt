package com.example.notetaker.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.notetaker.R
import com.example.notetaker.asr.ModelState
import com.example.notetaker.data.db.Recording
import com.example.notetaker.data.db.RecordingStatus
import com.example.notetaker.importer.ImportController
import com.example.notetaker.importer.JobKind
import com.example.notetaker.recording.Phase
import com.example.notetaker.recording.RecordingController
import com.example.notetaker.ui.theme.TriferTheme
import com.example.notetaker.util.formatDurationShort
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy, HH.mm", Locale.forLanguageTag("id-ID")).withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
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
    val importProgress by ImportController.progress.collectAsState()
    val rec by RecordingController.state.collectAsState()
    val isRecording = rec.phase != Phase.IDLE
    val extra = TriferTheme.extra
    var toDelete by remember { mutableStateOf<Recording?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    TextButton(onClick = onImportClick) { Text(stringResource(R.string.import_action)) }
                    TextButton(onClick = onOpenAbout) { Text(stringResource(R.string.about_action)) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onRecordClick,
                containerColor = if (isRecording) extra.recording else MaterialTheme.colorScheme.primary,
                contentColor = if (isRecording) extra.onRecording else MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(painterResource(R.drawable.ic_rekam), contentDescription = null) },
                text = {
                    Text(stringResource(if (isRecording) R.string.home_recording_now else R.string.home_record))
                },
            )
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            if (micDenied) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.home_mic_denied), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.home_open_settings)) }
                    }
                }
            }
            if (modelState !is ModelState.Ready) {
                ModelBanner(modelState, onDownloadModel)
            }
            if (recordings.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.home_empty), style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(recordings, key = { it.id }) { r ->
                        // Rekaman yang sedang berjalan tidak boleh dihapus.
                        val active = isRecording && rec.recordingId == r.id
                        val job = importProgress[r.id]
                        RecordingRow(
                            rec = r,
                            deletable = !active && job == null,
                            progress = job?.fraction,
                            progressLabel = if (job?.kind == JobKind.REFINE) R.string.refine_progress else R.string.import_progress,
                            onClick = { onOpenRecording(r.id) },
                            onDelete = { toDelete = r },
                            onCancel = { onCancelImport(r.id) },
                        )
                    }
                }
            }
        }
    }

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

@Composable
private fun ModelBanner(state: ModelState, onDownload: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.model_banner_title), style = MaterialTheme.typography.titleSmall)
            when (state) {
                is ModelState.Downloading -> {
                    Text(
                        stringResource(R.string.model_downloading, (state.fraction * 100).toInt()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth())
                }
                is ModelState.Failed -> {
                    Text(
                        stringResource(R.string.model_failed, state.message),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onDownload) { Text(stringResource(R.string.model_retry)) }
                }
                else -> {
                    Text(stringResource(R.string.model_banner_body), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onDownload) { Text(stringResource(R.string.model_download)) }
                }
            }
        }
    }
}

@Composable
private fun RecordingRow(
    rec: Recording,
    deletable: Boolean,
    progress: Float?,
    @StringRes progressLabel: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(rec.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${dateFormat.format(Instant.ofEpochMilli(rec.createdAt))} · " +
                        "${formatDurationShort(rec.durationMs)} · ${statusLabel(rec.status)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (progress != null) {
                    Text(
                        stringResource(progressLabel, (progress * 100).toInt()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
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
                    )
                }
            }
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
