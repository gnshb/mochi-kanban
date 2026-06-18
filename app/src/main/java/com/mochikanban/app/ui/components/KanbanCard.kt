package com.mochikanban.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mochikanban.app.data.db.entity.CardEntity
import com.mochikanban.app.domain.Column as KanbanColumn
import com.mochikanban.app.ui.theme.DarkTokens
import com.mochikanban.app.ui.theme.MochiCardShape
import com.mochikanban.app.ui.theme.glowTint
import com.mochikanban.app.util.ChecklistCodec
import com.mochikanban.app.util.Time

@Composable
fun KanbanCard(
    card: CardEntity,
    labelColor: Color,
    now: Long,
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
) {
    val isDone = card.effectiveColumn(now) == KanbanColumn.DONE
    val actionRequired = card.isActionRequired(now)
    val attentionWindow = card.isAttentionWindow(now)
    val borderColor = when {
        actionRequired -> DarkTokens.Error.copy(alpha = 0.95f)
        attentionWindow -> labelColor.copy(alpha = 0.95f)
        isDone -> labelColor.copy(alpha = 0.2f)
        else -> labelColor.copy(alpha = 0.40f)
    }
    val glowColor = if (actionRequired) DarkTokens.Error else labelColor
    val glowActive = actionRequired || attentionWindow
    val container = if (glowActive) DarkTokens.SurfaceVariant.glowTint(glowColor, 0.10f)
    else DarkTokens.SurfaceVariant
    val checklist = ChecklistCodec.decode(card.checklist)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (glowActive || elevated) 14.dp else 0.dp,
                shape = MochiCardShape,
                clip = false,
                ambientColor = glowColor.copy(alpha = if (glowActive) 0.55f else 0f),
                spotColor = glowColor.copy(alpha = if (glowActive) 0.55f else 0f),
            ),
        shape = MochiCardShape,
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (elevated || actionRequired || attentionWindow) 16.dp else 0.dp,
        ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(labelColor),
            )
            Spacer(Modifier.width(12.dp))
            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = card.title,
                        color = if (isDone) DarkTokens.Muted else DarkTokens.Ink,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = if (isDone) TextDecoration.LineThrough else null,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    AnimatedVisibility(
                        visible = !isDone && card.startUtc != null,
                        enter = fadeIn(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.Outlined.Schedule,
                                contentDescription = null,
                                tint = labelColor,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = card.startUtc?.let { Time.format(it) }.orEmpty(),
                                color = DarkTokens.Muted,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }

                if (!checklist.isEmpty) {
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.foundation.layout.Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        checklist.items.take(3).forEach { item ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (item.done) Icons.Filled.CheckBox
                                    else Icons.Outlined.CheckBoxOutlineBlank,
                                    contentDescription = null,
                                    tint = if (item.done) labelColor else DarkTokens.Muted,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = item.text,
                                    color = if (item.done) DarkTokens.Muted else DarkTokens.Ink,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        textDecoration = if (item.done) TextDecoration.LineThrough else null,
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (checklist.total > 3) {
                            Text(
                                text = "+${checklist.total - 3} more · ${checklist.doneCount}/${checklist.total} done",
                                color = DarkTokens.Muted,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ColumnHeader(
    label: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = DarkTokens.Ink,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = count.toString(),
            color = DarkTokens.Muted,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
