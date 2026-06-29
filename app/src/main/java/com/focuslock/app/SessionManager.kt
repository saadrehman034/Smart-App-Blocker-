package com.focuslock.app

import android.content.Context

object SessionManager {
    private const val PREFS = "session_prefs"
    private const val KEY_ACTIVE = "is_active"
    private const val KEY_END_TIME = "end_time"
    private const val KEY_BLOCKED_APPS = "blocked_apps"

    // In-memory set of packages the user unlocked mid-session (cleared on session end/relock)
    private val temporarilyUnlocked = mutableSetOf<String>()

    fun startSession(context: Context) {
        val endTime = System.currentTimeMillis() + 3_600_000L
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ACTIVE, true)
            .putLong(KEY_END_TIME, endTime)
            .apply()
        temporarilyUnlocked.clear()
    }

    fun endSession(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ACTIVE, false)
            .putLong(KEY_END_TIME, 0L)
            .apply()
        temporarilyUnlocked.clear()
    }

    fun isSessionActive(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ACTIVE, false)) return false
        if (System.currentTimeMillis() >= prefs.getLong(KEY_END_TIME, 0L)) {
            endSession(context)
            return false
        }
        return true
    }

    fun getEndTime(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_END_TIME, 0L)

    fun getRemainingMinutes(context: Context): Long {
        val remaining = getEndTime(context) - System.currentTimeMillis()
        return if (remaining > 0L) remaining / 60_000L else 0L
    }

    fun saveBlockedApps(context: Context, packages: Set<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY_BLOCKED_APPS, packages).apply()
    }

    fun getBlockedApps(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_BLOCKED_APPS, emptySet()) ?: emptySet()

    fun isAppBlocked(context: Context, packageName: String): Boolean {
        if (!isSessionActive(context)) return false
        if (temporarilyUnlocked.contains(packageName)) return false
        return getBlockedApps(context).contains(packageName)
    }

    /** Called when correct PIN entered in BlockOverlayActivity — allows one pass-through. */
    fun temporarilyUnlock(packageName: String) {
        temporarilyUnlocked.add(packageName)
    }

    /** Called by AccessibilityService when app leaves foreground — re-engages block. */
    fun reLock(packageName: String) {
        temporarilyUnlocked.remove(packageName)
    }
}
