package com.focuslock.app

import android.content.Context
import java.security.MessageDigest

object PinManager {
    private const val PREFS = "pin_prefs"
    private const val KEY_HASH = "pin_hash"

    fun isPinSet(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_HASH, null) != null

    fun setPin(context: Context, pin: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_HASH, sha256(pin)).apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_HASH, null) ?: return false
        return sha256(pin) == stored
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
