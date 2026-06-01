package com.mochikanban.app.ui.board

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mochikanban.app.data.db.entity.CardEntity
import com.mochikanban.app.data.db.entity.LabelEntity
import com.mochikanban.app.domain.Column as KanbanColumn
import com.mochikanban.app.sync.SyncSnapshot
import com.mochikanban.app.ui.components.ColumnHeader
import com.mochikanban.app.ui.components.KanbanCard
import com.mochikanban.app.ui.components.KawaiiBackdrop
import com.mochikanban.app.ui.components.MochiMascot
import com.mochikanban.app.ui.edit.EditCardSheet
import com.mochikanban.app.ui.theme.DarkTokens
import com.mochikanban.app.util.HexColor
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private sealed class Drag {
    abstract val pressOffsetInElement: Offset

    data class CardDrag(
        val card: CardEntity,
        val sizePx: IntSize,
        override val pressOffsetInElement: Offset,
    ) : Drag()

    data class LabelDrag(
        val label: LabelEntity,
        override val pressOffsetInElement: Offset,
    ) : Drag()
}

private data class ColumnBounds(
    val column: KanbanColumn,
    val rect: Rect,
    val itemTops: List<Float>,
)

private data class CardBoundsInfo(val cardId: String, val rect: Rect)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BoardScreen(
    onOpenSettings: () -> Unit,
    initialEditCardId: String? = null,
    initialQuickAdd: Boolean = false,
    vm: BoardViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val labels by vm.labels.collectAsStateWithLifecycle()
    val labelsById by vm.labelsById.collectAsStateWithLifecycle()
    val syncSnapshot by vm.syncStatus.state.collectAsStateWithLifecycle()

    var editingCardId by remember { mutableStateOf<String?>(initialEditCardId) }
    var editorOpen by remember { mutableStateOf(initialEditCardId != null || initialQuickAdd) }
    var labelEditorOpen by remember { mutableStateOf(false) }
    var celebrateCardId by remember { mutableStateOf<String?>(null) }

    var columnBounds by remember { mutableStateOf<List<ColumnBounds>>(emptyList()) }
    var cardBoundsMap by remember { mutableStateOf<Map<String, CardBoundsInfo>>(emptyMap()) }

    var drag by remember { mutableStateOf<Drag?>(null) }
    var dragPointer by remember { mutableStateOf(Offset.Zero) }
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }

    val density = LocalDensity.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(syncSnapshot) {
        when (val s = syncSnapshot) {
            is SyncSnapshot.Success -> snackbar.showSnackbar(s.message)
            is SyncSnapshot.Failure -> snackbar.showSnackbar("Sync failed: ${s.message}")
            else -> Unit
        }
    }

    LaunchedEffect(celebrateCardId) {
        if (celebrateCardId != null) {
            delay(900)
            celebrateCardId = null
        }
    }

    fun labelColorFor(card: CardEntity): Color {
        val hex = card.labelId?.let { labelsById[it]?.colorHex }
        return HexColor.parseOr(hex, DarkTokens.Outline)
    }

    val reportColumnBounds: (KanbanColumn, Rect, List<Float>) -> Unit = { col, rect, tops ->
        val current = columnBounds.toMutableList()
        val idx = current.indexOfFirst { it.column == col }
        val entry = ColumnBounds(col, rect, tops)
        if (idx >= 0) current[idx] = entry else current.add(entry)
        columnBounds = current
    }

    val reportCardBounds: (String, Rect) -> Unit = { id, rect ->
        cardBoundsMap = cardBoundsMap + (id to CardBoundsInfo(id, rect))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        KawaiiBackdrop(modifier = Modifier.fillMaxSize())

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MochiMascot(
                                size = 38.dp,
                                spinning = syncSnapshot is SyncSnapshot.Syncing,
                                modifier = Modifier.clickable { vm.syncNow() },
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Mochi Kanban", style = MaterialTheme.typography.titleLarge)
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = "Settings",
                                tint = DarkTokens.Ink,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = DarkTokens.Ink,
                    ),
                )
            },
            floatingActionButton = {
                BouncyFab(onClick = {
                    editingCardId = null
                    editorOpen = true
                })
            },
            snackbarHost = { SnackbarHost(snackbar) { d -> Snackbar(snackbarData = d) } },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .onGloballyPositioned { c ->
                        overlayOrigin = c.positionInWindow()
                    },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                ) {
                    LabelPalette(
                        labels = labels,
                        activeDrag = drag,
                        onLabelLongPressStart = { label, pressInElement, windowPos ->
                            drag = Drag.LabelDrag(label, pressInElement)
                            dragPointer = windowPos
                        },
                        onDrag = { delta -> dragPointer += delta },
                        onDragEnd = {
                            val current = drag
                            if (current is Drag.LabelDrag) {
                                val cardId = cardBoundsMap.values.firstOrNull {
                                    it.rect.contains(dragPointer)
                                }?.cardId
                                if (cardId != null) vm.setCardLabel(cardId, current.label.id)
                            }
                            drag = null
                            dragPointer = Offset.Zero
                        },
                        onEdit = { labelEditorOpen = true },
                    )

                    Spacer(Modifier.height(8.dp))

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        KanbanColumn.values().forEach { column ->
                            val cards = state.columns[column].orEmpty()
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                BoardColumn(
                                    column = column,
                                    cards = cards,
                                    labelColorFor = ::labelColorFor,
                                    isCardDragHover = drag is Drag.CardDrag &&
                                        hoveredColumn(columnBounds, dragPointer) == column,
                                    isLabelDragHover = drag is Drag.LabelDrag,
                                    celebrateCardId = celebrateCardId,
                                    onCardTap = { id ->
                                        editingCardId = id
                                        editorOpen = true
                                    },
                                    onCardLongPressStart = { card, sizePx, pressInCard, windowPos ->
                                        drag = Drag.CardDrag(card, sizePx, pressInCard)
                                        dragPointer = windowPos
                                    },
                                    onDrag = { delta -> dragPointer += delta },
                                    onDragEnd = {
                                        val current = drag
                                        if (current is Drag.CardDrag) {
                                            val target = hoveredColumn(columnBounds, dragPointer)
                                            if (target != null) {
                                                val idx = dropIndex(columnBounds, target, dragPointer)
                                                vm.moveCard(current.card.id, target, idx)
                                                if (target == KanbanColumn.DONE &&
                                                    current.card.column != KanbanColumn.DONE
                                                ) {
                                                    celebrateCardId = current.card.id
                                                }
                                            }
                                        }
                                        drag = null
                                        dragPointer = Offset.Zero
                                    },
                                    reportColumnBounds = reportColumnBounds,
                                    reportCardBounds = reportCardBounds,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                // Drag overlay (positioned in window coords, then made relative to this Box's origin)
                when (val current = drag) {
                    is Drag.CardDrag -> {
                        val widthDp = with(density) { current.sizePx.width.toDp() }
                        Box(
                            modifier = Modifier
                                .width(widthDp)
                                .graphicsLayer {
                                    translationX = (dragPointer.x - overlayOrigin.x - current.pressOffsetInElement.x)
                                    translationY = (dragPointer.y - overlayOrigin.y - current.pressOffsetInElement.y)
                                    rotationZ = 2.5f
                                    scaleX = 1.03f
                                    scaleY = 1.03f
                                    alpha = 0.97f
                                },
                        ) { KanbanCard(card = current.card, labelColor = labelColorFor(current.card), elevated = true) }
                    }
                    is Drag.LabelDrag -> {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .graphicsLayer {
                                    translationX = (dragPointer.x - overlayOrigin.x - current.pressOffsetInElement.x)
                                    translationY = (dragPointer.y - overlayOrigin.y - current.pressOffsetInElement.y)
                                    scaleX = 1.4f
                                    scaleY = 1.4f
                                    shadowElevation = 24f
                                    shape = CircleShape
                                    clip = true
                                }
                                .background(HexColor.parseOr(current.label.colorHex, DarkTokens.MintDark)),
                        )
                    }
                    null -> Unit
                }
            }
        }
    }

    if (editorOpen) {
        EditCardSheet(
            cardId = editingCardId,
            onDismiss = {
                editorOpen = false
                editingCardId = null
            },
        )
    }

    if (labelEditorOpen) {
        LabelEditorDialog(
            labels = labels,
            onAdd = vm::addLabel,
            onUpdate = vm::updateLabel,
            onDelete = vm::deleteLabel,
            onClose = { labelEditorOpen = false },
        )
    }
}

