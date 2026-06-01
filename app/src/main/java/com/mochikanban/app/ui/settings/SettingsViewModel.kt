package com.mochikanban.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochikanban.app.BuildConfig
import com.mochikanban.app.data.db.entity.CalendarEntity
import com.mochikanban.app.data.repo.CalendarRepository
import com.mochikanban.app.sync.SyncStatus
import com.mochikanban.app.sync.SyncTrigger
import com.mochikanban.app.sync.auth.GoogleAuth
import com.mochikanban.app.sync.auth.TokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val accounts: List<String> = emptyList(),
    val defaultAccount: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val tokenStore: TokenStore,
    private val auth: GoogleAuth,
    private val calendars: CalendarRepository,
    private val sync: SyncTrigger,
    val syncStatus: SyncStatus,
) : ViewModel() {

    val syncAvailable: Boolean = BuildConfig.GOOGLE_OAUTH_CLIENT_ID.isNotBlank()

    private val _state = MutableStateFlow(
        SettingsUiState(accounts = tokenStore.emails(), defaultAccount = tokenStore.defaultAccount()),
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private fun refreshAccounts() {
        _state.value = _state.value.copy(
            accounts = tokenStore.emails(),
            defaultAccount = tokenStore.defaultAccount(),
        )
    }

    val calendarList: StateFlow<List<CalendarEntity>> =
        calendars.observe().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    suspend fun signInBlocking(activityContext: Context): GoogleAuth.SignInResult =
        auth.signIn(activityContext, BuildConfig.GOOGLE_OAUTH_CLIENT_ID)

    fun rememberAccount(email: String) {
        tokenStore.add(email)
        refreshAccounts()
        sync.requestFullSync()
    }

    fun completeAuthorization(email: String, intent: android.content.Intent): String? =
        auth.completeAuthorization(email, intent)?.also { rememberAccount(email) }

    fun removeAccount(email: String) {
        tokenStore.remove(email)
        viewModelScope.launch { calendars.clearForAccount(email) }
        refreshAccounts()
    }

    fun setCalendarSelected(id: String, selected: Boolean) {
        viewModelScope.launch { calendars.setSelected(id, selected) }
    }

    fun setDefaultAccount(email: String) {
        tokenStore.setDefaultAccount(email)
        refreshAccounts()
    }

    fun syncNow() { sync.requestFullSync() }
}
