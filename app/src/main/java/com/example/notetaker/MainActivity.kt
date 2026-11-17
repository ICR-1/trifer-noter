package com.example.notetaker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import com.example.notetaker.ui.detail.DetailScreen
import com.example.notetaker.ui.detail.DetailViewModel
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.notetaker.ai.AiPrefs
import com.example.notetaker.asr.ModelState
import com.example.notetaker.ui.send.SendToAiScreen
import com.example.notetaker.importer.ImportController
import com.example.notetaker.importer.ImportService
import com.example.notetaker.importer.JobKind
import com.example.notetaker.refine.RefineService
import com.example.notetaker.recording.Phase
import com.example.notetaker.recording.RecordingController
import com.example.notetaker.recording.RecordingService
import com.example.notetaker.ui.about.AboutScreen
import com.example.notetaker.ui.home.HomeScreen
import com.example.notetaker.ui.home.HomeViewModel
import com.example.notetaker.ui.record.RecordScreen
import com.example.notetaker.ui.theme.TriferNoterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as NoterApp).container
        setContent {
            TriferNoterTheme {
                val navController = rememberNavController()
                val aiPrefs = remember { AiPrefs(applicationContext) }
                var micDenied by remember { mutableStateOf(false) }
                val modelState by container.modelStore.state.collectAsState()
                val whisperState by container.whisperModel.state.collectAsState()

                fun startAndOpen() {
                    micDenied = false
                    if (RecordingController.state.value.phase == Phase.IDLE) {
                        RecordingService.send(this@MainActivity, RecordingService.ACTION_START)
                    }
                    navController.navigate(ROUTE_RECORD) { launchSingleTop = true }
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { result ->
                    // Izin notifikasi boleh ditolak; mikrofon wajib.
                    if (result[Manifest.permission.RECORD_AUDIO] == true || hasMic()) startAndOpen() else micDenied = true
                }

                val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) {
                        // Simpan izin baca selama mungkin (bila penyedia berkas mendukung); abaikan bila tidak.
                        runCatching {
                            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        ImportService.enqueue(this@MainActivity, uri)
                        Toast.makeText(this@MainActivity, R.string.import_started, Toast.LENGTH_SHORT).show()
                    }
                }

                NavHost(navController = navController, startDestination = ROUTE_HOME) {
                    composable(ROUTE_HOME) {
                        val vm: HomeViewModel = viewModel(
                            factory = HomeViewModel.Factory(container.recordings),
                        )
                        HomeScreen(
                            viewModel = vm,
                            micDenied = micDenied,
                            onOpenRecording = { id -> navController.navigate("$ROUTE_DETAIL/$id") },
                            modelState = modelState,
                            onImportClick = {
                                if (modelState is ModelState.Ready) {
                                    importLauncher.launch(arrayOf("audio/*", "video/*", "application/ogg"))
                                } else {
                                    Toast.makeText(this@MainActivity, R.string.import_need_model, Toast.LENGTH_LONG).show()
                                }
                            },
                            onCancelImport = { id ->
                                if (ImportController.progress.value[id]?.kind == JobKind.REFINE) {
                                    RefineService.cancel(this@MainActivity, id)
                                } else {
                                    ImportService.cancel(this@MainActivity, id)
                                }
                            },
                            onDownloadModel = { container.appScope.launch { container.modelStore.download() } },
                            onRecordClick = {
                                when {
                                    RecordingController.state.value.phase != Phase.IDLE ->
                                        navController.navigate(ROUTE_RECORD) { launchSingleTop = true }
                                    hasMic() -> startAndOpen()
                                    else -> permissionLauncher.launch(requiredPermissions())
                                }
                            },
                            onOpenAbout = { navController.navigate(ROUTE_ABOUT) { launchSingleTop = true } },
                            onOpenSettings = {
                                startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                        .setData(Uri.fromParts("package", packageName, null)),
                                )
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    composable(
                        route = "$ROUTE_DETAIL/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.LongType }),
                    ) { entry ->
                        val id = entry.arguments?.getLong("id") ?: return@composable
                        val vm: DetailViewModel = viewModel(
                            key = "detail-$id",
                            factory = DetailViewModel.Factory(container.recordings, id),
                        )
                        DetailScreen(
                            viewModel = vm,
                            onBack = { navController.popBackStack() },
                            whisperState = whisperState,
                            onDownloadWhisper = { container.appScope.launch { container.whisperModel.download() } },
                            onRefine = { RefineService.enqueue(this@MainActivity, id) },
                            onSendToAi = { navController.navigate("$ROUTE_SEND/$id") },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    composable(
                        route = "$ROUTE_SEND/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.LongType }),
                    ) { entry ->
                        val id = entry.arguments?.getLong("id") ?: return@composable
                        val vm: DetailViewModel = viewModel(
                            key = "send-$id",
                            factory = DetailViewModel.Factory(container.recordings, id),
                        )
                        SendToAiScreen(
                            viewModel = vm,
                            prefs = aiPrefs,
                            onBack = { navController.popBackStack() },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    composable(ROUTE_ABOUT) {
                        AboutScreen(onBack = { navController.popBackStack() }, modifier = Modifier.fillMaxSize())
                    }
                    composable(ROUTE_RECORD) {
                        RecordScreen(
                            onFinished = { navController.popBackStack(ROUTE_HOME, inclusive = false) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    private fun hasMic() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }

    private companion object {
        const val ROUTE_HOME = "home"
        const val ROUTE_RECORD = "record"
        const val ROUTE_DETAIL = "detail"
        const val ROUTE_SEND = "send"
        const val ROUTE_ABOUT = "about"
    }
}
