package com.focuslock.app

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class AdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        // We can't prevent deactivation programmatically, but we can show a message.
        // Open the app so the user sees the PIN prompt before they proceed.
        val open = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("from_admin_disable", true)
        }
        context.startActivity(open)
        return "Open FocusLock and enter your PIN before deactivating device admin."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        // Admin deactivated — app can now be uninstalled normally.
    }
}
