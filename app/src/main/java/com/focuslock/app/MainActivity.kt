package com.focuslock.app

import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {

    private val REQ_DEVICE_ADMIN = 10
    private var isAuthenticated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!PinManager.isPinSet(this)) {
            showPinSetupDialog()
        } else {
            showPinEntryDialog()
        }
    }

    // ── PIN setup (first launch) ──────────────────────────────────────────────

    private fun showPinSetupDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 24, 60, 0)
        }
        val pin1 = makePin6Field("Enter 6-digit PIN")
        val pin2 = makePin6Field("Confirm PIN")
        container.addView(pin1)
        container.addView(pin2)

        AlertDialog.Builder(this)
            .setTitle("Set Your PIN")
            .setMessage("This PIN protects FocusLock and is required to open or uninstall the app.")
            .setView(container)
            .setCancelable(false)
            .setPositiveButton("Set PIN") { _, _ ->
                val p1 = pin1.text.toString()
                val p2 = pin2.text.toString()
                if (p1.length == 6 && p1 == p2) {
                    PinManager.setPin(this, p1)
                    proceedToMain()
                } else {
                    Toast.makeText(this, "PINs don't match or not 4 digits. Restart to try again.", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
            .show()
    }

    // ── PIN verification (subsequent launches) ────────────────────────────────

    private fun showPinEntryDialog() {
        val pinField = makePin6Field("Enter PIN")

        AlertDialog.Builder(this)
            .setTitle("FocusLock")
            .setMessage("Enter your PIN to continue")
            .setView(LinearLayout(this).apply {
                setPadding(60, 24, 60, 0)
                addView(pinField)
            })
            .setCancelable(false)
            .setPositiveButton("Unlock") { _, _ ->
                if (PinManager.verifyPin(this, pinField.text.toString())) {
                    proceedToMain()
                } else {
                    Toast.makeText(this, "Wrong PIN.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .show()
    }

    // ── Main UI ───────────────────────────────────────────────────────────────

    private fun proceedToMain() {
        isAuthenticated = true
        // Start persistent foreground service
        startForegroundService(Intent(this, LockForegroundService::class.java))

        // Auto-start 1hr lock session on every app open (if not already active)
        if (!SessionManager.isSessionActive(this)) {
            sendBroadcast(Intent(LockForegroundService.ACTION_START_LOCK).apply {
                `package` = packageName
            })
        }

        // Request POST_NOTIFICATIONS (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 20)
            }
        }

        // Request SYSTEM_ALERT_WINDOW if not granted
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }

        buildMainUI()
    }

    private fun buildMainUI() {
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, AdminReceiver::class.java)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        // Instructions banner
        root.addView(TextView(this).apply {
            text = "Step 1: Enable Accessibility Service  →  Step 2: Activate Device Admin  →  Step 3: Select apps  →  Step 4: Use notification to start lock"
            setPadding(0, 0, 0, 20)
        })

        // Accessibility button
        val a11yLabel = if (isAccessibilityEnabled())
            "✓ Accessibility Service Enabled"
        else
            "Enable Accessibility Service (required)"

        root.addView(Button(this).apply {
            text = a11yLabel
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })

        // Device admin button
        val adminLabel = if (dpm.isAdminActive(admin))
            "✓ Device Admin Active"
        else
            "Activate Device Admin (uninstall protection)"

        root.addView(Button(this).apply {
            text = adminLabel
            setOnClickListener {
                if (!dpm.isAdminActive(admin)) {
                    val i = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                        putExtra(
                            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "FocusLock uses device admin to prevent accidental uninstalls during active focus sessions."
                        )
                    }
                    startActivityForResult(i, REQ_DEVICE_ADMIN)
                }
            }
        })

        root.addView(TextView(this).apply {
            text = "Select apps to block:"
            setPadding(0, 24, 0, 8)
            gravity = Gravity.START
        })

        // Scrollable app list
        val savedApps = SessionManager.getBlockedApps(this)
        val userApps = loadUserApps()
        val checkboxMap = LinkedHashMap<String, CheckBox>()

        val appListLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for ((label, pkg) in userApps) {
            val cb = CheckBox(this).apply {
                text = "$label\n$pkg"
                isChecked = savedApps.contains(pkg)
            }
            checkboxMap[pkg] = cb
            appListLayout.addView(cb)
        }

        val scroll = ScrollView(this)
        scroll.addView(appListLayout)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        // Save button
        root.addView(Button(this).apply {
            text = "Save Selection"
            setOnClickListener {
                val selected = checkboxMap.filterValues { it.isChecked }.keys.toSet()
                SessionManager.saveBlockedApps(this@MainActivity, selected)
                Toast.makeText(this@MainActivity, "Saved ${selected.size} app(s)", Toast.LENGTH_SHORT).show()
            }
        })

        setContentView(root)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makePin6Field(hint: String) = EditText(this).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        maxLines = 1
        gravity = Gravity.CENTER
        filters = arrayOf(InputFilter.LengthFilter(6))
    }

    private fun isAccessibilityEnabled(): Boolean {
        return try {
            val enabled = Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
            if (enabled == 0) return false
            val services = Settings.Secure.getString(
                contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            services.contains("$packageName/${packageName}.LockAccessibilityService", ignoreCase = true)
        } catch (_: Exception) { false }
    }

    private fun loadUserApps(): List<Pair<String, String>> {
        val pm = packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        @Suppress("DEPRECATION")
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != packageName }
            .mapNotNull { pkg ->
                try {
                    val info = pm.getApplicationInfo(pkg, 0)
                    Pair(pm.getApplicationLabel(info).toString(), pkg)
                } catch (_: PackageManager.NameNotFoundException) { null }
            }
            .sortedBy { it.first.lowercase() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_DEVICE_ADMIN) {
            // Rebuild UI to reflect new admin state
            buildMainUI()
        }
    }

    override fun onResume() {
        super.onResume()
        // Rebuild UI when returning from Settings so button labels refresh
        if (isAuthenticated) {
            buildMainUI()
        }
    }
}
