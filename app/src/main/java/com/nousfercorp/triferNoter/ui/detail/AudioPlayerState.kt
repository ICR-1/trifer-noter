package com.nousfercorp.triferNoter.ui.detail

import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** Pemutar sederhana untuk memutar ulang rekaman WAV. */
@Stable
class AudioPlayerState(private val path: String?, private val knownDurationMs: Long) {
    private var player: MediaPlayer? = null

    var isPlaying by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(knownDurationMs)
        private set
    var error by mutableStateOf(false)
        private set

    private fun ensure(): MediaPlayer? {
        player?.let { return it }
        val p = path ?: return null.also { error = true }
        return runCatching {
            MediaPlayer().apply {
                setDataSource(p)
                prepare()
                setOnCompletionListener {
                    this@AudioPlayerState.isPlaying = false
                    this@AudioPlayerState.positionMs = 0
                    seekTo(0)
                }
            }
        }.onFailure { error = true }.getOrNull()?.also {
            player = it
            durationMs = it.duration.toLong().coerceAtLeast(knownDurationMs)
        }
    }

    fun toggle() {
        val p = ensure() ?: return
        if (p.isPlaying) {
            p.pause()
            isPlaying = false
        } else {
            p.start()
            isPlaying = true
        }
    }

    fun seekTo(ms: Long) {
        val p = ensure() ?: return
        p.seekTo(ms.coerceIn(0, durationMs).toInt())
        positionMs = ms.coerceIn(0, durationMs)
    }

    fun poll() {
        player?.let { if (isPlaying) positionMs = it.currentPosition.toLong() }
    }

    fun release() {
        runCatching { player?.release() }
        player = null
        isPlaying = false
    }
}

@Composable
fun rememberAudioPlayer(path: String?, durationMs: Long): AudioPlayerState {
    val state = remember(path) { AudioPlayerState(path, durationMs) }
    DisposableEffect(state) { onDispose { state.release() } }
    LaunchedEffect(state, state.isPlaying) {
        while (state.isPlaying) {
            state.poll()
            delay(200)
        }
    }
    return state
}
