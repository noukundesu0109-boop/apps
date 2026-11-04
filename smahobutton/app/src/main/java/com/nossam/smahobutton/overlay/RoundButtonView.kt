package com.nossam.smahobutton.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Region
import android.view.View
import android.view.ViewOutlineProvider
import com.nossam.smahobutton.dp
import com.nossam.smahobutton.logic.ButtonSpec
import com.nossam.smahobutton.logic.SHAPE_ROBOT
import com.nossam.smahobutton.logic.paletteArgb
import com.nossam.smahobutton.logic.prefersDarkInk
import kotlin.math.hypot

class RoundButtonView(context: Context) : View(context) {
    var spec: ButtonSpec = ButtonSpec()
        set(value) {
            field = value
            contentDescription = value.label.ifBlank { if (value.shape == SHAPE_ROBOT) "ロボット" else "ボタン" }
            invalidate()
            invalidateOutline()
        }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
    }
    private val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val oval = RectF()
    private val robotPath = Path()

    init {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                if (spec.shape == SHAPE_ROBOT && view.width > 0) {
                    val head = robotBox(view.width.toFloat())
                    outline.setRoundRect(
                        head.head.left.toInt(),
                        head.head.top.toInt(),
                        head.head.right.toInt(),
                        head.head.bottom.toInt(),
                        head.head.width() * 0.22f,
                    )
                } else {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
        }
        elevation = context.dp(3).toFloat()
        isClickable = true
        isHapticFeedbackEnabled = true
    }

    fun hit(x: Float, y: Float): Boolean {
        if (width <= 0 || height <= 0) return false
        return if (spec.shape == SHAPE_ROBOT) {
            val region = Region()
            region.setPath(
                robotOutline(width.toFloat()),
                Region(0, 0, width, height),
            )
            region.contains(x.toInt(), y.toInt())
        } else {
            val cx = width / 2f
            val cy = height / 2f
            hypot(x - cx, y - cy) <= cx
        }
    }

    fun touchPath(): Path = if (spec.shape == SHAPE_ROBOT && width > 0) {
        robotOutline(width.toFloat())
    } else {
        Path().apply { addCircle(width / 2f, height / 2f, width / 2f, Path.Direction.CW) }
    }

    override fun onDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0) return
        if (spec.shape == SHAPE_ROBOT) {
            drawRobot(canvas, width.toFloat())
            return
        }
        val cx = width / 2f
        val cy = height / 2f
        val radius = cx - context.dp(1).toFloat()
        fill.color = paletteArgb(spec.color).toInt()
        canvas.drawCircle(cx, cy, radius, fill)
        ring.strokeWidth = context.dp(1.5f)
        ring.alpha = 190
        canvas.drawCircle(cx, cy, radius - ring.strokeWidth / 2f, ring)
        val shown = spec.label.trim()
        if (shown.isNotEmpty()) {
            drawLabel(canvas, shown.take(4), cx, cy)
        } else {
            drawIcon(canvas, spec.icon, cx, cy, radius)
        }
    }

    private fun drawRobot(canvas: Canvas, size: Float) {
        val box = robotBox(size)
        val body = paletteArgb(spec.color).toInt()
        val ink = if (prefersDarkInk(paletteArgb(spec.color))) 0xFF1C1B19.toInt() else Color.WHITE
        fill.color = body
        canvas.drawRoundRect(box.earLeft, box.earRadius, box.earRadius, fill)
        canvas.drawRoundRect(box.earRight, box.earRadius, box.earRadius, fill)
        canvas.drawRoundRect(box.stick, box.stick.width() / 2f, box.stick.width() / 2f, fill)
        canvas.drawCircle(box.ballX, box.ballY, box.ballRadius, fill)
        canvas.drawRoundRect(box.head, box.headCorner, box.headCorner, fill)
        fill.color = ink
        canvas.drawOval(box.eyeLeft, fill)
        canvas.drawOval(box.eyeRight, fill)
        icon.color = ink
        icon.style = Paint.Style.STROKE
        icon.strokeWidth = size * 0.035f
        icon.strokeCap = Paint.Cap.ROUND
        val smile = Path()
        smile.moveTo(box.smileLeft, box.smileY)
        smile.quadTo(size / 2f, box.smileY + size * 0.09f, box.smileRight, box.smileY)
        canvas.drawPath(smile, icon)
        icon.style = Paint.Style.FILL
    }

    private fun robotOutline(size: Float): Path {
        val box = robotBox(size)
        robotPath.rewind()
        robotPath.addRoundRect(box.head, box.headCorner, box.headCorner, Path.Direction.CW)
        robotPath.addRoundRect(box.earLeft, box.earRadius, box.earRadius, Path.Direction.CW)
        robotPath.addRoundRect(box.earRight, box.earRadius, box.earRadius, Path.Direction.CW)
        robotPath.addRoundRect(box.stick, box.stick.width() / 2f, box.stick.width() / 2f, Path.Direction.CW)
        robotPath.addCircle(box.ballX, box.ballY, box.ballRadius, Path.Direction.CW)
        return robotPath
    }

    private fun robotBox(size: Float): RobotBox {
        val head = RectF(size * 0.16f, size * 0.28f, size * 0.84f, size * 0.96f)
        return RobotBox(
            head = head,
            headCorner = size * 0.16f,
            earLeft = RectF(size * 0.07f, size * 0.50f, size * 0.20f, size * 0.68f),
            earRight = RectF(size * 0.80f, size * 0.50f, size * 0.93f, size * 0.68f),
            earRadius = size * 0.04f,
            stick = RectF(size * 0.46f, size * 0.14f, size * 0.54f, size * 0.32f),
            ballX = size * 0.50f,
            ballY = size * 0.09f,
            ballRadius = size * 0.065f,
            eyeLeft = RectF(size * 0.30f, size * 0.44f, size * 0.44f, size * 0.64f),
            eyeRight = RectF(size * 0.56f, size * 0.44f, size * 0.70f, size * 0.64f),
            smileLeft = size * 0.38f,
            smileRight = size * 0.62f,
            smileY = size * 0.72f,
        )
    }

    private data class RobotBox(
        val head: RectF,
        val headCorner: Float,
        val earLeft: RectF,
        val earRight: RectF,
        val earRadius: Float,
        val stick: RectF,
        val ballX: Float,
        val ballY: Float,
        val ballRadius: Float,
        val eyeLeft: RectF,
        val eyeRight: RectF,
        val smileLeft: Float,
        val smileRight: Float,
        val smileY: Float,
    )

    private fun drawLabel(canvas: Canvas, label: String, cx: Float, cy: Float) {
        text.textSize = width * 0.28f
        while (text.measureText(label) > width * 0.72f && text.textSize > context.dp(8).toFloat()) {
            text.textSize *= 0.88f
        }
        val y = cy - (text.descent() + text.ascent()) / 2f
        canvas.drawText(label, cx, y, text)
    }

    private fun drawIcon(canvas: Canvas, key: String, cx: Float, cy: Float, radius: Float) {
        icon.color = Color.WHITE
        icon.style = Paint.Style.FILL
        icon.alpha = 255
        val s = radius * 0.46f
        when (key) {
            "home" -> drawHome(canvas, cx, cy, s)
            "back" -> drawBack(canvas, cx, cy, s, radius)
            "volume" -> drawVolume(canvas, cx, cy, s)
            "camera" -> drawCamera(canvas, cx, cy, s, radius)
            "flash" -> drawFlash(canvas, cx, cy, s)
            "bell" -> drawBell(canvas, cx, cy, s)
            "lock" -> drawLock(canvas, cx, cy, s, radius)
            "app" -> drawApps(canvas, cx, cy, s)
            "menu" -> drawMenu(canvas, cx, cy, s, radius)
            else -> canvas.drawCircle(cx, cy, s * 0.38f, icon)
        }
    }

    private fun drawHome(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val path = Path()
        path.moveTo(cx, cy - s)
        path.lineTo(cx + s, cy - s * 0.05f)
        path.lineTo(cx + s * 0.72f, cy - s * 0.05f)
        path.lineTo(cx + s * 0.72f, cy + s * 0.85f)
        path.lineTo(cx - s * 0.72f, cy + s * 0.85f)
        path.lineTo(cx - s * 0.72f, cy - s * 0.05f)
        path.lineTo(cx - s, cy - s * 0.05f)
        path.close()
        canvas.drawPath(path, icon)
        val door = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill.color }
        canvas.drawRoundRect(cx - s * 0.18f, cy + s * 0.18f, cx + s * 0.18f, cy + s * 0.85f, 6f, 6f, door)
    }

    private fun drawBack(canvas: Canvas, cx: Float, cy: Float, s: Float, radius: Float) {
        icon.style = Paint.Style.STROKE
        icon.strokeWidth = radius * 0.13f
        val path = Path()
        path.moveTo(cx + s * 0.5f, cy - s * 0.55f)
        path.lineTo(cx - s * 0.45f, cy)
        path.lineTo(cx + s * 0.5f, cy + s * 0.55f)
        canvas.drawPath(path, icon)
    }

    private fun drawVolume(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val path = Path()
        path.moveTo(cx - s * 0.55f, cy - s * 0.2f)
        path.lineTo(cx - s * 0.12f, cy - s * 0.2f)
        path.lineTo(cx + s * 0.55f, cy - s * 0.75f)
        path.lineTo(cx + s * 0.55f, cy + s * 0.75f)
        path.lineTo(cx - s * 0.12f, cy + s * 0.2f)
        path.lineTo(cx - s * 0.55f, cy + s * 0.2f)
        path.close()
        canvas.drawPath(path, icon)
    }

    private fun drawCamera(canvas: Canvas, cx: Float, cy: Float, s: Float, radius: Float) {
        canvas.drawRoundRect(
            cx - s * 0.8f,
            cy - s * 0.4f,
            cx + s * 0.8f,
            cy + s * 0.55f,
            radius * 0.12f,
            radius * 0.12f,
            icon,
        )
        val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill.color }
        canvas.drawCircle(cx, cy + s * 0.08f, s * 0.32f, hole)
    }

    private fun drawFlash(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val path = Path()
        path.moveTo(cx + s * 0.18f, cy - s)
        path.lineTo(cx - s * 0.5f, cy + s * 0.02f)
        path.lineTo(cx - s * 0.02f, cy + s * 0.02f)
        path.lineTo(cx - s * 0.22f, cy + s)
        path.lineTo(cx + s * 0.55f, cy - s * 0.05f)
        path.lineTo(cx + s * 0.02f, cy - s * 0.05f)
        path.close()
        canvas.drawPath(path, icon)
    }

    private fun drawBell(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val path = Path()
        path.moveTo(cx - s * 0.62f, cy + s * 0.28f)
        path.quadTo(cx - s * 0.55f, cy - s * 0.85f, cx, cy - s * 0.8f)
        path.quadTo(cx + s * 0.55f, cy - s * 0.85f, cx + s * 0.62f, cy + s * 0.28f)
        path.close()
        canvas.drawPath(path, icon)
        canvas.drawCircle(cx, cy + s * 0.55f, s * 0.16f, icon)
    }

    private fun drawLock(canvas: Canvas, cx: Float, cy: Float, s: Float, radius: Float) {
        icon.style = Paint.Style.STROKE
        icon.strokeWidth = radius * 0.1f
        oval.set(cx - s * 0.38f, cy - s * 0.95f, cx + s * 0.38f, cy)
        canvas.drawArc(oval, 180f, 180f, false, icon)
        icon.style = Paint.Style.FILL
        canvas.drawRoundRect(cx - s * 0.55f, cy - s * 0.12f, cx + s * 0.55f, cy + s * 0.78f, 10f, 10f, icon)
    }

    private fun drawApps(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val g = s * 0.34f
        val gap = s * 0.1f
        fun cell(left: Float, top: Float) {
            canvas.drawRoundRect(left, top, left + g, top + g, 5f, 5f, icon)
        }
        cell(cx - g - gap, cy - g - gap)
        cell(cx + gap, cy - g - gap)
        cell(cx - g - gap, cy + gap)
        cell(cx + gap, cy + gap)
    }

    private fun drawMenu(canvas: Canvas, cx: Float, cy: Float, s: Float, radius: Float) {
        val w = s * 0.72f
        val h = radius * 0.1f
        canvas.drawRoundRect(cx - w, cy - s * 0.48f, cx + w, cy - s * 0.48f + h, h, h, icon)
        canvas.drawRoundRect(cx - w, cy - h / 2f, cx + w, cy + h / 2f, h, h, icon)
        canvas.drawRoundRect(cx - w, cy + s * 0.48f - h, cx + w, cy + s * 0.48f, h, h, icon)
    }
}
