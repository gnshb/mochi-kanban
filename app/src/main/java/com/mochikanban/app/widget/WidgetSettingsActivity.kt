package com.mochikanban.app.widget

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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mochikanban.app.data.WidgetPrefs
import com.mochikanban.app.ui.theme.DarkTokens
import com.mochikanban.app.ui.theme.MochiKanbanTheme
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Standalone settings surface for the home-screen widget, launched from the gear
 * icon in the widget header. Rendered as a translucent dialog over the launcher.
 */
@AndroidEntryPoint
class WidgetSettingsActivity : ComponentActivity() {

    @Inject lateinit var widgetPrefs: WidgetPrefs
    @Inject lateinit var widgetUpdater: GlanceWidgetUpdater

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val opacityFlow = widgetPrefs.opacity
            .stateIn(lifecycleScope, SharingStarted.Eagerly, 0.9f)

        setContent {
            MochiKanbanTheme {
                val opacity by opacityFlow.collectAsState()
                // Local drag value; persisted once on release to avoid out-of-order
                // writes (the bug where only the first change stuck).
                var dragValue by remember { mutableStateOf<Float?>(null) }
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
                            // Swallow taps so they don't dismiss via the scrim.
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {},
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                "Widget",
                                style = MaterialTheme.typography.titleLarge,
                                color = DarkTokens.Ink,
                            )
                            Text(
                                "Background opacity",
                                color = DarkTokens.Muted,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Slider(
                                value = dragValue ?: opacity,
                                onValueChange = { dragValue = it },
                                onValueChangeFinished = {
                                    val value = dragValue
                                    if (value != null) {
                                        lifecycleScope.launch {
                                            widgetPrefs.setOpacity(value)
                                            widgetUpdater.refreshNow()
                                        }
                                    }
                                },
                                valueRange = 0.3f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = DarkTokens.MintDark,
                                    activeTrackColor = DarkTokens.MintDark,
                                    inactiveTrackColor = DarkTokens.SurfaceVariant,
                                ),
                            )
                            Button(
                                onClick = { finish() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DarkTokens.MintDark,
                                    contentColor = DarkTokens.Background,
                                ),
                            ) { Text("Done") }
                        }
                    }
                }
            }
        }
    }
}
