package com.mochikanban.app.sync.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores a list of signed-in account emails. Access tokens are re-vended on
 * demand by AuthorizationClient (which uses Play Services as the cache), so
 * we don't persist them ourselves.
 */
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    }

    private val prefs by lazy {
        EncryptedSharedPreferences.create(
            ctx,
            "mochi_oauth",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun emails(): List<String> {
        val raw = prefs.getString(KEY_EMAILS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList { for (i in 0 until arr.length()) add(arr.getString(i)) }
        } catch (_: Throwable) { emptyList() }
    }

    fun isConfigured(): Boolean = emails().isNotEmpty()

    fun add(email: String) {
        val current = emails().toMutableList()
        if (current.none { it.equals(email, ignoreCase = true) }) current.add(email)
        save(current)
    }

    fun remove(email: String) {
        save(emails().filterNot { it.equals(email, ignoreCase = true) })
        if (defaultAccount()?.equals(email, ignoreCase = true) == true) {
            prefs.edit().remove(KEY_DEFAULT_ACCOUNT).apply()
        }
    }

    fun clear() { prefs.edit().clear().apply() }

    /**
     * Account whose calendar new app-created events sync to. Falls back to the
     * sole account when only one exists; null if unset and multiple exist.
     */
    fun defaultAccount(): String? {
        val all = emails()
        val stored = prefs.getString(KEY_DEFAULT_ACCOUNT, null)
            ?.takeIf { saved -> all.any { it.equals(saved, ignoreCase = true) } }
        return stored ?: all.singleOrNull()
    }

    fun setDefaultAccount(email: String) {
        prefs.edit().putString(KEY_DEFAULT_ACCOUNT, email).apply()
    }

    private fun save(list: List<String>) {
        val arr = JSONArray()
        list.forEach(arr::put)
        prefs.edit().putString(KEY_EMAILS, arr.toString()).apply()
    }

    private companion object {
        const val KEY_EMAILS = "emails"
        const val KEY_DEFAULT_ACCOUNT = "default_account"
    }
}
