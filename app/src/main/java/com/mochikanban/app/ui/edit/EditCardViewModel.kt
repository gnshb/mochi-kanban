package com.mochikanban.app.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochikanban.app.data.db.entity.CardEntity
import com.mochikanban.app.data.db.entity.LabelEntity
import com.mochikanban.app.data.repo.CardRepository
import com.mochikanban.app.data.repo.LabelRepository
import com.mochikanban.app.domain.Checklist
import com.mochikanban.app.domain.ChecklistItem
import com.mochikanban.app.domain.Column
import com.mochikanban.app.util.ChecklistCodec
import com.mochikanban.app.util.NaturalDateParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class EditUiState(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val checklist: Checklist = Checklist(),
    val startUtc: Long? = null,
    val durationMin: Int? = null,
    val column: Column = Column.TODO,
    val labelId: String? = null,
    val reminderAtUtc: Long? = null,
    val readOnly: Boolean = false,
    val existing: CardEntity? = null,
)

@HiltViewModel
class EditCardViewModel @Inject constructor(
    private val repo: CardRepository,
    private val labelRepo: LabelRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(EditUiState())
    val state: StateFlow<EditUiState> = _state.asStateFlow()

    /** True while the start time was inferred from the title (vs. set by the user). */
    private var autoStart = false

    val labels: StateFlow<List<LabelEntity>> = labelRepo.observeVisible()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun loadFor(cardId: String?) {
        autoStart = false
        if (cardId == null) {
            _state.value = EditUiState()
            return
        }
        viewModelScope.launch {
            val found = repo.getCard(cardId) ?: return@launch
            _state.value = EditUiState(
                id = found.id,
                title = found.title,
                checklist = ChecklistCodec.decode(found.checklist),
                startUtc = found.startUtc,
                durationMin = found.durationMin,
                column = found.column,
                labelId = found.labelId,
                reminderAtUtc = found.reminderAtUtc,
                readOnly = found.readOnly,
                existing = found,
            )
        }
    }

    fun setTitle(v: String) {
        _state.update { it.copy(title = v) }
        // Recognise a date/time phrase in the title and reflect it live in the
        // "When" chip — unless the user already picked a date manually.
        val parsed = NaturalDateParser.parse(v)
        when {
            parsed != null && (autoStart || _state.value.startUtc == null) -> {
                autoStart = true
                _state.update { it.copy(startUtc = parsed.startUtc) }
            }
            parsed == null && autoStart -> {
                autoStart = false
                _state.update { it.copy(startUtc = null) }
            }
        }
    }
    fun setColumn(v: Column) { _state.update { it.copy(column = v) } }
    fun setLabel(v: String?) { _state.update { it.copy(labelId = v) } }
    fun setStart(v: Long?) {
        autoStart = false
        _state.update { it.copy(startUtc = v) }
    }
    fun setDuration(v: Int?) { _state.update { it.copy(durationMin = v) } }
    fun setReminderAt(v: Long?) { _state.update { it.copy(reminderAtUtc = v) } }

    fun addChecklistItem(text: String) {
        if (text.isBlank()) return
        val item = ChecklistItem(id = UUID.randomUUID().toString(), text = text.trim())
        _state.update { it.copy(checklist = Checklist(it.checklist.items + item)) }
    }

    fun toggleChecklistItem(id: String) {
        _state.update { ui ->
            val newItems = ui.checklist.items.map { if (it.id == id) it.copy(done = !it.done) else it }
            ui.copy(checklist = Checklist(newItems))
        }
    }

    fun removeChecklistItem(id: String) {
        _state.update { ui ->
            ui.copy(checklist = Checklist(ui.checklist.items.filterNot { it.id == id }))
        }
    }

    fun editChecklistItem(id: String, text: String) {
        _state.update { ui ->
            val newItems = ui.checklist.items.map { if (it.id == id) it.copy(text = text) else it }
            ui.copy(checklist = Checklist(newItems))
        }
    }

    fun save() {
        val s = _state.value
        if (s.title.isBlank()) return
        // If the date came from the title, strip the recognised phrase out of it.
        val finalTitle = if (autoStart) {
            NaturalDateParser.parse(s.title)?.cleanedTitle?.takeIf { it.isNotBlank() } ?: s.title.trim()
        } else {
            s.title.trim()
        }
        viewModelScope.launch {
            val base = s.existing ?: CardEntity(id = s.id, title = "")
            repo.upsert(
                base.copy(
                    id = s.id,
                    title = finalTitle,
                    checklist = ChecklistCodec.encode(s.checklist),
                    startUtc = s.startUtc,
                    // Default a dated event to a 1-hour window.
                    durationMin = s.durationMin ?: if (s.startUtc != null) 60 else null,
                    column = s.column,
                    labelId = s.labelId,
                    reminderAtUtc = s.reminderAtUtc,
                )
            )
        }
    }

    fun delete() {
        val id = _state.value.id
        viewModelScope.launch { repo.delete(id) }
    }

    private inline fun MutableStateFlow<EditUiState>.update(crossinline transform: (EditUiState) -> EditUiState) {
        value = transform(value)
    }
}
