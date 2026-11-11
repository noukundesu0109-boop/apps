package com.nossam.smahobutton

import android.content.Context
import kotlin.math.roundToInt

internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

internal fun Context.dp(value: Float): Float = value * resources.displayMetrics.density
