package com.nossam.smahobutton.a11y

import android.accessibilityservice.AccessibilityService
import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.nossam.smahobutton.overlay.FloatingOverlay
import com.nossam.smahobutton.runtime.SmahoRuntime

class SmahoAccessibilityService : AccessibilityService() {
    private var overlay: FloatingOverlay? = null
    private var tornDown = false
    private val configCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            overlay?.relayout()
        }

        @Suppress("DEPRECATION")
        override fun onLowMemory() = Unit
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        tornDown = false
        SmahoRuntime.accessibility = this
        overlay = FloatingOverlay(
            this,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            SmahoRuntime.callbacks(),
        )
        SmahoRuntime.a11yOverlay = overlay
        registerComponentCallbacks(configCallbacks)
        SmahoRuntime.refreshButtons()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        SmahoRuntime.onWindowPackage(event.packageName?.toString())
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        tearDown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        tearDown()
        super.onDestroy()
    }

    private fun tearDown() {
        if (tornDown) return
        tornDown = true
        runCatching { unregisterComponentCallbacks(configCallbacks) }
        if (SmahoRuntime.accessibility === this) SmahoRuntime.accessibility = null
        if (SmahoRuntime.a11yOverlay === overlay) SmahoRuntime.a11yOverlay = null
        overlay?.hide()
        overlay = null
        SmahoRuntime.refreshButtons()
    }
}
