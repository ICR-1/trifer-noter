package com.nousfercorp.triferNoter.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nousfercorp.triferNoter.data.RecordingRepository
import com.nousfercorp.triferNoter.data.db.Recording
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val repo: RecordingRepository) : ViewModel() {
    val recordings: StateFlow<List<Recording>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(recording: Recording) {
        viewModelScope.launch { repo.delete(recording) }
    }

    class Factory(private val repo: RecordingRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(repo) as T
    }
}
