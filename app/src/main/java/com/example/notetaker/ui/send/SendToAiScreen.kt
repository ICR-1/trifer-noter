package com.example.notetaker.ui.send

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.notetaker.R
import com.example.notetaker.ai.AiApp
import com.example.notetaker.ai.AiApps
import com.example.notetaker.ai.AiExportBuilder
import com.example.notetaker.ai.AiExportOptions
import com.example.notetaker.ai.AiPrefs
import com.example.notetaker.ai.AiTemplate
import com.example.notetaker.ui.detail.DetailViewModel

/** Teks di atas ini tidak aman lewat Intent (batas Binder ±1 MB), jadi "Bagikan" memberi petunjuk memakai Salin. */
private const val MAX_SHARE_CHARS = 400_000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendToAiScreen(
    viewModel: DetailViewModel,
    prefs: AiPrefs,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val rec by viewModel.recording.collectAsState()
    val segments by viewModel.segments.collectAsState()

    var template by remember { mutableStateOf(prefs.template) }
    var custom by remember { mutableStateOf(prefs.customPrompt) }
    var withTs by remember { mutableStateOf(prefs.withTimestamps) }
    var split by remember { mutableStateOf(prefs.split) }
    var selected by remember { mutableIntStateOf(0) }
    val done = remember { mutableStateListOf<Int>() }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    var pinned by remember { mutableStateOf(prefs.pinnedApps) }
    val detected = remember(pinned) { AiApps.detect(context, pinned) }
    val aiApps = detected.first
    val otherApps = detected.second
    var showOthers by remember { mutableStateOf(false) }

    val current = rec
    val options = AiExportOptions(
        template = template,
        customPrompt = custom,
        withTimestamps = withTs,
        maxWordsPerPart = if (split) AiExportOptions.DEFAULT_MAX_WORDS else null,
    )
    val parts = remember(current?.id, segments, template, custom, withTs, split) {
        if (current == null) emptyList() else AiExportBuilder.build(
            current.title, current.createdAt, current.durationMs, segments, options,
        )
    }
    val totalWords = remember(segments) { AiExportBuilder.wordCount(segments) }
    val index = selected.coerceIn(0, (parts.size - 1).coerceAtLeast(0))
    val part = parts.getOrNull(index)

    /** Jalankan [action] setelah pemberitahuan privasi (sekali saja). */
    fun guarded(action: () -> Unit) {
        if (prefs.noticeSeen) action() else pendingAction = action
    }

    fun sendVia(app: AiApp) {
        val p = part ?: return
        val rec0 = current ?: return
        guarded {
            when {
                p.text.length > MAX_SHARE_CHARS ->
                    Toast.makeText(context, R.string.ai_share_too_big, Toast.LENGTH_LONG).show()
                AiApps.send(context, app.packageName, rec0.title, p.text) -> {
                    if (index !in done) done += index
                }
                else -> Toast.makeText(context, context.getString(R.string.ai_app_failed, app.label), Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ai_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_kembali), contentDescription = stringResource(R.string.detail_back))
                    }
                },
            )
        },
    ) { inner ->
        if (current == null || part == null) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.ai_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item { Text(stringResource(R.string.ai_task), style = MaterialTheme.typography.titleSmall) }
            items(AiTemplate.entries.toList(), key = { it.id }) { t ->
                TemplateRow(
                    title = stringResource(templateTitle(t)),
                    subtitle = stringResource(templateSubtitle(t)),
                    selected = template == t,
                    onClick = {
                        template = t
                        prefs.template = t
                        selected = 0
                    },
                )
            }
            if (template == AiTemplate.CUSTOM) {
                item {
                    OutlinedTextField(
                        value = custom,
                        onValueChange = {
                            custom = it
                            prefs.customPrompt = it
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        label = { Text(stringResource(R.string.ai_custom_label)) },
                        supportingText = { Text(stringResource(R.string.ai_custom_hint)) },
                    )
                }
            }

            item { Text(stringResource(R.string.ai_options), style = MaterialTheme.typography.titleSmall) }
            item {
                SwitchRow(stringResource(R.string.ai_timestamps), withTs) {
                    withTs = it
                    prefs.withTimestamps = it
                }
            }
            item {
                SwitchRow(stringResource(R.string.ai_split, AiExportOptions.DEFAULT_MAX_WORDS), split) {
                    split = it
                    prefs.split = it
                    selected = 0
                    done.clear()
                }
            }

            item {
                Text(
                    stringResource(R.string.ai_summary, totalWords, parts.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (parts.size > 1) {
                item {
                    Text(
                        stringResource(R.string.ai_multi_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(parts, key = { it.index }) { p ->
                            FilterChip(
                                selected = p.index - 1 == index,
                                onClick = { selected = p.index - 1 },
                                label = {
                                    Text(
                                        stringResource(R.string.ai_part_chip, p.index) +
                                            if ((p.index - 1) in done) " ✓" else "",
                                    )
                                },
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.ai_preview, part.index, part.total, part.words),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            part.text.take(900) + if (part.text.length > 900) "…" else "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            guarded {
                                copyToClipboard(context, current.title, part.text)
                                if (index !in done) done += index
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.ai_copied, part.index),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                    ) {
                        Icon(painterResource(R.drawable.ic_salin), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(if (parts.size > 1) R.string.ai_copy_part else R.string.ai_copy, part.index))
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            guarded {
                                if (part.text.length > MAX_SHARE_CHARS) {
                                    Toast.makeText(context, R.string.ai_share_too_big, Toast.LENGTH_LONG).show()
                                } else {
                                    shareText(context, current.title, part.text)
                                    if (index !in done) done += index
                                }
                            }
                        },
                    ) {
                        Icon(painterResource(R.drawable.ic_bagikan), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.ai_share))
                    }
                }
            }

            if (aiApps.isNotEmpty() || otherApps.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.ai_direct), style = MaterialTheme.typography.titleSmall)
                    if (aiApps.isEmpty()) {
                        Text(
                            stringResource(R.string.ai_none_detected),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    LazyRow(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(aiApps, key = { it.packageName }) { app ->
                            OutlinedButton(onClick = { sendVia(app) }) { AppLabel(app) }
                        }
                        if (otherApps.isNotEmpty()) {
                            item {
                                TextButton(onClick = { showOthers = true }) {
                                    Text(stringResource(R.string.ai_more_apps))
                                }
                            }
                        }
                    }
                }
            }

            if (parts.size > 1) {
                item {
                    val next = (index + 1).coerceAtMost(parts.size - 1)
                    TextButton(onClick = { selected = next }, enabled = index < parts.size - 1) {
                        Text(stringResource(R.string.ai_next_part))
                    }
                }
            }
        }
    }

    if (showOthers) {
        AlertDialog(
            onDismissRequest = { showOthers = false },
            title = { Text(stringResource(R.string.ai_more_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.ai_more_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                        items(otherApps, key = { it.packageName }) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showOthers = false
                                        sendVia(app)
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { AppLabel(app) }
                                Text(
                                    stringResource(R.string.ai_pin),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Checkbox(
                                    checked = app.packageName in pinned,
                                    onCheckedChange = { on ->
                                        val next = if (on) pinned + app.packageName else pinned - app.packageName
                                        pinned = next
                                        prefs.pinnedApps = next
                                    },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showOthers = false }) { Text(stringResource(R.string.ai_more_close)) } },
        )
    }

    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(stringResource(R.string.ai_notice_title)) },
            text = { Text(stringResource(R.string.ai_notice_body)) },
            confirmButton = {
                TextButton(onClick = {
                    prefs.noticeSeen = true
                    pendingAction = null
                    action()
                }) { Text(stringResource(R.string.ai_notice_ok)) }
            },
            dismissButton = { TextButton(onClick = { pendingAction = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Ikon dan nama aplikasi dalam satu baris. */
@Composable
private fun AppLabel(app: AiApp) {
    val bmp = remember(app.packageName) { app.icon?.toBitmap(64, 64)?.asImageBitmap() }
    if (bmp != null) {
        Image(bitmap = bmp, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
    }
    Text(app.label)
}

@Composable
private fun TemplateRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun templateTitle(t: AiTemplate): Int = when (t) {
    AiTemplate.LECTURE -> R.string.ai_t_lecture
    AiTemplate.MEETING -> R.string.ai_t_meeting
    AiTemplate.ACTIONS -> R.string.ai_t_actions
    AiTemplate.CUSTOM -> R.string.ai_t_custom
    AiTemplate.NONE -> R.string.ai_t_none
}

private fun templateSubtitle(t: AiTemplate): Int = when (t) {
    AiTemplate.LECTURE -> R.string.ai_t_lecture_sub
    AiTemplate.MEETING -> R.string.ai_t_meeting_sub
    AiTemplate.ACTIONS -> R.string.ai_t_actions_sub
    AiTemplate.CUSTOM -> R.string.ai_t_custom_sub
    AiTemplate.NONE -> R.string.ai_t_none_sub
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun shareText(context: Context, title: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(send, title)) }
        .onFailure { Toast.makeText(context, R.string.ai_share_failed, Toast.LENGTH_LONG).show() }
}
