package com.mochikanban.app.sync.engine

import com.mochikanban.app.data.db.entity.CardEntity

object ConflictResolver {
    /** Returns true if the incoming remote card should win over the existing local copy. */
    fun remoteWins(local: CardEntity?, incomingRemoteUpdatedAt: Long?): Boolean {
        if (local == null) return true
        if (!local.dirty) return true
        if (incomingRemoteUpdatedAt == null) return false
        return incomingRemoteUpdatedAt > local.updatedAtLocal
    }
}
