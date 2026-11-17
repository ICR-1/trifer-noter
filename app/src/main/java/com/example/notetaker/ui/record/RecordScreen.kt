package com.example.notetaker.ui.record

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.notetaker.R
import com.example.notetaker.recording.LiveStatus
import com.example.notetaker.recording.Phase
import com.example.notetaker.recording.RecState
import com.example.notetaker.recording.RecordingController
import com.example.notetaker.recording.RecordingLimits
import com.example.notetaker.recording.RecordingService
import com.example.notetaker.ui.theme.TriferTheme
import com.example.notetaker.util.formatClock
import kotlin.math.sqrt

@Composable
fun RecordScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state by RecordingController.state.collectAsState()
    val extra = TriferTheme.extra

    // Selesai merekam (atau gagal): tampilkan galat bila ada, lalu kembali ke Beranda.
    LaunchedEffect(state.lastFinishedId, state.error, state.phase) {
        if (state.phase == Phase.IDLE && (state.lastFinishedId != null || state.error != null)) {
            state.error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
            RecordingController.consumeFinished()
            RecordingController.consumeError()
            onFinished()
        }
    }

    val level by animateFloatAsState(targetValue = sqrt(state.level).coerceIn(0f, 1f), label = "level")
    val recording = state.phase == Phase.RECORDING
    val paused = state.phase == Phase.PAUSED
    val accent = if (recording) extra.recording else MaterialTheme.colorScheme.outline

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))

            // Lencana status
            Surface(
                shape = RoundedCornerShape(50),
                color = if (recording) extra.recordingContainer else MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(10.dp).background(accent, CircleShape))
                    Text(
                        text = when (state.phase) {
                            Phase.RECORDING -> stringResource(R.string.record_state_recording)
                            Phase.PAUSED -> stringResource(R.string.record_state_paused)
                            Phase.FINALIZING -> stringResource(R.string.record_state_finalizing)
                            Phase.IDLE -> stringResource(R.string.record_starting)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (recording) extra.onRecordingContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = formatClock(state.elapsedMs),
                fontSize = 56.sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(
                    R.string.record_remaining,
                    formatClock(RecordingLimits.remainingMs(state.elapsedMs)),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))
            LinearProgressIndicator(
                progress = { if (recording) level else 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = extra.recording,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(Modifier.height(24.dp))

            if (state.nearLimit) {
                NoticeCard(
                    text = stringResource(
                        R.string.record_near_limit,
                        (RecordingLimits.remainingMs(state.elapsedMs) / 60_000).toInt(),
                    ),
                    container = extra.processingContainer,
                    content = extra.onProcessingContainer,
                )
                Spacer(Modifier.height(12.dp))
            }
            if (paused && state.pausedBySystem) {
                NoticeCard(
                    text = stringResource(R.string.record_paused_system),
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.height(12.dp))
            }

            LiveTranscriptCard(state = state, modifier = Modifier.fillMaxWidth().weight(1f))

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ControlButton(label = stringResource(R.string.record_bookmark)) {
                    OutlinedIconButton(
                        onClick = { RecordingService.send(context, RecordingService.ACTION_BOOKMARK) },
                        enabled = recording || paused,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_penanda), contentDescription = null)
                    }
                    if (state.bookmarkCount > 0) {
                        Text(
                            stringResource(R.string.record_bookmark_count, state.bookmarkCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                ControlButton(
                    label = stringResource(if (paused) R.string.record_resume else R.string.record_pause),
                ) {
                    FilledIconButton(
                        onClick = {
                            RecordingService.send(
                                context,
                                if (paused) RecordingService.ACTION_RESUME else RecordingService.ACTION_PAUSE,
                            )
                        },
                        enabled = recording || paused,
                        modifier = Modifier.size(72.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(
                            painterResource(if (paused) R.drawable.ic_rekam else R.drawable.ic_jeda),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }

                ControlButton(label = stringResource(R.string.record_stop)) {
                    FilledIconButton(
                        onClick = { RecordingService.send(context, RecordingService.ACTION_STOP) },
                        enabled = recording || paused,
                        modifier = Modifier.size(56.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = extra.recording,
                            contentColor = extra.onRecording,
                        ),
                    ) {
                        Icon(painterResource(R.drawable.ic_stop), contentDescription = null)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LiveTranscriptCard(state: RecState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val hasPartial = state.livePartial.isNotEmpty()
    val total = state.liveLines.size + if (hasPartial) 1 else 0

    // Selalu gulir ke teks terbaru.
    LaunchedEffect(total, state.livePartial) {
        if (total > 0) listState.scrollToItem(total - 1)
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            if (state.liveLagMs > 3_000) {
                Text(
                    stringResource(R.string.live_catching_up, (state.liveLagMs / 1000).toInt()),
                    style = MaterialTheme.typography.labelMedium,
                    color = TriferTheme.extra.processing,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            val message: String? = when {
                state.liveStatus == LiveStatus.OFF_NO_MODEL -> stringResource(R.string.live_no_model)
                state.liveStatus == LiveStatus.ERROR && total == 0 ->
                    stringResource(R.string.live_error, state.liveError ?: "")
                state.liveStatus == LiveStatus.LOADING && total == 0 -> stringResource(R.string.live_loading)
                total == 0 -> stringResource(R.string.live_waiting)
                else -> null
            }
            if (message != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.liveLines.size) { i ->
                        Text(state.liveLines[i], style = MaterialTheme.typography.bodyLarge)
                    }
                    if (hasPartial) {
                        item {
                            Text(
                                state.livePartial,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontStyle = FontStyle.Italic,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlButton(label: String, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        content()
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun NoticeCard(text: String, container: Color, content: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
