package com.mochikanban.app.ui.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochikanban.app.data.db.entity.CardEntity
import com.mochikanban.app.data.db.entity.LabelEntity
import com.mochikanban.app.data.repo.CardRepository
import com.mochikanban.app.data.repo.LabelRepository
import com.mochikanban.app.domain.Column
import com.mochikanban.app.sync.SyncStatus
import com.mochikanban.app.sync.SyncTrigger
import com.mochikanban.app.util.Time
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BoardUiState(
    val now: Long = Time.now(),
    val columns: Map<Column, List<CardEntity>> = emptyMap(),
)

@HiltViewModel
class BoardViewModel @Inject constructor(
    private val repo: CardRepository,
    private val labelRepo: LabelRepository,
    private val sync: SyncTrigger,
    val syncStatus: SyncStatus,
) : ViewModel() {

    val state: StateFlow<BoardUiState> = repo.observeByColumn()
        .map { BoardUiState(now = it.now, columns = it.columns) }
        .stateIn(viewModelScope, SharingStarted.Lazily, BoardUiState())

    // Palette excludes hidden per-account colour labels…
    val labels: StateFlow<List<LabelEntity>> = labelRepo.observeVisible()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // …but the colour lookup keeps them, so account-coloured cards still resolve.
    val labelsById: StateFlow<Map<String, LabelEntity>> = labelRepo.observe()
        .map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    init {
        // Sync all accounts whenever the board opens (this VM is created once per
        // board entry), so events are current without a manual tap.
        sync.requestFullSync()
    }

    /**
     * Creates a card in [Column.TODO] from the quick-add sheet. [onCreated] runs
     * on the main dispatcher with the new id (used by "More options" to open the
     * full editor on the freshly-created card).
     */
    fun quickAdd(title: String, startUtc: Long?, onCreated: (String) -> Unit = {}) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val created = repo.create(
                CardEntity(id = "", title = trimmed, column = Column.TODO, startUtc = startUtc),
            )
            onCreated(created.id)
        }
    }

    fun moveCard(cardId: String, toColumn: Column, toIndex: Int) {
        viewModelScope.launch { repo.moveCard(cardId, toColumn, toIndex) }
    }

    fun setCardLabel(cardId: String, labelId: String?) {
        viewModelScope.launch { repo.setLabel(cardId, labelId) }
    }

    fun deleteCard(id: String) {
        viewModelScope.launch { repo.delete(id) }
    }

    fun completeCard(id: String) {
        viewModelScope.launch { repo.completeCard(id) }
    }

    fun snoozeCard(id: String, minutes: Int) {
        viewModelScope.launch { repo.snooze(id, minutes) }
    }

    fun addLabel(name: String, colorHex: String) {
        viewModelScope.launch { labelRepo.add(name, colorHex) }
    }

    fun updateLabel(label: LabelEntity) {
        viewModelScope.launch { labelRepo.update(label) }
    }

    fun deleteLabel(id: String) {
        viewModelScope.launch { labelRepo.delete(id) }
    }

    fun syncNow() { sync.requestFullSync() }
}
