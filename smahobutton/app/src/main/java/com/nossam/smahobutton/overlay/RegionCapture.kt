package com.nossam.smahobutton.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.nossam.smahobutton.apps.ScreenshotStore
import com.nossam.smahobutton.dp
import com.nossam.smahobutton.logic.Corner
import com.nossam.smahobutton.logic.PixelRect
import com.nossam.smahobutton.logic.Selection
import com.nossam.smahobutton.logic.buttonRowTop
import com.nossam.smahobutton.logic.clampSelection
import com.nossam.smahobutton.logic.containsPoint
import com.nossam.smahobutton.logic.cornerHit
import com.nossam.smahobutton.logic.mapSelectionToBitmap
import com.nossam.smahobutton.logic.moveSelection
import com.nossam.smahobutton.logic.resizeSelection
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.nossam.smahobutton.logic.JoinedRead
import com.nossam.smahobutton.logic.OcrLine
import com.nossam.smahobutton.logic.ReadPass
import com.nossam.smahobutton.logic.joinLines
import com.nossam.smahobutton.logic.pickReadText
import com.nossam.smahobutton.logic.refineRead

/**
 * Full-screen frozen screenshot. The user draws a rectangle, then saves or copies the text inside.
 */
class RegionCapture(
    private val context: Context,
    private val windowType: Int,
    private val bitmap: Bitmap,
    private val textIn: (PixelRect) -> String,
    private val onDismiss: () -> Unit,
    private val onMessage: (String) -> Unit,
) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private var root: FrameLayout? = null
    private var canvasView: RegionView? = null
    private var actions: LinearLayout? = null
    private var editor: LinearLayout? = null
    private var reading: TextView? = null
    private var savedUri: android.net.Uri? = null
    private var closed = false

    fun show() {
        if (root != null || wm == null) return
        val view = RegionView(context, bitmap) { placeActions() }
        val buttons = actionRow()
        val frame = FrameLayout(context).apply {
            addView(view, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ))
            addView(buttons, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ))
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    dismiss()
                    true
                } else {
                    false
                }
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
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            if (Build.VERSION.SDK_INT >= 30) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        try {
            wm.addView(frame, params)
            root = frame
            canvasView = view
            actions = buttons
            frame.requestFocus()
            if (Build.VERSION.SDK_INT >= 33) {
                frame.post {
                    frame.findOnBackInvokedDispatcher()?.registerOnBackInvokedCallback(0) { dismiss() }
                }
            }
        } catch (_: Exception) {
            bitmap.recycle()
            onDismiss()
        }
    }

    fun dismiss() {
        if (closed) return
        closed = true
        val shown = root
        root = null
        if (shown != null) runCatching { wm?.removeView(shown) }
        if (!bitmap.isRecycled) bitmap.recycle()
        onDismiss()
    }

    private fun actionRow(): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
        }
        row.addView(chip("スクショ") { saveCrop(shareAfter = false) })
        row.addView(chip("文字をコピー") { copyText() })
        row.addView(chip("共有") { saveCrop(shareAfter = true) })
        row.addView(chip("やめる") { dismiss() })
        return row
    }

    private fun chip(label: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            val padH = context.dp(14)
            val padV = context.dp(12)
            setPadding(padH, padV, padH, padV)
            minimumHeight = context.dp(52)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = context.dp(16).toFloat()
                setColor(0xFFC4493A.toInt())
            }
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = context.dp(8) }
        }
    }

    private fun placeActions() {
        val view = canvasView ?: return
        val row = actions ?: return
        val box = view.committed ?: run {
            row.visibility = View.GONE
            return
        }
        row.visibility = View.VISIBLE
        row.measure(
            View.MeasureSpec.makeMeasureSpec(view.width, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(view.height, View.MeasureSpec.AT_MOST),
        )
        val (insetTop, insetBottom) = insets()
        val top = buttonRowTop(
            rectTop = box.top.toInt(),
            rectBottom = box.bottom.toInt(),
            rowHeight = row.measuredHeight.coerceAtLeast(context.dp(52)),
            viewHeight = view.height,
            insetTop = insetTop,
            insetBottom = insetBottom,
            gap = context.dp(12),
        )
        val left = box.left.toInt().coerceIn(context.dp(8), (view.width - row.measuredWidth - context.dp(8)).coerceAtLeast(context.dp(8)))
        (row.layoutParams as FrameLayout.LayoutParams).apply {
            leftMargin = left
            topMargin = top
        }
        row.requestLayout()
    }

    private fun currentCrop(): Bitmap? {
        val view = canvasView ?: return null
        val box = view.committed ?: return null
        val rect = mapSelectionToBitmap(box, view.width, view.height, bitmap.width, bitmap.height)
        if (rect.width < 2 || rect.height < 2) return null
        return runCatching {
            Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width, rect.height)
        }.getOrNull()
    }

    private fun saveCrop(shareAfter: Boolean) {
        val crop = currentCrop()
        if (crop == null) {
            onMessage("もう少し大きく囲んでね")
            return
        }
        val uri = ScreenshotStore.savePng(context, crop).also { savedUri = it }
        crop.recycle()
        if (uri == null) {
            onMessage("保存できませんでした")
            return
        }
        onMessage("囲ったところを保存したよ")
        if (shareAfter) {
            dismiss()
            ScreenshotStore.share(context, uri)
        }
    }

    private fun copyText() {
        val view = canvasView ?: return
        val box = view.committed ?: return
        val rect = mapSelectionToBitmap(box, view.width, view.height, bitmap.width, bitmap.height)
        if (rect.width < 2 || rect.height < 2) {
            onMessage("もう少し大きく囲んでね")
            return
        }
        val crop = currentCrop()
        if (crop == null) {
            finishRead(null, textIn(rect))
            return
        }
        showReading()
        val base = RegionImages.prepareBase(crop)
        if (base !== crop) crop.recycle()
        val sharpLatin = RegionImages.sharpenStrong(base)
        val jobs = listOf(
            OcrJob(base, digitFriendly = false, latinStrong = true),
            OcrJob(RegionImages.grayContrast(base), digitFriendly = true, latinStrong = false),
            OcrJob(RegionImages.equalize(base), digitFriendly = false, latinStrong = false),
            OcrJob(RegionImages.adaptiveBinary(base), digitFriendly = true, latinStrong = false),
            OcrJob(RegionImages.gammaBright(base), digitFriendly = false, latinStrong = false),
            OcrJob(sharpLatin, digitFriendly = false, latinStrong = true),
        )
        val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
        val passes = ArrayList<ReadPass>()
        fun finish() {
            recognizer.close()
            recycleQuietly(*jobs.map { it.bitmap }.distinct().toTypedArray())
            finishRead(refineRead(passes, textIn(rect)), fallback = null)
        }
        fun step(index: Int, rotation: Int) {
            if (index >= jobs.size) {
                finish()
                return
            }
            val job = jobs[index]
            recognizer.process(InputImage.fromBitmap(job.bitmap, rotation))
                .addOnSuccessListener { result ->
                    passes += passFrom(result, job.digitFriendly, job.latinStrong && rotation == 0)
                    if (rotation == 0) step(index, 90) else step(index + 1, 0)
                }
                .addOnFailureListener {
                    if (rotation == 0) step(index, 90) else step(index + 1, 0)
                }
        }
        step(0, 0)
    }

    private data class OcrJob(val bitmap: Bitmap, val digitFriendly: Boolean, val latinStrong: Boolean)

    private fun passFrom(result: Text, digitFriendly: Boolean, latinStrong: Boolean): ReadPass {
        val lines = ArrayList<OcrLine>()
        for (block in result.textBlocks) {
            for (line in block.lines) {
                val elements = line.elements
                if (elements.isEmpty()) {
                    val box = line.boundingBox ?: continue
                    lines += OcrLine(line.text, box.left, box.top, box.right, box.bottom, line.confidence.coerceAtLeast(0f))
                    continue
                }
                for (element in elements) {
                    val box = element.boundingBox ?: continue
                    val text = element.text?.trim().orEmpty()
                    if (text.isEmpty()) continue
                    lines += OcrLine(
                        text = text,
                        left = box.left,
                        top = box.top,
                        right = box.right,
                        bottom = box.bottom,
                        confidence = element.confidence.coerceAtLeast(0f),
                    )
                }
            }
        }
        val joined: JoinedRead = joinLines(lines)
        val confidence = lines.sumOf { it.confidence.toDouble() }.toFloat()
        return ReadPass(joined.text, confidence, joined.verticalColumns, lines, digitFriendly, latinStrong)
    }

    private fun finishRead(chosen: String?, fallback: String?) {
        hideReading()
        val text = chosen?.trim().orEmpty().ifEmpty { fallback?.trim().orEmpty() }
        if (text.length < 2) {
            onMessage("文字をうまく読めなかった。もっと大きく囲むか、明るいところで試してね")
            return
        }
        showEditor(text)
        maybeOnceHint()
    }

    private fun maybeOnceHint() {
        val prefs = context.getSharedPreferences("smahobutton_hints", Context.MODE_PRIVATE)
        if (prefs.getBoolean("ocr_closeup_hint", false)) return
        prefs.edit().putBoolean("ocr_closeup_hint", true).apply()
        onMessage("文字が小さいときは、もっと近くで撮った写真を大きく囲むと読みやすいよ")
    }

    private fun recycleQuietly(vararg bitmaps: Bitmap) {
        for (item in bitmaps) {
            if (!item.isRecycled) item.recycle()
        }
    }

    private fun showReading() {
        val host = root ?: return
        if (reading != null) return
        val label = TextView(context).apply {
            text = "読んでる…"
            setTextColor(Color.WHITE)
            textSize = 20f
            setBackgroundColor(0xCC222222.toInt())
            val pad = context.dp(12)
            setPadding(pad, pad, pad, pad)
        }
        val (insetTop, _) = insets()
        host.addView(label, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_HORIZONTAL,
        ).apply { topMargin = insetTop + context.dp(12) })
        reading = label
    }

    private fun hideReading() {
        val label = reading ?: return
        reading = null
        root?.removeView(label)
    }

    private fun showEditor(text: String) {
        hideReading()
        if (text.isBlank()) {
            onMessage("文字をうまく読めなかった。もっと大きく囲むか、明るいところで試してね")
            return
        }
        val host = root ?: return
        actions?.visibility = View.GONE
        editor?.let { host.removeView(it) }
        val field = EditText(context).apply {
            setText(text)
            setTextColor(Color.BLACK)
            textSize = 18f
            setBackgroundColor(Color.WHITE)
            val pad = context.dp(12)
            setPadding(pad, pad, pad, pad)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
            minLines = 4
            setSelection(0, text.length)
        }
        val copy = chip("コピー") {
            val start = field.selectionStart
            val end = field.selectionEnd
            val chosen = if (start >= 0 && end > start) {
                field.text.substring(start, end)
            } else {
                field.text.toString()
            }
            if (chosen.isBlank()) {
                onMessage("文字が見つからなかったよ")
                return@chip
            }
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText("文字", chosen))
            onMessage("コピーしたよ")
            dismiss()
        }
        val close = chip("やめる") { dismiss() }
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xF2FFFFFF.toInt())
            val pad = context.dp(12)
            setPadding(pad, pad, pad, pad)
            addView(field, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                val gap = context.dp(8)
                setPadding(0, gap, 0, 0)
                addView(copy)
                addView(close)
            })
        }
        val (insetTop, _) = insets()
        host.addView(panel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            leftMargin = context.dp(16)
            rightMargin = context.dp(16)
            topMargin = insetTop + context.dp(48)
        })
        editor = panel
        field.requestFocus()
    }

    private fun insets(): Pair<Int, Int> {
        val shown = root
        val bars = shown?.rootWindowInsets
        if (bars == null) return context.dp(28) to context.dp(24)
        return if (Build.VERSION.SDK_INT >= 30) {
            val inset = bars.getInsets(WindowInsets.Type.systemBars())
            inset.top to inset.bottom
        } else {
            @Suppress("DEPRECATION")
            bars.systemWindowInsetTop to bars.systemWindowInsetBottom
        }
    }
}

