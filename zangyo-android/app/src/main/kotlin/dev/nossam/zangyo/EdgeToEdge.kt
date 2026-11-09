package dev.nossam.zangyo

import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Android 15 draws every screen edge-to-edge. Pad the content root, not the
 * WebView: WebView paints its page across its own padding, so a padded WebView
 * still starts at y=0 under the status bar.
 */
internal fun ComponentActivity.prepareEdgeToEdge() {
    enableEdgeToEdge()
}

internal fun ComponentActivity.applySafeSystemBarPadding() {
    val content = findViewById<View>(android.R.id.content)
    ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
        )
        view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
        WindowInsetsCompat.CONSUMED
    }
    ViewCompat.requestApplyInsets(content)
}
