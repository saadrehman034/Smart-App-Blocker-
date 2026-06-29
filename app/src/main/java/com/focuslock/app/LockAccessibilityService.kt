package com.focuslock.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class LockAccessibilityService : AccessibilityService() {

    private var lastForegroundPackage = ""

    override fun onServiceConnected() {
        serviceInfo = serviceInfo.also {
            it.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            it.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            it.notificationTimeout = 100
            it.packageNames = null // watch all packages
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return

        // Never block our own overlay or the system UI
        if (pkg == packageName) return
        if (pkg == "com.android.systemui") return

        if (pkg != lastForegroundPackage) {
            // Previous app left foreground — re-engage its lock
            if (lastForegroundPackage.isNotEmpty()) {
                SessionManager.reLock(lastForegroundPackage)
            }
            lastForegroundPackage = pkg
        }

        if (SessionManager.isAppBlocked(this, pkg)) {
            val overlay = Intent(this, BlockOverlayActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                putExtra(BlockOverlayActivity.EXTRA_BLOCKED_PACKAGE, pkg)
            }
            startActivity(overlay)
        }
    }

    override fun onInterrupt() {}
}