private class RegionView(
    context: Context,
    private val bitmap: Bitmap,
    private val onSelection: () -> Unit,
) : View(context) {
    var committed: Selection? = null
        private set

    private var draft: Selection? = null
    private var corner: Corner? = null
    private var moving = false
    private var lastX = 0f
    private var lastY = 0f
    private val src = Rect()
    private val dst = RectF()
    private val dim = Paint().apply { color = 0x66000000 }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = context.dp(3f)
    }
    private val handle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC4493A.toInt() }
    private val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = context.dp(18f)
        textAlign = Paint.Align.CENTER
        setShadowLayer(8f, 0f, 2f, Color.BLACK)
    }

    override fun onDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0 || bitmap.isRecycled) return
        src.set(0, 0, bitmap.width, bitmap.height)
        dst.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawBitmap(bitmap, src, dst, null)
        canvas.drawRect(dst, dim)
        val box = (draft ?: committed)?.normalized()
        if (box != null && box.width > 1f && box.height > 1f) {
            val mapped = mapSelectionToBitmap(box, width, height, bitmap.width, bitmap.height)
            src.set(mapped.left, mapped.top, mapped.right, mapped.bottom)
            dst.set(box.left, box.top, box.right, box.bottom)
            if (!mapped.isEmpty()) canvas.drawBitmap(bitmap, src, dst, null)
            canvas.drawRect(dst, stroke)
            if (draft == null) {
                val r = context.dp(10f)
                canvas.drawCircle(box.left, box.top, r, handle)
                canvas.drawCircle(box.right, box.top, r, handle)
                canvas.drawCircle(box.left, box.bottom, r, handle)
                canvas.drawCircle(box.right, box.bottom, r, handle)
            }
        } else {
            val top = (rootWindowInsets?.let { bars ->
                if (Build.VERSION.SDK_INT >= 30) bars.getInsets(WindowInsets.Type.systemBars()).top
                else {
                    @Suppress("DEPRECATION")
                    bars.systemWindowInsetTop
                }
            } ?: context.dp(28)) + context.dp(28)
            canvas.drawText("指で四角く囲んでね", width / 2f, top.toFloat(), hint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val current = committed
                val hit = current?.let { cornerHit(it, x, y, context.dp(28f)) }
                when {
                    hit != null && current != null -> {
                        corner = hit
                        draft = current
                    }
                    current != null && containsPoint(current, x, y) -> {
                        moving = true
                        lastX = x
                        lastY = y
                        draft = current
                    }
                    else -> {
                        committed = null
                        corner = null
                        moving = false
                        draft = Selection(x, y, x, y)
                    }
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val current = draft ?: return true
                draft = when {
                    corner != null -> resizeSelection(current, corner!!, x, y, width, height)
                    moving -> moveSelection(current, x - lastX, y - lastY, width, height).also {
                        lastX = x
                        lastY = y
                    }
                    else -> clampSelection(Selection(current.left, current.top, x, y), width, height)
                }
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val finished = draft?.let { clampSelection(it, width, height) }
                draft = null
                corner = null
                moving = false
                committed = if (finished != null && finished.bigEnough(context.dp(24f))) finished else null
                invalidate()
                onSelection()
            }
        }
        return true
    }
}
