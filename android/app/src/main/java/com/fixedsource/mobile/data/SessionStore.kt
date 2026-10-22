package com.fixedsource.mobile.data

import android.content.Context
import android.content.SharedPreferences
import com.fixedsource.mobile.BuildConfig

object SessionStore {
    private const val PREFS = "fixed_source_session"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    var baseUrl: String
        get() = prefs.getString("base_url", BuildConfig.DEFAULT_BASE_URL)
            ?.takeIf { it.isNotBlank() } ?: BuildConfig.DEFAULT_BASE_URL
        set(value) {
            prefs.edit().putString("base_url", value).apply()
        }

    var token: String?
        get() = prefs.getString("token", null)
        set(value) {
            prefs.edit().putString("token", value).apply()
        }

    var username: String
        get() = prefs.getString("username", "") ?: ""
        set(value) {
            prefs.edit().putString("username", value).apply()
        }

    var name: String
        get() = prefs.getString("name", "现场核查员") ?: "现场核查员"
        set(value) {
            prefs.edit().putString("name", value).apply()
        }

    var role: String
        get() = prefs.getString("role", "") ?: ""
        set(value) {
            prefs.edit().putString("role", value).apply()
        }

    var roleLabel: String
        get() = prefs.getString("role_label", "现场核查人员") ?: "现场核查人员"
        set(value) {
            prefs.edit().putString("role_label", value).apply()
        }

    val isLoggedIn: Boolean
        get() = !token.isNullOrBlank()

    fun saveLogin(base: String, login: LoginResult) {
        prefs.edit()
            .putString("base_url", base)
            .putString("token", login.token)
            .putString("username", login.username)
            .putString("name", login.name)
            .putString("role", login.role)
            .putString("role_label", login.roleLabel)
            .apply()
    }

    fun clearSession() {
        prefs.edit()
            .remove("token")
            .remove("username")
            .remove("name")
            .remove("role")
            .remove("role_label")
            .apply()
    }
}
