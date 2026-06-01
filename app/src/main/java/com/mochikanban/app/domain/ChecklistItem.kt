package com.mochikanban.app.domain

import kotlinx.serialization.Serializable

@Serializable
data class ChecklistItem(
    val id: String,
    val text: String,
    val done: Boolean = false,
)

@Serializable
data class Checklist(
    val items: List<ChecklistItem> = emptyList(),
) {
    val total: Int get() = items.size
    val doneCount: Int get() = items.count { it.done }
    val isEmpty: Boolean get() = items.isEmpty()
}
