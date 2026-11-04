package com.nossam.smahobutton.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

/** One translucent full-screen tap, used to remember where the shutter is. */
class ShutterAim(
    private val context: Context,
    private val windowType: Int,
    private val onTap: (xFraction: Float, yFraction: Float) -> Unit,
) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private var view: View? = null

    fun show() {
        if (view != null || wm == null) return
        val root = FrameLayout(context).apply {
            setBackgroundColor(0x66FFFFFF)
            setOnTouchListener { v, event ->
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    val width = v.width.coerceAtLeast(1)
                    val height = v.height.coerceAtLeast(1)
                    onTap(event.x / width, event.y / height)
                    dismiss()
                }
                true
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= 30) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        try {
            wm.addView(root, params)
            view = root
        } catch (_: Exception) {
            view = null
        }
    }

    fun dismiss() {
        val shown = view ?: return
        view = null
        runCatching { wm?.removeView(shown) }
    }
}
