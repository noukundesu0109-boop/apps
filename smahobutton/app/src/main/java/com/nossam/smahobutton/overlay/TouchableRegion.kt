package com.nossam.smahobutton.overlay

import android.graphics.Path
import android.graphics.Region
import android.view.View
import java.lang.reflect.Proxy

/**
 * Asks the window to accept touches only inside [pathOf]. Overlay windows are
 * rectangles; without this, the empty corners around the robot still eat taps.
 */
internal fun installTouchableRegion(view: View, pathOf: () -> Path) {
    try {
        val listenerClass = Class.forName("android.view.ViewTreeObserver\$OnComputeInternalInsetsListener")
        val infoClass = Class.forName("android.view.ViewTreeObserver\$InternalInsetsInfo")
        val regionField = infoClass.getField("touchableRegion")
        val setInsets = infoClass.getMethod("setTouchableInsets", Int::class.javaPrimitiveType)
        val listener = Proxy.newProxyInstance(
            listenerClass.classLoader,
            arrayOf(listenerClass),
        ) { _, method, args ->
            if (method.name == "onComputeInternalInsets" && !args.isNullOrEmpty()) {
                val info = args[0]
                val region = regionField.get(info) as Region
                val path = pathOf()
                region.setEmpty()
                region.setPath(path, Region(0, 0, view.width.coerceAtLeast(1), view.height.coerceAtLeast(1)))
                setInsets.invoke(info, 3)
            }
            null
        }
        val add = view.viewTreeObserver.javaClass.getMethod(
            "addOnComputeInternalInsetsListener",
            listenerClass,
        )
        add.invoke(view.viewTreeObserver, listener)
    } catch (_: Throwable) {
        // Presses outside the shape are still ignored by the touch listener.
    }
}
