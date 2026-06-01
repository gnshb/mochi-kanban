package com.mochikanban.app.sync.engine

import com.mochikanban.app.data.db.dao.CalendarDao
import com.mochikanban.app.data.db.dao.CardDao
import com.mochikanban.app.data.db.dao.SyncStateDao
import com.mochikanban.app.data.db.entity.CalendarEntity
import com.mochikanban.app.data.db.entity.SyncStateEntity
import com.mochikanban.app.reminders.ReminderScheduler
import com.mochikanban.app.sync.SyncStatus
import com.mochikanban.app.sync.auth.GoogleAuth
import com.mochikanban.app.sync.auth.TokenStore
import com.mochikanban.app.sync.net.CalendarApi
import com.mochikanban.app.sync.net.EventDto
import com.mochikanban.app.sync.net.EventMapper
import com.mochikanban.app.util.Time
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncEngine @Inject constructor(
    private val api: CalendarApi,
    private val cardDao: CardDao,
    private val calendarDao: CalendarDao,
    private val syncStateDao: SyncStateDao,
    private val outboxProcessor: OutboxProcessor,
    private val reminders: ReminderScheduler,
    private val tokenStore: TokenStore,
    private val auth: GoogleAuth,
    private val status: SyncStatus,
    private val eventMapper: EventMapper,
    private val widget: com.mochikanban.app.widget.WidgetUpdater,
) {

    suspend fun runOnce(): Result<Unit> = runCatching {
        val accounts = tokenStore.emails()
        if (accounts.isEmpty()) {
            status.finishSync("No accounts configured")
            return@runCatching
        }
        status.startSync()
        var totalFetched = 0
        var listError: String? = null
        for (email in accounts) {
            try {
                ensureCalendarList(email)
            } catch (t: Throwable) {
                listError = "Calendar list ($email): ${t.message ?: t.javaClass.simpleName}"
            }
        }
        outboxProcessor.drain()

        for (cal in calendarDao.selected()) {
            try {
                totalFetched += pullCalendar(cal.accountEmail, cal.id, cal.backgroundColor)
            } catch (t: Throwable) {
                status.failSync("Sync (${cal.summary}): ${t.message ?: t.javaClass.simpleName}")
                return@runCatching
            }
        }
        // Sync writes via cardDao directly (not the repo), so refresh the widget here.
        widget.refreshNow()
        if (listError != null) {
            status.failSync(listError)
        } else {
            // Always settle to a "done, nothing pending" message — a lingering
            // "N updates" reads like work is still outstanding.
            status.finishSync("Up to date")
        }
    }.onFailure {
        status.failSync(it.message ?: it.javaClass.simpleName)
    }

    private suspend fun ensureCalendarList(email: String) {
        val token = auth.freshAccessToken(email)
            ?: throw IllegalStateException("Not signed in: $email")
        val resp = api.listCalendars("Bearer $token")
        if (!resp.isSuccessful) {
            throw IllegalStateException("listCalendars ${resp.code()}: ${resp.errorBody()?.string()?.take(200)}")
        }
        val body = resp.body() ?: return
        val existing = calendarDao.observe().first()
            .filter { it.accountEmail == email }
            .associateBy { it.id }
        val isFirst = existing.isEmpty()

        val mapped = body.items.map { entry ->
            val prior = existing[entry.id]
            CalendarEntity(
                id = entry.id,
                accountEmail = email,
                summary = entry.summary,
                primary = entry.primary == true,
                selected = prior?.selected ?: (isFirst && entry.primary == true),
                backgroundColor = entry.backgroundColor,
            )
        }
        if (mapped.isNotEmpty()) calendarDao.upsertAll(mapped)
    }

    private suspend fun pullCalendar(email: String, calendarId: String, calendarColor: String?): Int {
        var state = syncStateDao.get(calendarId) ?: SyncStateEntity(calendarId = calendarId)
        val token = auth.freshAccessToken(email)
            ?: throw IllegalStateException("Not signed in: $email")
        val authHeader = "Bearer $token"
        var ingested = 0
        var pageToken: String? = null
        var nextSyncToken: String? = state.syncToken

        do {
            val resp = if (state.syncToken != null) {
                api.listEvents(authHeader, calendarId, syncToken = state.syncToken, pageToken = pageToken)
            } else {
                api.listEvents(
                    authHeader, calendarId,
                    timeMin = isoUtc(Time.startOfToday()),
                    timeMax = isoUtc(Time.now() + WINDOW_DAYS * 86_400_000L),
                    pageToken = pageToken,
                )
            }
            if (resp.code() == 410) {
                state = state.copy(syncToken = null)
                syncStateDao.upsert(state)
                pageToken = null
                continue
            }
            if (!resp.isSuccessful) {
                throw IllegalStateException("listEvents ${resp.code()}: ${resp.errorBody()?.string()?.take(200)}")
            }
            val body = resp.body() ?: return ingested

            for (event in body.items) {
                if (ingest(calendarId, event, calendarColor)) ingested++
            }

            pageToken = body.nextPageToken
            if (body.nextSyncToken != null) nextSyncToken = body.nextSyncToken
        } while (pageToken != null)

        syncStateDao.upsert(state.copy(syncToken = nextSyncToken, lastFullSyncAt = Time.now()))
        return ingested
    }

    private suspend fun ingest(calendarId: String, event: EventDto, calendarColor: String?): Boolean {
        val eventId = event.id ?: return false
        val existing = cardDao.byRemoteId(eventId)
        val remoteUpdated = event.updated?.let {
            runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull()
        }
        if (!ConflictResolver.remoteWins(existing, remoteUpdated)) return false
        val mapped = eventMapper.fromRemote(event, calendarId, existing, calendarColor) ?: return false
        // Keep only the next-15-days window; prune events that fall (or move) past it.
        val windowEnd = Time.now() + WINDOW_DAYS * 86_400_000L
        val outOfWindow = mapped.startUtc != null && mapped.startUtc > windowEnd
        if (mapped.deletedLocal || outOfWindow) {
            if (existing != null) cardDao.hardDelete(existing.id)
            return existing != null
        }
        cardDao.upsert(mapped)
        reminders.reschedule(mapped)
        return true
    }

    private fun isoUtc(ms: Long): String =
        OffsetDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    private companion object {
        const val WINDOW_DAYS = 15L
    }
}
