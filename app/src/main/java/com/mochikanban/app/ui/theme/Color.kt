package com.mochikanban.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

val MochiCream = Color(0xFFFFFAF2)
val MochiSurface = Color(0xFFFFFEFB)
val MochiInk = Color(0xFF252321)
val MochiRose = Color(0xFFE95778)
val MochiMint = Color(0xFF18A978)
val MochiSky = Color(0xFF4288E8)
val MochiLemon = Color(0xFFF6BE4B)
val MochiLilac = Color(0xFF9776D5)
val MochiCoral = Color(0xFFFF815F)
val MochiOutline = Color(0xFFEADCCD)
val MochiPositive = Color(0xFF16825F)
val MochiWarning = Color(0xFFB85E00)

object DarkTokens {
    // True black AMOLED — pure #000000 background with gently lifted surfaces.
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF0F0D11)
    val SurfaceVariant = Color(0xFF1A171D)
    val Outline = Color(0xFF3B3540)
    val OutlineVariant = Color(0xFF26222A)
    // Solid, high-contrast on AMOLED black.
    val Ink = Color(0xFFFFFFFF)
    val Muted = Color(0xFFCFC7BD)

    val MintDark = Color(0xFF86E7BF)
    val MintContainer = Color(0xFF1E5B49)
    val RoseDark = Color(0xFFFFB4C4)
    val RoseContainer = Color(0xFF82354A)
    val SkyDark = Color(0xFFB6D0FF)
    val SkyContainer = Color(0xFF28518C)
    val LemonDark = Color(0xFFFFD988)
    val LemonContainer = Color(0xFF6B4E0E)
    val LilacDark = Color(0xFFD4BBFF)
    val LilacContainer = Color(0xFF513C82)
    val CoralDark = Color(0xFFFFB39E)
    val CoralContainer = Color(0xFF8C3B22)

    val Error = Color(0xFFFFB4AB)

    val ColumnTodo = MintContainer
    val ColumnDoing = LemonContainer
    val ColumnDone = SkyContainer
}

fun Color.matteLabelColor(): Color =
    lerp(this.copy(alpha = 1f), Color.White, 0.16f)

fun Color.glowTint(accent: Color, amount: Float): Color =
    lerp(this.copy(alpha = 1f), accent.copy(alpha = 1f), amount).copy(alpha = alpha)
