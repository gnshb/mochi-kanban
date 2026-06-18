package com.mochikanban.app.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.mochikanban.app.MainActivity
import com.mochikanban.app.R
import com.mochikanban.app.data.WidgetPrefs
import com.mochikanban.app.data.db.entity.CardEntity
import com.mochikanban.app.data.db.entity.LabelEntity
import com.mochikanban.app.data.repo.LabelRepository
import com.mochikanban.app.domain.GoogleCalendarColors
import com.mochikanban.app.domain.Column as KanbanColumn
import com.mochikanban.app.ui.theme.DarkTokens
import com.mochikanban.app.ui.theme.glowTint
import com.mochikanban.app.ui.theme.matteLabelColor
import com.mochikanban.app.util.HexColor
import com.mochikanban.app.util.Time
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class KanbanGlanceWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun widgetRepository(): WidgetRepository
        fun labelRepository(): LabelRepository
        fun widgetPrefs(): WidgetPrefs
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Load defensively: an exception here surfaces to the launcher as
        // "Couldn't add widget", so always fall back to empty content instead.
        val data = runCatching {
            val entry = EntryPointAccessors
                .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            WidgetData(
                cards = entry.widgetRepository().allCards(),
                labels = entry.labelRepository().all().associateBy { it.id },
                opacity = entry.widgetPrefs().opacitySnapshot(),
                completingId = entry.widgetPrefs().completingIdSnapshot(),
            )
        }.getOrElse { WidgetData(emptyList(), emptyMap(), 0.9f, null) }

        provideContent {
            GlanceTheme { ListWidget(data.cards, data.labels, data.opacity, data.completingId) }
        }
    }

    private data class WidgetData(
        val cards: List<CardEntity>,
        val labels: Map<String, LabelEntity>,
        val opacity: Float,
        val completingId: String?,
    )
}

private val InkLight = ColorProvider(DarkTokens.Ink)
private val Muted = ColorProvider(DarkTokens.Muted)
private val OutlineColor = ColorProvider(DarkTokens.Outline)
private val DATE_FMT = DateTimeFormatter.ofPattern("MMM d")

@androidx.compose.runtime.Composable
private fun ListWidget(
    allCards: List<CardEntity>,
    labelsById: Map<String, LabelEntity>,
    opacity: Float,
    completingId: String?,
) {
    val now = System.currentTimeMillis()
    val todayStart = Time.startOfToday()
    val items = allCards
        .filterNot { it.isScheduledBefore(todayStart) }
        .filterNot { it.isLikelyLegacyAllDayImport() }
        .filter { it.effectiveColumn(now) == KanbanColumn.TODO }
        .sortedWith(
            compareBy<CardEntity> { it.todoSortBucket(now) }
                .thenBy { it.startUtc ?: Long.MAX_VALUE }
                .thenBy { it.position },
        )

    val bg = DarkTokens.Background.copy(alpha = opacity)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            // Tapping anywhere on the widget opens the app (cards/icons override this).
            .clickable(actionStartActivity<MainActivity>())
            .background(ColorProvider(bg))
            .cornerRadius(24.dp)
            .padding(12.dp),
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "To do",
                style = TextStyle(
                    color = InkLight,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                ),
            )
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = items.size.toString(),
                style = TextStyle(color = Muted, fontSize = 14.sp),
            )
            Spacer(GlanceModifier.width(10.dp))
            Text(
                text = "⚙",
                style = TextStyle(color = Muted, fontSize = 16.sp),
                modifier = GlanceModifier.clickable(actionStartActivity<WidgetSettingsActivity>()),
            )
            Spacer(GlanceModifier.defaultWeight())
            // Fixed square target with a centred vector "+" (crisp & properly centred).
            Box(
                modifier = GlanceModifier
                    .size(32.dp)
                    .clickable(
                        actionStartActivity<MainActivity>(
                            parameters = actionParametersOf(
                                ActionParameters.Key<String>("quickAdd") to "1",
                            )
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_add),
                    contentDescription = "New card",
                    colorFilter = ColorFilter.tint(InkLight),
                    modifier = GlanceModifier.size(20.dp),
                )
            }
        }

        if (items.isEmpty()) {
            Text(
                text = "Nothing on the list",
                style = TextStyle(color = Muted, fontSize = 13.sp),
                modifier = GlanceModifier.padding(top = 4.dp),
            )
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(items, itemId = { it.id.hashCode().toLong() }) { card ->
                    WidgetRow(card, labelsById, now = now, completing = card.id == completingId)
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun WidgetRow(
    card: CardEntity,
    labelsById: Map<String, LabelEntity>,
    now: Long,
    completing: Boolean = false,
) {
    val labelAccent = HexColor.parseOr(
        card.labelId?.let { labelsById[it]?.colorHex },
        HexColor.parseOr(GoogleCalendarColors.defaultEventColor, DarkTokens.SkyDark),
    ).matteLabelColor()
    val actionRequired = card.isActionRequired(now)
    val attentionWindow = card.isAttentionWindow(now)
    val rowBackground = when {
        actionRequired -> DarkTokens.SurfaceVariant.glowTint(DarkTokens.Error, 0.30f).copy(alpha = 0.88f)
        attentionWindow -> DarkTokens.SurfaceVariant.glowTint(labelAccent, 0.34f).copy(alpha = 0.88f)
        else -> DarkTokens.Surface.copy(alpha = 0.72f)
    }
    val openCardKey = ActionParameters.Key<String>(
        if (actionRequired) "actionCardId" else "cardId",
    )
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            // Inset from the right so the system scroll bar doesn't overlap the card.
            .padding(vertical = 4.dp)
            .padding(end = 12.dp)
            .cornerRadius(12.dp)
            .background(ColorProvider(rowBackground))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left color stripe
        Box(
            modifier = GlanceModifier
                .width(4.dp)
                .height(20.dp)
                .cornerRadius(2.dp)
                .background(ColorProvider(labelAccent)),
            content = {},
        )
        Spacer(GlanceModifier.width(8.dp))
        // Tap-to-complete checkbox; fills with a check while completing.
        Box(
            modifier = GlanceModifier
                .width(20.dp)
                .height(20.dp)
                .cornerRadius(6.dp)
                .background(ColorProvider(if (completing) labelAccent else labelAccent.copy(alpha = 0.2f)))
                .clickable(
                    actionRunCallback<CompleteCardAction>(
                        actionParametersOf(CompleteCardAction.KEY_CARD_ID to card.id)
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (completing) {
                Text(
                    text = "✓",
                    style = TextStyle(color = ColorProvider(DarkTokens.Background), fontSize = 13.sp),
                )
            }
        }
        Spacer(GlanceModifier.width(10.dp))
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .clickable(
                    actionStartActivity<MainActivity>(
                        parameters = actionParametersOf(
                            openCardKey to card.id,
                        )
                    )
                ),
        ) {
            Text(
                text = card.title,
                style = TextStyle(
                    color = if (completing) Muted else InkLight,
                    fontSize = 14.sp,
                    textDecoration = if (completing) TextDecoration.LineThrough else null,
                ),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.width(8.dp))
        if (card.startUtc != null) {
            Text(
                text = formatDate(card.startUtc),
                style = TextStyle(color = Muted, fontSize = 12.sp),
            )
        }
    }
}

private fun formatDate(ms: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (date) {
        today -> Instant.ofEpochMilli(ms).atZone(zone).toLocalTime()
            .format(DateTimeFormatter.ofPattern("HH:mm"))
        today.plusDays(1) -> "Tomorrow"
        else -> date.format(DATE_FMT)
    }
}
