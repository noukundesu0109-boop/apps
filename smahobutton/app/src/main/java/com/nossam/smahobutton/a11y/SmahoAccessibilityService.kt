package com.nossam.smahobutton.a11y

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.graphics.Path
import android.graphics.Rect
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.nossam.smahobutton.logic.isCameraPackage
import com.nossam.smahobutton.logic.shutterFractions
import com.nossam.smahobutton.logic.shutterScore
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

    fun pressShutter(onDone: (Boolean) -> Unit) {
        if (clickShutter()) {
            onDone(true)
            return
        }
        val pkg = SmahoRuntime.confirmedPackage?.takeIf { isCameraPackage(it) }
            ?: "com.google.android.GoogleCamera"
        val settings = runCatching { SmahoRuntime.repo.state.value }.getOrNull()
        val portrait = resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        val bounds = if (android.os.Build.VERSION.SDK_INT >= 30) {
            getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
        } else {
            val metrics = resources.displayMetrics
            Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
        val (xFrac, yFrac) = shutterFractions(settings?.shutterSpots.orEmpty(), pkg, portrait)
        val x = bounds.width() * xFrac
        val y = bounds.height() * yFrac
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 70)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val sent = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) = onDone(true)
                override fun onCancelled(gestureDescription: GestureDescription?) = onDone(false)
            },
            null,
        )
        if (!sent) onDone(false)
    }

    private fun clickShutter(): Boolean {
        val root = rootInActiveWindow ?: return false
        val held = ArrayList<AccessibilityNodeInfo>()
        var best: AccessibilityNodeInfo? = null
        var bestScore = 0
        fun walk(node: AccessibilityNodeInfo) {
            val score = shutterScore(
                node.text?.toString(),
                node.contentDescription?.toString(),
                node.viewIdResourceName,
                node.isClickable,
            )
            if (score > bestScore) {
                best = node
                bestScore = score
            }
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                held += child
                walk(child)
            }
        }
        return try {
            walk(root)
            bestScore >= 40 && best?.let(::clickNode) == true
        } catch (_: Exception) {
            false
        } finally {
            held.forEach { runCatching { it.recycle() } }
            runCatching { root.recycle() }
        }
    }

    private fun clickNode(start: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = start
        repeat(4) {
            val node = current ?: return false
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            current = node.parent
        }
        return false
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
