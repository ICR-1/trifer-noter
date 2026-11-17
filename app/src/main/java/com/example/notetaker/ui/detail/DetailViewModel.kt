package com.example.notetaker.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.notetaker.data.RecordingRepository
import com.example.notetaker.data.db.Bookmark
import com.example.notetaker.data.db.Recording
import com.example.notetaker.data.db.Segment
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DetailViewModel(private val repo: RecordingRepository, id: Long) : ViewModel() {
    val recording: StateFlow<Recording?> =
        repo.observe(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val segments: StateFlow<List<Segment>> =
        repo.observeSegments(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val bookmarks: StateFlow<List<Bookmark>> =
        repo.observeBookmarks(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun editSegment(segmentId: Long, text: String) {
        viewModelScope.launch { repo.editSegment(segmentId, text) }
    }

    fun delete(onDone: () -> Unit) {
        val rec = recording.value ?: return
        viewModelScope.launch {
            repo.delete(rec)
            onDone()
        }
    }

    class Factory(private val repo: RecordingRepository, private val id: Long) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DetailViewModel(repo, id) as T
    }
}
