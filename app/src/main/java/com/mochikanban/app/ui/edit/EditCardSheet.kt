package com.mochikanban.app.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mochikanban.app.domain.Column as KanbanColumn
import com.mochikanban.app.domain.GoogleCalendarColors
import com.mochikanban.app.ui.theme.DarkTokens
import com.mochikanban.app.ui.theme.matteLabelColor
import com.mochikanban.app.util.HexColor
import com.mochikanban.app.util.Time
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Calendar

private enum class PickerTarget { WHEN_ }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCardSheet(
    cardId: String?,
    onDismiss: () -> Unit,
    vm: EditCardViewModel = hiltViewModel(),
) {
    LaunchedEffect(cardId) { vm.loadFor(cardId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val labels by vm.labels.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var datePicker by remember { mutableStateOf<PickerTarget?>(null) }
    var timePicker by remember { mutableStateOf<Pair<PickerTarget, Long>?>(null) }
    var newChecklist by remember { mutableStateOf("") }

    val focusManager = LocalFocusManager.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkTokens.Surface,
        contentColor = DarkTokens.Ink,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        // Final pass runs AFTER children. If nothing else consumed the
                        // press, the tap was on empty space → clear focus.
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        if (event.type == PointerEventType.Press &&
                            event.changes.none { it.isConsumed }
                        ) {
                            focusManager.clearFocus(force = true)
                        }
                    }
                },
        ) { Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (state.existing == null) "New card" else "Edit card",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                if (state.existing != null && !state.readOnly) {
                    IconButton(onClick = { vm.delete(); onDismiss() }) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete",
                            tint = DarkTokens.RoseDark,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.title,
                onValueChange = vm::setTitle,
                label = { Text("Title") },
                singleLine = true,
                enabled = !state.readOnly,
                modifier = Modifier.fillMaxWidth(),
                colors = inputColors(),
            )

            if (state.existing != null) {
                Text("Label", style = MaterialTheme.typography.labelLarge, color = DarkTokens.Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // None swatch
                    LabelSwatch(
                        color = HexColor.parseOr(
                            GoogleCalendarColors.defaultEventColor,
                            DarkTokens.SkyDark,
                        ).matteLabelColor(),
                        selected = state.labelId == null,
                        crossed = true,
                        onClick = { vm.setLabel(null) },
                    )
                    labels.forEach { lbl ->
                        LabelSwatch(
                            color = HexColor.parseOr(lbl.colorHex, DarkTokens.MintDark).matteLabelColor(),
                            selected = state.labelId == lbl.id,
                            onClick = { vm.setLabel(lbl.id) },
                        )
                    }
                }
            }

            Text("Checklist", style = MaterialTheme.typography.labelLarge, color = DarkTokens.Muted)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.checklist.items.forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = item.done,
                            onCheckedChange = { vm.toggleChecklistItem(item.id) },
                            enabled = !state.readOnly,
                            colors = CheckboxDefaults.colors(
                                checkedColor = DarkTokens.MintDark,
                                uncheckedColor = DarkTokens.Outline,
                            ),
                        )
                        TextField(
                            value = item.text,
                            onValueChange = { vm.editChecklistItem(item.id, it) },
                            singleLine = true,
                            enabled = !state.readOnly,
                            modifier = Modifier.weight(1f),
                            colors = inlineColors(),
                        )
                        IconButton(onClick = { vm.removeChecklistItem(item.id) }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Remove",
                                tint = DarkTokens.Muted)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newChecklist,
                        onValueChange = { newChecklist = it },
                        placeholder = { Text("Add an item…") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            vm.addChecklistItem(newChecklist)
                            newChecklist = ""
                        }),
                        modifier = Modifier
                            .weight(1f)
                            // Commit the pending item if focus leaves the field (e.g. tapping
                            // elsewhere or dismissing) so the last entry isn't lost.
                            .onFocusChanged { focus ->
                                if (!focus.isFocused && newChecklist.isNotBlank()) {
                                    vm.addChecklistItem(newChecklist)
                                    newChecklist = ""
                                }
                            },
                        colors = inputColors(),
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            vm.addChecklistItem(newChecklist)
                            newChecklist = ""
                        },
                        enabled = newChecklist.isNotBlank() && !state.readOnly,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add", tint = DarkTokens.MintDark)
                    }
                }
            }

            Text("When", style = MaterialTheme.typography.labelLarge, color = DarkTokens.Muted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = { datePicker = PickerTarget.WHEN_ },
                    label = { Text(state.startUtc?.let { Time.format(it) } ?: "Pick date & time") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = DarkTokens.SurfaceVariant,
                        labelColor = DarkTokens.Ink,
                    ),
                )
                if (state.startUtc != null) {
                    IconButton(onClick = { vm.setStart(null); vm.setReminderAt(null) }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Clear date", tint = DarkTokens.Muted)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { vm.save(); onDismiss() },
                enabled = state.title.isNotBlank() && !state.readOnly,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkTokens.MintDark,
                    contentColor = DarkTokens.Background,
                ),
            ) { Text(if (state.existing == null) "Add card" else "Save") }
            Spacer(Modifier.height(8.dp))
        } }
    }

    when (val target = datePicker) {
        null -> Unit
        else -> {
            val seed = state.startUtc ?: System.currentTimeMillis()
            val dpState = rememberDatePickerState(initialSelectedDateMillis = seed)
            DatePickerDialog(
                onDismissRequest = { datePicker = null },
                confirmButton = {
                    TextButton(onClick = {
                        val selected = dpState.selectedDateMillis ?: seed
                        timePicker = target to selected
                        datePicker = null
                    }) { Text("Next") }
                },
                dismissButton = { TextButton(onClick = { datePicker = null }) { Text("Cancel") } },
            ) {
                DatePicker(state = dpState)
            }
        }
    }

    when (val tp = timePicker) {
        null -> Unit
        else -> {
            val (_, dateMs) = tp
            val seedDateTime = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(state.startUtc ?: dateMs),
                ZoneId.systemDefault(),
            )
            val tpState = rememberTimePickerState(
                initialHour = seedDateTime.hour,
                initialMinute = seedDateTime.minute,
                is24Hour = true,
            )
            ModalBottomSheet(
                onDismissRequest = { timePicker = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = DarkTokens.Surface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Pick a time", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(12.dp))
                    TimePicker(state = tpState)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { timePicker = null }) { Text("Cancel") }
                        Button(
                            onClick = {
                                val cal = Calendar.getInstance().apply {
                                    timeInMillis = dateMs
                                    set(Calendar.HOUR_OF_DAY, tpState.hour)
                                    set(Calendar.MINUTE, tpState.minute)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                                vm.setStart(cal.timeInMillis)
                                vm.setReminderAt(cal.timeInMillis)
                                timePicker = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DarkTokens.MintDark,
                                contentColor = DarkTokens.Background,
                            ),
                        ) { Text("Set") }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun inputColors() = TextFieldDefaults.colors(
    focusedTextColor = DarkTokens.Ink,
    unfocusedTextColor = DarkTokens.Ink,
    focusedContainerColor = DarkTokens.SurfaceVariant,
    unfocusedContainerColor = DarkTokens.SurfaceVariant,
    cursorColor = DarkTokens.MintDark,
    focusedLabelColor = DarkTokens.MintDark,
    unfocusedLabelColor = DarkTokens.Muted,
    focusedIndicatorColor = DarkTokens.MintDark,
    unfocusedIndicatorColor = DarkTokens.Outline,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun inlineColors() = TextFieldDefaults.colors(
    focusedTextColor = DarkTokens.Ink,
    unfocusedTextColor = DarkTokens.Ink,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    cursorColor = DarkTokens.MintDark,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
)

@Composable
private fun LabelSwatch(
    color: Color,
    selected: Boolean,
    crossed: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(if (selected) 34.dp else 28.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (crossed) {
            Text("✕", color = DarkTokens.Ink, style = MaterialTheme.typography.labelMedium)
        }
    }
}
