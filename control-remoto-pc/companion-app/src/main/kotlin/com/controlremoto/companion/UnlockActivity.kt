package com.controlremoto.companion

import android.app.Activity
import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle

/**
 * Launched remotely from the PC:
 *   adb shell am start -n com.controlremoto.companion/.UnlockActivity
 *
 * Uses the official KeyguardManager.requestDismissKeyguard() API — this is
 * NOT input injection (which is what `adb shell input swipe/keyevent` tried
 * and got blocked by INJECT_EVENTS). Starting an activity via `am start` is
 * a normal, allowed ADB operation even while the phone is locked.
 *
 * If the lock screen has no PIN/pattern/fingerprint (your Redmi 13's case),
 * this dismisses it automatically, no human interaction needed.
 * If it DOES have a secure credential, Android will show the normal unlock
 * UI instead — it can't bypass an actual PIN, by design.
 */
class UnlockActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        val keyguardManager = getSystemService(KeyguardManager::class.java)
        if (keyguardManager == null) {
            finish()
            return
        }

        keyguardManager.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() {
                finish()
            }

            override fun onDismissError() {
                finish()
            }

            override fun onDismissCancelled() {
                finish()
            }
        })
    }
}