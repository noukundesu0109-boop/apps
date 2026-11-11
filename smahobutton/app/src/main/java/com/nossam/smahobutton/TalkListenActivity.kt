package com.nossam.smahobutton

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.nossam.smahobutton.runtime.SmahoRuntime

/**
 * A thin, non-focusable bar. It makes this process visibly in the foreground so Android
 * allows the microphone foreground service, without taking the editor's input focus.
 */
class TalkListenActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        )
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED)
        window.setGravity(Gravity.TOP)
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, dp(36))
        setContentView(ListeningBarView(this))
        instance = this
    }

    override fun onResume() {
        super.onResume()
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        )
        window.setGravity(Gravity.TOP)
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, dp(36))
        window.decorView.post { SmahoRuntime.onTalkActivityVisible() }
    }

    override fun onDestroy() {
        val current = instance === this
        if (current) instance = null
        super.onDestroy()
        if (current) SmahoRuntime.onTalkActivityDestroyed()
    }

    private fun close() {
        if (isFinishing) return
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        private var instance: TalkListenActivity? = null

        fun open(context: Context): Boolean {
            if (instance != null) return true
            val intent = Intent(context, TalkListenActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION,
            )
            return try {
                context.startActivity(intent)
                true
            } catch (_: Exception) {
                false
            }
        }

        fun close() {
            instance?.close()
        }
    }
}

/** A sliding color bar, like the voice-typing line. It does not take touches. */
private class ListeningBarView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shift = 0f
    private val animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1400L
        repeatCount = android.animation.ValueAnimator.INFINITE
        addUpdateListener {
            shift = it.animatedValue as Float
            invalidate()
        }
        start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val start = -w + shift * w * 2f
        paint.shader = LinearGradient(
            start,
            0f,
            start + w,
            0f,
            intArrayOf(0xFF1A73E8.toInt(), 0xFF34A853.toInt(), 0xFFFBBC04.toInt(), 0xFFEA4335.toInt(), 0xFF1A73E8.toInt()),
            null,
            Shader.TileMode.CLAMP,
        )
        val radius = h / 2f
        canvas.drawRoundRect(0f, h * 0.28f, w, h * 0.82f, radius, radius, paint)
    }
}
