package com.nossam.smahobutton.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import com.nossam.smahobutton.dp

class BubbleToast {
    private var view: TextView? = null
    private var windowManager: WindowManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private val dismiss = Runnable { remove() }

    fun show(context: Context, message: String, type: Int) {
        handler.post {
            remove()
            val text = TextView(context).apply {
                this.text = message
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 15f
                val pad = context.dp(14)
                setPadding(pad, context.dp(10), pad, context.dp(10))
                background = GradientDrawable().apply {
                    cornerRadius = context.dp(18).toFloat()
                    setColor(0xF2333333.toInt())
                }
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = context.dp(96)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }
            }
            try {
                val manager = context.getSystemService(WindowManager::class.java)
                manager.addView(text, params)
                view = text
                windowManager = manager
                handler.postDelayed(dismiss, 1700)
            } catch (_: Exception) {
                Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun remove() {
        handler.removeCallbacks(dismiss)
        val current = view ?: return
        view = null
        runCatching { windowManager?.removeView(current) }
        windowManager = null
    }
}
