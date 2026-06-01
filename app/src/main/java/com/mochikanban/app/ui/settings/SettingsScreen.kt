package com.mochikanban.app.ui.settings

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mochikanban.app.sync.SyncSnapshot
import com.mochikanban.app.ui.theme.DarkTokens
import com.mochikanban.app.util.HexColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val calendars by vm.calendarList.collectAsStateWithLifecycle()
    val syncSnap by vm.syncStatus.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var pendingEmail by remember { mutableStateOf<String?>(null) }
    var manageCalendarsEmail by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(syncSnap) {
        when (val s = syncSnap) {
            is SyncSnapshot.Success -> snackbar.showSnackbar(s.message)
            is SyncSnapshot.Failure -> snackbar.showSnackbar("Sync failed: ${s.message}")
            else -> Unit
        }
    }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val email = pendingEmail
            if (data != null && email != null) {
                vm.completeAuthorization(email, data)
                scope.launch { snackbar.showSnackbar("Signed in as $email") }
            }
            pendingEmail = null
        } else {
            pendingEmail = null
            scope.launch { snackbar.showSnackbar("Sign-in cancelled") }
        }
    }

    val deepLinkLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { /* informational only */ }

    // Re-read permission state whenever we return to this screen (e.g. back from
    // the system settings deep links), so the status rows stay accurate.
    val lifecycleOwner = LocalLifecycleOwner.current
    var permTick by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) permTick++
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    val notificationsGranted = remember(permTick) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) true
        else ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
    val canExact = remember(permTick) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) true
        else ctx.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { permTick++ }

    Scaffold(
        containerColor = DarkTokens.Background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = DarkTokens.Ink,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkTokens.Background,
                    titleContentColor = DarkTokens.Ink,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) { d -> Snackbar(snackbarData = d) } },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (vm.syncAvailable) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Google Calendar", style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f))
                    AnimatedVisibility(visible = syncSnap is SyncSnapshot.Syncing) {
                        CircularProgressIndicator(
                            color = DarkTokens.MintDark,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                state.accounts.forEach { email ->
                    AccountCard(
                        email = email,
                        calendars = calendars.filter { it.accountEmail == email },
                        onManageCalendars = { manageCalendarsEmail = email },
                        onRemove = { vm.removeAccount(email) },
                    )
                }

                if (state.accounts.size > 1) {
                    Text(
                        "New events sync to",
                        color = DarkTokens.Muted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    state.accounts.forEach { email ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.setDefaultAccount(email) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(
                                        if (email == state.defaultAccount) DarkTokens.MintDark
                                        else DarkTokens.SurfaceVariant,
                                        CircleShape,
                                    ),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(email, color = DarkTokens.Ink, modifier = Modifier.weight(1f))
                        }
                    }
                }

                if (state.accounts.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { vm.syncNow() },
                            enabled = syncSnap !is SyncSnapshot.Syncing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DarkTokens.MintDark,
                                contentColor = DarkTokens.Background,
                            ),
                        ) { Text("Sync now") }
                        when (val s = syncSnap) {
                            is SyncSnapshot.Success ->
                                Text(s.message, color = DarkTokens.Muted,
                                    modifier = Modifier.padding(start = 8.dp).align(Alignment.CenterVertically))
                            is SyncSnapshot.Failure ->
                                Text(s.message, color = DarkTokens.RoseDark,
                                    modifier = Modifier.padding(start = 8.dp).align(Alignment.CenterVertically))
                            else -> Unit
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color = DarkTokens.OutlineVariant)
                }

                Button(
                    onClick = {
                        scope.launch {
                            try {
                                val r = vm.signInBlocking(ctx)
                                val intentSender = r.needsConsentIntentSender
                                if (intentSender != null) {
                                    pendingEmail = r.email
                                    consentLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                                } else {
                                    vm.rememberAccount(r.email)
                                    snackbar.showSnackbar("Signed in as ${r.email}")
                                }
                            } catch (t: Throwable) {
                                snackbar.showSnackbar(t.message ?: "Sign-in failed")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkTokens.MintDark,
                        contentColor = DarkTokens.Background,
                    ),
                ) { Text(if (state.accounts.isEmpty()) "Sign in with Google" else "Add another account") }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = DarkTokens.OutlineVariant)
            }

            Text("Reminders", style = MaterialTheme.typography.titleLarge)
            Text(
                "Reminders fire as local notifications. These permissions control whether they show, and whether they fire at the exact time.",
                color = DarkTokens.Muted,
                style = MaterialTheme.typography.bodyMedium,
            )

            PermissionRow(
                title = "Notifications",
                granted = notificationsGranted,
                grantedHint = "Reminders can show",
                deniedHint = "Reminders are silenced",
                actionLabel = "Enable",
                onAction = {
                    // On 13+ ask directly the first time; otherwise the request is a
                    // no-op (already decided), so fall back to the settings page.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName)
                        deepLinkLauncher.launch(intent)
                    }
                },
                onOpenSettings = {
                    val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName)
                    deepLinkLauncher.launch(intent)
                },
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PermissionRow(
                    title = "Exact alarms",
                    granted = canExact,
                    grantedHint = "Reminders fire on time",
                    deniedHint = "Reminders may be delayed",
                    actionLabel = "Allow",
                    onAction = {
                        val intent = Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        intent.data = Uri.parse("package:${ctx.packageName}")
                        deepLinkLauncher.launch(intent)
                    },
                    onOpenSettings = {
                        val intent = Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        intent.data = Uri.parse("package:${ctx.packageName}")
                        deepLinkLauncher.launch(intent)
                    },
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    manageCalendarsEmail?.let { email ->
        ManageCalendarsDialog(
            email = email,
            calendars = calendars.filter { it.accountEmail == email },
            onToggle = { id, sel -> vm.setCalendarSelected(id, sel) },
            onClose = { manageCalendarsEmail = null },
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    granted: Boolean,
    grantedHint: String,
    deniedHint: String,
    actionLabel: String,
    onAction: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkTokens.Surface),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        if (granted) DarkTokens.MintDark else DarkTokens.RoseDark,
                        CircleShape,
                    ),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = DarkTokens.Ink, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (granted) grantedHint else deniedHint,
                    color = DarkTokens.Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (granted) {
                OutlinedButton(onClick = onOpenSettings) { Text("Manage", color = DarkTokens.Muted) }
            } else {
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkTokens.MintDark,
                        contentColor = DarkTokens.Background,
                    ),
                ) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun AccountCard(
    email: String,
    calendars: List<com.mochikanban.app.data.db.entity.CalendarEntity>,
    onManageCalendars: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkTokens.Surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    email,
                    color = DarkTokens.Ink,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRemove) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Remove",
                        tint = DarkTokens.RoseDark)
                }
            }

            if (calendars.isEmpty()) {
                Text(
                    "Tap 'Sync now' to fetch this account's calendars.",
                    color = DarkTokens.Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${calendars.count { it.selected }} of ${calendars.size} calendars syncing",
                        color = DarkTokens.Muted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = onManageCalendars) {
                        Text("Manage", color = DarkTokens.MintDark)
                    }
                }
            }
        }
    }
}

@Composable
private fun ManageCalendarsDialog(
    email: String,
    calendars: List<com.mochikanban.app.data.db.entity.CalendarEntity>,
    onToggle: (String, Boolean) -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = DarkTokens.Surface,
        title = { Text("Calendars", color = DarkTokens.Ink) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    email,
                    color = DarkTokens.Muted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                calendars.forEach { cal ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = cal.selected,
                            onCheckedChange = { onToggle(cal.id, it) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = DarkTokens.MintDark,
                                uncheckedColor = DarkTokens.Outline,
                            ),
                        )
                        Text(
                            text = cal.summary + if (cal.primary) " (primary)" else "",
                            color = DarkTokens.Ink,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text("Done", color = DarkTokens.MintDark) }
        },
    )
}
