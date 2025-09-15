package pl.poznan.put.boatcontroller.auth

import android.content.Context
import androidx.core.content.edit

object TokenManager {
    private const val PREFS_NAME = "bc_prefs"
    private const val TOKEN_KEY = "token"

    fun saveToken(context: Context, token: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(TOKEN_KEY, token) }
    }

    fun getToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(TOKEN_KEY, null)
    }
}