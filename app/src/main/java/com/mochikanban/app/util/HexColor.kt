package com.mochikanban.app.util

import androidx.compose.ui.graphics.Color

object HexColor {
    fun parse(hex: String?): Color? {
        val h = hex?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { Color(android.graphics.Color.parseColor(h)) }.getOrNull()
    }

    fun parseOr(hex: String?, fallback: Color): Color = parse(hex) ?: fallback

    fun toHex(color: Color): String {
        val argb = color.value.toLong() ushr 32 // top 32 bits: AARRGGBB
        val rgb = (argb and 0xFFFFFFL).toInt()
        return "#%06X".format(rgb)
    }
}
