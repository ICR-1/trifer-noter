package com.example.notetaker

import android.app.Application
import com.example.notetaker.asr.ModelStore
import com.example.notetaker.asr.WhisperModelStore
import com.example.notetaker.data.RecordingRepository
import com.example.notetaker.data.db.AppDatabase
import com.example.notetaker.recording.RecordingRecovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Kontainer dependensi sederhana (tanpa framework DI). */
class AppContainer(app: Application) {
    /** Lingkup proses untuk pekerjaan yang harus selamat dari perpindahan layar (mis. unduh model). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database: AppDatabase by lazy { AppDatabase.build(app) }
    val recordings: RecordingRepository by lazy { RecordingRepository(database) }
    val modelStore: ModelStore by lazy { ModelStore(app) }
    val whisperModel: WhisperModelStore by lazy { WhisperModelStore(app) }
}

class NoterApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Pulihkan rekaman yang terputus bila proses sebelumnya dimatikan sistem.
        appScope.launch { RecordingRecovery.run(container.database) }
    }
}
