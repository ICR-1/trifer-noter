package com.nousfercorp.triferNoter.ui.about

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nousfercorp.triferNoter.R
import com.nousfercorp.triferNoter.about.Credit
import com.nousfercorp.triferNoter.about.Credits

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = remember { versionName(context) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_kembali), contentDescription = stringResource(R.string.detail_back))
                    }
                },
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(R.string.about_version, version),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(stringResource(R.string.about_tagline), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.about_privacy_title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.about_privacy_body), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item { SpecCard() }
            item { MakerCard() }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.about_credits_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.about_credits_intro),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(Credits.ALL, key = { it.name }) { credit -> CreditCard(credit) }
            item {
                Text(
                    stringResource(R.string.about_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SpecCard() {
    val rows = listOf(
        R.string.spec_os_k to R.string.spec_os_v,
        R.string.spec_cpu_k to R.string.spec_cpu_v,
        R.string.spec_lang_k to R.string.spec_lang_v,
        R.string.spec_limit_k to R.string.spec_limit_v,
        R.string.spec_audio_k to R.string.spec_audio_v,
        R.string.spec_live_k to R.string.spec_live_v,
        R.string.spec_refine_k to R.string.spec_refine_v,
        R.string.spec_ram_k to R.string.spec_ram_v,
        R.string.spec_storage_k to R.string.spec_storage_v,
        R.string.spec_perm_k to R.string.spec_perm_v,
        R.string.spec_tested_k to R.string.spec_tested_v,
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.about_spec_title), style = MaterialTheme.typography.titleMedium)
            for ((k, v) in rows) {
                Column {
                    Text(stringResource(k), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(v), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun MakerCard() {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.about_maker_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.about_maker_body), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { expanded = !expanded }) {
                Text(stringResource(if (expanded) R.string.about_hide_app_license else R.string.about_show_app_license))
            }
            if (expanded) {
                val text = remember { readRaw(context, Credits.APP_LICENSE) }
                Text(text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp))
            }
        }
    }
}

@Composable
private fun CreditCard(credit: Credit) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(credit.name, style = MaterialTheme.typography.titleSmall)
            Text(credit.role, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${credit.author} · ${credit.license}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { expanded = !expanded }) {
                Text(stringResource(if (expanded) R.string.about_hide_license else R.string.about_show_license))
            }
            if (expanded) {
                val text = remember(credit.licenseRaw) { readRaw(context, credit.licenseRaw) }
                Text(text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp))
            }
        }
    }
}

private fun versionName(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"

/** Baca teks lisensi dari res/raw; kosong bila tidak ditemukan. */
private fun readRaw(context: Context, name: String): String {
    val id = context.resources.getIdentifier(name, "raw", context.packageName)
    if (id == 0) return ""
    return runCatching { context.resources.openRawResource(id).bufferedReader().use { it.readText() } }.getOrDefault("")
}
