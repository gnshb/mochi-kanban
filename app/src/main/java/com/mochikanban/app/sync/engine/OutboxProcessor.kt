package com.mochikanban.app.sync.engine

import com.mochikanban.app.data.db.dao.CalendarDao
import com.mochikanban.app.data.db.dao.CardDao
import com.mochikanban.app.data.db.dao.OutboxDao
import com.mochikanban.app.data.db.entity.OutboxEntity
import com.mochikanban.app.domain.OpType
import com.mochikanban.app.domain.SyncState
import com.mochikanban.app.sync.auth.GoogleAuth
import com.mochikanban.app.sync.auth.TokenStore
import com.mochikanban.app.sync.net.CalendarApi
import com.mochikanban.app.sync.net.EventMapper
import com.mochikanban.app.util.Time
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OutboxProcessor @Inject constructor(
    private val api: CalendarApi,
    private val outboxDao: OutboxDao,
    private val cardDao: CardDao,
    private val calendarDao: CalendarDao,
    private val tokenStore: TokenStore,
    private val auth: GoogleAuth,
    private val eventMapper: EventMapper,
) {

    suspend fun drain() {
        if (!tokenStore.isConfigured()) return
        val pending = outboxDao.pending()
        for (entry in pending) {
            val card = cardDao.byId(entry.cardId)
            if (card == null) {
                outboxDao.delete(entry)
                continue
            }
            val calendar = card.calendarId?.let { calendarDao.byId(it) }
                ?: defaultTargetCalendar()
                ?: calendarDao.selected().firstOrNull()
                ?: continue
            val email = calendar.accountEmail
            val token = auth.freshAccessToken(email)
            if (token == null) {
                handleFailure(entry, "no token for $email")
                continue
            }
            val authHeader = "Bearer $token"

            try {
                when (entry.opType) {
                    // Create and update are keyed off remoteEventId, not the stored op:
                    // once a card has a remote id we PATCH, so duplicate CREATE entries
                    // (queued by edits before the first sync) can't create extra events.
                    OpType.CREATE, OpType.UPDATE -> {
                        val dto = eventMapper.toRemote(card)
                        val eventId = card.remoteEventId
                        val resp = if (eventId == null) {
                            api.createEvent(authHeader, calendar.id, dto)
                        } else {
                            api.patchEvent(authHeader, calendar.id, eventId, dto)
                        }
                        if (resp.isSuccessful) {
                            val body = resp.body()
                            cardDao.update(
                                card.copy(
                                    remoteEventId = body?.id ?: card.remoteEventId,
                                    etag = body?.etag,
                                    calendarId = calendar.id,
                                    dirty = false,
                                    syncState = SyncState.IDLE,
                                )
                            )
                            outboxDao.delete(entry)
                        } else handleFailure(entry, "push ${resp.code()}")
                    }
                    OpType.DELETE -> {
                        val eventId = card.remoteEventId
                        if (eventId == null) {
                            cardDao.hardDelete(card.id)
                            outboxDao.delete(entry)
                            continue
                        }
                        val resp = api.deleteEvent(authHeader, calendar.id, eventId)
                        if (resp.isSuccessful || resp.code() == 410 || resp.code() == 404) {
                            cardDao.hardDelete(card.id)
                            outboxDao.delete(entry)
                        } else handleFailure(entry, "delete ${resp.code()}")
                    }
                }
            } catch (t: Throwable) {
                handleFailure(entry, t.message ?: t.javaClass.simpleName)
            }
        }
    }

    /** Primary (else first selected) calendar of the user-chosen default account. */
    private suspend fun defaultTargetCalendar() =
        tokenStore.defaultAccount()?.let { email ->
            val cals = calendarDao.forAccount(email)
            cals.firstOrNull { it.primary && it.selected }
                ?: cals.firstOrNull { it.selected }
                ?: cals.firstOrNull { it.primary }
        }

    private suspend fun handleFailure(entry: OutboxEntity, msg: String) {
        outboxDao.update(
            entry.copy(
                attemptCount = entry.attemptCount + 1,
                lastTriedAt = Time.now(),
                lastError = msg,
            )
        )
    }
}
