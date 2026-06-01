package com.mochikanban.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mochikanban.app.ui.theme.DarkTokens
import com.mochikanban.app.ui.theme.MochiCoral
import com.mochikanban.app.ui.theme.MochiLemon
import com.mochikanban.app.ui.theme.MochiMint
import com.mochikanban.app.ui.theme.MochiRose
import com.mochikanban.app.ui.theme.MochiSky

enum class MochiMood { Calm, Happy, Focused }

/** Animated soft-blob backdrop, tuned for AMOLED black. */
@Composable
fun KawaiiBackdrop(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "backdrop")
    val drift by transition.animateFloat(
        initialValue = -20f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(tween(14000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "drift",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(color = DarkTokens.Background)
        drawSoftBean(Offset(w * 0.18f + drift, h * 0.16f), w * 0.32f, MochiRose.copy(alpha = 0.10f))
        drawSoftBean(Offset(w * 0.86f - drift, h * 0.30f), w * 0.26f, MochiSky.copy(alpha = 0.09f))
        drawSoftBean(Offset(w * 0.78f, h * 0.82f + drift), w * 0.30f, MochiMint.copy(alpha = 0.09f))
        drawSoftBean(Offset(w * 0.10f, h * 0.74f - drift), w * 0.22f, MochiLemon.copy(alpha = 0.07f))
        repeat(6) { index ->
            val x = ((index * 73) % 100) / 100f * w
            val y = ((index * 41) % 100) / 100f * h
            drawSparkle(Offset(x, y), (6.dp + (index % 3).dp).toPx(),
                listOf(MochiLemon, MochiCoral, MochiSky)[index % 3])
        }
    }
}

/** Tiny mochi face — used as the brand mark at the top of the board. */
@Composable
fun MochiMascot(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    mood: MochiMood = MochiMood.Happy,
    spinning: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "mascot")
    val bounce by transition.animateFloat(
        initialValue = 0f,
        targetValue = with(LocalDensity.current) { 2.dp.toPx() },
        animationSpec = infiniteRepeatable(tween(1300), RepeatMode.Reverse),
        label = "bounce",
    )
    val blush by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(950), RepeatMode.Reverse),
        label = "blush",
    )
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (spinning) 360f else 0f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Restart),
        label = "spin",
    )

    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val body = Rect(
            left = s * 0.06f,
            top = s * 0.12f + bounce,
            right = s * 0.94f,
            bottom = s * 0.88f + bounce,
        )
        rotate(degrees = if (spinning) spin else 0f, pivot = body.center) {
            drawMochiBody(body, blush, mood)
        }
    }
}

private fun DrawScope.drawSoftBean(center: Offset, radius: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x - radius * 0.8f, center.y)
        cubicTo(
            center.x - radius * 0.9f, center.y - radius * 0.55f,
            center.x - radius * 0.15f, center.y - radius * 0.92f,
            center.x + radius * 0.32f, center.y - radius * 0.72f,
        )
        cubicTo(
            center.x + radius * 0.92f, center.y - radius * 0.48f,
            center.x + radius * 0.86f, center.y + radius * 0.42f,
            center.x + radius * 0.18f, center.y + radius * 0.74f,
        )
        cubicTo(
            center.x - radius * 0.38f, center.y + radius,
            center.x - radius * 0.9f, center.y + radius * 0.58f,
            center.x - radius * 0.8f, center.y,
        )
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawSparkle(center: Offset, radius: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        lineTo(center.x + radius * 0.28f, center.y - radius * 0.28f)
        lineTo(center.x + radius, center.y)
        lineTo(center.x + radius * 0.28f, center.y + radius * 0.28f)
        lineTo(center.x, center.y + radius)
        lineTo(center.x - radius * 0.28f, center.y + radius * 0.28f)
        lineTo(center.x - radius, center.y)
        lineTo(center.x - radius * 0.28f, center.y - radius * 0.28f)
        close()
    }
    drawPath(path, color.copy(alpha = 0.35f))
}

private fun DrawScope.drawMochiBody(body: Rect, blush: Float, mood: MochiMood) {
    val cream = Color(0xFFF5EFE6)
    drawRoundRect(
        color = cream,
        topLeft = body.topLeft,
        size = body.size,
        cornerRadius = CornerRadius(body.width * 0.32f, body.height * 0.36f),
    )
    drawCircle(MochiRose.copy(alpha = 0.55f * blush), body.width * 0.10f,
        Offset(body.left + body.width * 0.26f, body.top + body.height * 0.58f))
    drawCircle(MochiRose.copy(alpha = 0.55f * blush), body.width * 0.10f,
        Offset(body.right - body.width * 0.26f, body.top + body.height * 0.58f))
    drawFace(body, mood)
}

private fun DrawScope.drawFace(body: Rect, mood: MochiMood) {
    val eye = Color(0xFF1A1118)
    val eyeY = body.top + body.height * 0.45f
    when (mood) {
        MochiMood.Calm -> {
            drawCircle(eye, body.width * 0.045f, Offset(body.left + body.width * 0.36f, eyeY))
            drawCircle(eye, body.width * 0.045f, Offset(body.right - body.width * 0.36f, eyeY))
            drawRoundRect(
                color = eye,
                topLeft = Offset(body.center.x - body.width * 0.06f, body.top + body.height * 0.62f),
                size = Size(body.width * 0.12f, body.height * 0.025f),
                cornerRadius = CornerRadius(20f, 20f),
            )
        }
        MochiMood.Happy -> {
            drawCircle(eye, body.width * 0.05f, Offset(body.left + body.width * 0.36f, eyeY))
            drawCircle(eye, body.width * 0.05f, Offset(body.right - body.width * 0.36f, eyeY))
            drawArc(
                color = eye,
                startAngle = 10f, sweepAngle = 160f, useCenter = false,
                topLeft = Offset(body.center.x - body.width * 0.09f, body.top + body.height * 0.52f),
                size = Size(body.width * 0.18f, body.height * 0.18f),
                style = Stroke(width = body.width * 0.04f),
            )
        }
        MochiMood.Focused -> {
            drawRoundRect(
                color = eye,
                topLeft = Offset(body.left + body.width * 0.30f, eyeY - body.height * 0.015f),
                size = Size(body.width * 0.12f, body.height * 0.025f),
                cornerRadius = CornerRadius(20f, 20f),
            )
            drawRoundRect(
                color = eye,
                topLeft = Offset(body.right - body.width * 0.42f, eyeY - body.height * 0.015f),
                size = Size(body.width * 0.12f, body.height * 0.025f),
                cornerRadius = CornerRadius(20f, 20f),
            )
        }
    }
}
