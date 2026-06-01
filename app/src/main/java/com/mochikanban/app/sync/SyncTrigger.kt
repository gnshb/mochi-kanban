package com.mochikanban.app.sync

interface SyncTrigger {
    fun requestFlush()
    /** Flush after a short delay; repeated calls debounce, letting a quick drag-back revert. */
    fun requestFlushDelayed()
    fun requestFullSync()
}
