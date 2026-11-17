package com.nousfercorp.triferNoter

import android.app.Application
import com.nousfercorp.triferNoter.asr.ModelStore
import com.nousfercorp.triferNoter.asr.WhisperModelStore
import com.nousfercorp.triferNoter.data.RecordingRepository
import com.nousfercorp.triferNoter.data.db.AppDatabase
import com.nousfercorp.triferNoter.recording.RecordingRecovery
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