@Composable
private fun BouncyFab(onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "fabScale",
    )
    val rotation by animateFloatAsState(
        targetValue = if (pressed) 25f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "fabRotation",
    )
    FloatingActionButton(
        onClick = { pressed = true; onClick(); pressed = false },
        containerColor = DarkTokens.MintDark,
        contentColor = DarkTokens.Background,
        modifier = Modifier.graphicsLayer {
            scaleX = scale; scaleY = scale; rotationZ = rotation
        },
    ) { Icon(Icons.Filled.Add, contentDescription = "New card") }
}

@Composable
private fun LabelPalette(
    labels: List<LabelEntity>,
    activeDrag: Drag?,
    onLabelLongPressStart: (LabelEntity, Offset, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(DarkTokens.Surface.copy(alpha = 0.85f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "labels",
            color = DarkTokens.Muted,
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.width(2.dp))
        labels.forEach { label ->
            val isThisDragging = activeDrag is Drag.LabelDrag && activeDrag.label.id == label.id
            LabelDot(
                label = label,
                dimmed = isThisDragging,
                onLongPressStart = { pressInElement, windowPos ->
                    onLabelLongPressStart(label, pressInElement, windowPos)
                },
                onDrag = onDrag,
                onDragEnd = onDragEnd,
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onEdit) {
            Icon(
                Icons.Outlined.Edit,
                contentDescription = "Edit labels",
                tint = DarkTokens.Muted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun LabelDot(
    label: LabelEntity,
    dimmed: Boolean,
    onLongPressStart: (Offset, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    val alpha by animateFloatAsState(
        targetValue = if (dimmed) 0.25f else 1f,
        animationSpec = spring(),
        label = "labelAlpha",
    )
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = Modifier
            .size(26.dp)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(HexColor.parseOr(label.colorHex, DarkTokens.MintDark))
            .onGloballyPositioned { c -> origin = c.positionInWindow() }
            .pointerInput(label.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        onLongPressStart(offset, origin + offset)
                    },
                    onDrag = { _, amount -> onDrag(amount) },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                )
            },
    )
}

private fun hoveredColumn(bounds: List<ColumnBounds>, p: Offset): KanbanColumn? =
    bounds.firstOrNull { p.x in it.rect.left..it.rect.right && p.y in it.rect.top..it.rect.bottom }
        ?.column

private fun dropIndex(bounds: List<ColumnBounds>, column: KanbanColumn, p: Offset): Int {
    val b = bounds.firstOrNull { it.column == column } ?: return 0
    val tops = b.itemTops
    if (tops.isEmpty()) return 0
    var idx = tops.indexOfFirst { it > p.y }
    if (idx < 0) idx = tops.size
    return idx
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BoardColumn(
    column: KanbanColumn,
    cards: List<CardEntity>,
    labelColorFor: (CardEntity) -> Color,
    isCardDragHover: Boolean,
    isLabelDragHover: Boolean,
    celebrateCardId: String?,
    onCardTap: (String) -> Unit,
    onCardLongPressStart: (CardEntity, IntSize, Offset, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    reportColumnBounds: (KanbanColumn, Rect, List<Float>) -> Unit,
    reportCardBounds: (String, Rect) -> Unit,
) {
    val containerColor = if (isCardDragHover)
        DarkTokens.SurfaceVariant.copy(alpha = 0.95f)
    else
        DarkTokens.Surface.copy(alpha = 0.78f)
    val hoverElevation by animateDpAsState(
        targetValue = if (isCardDragHover) 6.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "columnHover",
    )
    var itemTops by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }
    var origin by remember { mutableStateOf(Offset.Zero) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(containerColor)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .graphicsLayer { shadowElevation = hoverElevation.toPx() }
            .onGloballyPositioned { c ->
                val pos = c.positionInWindow()
                origin = Offset(pos.x, pos.y)
                val rect = Rect(pos.x, pos.y, pos.x + c.size.width, pos.y + c.size.height)
                val tops = cards.mapNotNull { itemTops[it.id] }.sorted()
                reportColumnBounds(column, rect, tops)
            },
    ) {
        ColumnHeader(label = column.label(), count = cards.size)
        Spacer(Modifier.height(4.dp))
        if (cards.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Nothing here yet",
                    color = DarkTokens.Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 6.dp),
            ) {
                items(items = cards, key = { it.id }) { card ->
                    CardRow(
                        card = card,
                        labelColor = labelColorFor(card),
                        labelDragActive = isLabelDragHover,
                        celebrate = celebrateCardId == card.id,
                        onTap = { onCardTap(card.id) },
                        onLongPressStart = onCardLongPressStart,
                        onDrag = onDrag,
                        onDragEnd = onDragEnd,
                        reportTop = { y ->
                            itemTops = itemTops.toMutableMap().also { it[card.id] = y }
                        },
                        reportRect = { rect -> reportCardBounds(card.id, rect) },
                        modifier = Modifier.animateItem(
                            placementSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun CardRow(
    card: CardEntity,
    labelColor: Color,
    labelDragActive: Boolean,
    celebrate: Boolean,
    onTap: () -> Unit,
    onLongPressStart: (CardEntity, IntSize, Offset, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    reportTop: (Float) -> Unit,
    reportRect: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hoverScale by animateFloatAsState(
        targetValue = if (labelDragActive) 1.03f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "labelHover",
    )
    val celebrateScale by animateFloatAsState(
        targetValue = if (celebrate) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "celebrate",
    )
    val scale = hoverScale * celebrateScale

    var localSize by remember { mutableStateOf(IntSize.Zero) }
    var cardWindowOrigin by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onGloballyPositioned { c ->
                val pos = c.positionInWindow()
                cardWindowOrigin = pos
                reportTop(pos.y)
                reportRect(Rect(pos.x, pos.y, pos.x + c.size.width, pos.y + c.size.height))
                localSize = c.size
            }
            .pointerInput(card.id) {
                detectTapGestures(onTap = { onTap() })
            }
            .pointerInput(card.id, card.readOnly) {
                if (card.readOnly) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val windowPos = cardWindowOrigin + offset
                        onLongPressStart(card, localSize, offset, windowPos)
                    },
                    onDrag = { _, dragAmount -> onDrag(dragAmount) },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                )
            },
    ) {
        KanbanCard(card = card, labelColor = labelColor)
    }
}

@Composable
private fun LabelEditorDialog(
    labels: List<LabelEntity>,
    onAdd: (String, String) -> Unit,
    onUpdate: (LabelEntity) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
) {
    val palette = listOf(
        "#86E7BF", "#FFB4C4", "#B6D0FF", "#FFD988", "#D4BBFF",
        "#FFB39E", "#9DD1B5", "#A6E2FF", "#FFCFA0", "#E8B8FF",
    )
    var addName by remember { mutableStateOf("") }
    var addColor by remember { mutableStateOf(palette.first()) }

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = DarkTokens.Surface,
        title = { Text("Manage labels", color = DarkTokens.Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                labels.forEach { lbl ->
                    LabelRowEdit(
                        label = lbl,
                        palette = palette,
                        onUpdate = onUpdate,
                        onDelete = onDelete,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("Add new", color = DarkTokens.Muted, style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = addName,
                    onValueChange = { addName = it },
                    placeholder = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = labelInputColors(),
                )
                ColorRow(
                    palette = palette,
                    selected = addColor,
                    onSelect = { addColor = it },
                )
                TextButton(
                    onClick = {
                        if (addName.isNotBlank()) {
                            onAdd(addName.trim(), addColor)
                            addName = ""
                        }
                    },
                    enabled = addName.isNotBlank(),
                ) { Text("+ Add label", color = DarkTokens.MintDark) }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text("Done", color = DarkTokens.MintDark) }
        },
    )
}

@Composable
private fun LabelRowEdit(
    label: LabelEntity,
    palette: List<String>,
    onUpdate: (LabelEntity) -> Unit,
    onDelete: (String) -> Unit,
) {
    var name by remember(label.id) { mutableStateOf(label.name) }
    var color by remember(label.id) { mutableStateOf(label.colorHex) }
    var pickerOpen by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(HexColor.parseOr(color, DarkTokens.MintDark))
                .clickable { pickerOpen = !pickerOpen },
        )
        Spacer(Modifier.width(10.dp))
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                onUpdate(label.copy(name = it.trim().ifBlank { label.name }))
            },
            singleLine = true,
            modifier = Modifier.weight(1f),
            colors = labelInputColors(),
        )
        IconButton(onClick = { onDelete(label.id) }) {
            Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = DarkTokens.RoseDark)
        }
    }
    if (pickerOpen) {
        ColorRow(
            palette = palette,
            selected = color,
            onSelect = {
                color = it
                onUpdate(label.copy(colorHex = it))
                pickerOpen = false
            },
        )
    }
}

@Composable
private fun ColorRow(
    palette: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        palette.forEach { hex ->
            val isSel = hex.equals(selected, ignoreCase = true)
            Box(
                modifier = Modifier
                    .size(if (isSel) 26.dp else 22.dp)
                    .clip(CircleShape)
                    .background(HexColor.parseOr(hex, DarkTokens.MintDark))
                    .clickable { onSelect(hex) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun labelInputColors() = TextFieldDefaults.colors(
    focusedTextColor = DarkTokens.Ink,
    unfocusedTextColor = DarkTokens.Ink,
    focusedContainerColor = DarkTokens.SurfaceVariant,
    unfocusedContainerColor = DarkTokens.SurfaceVariant,
    cursorColor = DarkTokens.MintDark,
    focusedIndicatorColor = DarkTokens.MintDark,
    unfocusedIndicatorColor = DarkTokens.Outline,
)

private fun KanbanColumn.label(): String = when (this) {
    KanbanColumn.TODO -> "To do"
    KanbanColumn.DOING -> "Doing"
    KanbanColumn.DONE -> "Done"
}
