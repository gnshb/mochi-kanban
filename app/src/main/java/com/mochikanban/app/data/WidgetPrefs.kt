package com.mochikanban.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.widgetDataStore by preferencesDataStore("mochi_widget")

@Singleton
class WidgetPrefs @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    private val keyOpacity = floatPreferencesKey("opacity")

    val opacity: Flow<Float> =
        ctx.widgetDataStore.data.map { (it[keyOpacity] ?: 0.9f).coerceIn(0.3f, 1f) }

    suspend fun opacitySnapshot(): Float =
        (ctx.widgetDataStore.data.first()[keyOpacity] ?: 0.9f).coerceIn(0.3f, 1f)

    suspend fun setOpacity(value: Float) {
        ctx.widgetDataStore.edit { it[keyOpacity] = value.coerceIn(0.3f, 1f) }
    }

    // Card id currently animating its "done" check in the widget (transient).
    private val keyCompleting = stringPreferencesKey("completing_id")

    suspend fun completingIdSnapshot(): String? =
        ctx.widgetDataStore.data.first()[keyCompleting]

    suspend fun setCompleting(cardId: String?) {
        ctx.widgetDataStore.edit {
            if (cardId == null) it.remove(keyCompleting) else it[keyCompleting] = cardId
        }
    }
}
