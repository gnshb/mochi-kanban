package com.mochikanban.app.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class SyncSnapshot {
    data object Idle : SyncSnapshot()
    data object Syncing : SyncSnapshot()
    data class Success(val finishedAt: Long, val message: String) : SyncSnapshot()
    data class Failure(val finishedAt: Long, val message: String) : SyncSnapshot()
}

@Singleton
class SyncStatus @Inject constructor() {
    private val _state = MutableStateFlow<SyncSnapshot>(SyncSnapshot.Idle)
    val state: StateFlow<SyncSnapshot> = _state.asStateFlow()

    fun startSync() { _state.value = SyncSnapshot.Syncing }
    fun finishSync(message: String = "Synced") {
        _state.value = SyncSnapshot.Success(System.currentTimeMillis(), message)
    }
    fun failSync(message: String) {
        _state.value = SyncSnapshot.Failure(System.currentTimeMillis(), message)
    }
    fun reset() { _state.value = SyncSnapshot.Idle }
}
