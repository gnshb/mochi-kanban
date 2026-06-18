package com.mochikanban.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mochikanban.app.ui.board.BoardScreen
import com.mochikanban.app.ui.settings.SettingsScreen
import com.mochikanban.app.ui.theme.DarkTokens
import com.mochikanban.app.ui.theme.MochiKanbanTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* outcome ignored */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(DarkTokens.Background.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(DarkTokens.Background.toArgb()),
        )
        maybeRequestNotificationPermission()
        val openCardId = intent.getStringExtra("cardId")
        val openActionCardId = intent.getStringExtra("actionCardId")
        val openQuickAdd = intent.getStringExtra("quickAdd") == "1"
        setContent {
            MochiKanbanTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    App(
                        openCardId = openCardId,
                        openActionCardId = openActionCardId,
                        openQuickAdd = openQuickAdd,
                    )
                }
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun App(
    openCardId: String? = null,
    openActionCardId: String? = null,
    openQuickAdd: Boolean = false,
) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "board") {
        composable("board") {
            BoardScreen(
                onOpenSettings = { nav.navigate("settings") },
                initialEditCardId = openCardId,
                initialActionCardId = openActionCardId,
                initialQuickAdd = openQuickAdd,
            )
        }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
