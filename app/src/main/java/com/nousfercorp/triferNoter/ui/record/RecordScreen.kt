package com.nousfercorp.triferNoter.ui.record

import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.nousfercorp.triferNoter.R
import com.nousfercorp.triferNoter.recording.LiveStatus
import com.nousfercorp.triferNoter.recording.Phase
import com.nousfercorp.triferNoter.recording.RecState
import com.nousfercorp.triferNoter.recording.RecordingController
import com.nousfercorp.triferNoter.recording.RecordingLimits
import com.nousfercorp.triferNoter.recording.RecordingService
import com.nousfercorp.triferNoter.ui.theme.TriferTheme
import com.nousfercorp.triferNoter.util.formatClock
import kotlin.math.sqrt

/** Jumlah batang gelombang yang ditampilkan (≈ beberapa detik terakhir). */
const val WAVE_BARS = 48

/** Pembungkus berstatus: mengambil keadaan dari pengendali rekam dan meneruskan perintah ke layanan. */
@Composable
fun RecordScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state by RecordingController.state.collectAsState()

    // Selesai merekam (atau gagal): tampilkan galat bila ada, lalu kembali ke Beranda.
    LaunchedEffect(state.lastFinishedId, state.error, state.phase) {
        if (state.phase == Phase.IDLE && (state.lastFinishedId != null || state.error != null)) {
            state.error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
            RecordingController.consumeFinished()
            RecordingController.consumeError()
            onFinished()
        }
    }

    // Riwayat level untuk gelombang: satu batang per pembaruan tampilan, hanya saat benar-benar merekam.
    val history = remember { mutableStateListOf<Float>() }
    LaunchedEffect(state.elapsedMs) {
        if (state.phase == Phase.RECORDING) {
            history.add(sqrt(state.level).coerceIn(0f, 1f))
            while (history.size > WAVE_BARS) history.removeAt(0)
        }
    }

    val paused = state.phase == Phase.PAUSED
    RecordContent(
        state = state,
        levels = history,
        onBookmark = { RecordingService.send(context, RecordingService.ACTION_BOOKMARK) },
        onPauseResume = {
            RecordingService.send(context, if (paused) RecordingService.ACTION_RESUME else RecordingService.ACTION_PAUSE)
        },
        onStop = { RecordingService.send(context, RecordingService.ACTION_STOP) },
        modifier = modifier,
    )
}

/** Tampilan layar rekam tanpa ketergantungan pada pengendali/layanan, sehingga bisa dirender dengan data contoh. */
@Composable
fun RecordContent(
    state: RecState,
    levels: List<Float>,
    onBookmark: () -> Unit,
    onPauseResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extra = TriferTheme.extra
    val recording = state.phase == Phase.RECORDING
    val paused = state.phase == Phase.PAUSED
    val accent = if (recording) extra.recording else MaterialTheme.colorScheme.outline
    val pulse by rememberInfiniteTransition(label = "dot").animateFloat(
        initialValue = 1f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "dotAlpha",
    )

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))

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
                    Box(Modifier.size(10.dp).alpha(if (recording) pulse else 1f).background(accent, CircleShape))
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

            Spacer(Modifier.height(24.dp))

            Text(
                text = formatClock(state.elapsedMs),
                fontSize = 60.sp,
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

            Spacer(Modifier.height(20.dp))
            Waveform(
                levels = levels,
                active = recording,
                color = extra.recording,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            )
            Spacer(Modifier.height(20.dp))

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
                ControlButton(
                    label = stringResource(R.string.record_bookmark) +
                        if (state.bookmarkCount > 0) " · ${state.bookmarkCount}" else "",
                ) {
                    OutlinedIconButton(
                        onClick = onBookmark,
                        enabled = recording || paused,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_penanda), contentDescription = null)
                    }
                }

                ControlButton(
                    label = stringResource(if (paused) R.string.record_resume else R.string.record_pause),
                ) {
                    // Cincin lembut di belakang tombol utama.
                    Box(contentAlignment = Alignment.Center) {
                        Box(Modifier.size(96.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape))
                        FilledIconButton(
                            onClick = onPauseResume,
                            enabled = recording || paused,
                            modifier = Modifier.size(76.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Icon(
                                painterResource(if (paused) R.drawable.ic_rekam else R.drawable.ic_jeda),
                                contentDescription = null,
                                modifier = Modifier.size(34.dp),
                            )
                        }
                    }
                }

                ControlButton(label = stringResource(R.string.record_stop)) {
                    FilledIconButton(
                        onClick = onStop,
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

/** Gelombang suara: batang-batang bulat dari riwayat level, yang terbaru di kanan. Saat jeda tampil redup dan datar. */
@Composable
fun Waveform(levels: List<Float>, active: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val n = WAVE_BARS
        val gap = 4.dp.toPx()
        val barW = (size.width - gap * (n - 1)) / n
        val shown = levels.takeLast(n)
        val padded = List(n - shown.size) { 0f } + shown
        padded.forEachIndexed { i, v ->
            val h = (size.height * (0.10f + 0.90f * v.coerceIn(0f, 1f))).coerceAtMost(size.height)
            // Batang yang lebih baru sedikit lebih terang, memberi kesan bergerak.
            val a = if (active) 0.45f + 0.55f * (i + 1) / n else 0.25f
            drawRoundRect(
                color = color.copy(alpha = a),
                topLeft = Offset(i * (barW + gap), (size.height - h) / 2f),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f),
            )
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
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.height(96.dp), contentAlignment = Alignment.Center) { content() }
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
