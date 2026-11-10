package com.nossam.smahobutton.a11y

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.nossam.smahobutton.logic.insertAtCursor
import com.nossam.smahobutton.logic.PixelRect
import com.nossam.smahobutton.logic.TextSnippet
import com.nossam.smahobutton.logic.snippetsInside
import com.nossam.smahobutton.logic.AppWindow
import com.nossam.smahobutton.logic.FilterConfig
import com.nossam.smahobutton.logic.isCameraPackage
import com.nossam.smahobutton.logic.pickForegroundPackage
import com.nossam.smahobutton.logic.shutterFractions
import com.nossam.smahobutton.logic.shutterScore
import com.nossam.smahobutton.overlay.FloatingOverlay
import com.nossam.smahobutton.runtime.SmahoRuntime

class SmahoAccessibilityService : AccessibilityService() {
    private var overlay: FloatingOverlay? = null
    private var tornDown = false
    private val labels = HashMap<String, String?>()
    private val configCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            overlay?.relayout()
        }

        @Suppress("DEPRECATION")
        override fun onLowMemory() = Unit
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        widenEvents()
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
        val current = runCatching { probeForeground(null) }.getOrNull()
        if (current != null) SmahoRuntime.onWindowPackage(current)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val type = event?.eventType ?: return
        if (
            type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            return
        }
        val picked = runCatching { probeForeground(event.packageName?.toString()) }.getOrNull()
        SmahoRuntime.onWindowPackage(picked)
    }

    /** Re-reads the windows on screen. Ignores this app's overlay, the shade, and keyboards. */
    fun probeForeground(eventPackage: String?): String? {
        val snapshots = ArrayList<AppWindow>()
        val listed = runCatching { windows }.getOrNull().orEmpty()
        for (window in listed) {
            var root: AccessibilityNodeInfo? = null
            try {
                root = window.root
                val pkg = root?.packageName?.toString().orEmpty()
                if (pkg.isNotEmpty()) {
                    snapshots += AppWindow(
                        packageName = pkg,
                        typeApplication = window.type == AccessibilityWindowInfo.TYPE_APPLICATION,
                        focused = window.isFocused,
                        active = window.isActive,
                    )
                }
            } catch (_: Exception) {
            } finally {
                runCatching { root?.recycle() }
                runCatching { window.recycle() }
            }
        }
        var activeRoot: AccessibilityNodeInfo? = null
        val rootPkg = try {
            activeRoot = rootInActiveWindow
            activeRoot?.packageName?.toString()
        } catch (_: Exception) {
            null
        } finally {
            runCatching { activeRoot?.recycle() }
        }
        val filter = runCatching { SmahoRuntime.currentFilter() }.getOrNull() ?: FilterConfig(packageName)
        return pickForegroundPackage(
            windows = snapshots,
            eventPackage = eventPackage,
            rootPackage = rootPkg,
            config = filter,
            labelOf = { pkg -> labels.getOrPut(pkg) { SmahoRuntime.appLabel(pkg) } },
        )
    }

    private fun widenEvents() {
        val info = serviceInfo ?: return
        info.eventTypes = info.eventTypes or
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
            AccessibilityEvent.TYPE_WINDOWS_CHANGED
        info.flags = info.flags or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        serviceInfo = info
    }

    fun captureScreen(onResult: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT < 30) {
            onResult(null)
            return
        }
        val main = Handler(Looper.getMainLooper())
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            { command -> main.post(command) },
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val bitmap = runCatching {
                        val hardware = Bitmap.wrapHardwareBuffer(
                            screenshot.hardwareBuffer,
                            screenshot.colorSpace,
                        )
                        screenshot.hardwareBuffer.close()
                        val soft = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                        hardware?.recycle()
                        soft
                    }.getOrNull()
                    onResult(bitmap)
                }

                override fun onFailure(errorCode: Int) {
                    onResult(null)
                }
            },
        )
    }

    /**
     * Inserts [insert] at the focused field's cursor. The rest of the text stays.
     * True when the text was inserted. False when a field was found but rejected it.
     * Null when no field could be found.
     */
    fun insertTextAtCursor(insert: String): Boolean? {
        if (insert.isEmpty()) return false
        val node = findEditableFocus() ?: return null
        return try {
            val current = node.text?.toString().orEmpty()
            val (next, cursor) = insertAtCursor(current, node.textSelectionStart, insert)
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, next)
            val wrote = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (!wrote) return false
            val selection = Bundle()
            selection.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
            selection.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selection)
            true
        } catch (_: Exception) {
            false
        } finally {
            runCatching { node.recycle() }
        }
    }

    private fun findEditableFocus(): AccessibilityNodeInfo? {
        val listed = runCatching { windows }.getOrNull().orEmpty()
        val held = ArrayList<AccessibilityNodeInfo>()
        try {
            val apps = listed.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            val ordered = apps.sortedByDescending { window ->
                val active = if (window.isActive) 2 else 0
                val focused = if (window.isFocused) 1 else 0
                active + focused
            }
            val roots = ArrayList<AccessibilityNodeInfo>()
            for (window in ordered) {
                val root = runCatching { window.root }.getOrNull() ?: continue
                held += root
                roots += root
                val focus = runCatching { root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }.getOrNull()
                if (focus != null) held += focus
                val editable = focus?.let { editableAround(it, held) }
                if (editable != null) return detach(editable, held)
            }
            val root = roots.firstOrNull() ?: return null
            val found = ArrayList<AccessibilityNodeInfo>()
            collectEditable(root, found)
            for (node in found) {
                if (held.none { it === node }) held += node
            }
            val chosen = pickEditable(found) ?: return null
            return detach(chosen, held)
        } catch (_: Exception) {
            return null
        } finally {
            held.forEach { runCatching { it.recycle() } }
            listed.forEach { runCatching { it.recycle() } }
        }
    }

    private fun detach(chosen: AccessibilityNodeInfo, held: MutableList<AccessibilityNodeInfo>): AccessibilityNodeInfo {
        held.removeAll { it === chosen }
        return chosen
    }

    private fun editableAround(
        node: AccessibilityNodeInfo,
        held: MutableList<AccessibilityNodeInfo>,
    ): AccessibilityNodeInfo? {
        if (canAcceptText(node)) return node
        val child = findEditableChild(node)
        if (child != null) return child
        var parent = node.parent
        var guard = 0
        while (parent != null && guard++ < 6) {
            held += parent
            if (canAcceptText(parent)) return parent
            parent = parent.parent
        }
        return null
    }

    private fun findEditableChild(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            if (canAcceptText(child)) return child
            val nested = findEditableChild(child)
            if (nested != null) {
                if (nested !== child) runCatching { child.recycle() }
                return nested
            }
            runCatching { child.recycle() }
        }
        return null
    }

    private fun collectEditable(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
        if (canAcceptText(node) && (node.isVisibleToUser || node.isFocused || node.isAccessibilityFocused)) {
            out += node
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            collectEditable(child, out)
            if (out.none { it === child }) runCatching { child.recycle() }
        }
    }

    private fun pickEditable(nodes: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        nodes.firstOrNull { it.isFocused || it.isAccessibilityFocused }?.let { return it }
        nodes.firstOrNull { it.textSelectionStart >= 0 }?.let { return it }
        if (nodes.size == 1) return nodes[0]
        return nodes.maxByOrNull { node ->
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            bounds.bottom
        }
    }

    private fun canAcceptText(node: AccessibilityNodeInfo): Boolean {
        if (node.isEditable) return true
        return node.actionList?.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT } == true
    }

    fun textInside(rect: PixelRect): String {
        val snippets = ArrayList<TextSnippet>()
        val listed = runCatching { windows }.getOrNull().orEmpty()
        for (window in listed) {
            var root: AccessibilityNodeInfo? = null
            try {
                root = window.root
                if (root != null) collectText(root, snippets)
            } catch (_: Exception) {
            } finally {
                runCatching { root?.recycle() }
                runCatching { window.recycle() }
            }
        }
        val screenHeight = if (Build.VERSION.SDK_INT >= 30) {
            getSystemService(WindowManager::class.java).currentWindowMetrics.bounds.height()
        } else {
            resources.displayMetrics.heightPixels
        }
        return snippetsInside(snippets, rect, barHeight("status_bar_height"), screenHeight - barHeight("navigation_bar_height"))
    }

    private fun barHeight(name: String): Int {
        val id = resources.getIdentifier(name, "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    private fun collectText(node: AccessibilityNodeInfo, out: MutableList<TextSnippet>) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val text = node.text?.toString()?.trim().orEmpty().ifEmpty {
            node.contentDescription?.toString()?.trim().orEmpty()
        }
        if (text.isNotEmpty() && bounds.width() > 0 && bounds.height() > 0) {
            out += TextSnippet(bounds.left, bounds.top, bounds.right, bounds.bottom, text)
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                collectText(child, out)
            } finally {
                runCatching { child.recycle() }
            }
        }
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
