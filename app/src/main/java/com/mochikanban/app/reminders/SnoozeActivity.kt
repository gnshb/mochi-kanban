package com.mochikanban.app.reminders

import android.app.NotificationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.mochikanban.app.data.repo.CardRepository
import com.mochikanban.app.ui.theme.DarkTokens
import com.mochikanban.app.ui.theme.MochiKanbanTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

/** Snooze options shown when the user taps Snooze on a reminder notification. */
@AndroidEntryPoint
class SnoozeActivity : ComponentActivity() {

    @Inject lateinit var repo: CardRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cardId = intent.getStringExtra(EXTRA_CARD_ID)
        getSystemService(NotificationManager::class.java)?.cancel(cardId?.hashCode() ?: 0)
        if (cardId == null) { finish(); return }

        val options = listOf(
            "15 minutes" to 15,
            "30 minutes" to 30,
            "1 hour" to 60,
            "3 hours" to 180,
            "Tomorrow 9:00" to minutesUntilTomorrow9(),
        )

        setContent {
            MochiKanbanTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { finish() },
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = DarkTokens.Surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {},
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("Snooze", style = MaterialTheme.typography.titleLarge, color = DarkTokens.Ink)
                            options.forEach { (label, minutes) ->
                                Button(
                                    onClick = { snooze(cardId, minutes) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DarkTokens.SurfaceVariant,
                                        contentColor = DarkTokens.Ink,
                                    ),
                                ) { Text(label) }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun snooze(cardId: String, minutes: Int) {
        lifecycleScope.launch {
            repo.snooze(cardId, minutes)
            finish()
        }
    }

    private fun minutesUntilTomorrow9(): Int {
        val zone = ZoneId.systemDefault()
        val now = java.time.ZonedDateTime.now(zone)
        val target = now.toLocalDate().plusDays(1).atTime(LocalTime.of(9, 0)).atZone(zone)
        return java.time.Duration.between(now, target).toMinutes().toInt().coerceAtLeast(1)
    }

    companion object {
        const val EXTRA_CARD_ID = "cardId"
    }
}
