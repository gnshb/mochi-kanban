package com.mochikanban.app.sync.auth

import android.accounts.Account
import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google sign-in via Credential Manager (native one-tap) + AuthorizationClient
 * (calendar scope). The two are layered because Credential Manager returns an
 * ID token (proves identity) while Calendar API needs an OAuth access token.
 *
 * Multi-account: each call to [signIn] picks an account interactively; the
 * email is stored. Background refresh uses [freshAccessToken] which calls
 * AuthorizationClient with `setAccount(email)`.
 */
@Singleton
class GoogleAuth @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val tokenStore: TokenStore,
) {

    data class SignInResult(
        val email: String,
        val accessToken: String?,
        val needsConsentIntentSender: android.content.IntentSender?,
    )

    /**
     * Native one-tap sign-in (Credential Manager) followed by calendar scope
     * authorization. Must be called from an Activity context so the dialog can
     * appear. Returns a [SignInResult]:
     *  - If `needsConsentIntentSender` is non-null, the caller must launch it
     *    via `ActivityResultLauncher` and call [completeAuthorization] with
     *    the returned intent.
     *  - Otherwise `accessToken` is populated and the user is signed in.
     */
    suspend fun signIn(activityContext: Context, serverClientId: String): SignInResult {
        require(serverClientId.isNotBlank()) { "Server client ID is missing" }
        val email = pickGoogleAccount(activityContext, serverClientId)
        val auth = requestCalendarScope(activityContext.applicationContext, email)
        if (auth.hasResolution()) {
            return SignInResult(
                email = email,
                accessToken = null,
                needsConsentIntentSender = auth.pendingIntent?.intentSender,
            )
        }
        tokenStore.add(email)
        return SignInResult(email = email, accessToken = auth.accessToken, needsConsentIntentSender = null)
    }

    /** Caller passes the intent received from the consent PendingIntent's launcher. */
    fun completeAuthorization(email: String, intent: android.content.Intent): String? {
        val result = Identity.getAuthorizationClient(ctx).getAuthorizationResultFromIntent(intent)
        tokenStore.add(email)
        return result.accessToken
    }

    /** Headless silent token retrieval. Returns null if user must reauthorize. */
    suspend fun freshAccessToken(email: String): String? {
        val request = AuthorizationRequest.Builder()
            .setRequestedScopes(listOf(Scope(CALENDAR_SCOPE), Scope(CALENDAR_LIST_SCOPE)))
            .setAccount(Account(email, "com.google"))
            .build()
        val result: AuthorizationResult = try {
            Identity.getAuthorizationClient(ctx).authorize(request).await()
        } catch (_: Throwable) {
            return null
        }
        if (result.hasResolution()) return null
        return result.accessToken
    }

    private suspend fun pickGoogleAccount(activityContext: Context, serverClientId: String): String {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()
        val req = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val manager = CredentialManager.create(activityContext)
        val resp = try {
            manager.getCredential(activityContext, req)
        } catch (e: GetCredentialException) {
            throw IllegalStateException("Sign-in cancelled: ${e.message}", e)
        }
        val cred = resp.credential as? CustomCredential
            ?: throw IllegalStateException("Unsupported credential type")
        if (cred.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw IllegalStateException("Unexpected credential: ${cred.type}")
        }
        val gid = try {
            GoogleIdTokenCredential.createFrom(cred.data)
        } catch (e: GoogleIdTokenParsingException) {
            throw IllegalStateException("Invalid Google ID token", e)
        }
        return gid.id
    }

    private suspend fun requestCalendarScope(appContext: Context, email: String): AuthorizationResult {
        val req = AuthorizationRequest.Builder()
            .setRequestedScopes(listOf(Scope(CALENDAR_SCOPE), Scope(CALENDAR_LIST_SCOPE)))
            .setAccount(Account(email, "com.google"))
            .build()
        return Identity.getAuthorizationClient(appContext).authorize(req).await()
    }

    companion object {
        /** Read/write events on a calendar. Does NOT permit listing the user's calendars. */
        const val CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar.events"

        /** Required by users/me/calendarList; calendar.events alone returns 403 there. */
        const val CALENDAR_LIST_SCOPE = "https://www.googleapis.com/auth/calendar.calendarlist.readonly"
    }
}
