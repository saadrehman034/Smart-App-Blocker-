package com.focuslock.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.widget.*

class BlockOverlayActivity : Activity() {

    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "blocked_package"
    }

    private var blockedPackage = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        blockedPackage = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE) ?: ""

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            setPadding(80, 80, 80, 80)
        }

        root.addView(TextView(this).apply {
            text = "App Locked"
            textSize = 28f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        })

        root.addView(TextView(this).apply {
            text = "Enter PIN to unlock"
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        })

        val pinInput = EditText(this).apply {
            hint = "4-digit PIN"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            gravity = Gravity.CENTER
            maxLines = 1
            filters = arrayOf(InputFilter.LengthFilter(4))
        }
        root.addView(pinInput)

        val errorText = TextView(this).apply {
            text = ""
            setTextColor(Color.RED)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }
        root.addView(errorText)

        val unlockBtn = Button(this).apply {
            text = "Unlock"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = 24 }
        }
        root.addView(unlockBtn)

        unlockBtn.setOnClickListener {
            val pin = pinInput.text.toString()
            if (pin.length == 4 && PinManager.verifyPin(this, pin)) {
                SessionManager.temporarilyUnlock(blockedPackage)
                finish()
            } else {
                errorText.text = "Wrong PIN"
                pinInput.text.clear()
            }
        }

        setContentView(root)
    }

    @Deprecated("Deprecated in API 33")
    override fun onBackPressed() {
        // Do not allow back — send user to home screen instead
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(home)
    }
}
