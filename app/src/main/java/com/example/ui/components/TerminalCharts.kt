package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CrimsonLoss
import com.example.ui.theme.EmeraldProfit
import com.example.ui.theme.TerminalBorder

@Composable
fun MiniSparkline(
    prices: List<Double>,
    isPositive: Boolean,
    modifier: Modifier = Modifier
) {
    val lineColor = if (isPositive) EmeraldProfit else CrimsonLoss
    Canvas(modifier = modifier) {
        if (prices.size < 2) return@Canvas
        val minVal = prices.minOrNull() ?: return@Canvas
        val maxVal = prices.maxOrNull() ?: return@Canvas
        val range = (maxVal - minVal).takeIf { it > 0.0001 } ?: 1.0
        val w = size.width
        val h = size.height
        val pad = 2.dp.toPx()
        val usableH = (h - pad * 2).coerceAtLeast(1f)
        val stepX = w / (prices.size - 1).coerceAtLeast(1)

        val linePath = Path()
        val fillPath = Path()

        prices.forEachIndexed { index, value ->
            val x = index * stepX
            val normalized = ((value - minVal) / range).toFloat()
            val y = h - pad - (normalized * usableH)
            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            if (index == prices.lastIndex) {
                fillPath.lineTo(x, h)
                fillPath.close()
            }
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.28f), Color.Transparent)
            )
        )
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(
                width = 1.8.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

@Composable
fun EquityCurveChart(
    points: List<Double>,
    baseline: Double = 500000.0,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val pad = 8.dp.toPx()
        val usableH = (h - pad * 2).coerceAtLeast(1f)

        // Subtle horizontal baseline grid line
        val safePoints = if (points.size >= 2) points else listOf(baseline, baseline)
        val minVal = (safePoints + baseline).minOrNull() ?: baseline
        val maxVal = (safePoints + baseline).maxOrNull() ?: baseline
        val range = (maxVal - minVal).takeIf { it > 1.0 } ?: 1000.0

        val baseNorm = ((baseline - minVal) / range).toFloat()
        val baseY = h - pad - (baseNorm * usableH)
        drawLine(
            color = TerminalBorder,
            start = Offset(0f, baseY),
            end = Offset(w, baseY),
            strokeWidth = 1.dp.toPx()
        )

        val isNetPositive = safePoints.last() >= baseline
        val strokeColor = if (isNetPositive) EmeraldProfit else CrimsonLoss
        val stepX = w / (safePoints.size - 1).coerceAtLeast(1)

        val linePath = Path()
        val fillPath = Path()
        var lastOffset = Offset(0f, baseY)

        safePoints.forEachIndexed { idx, value ->
            val x = idx * stepX
            val norm = ((value - minVal) / range).toFloat()
            val y = h - pad - (norm * usableH)
            lastOffset = Offset(x, y)
            if (idx == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            if (idx == safePoints.lastIndex) {
                fillPath.lineTo(x, h)
                fillPath.close()
            }
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(strokeColor.copy(alpha = 0.30f), Color.Transparent)
            )
        )
        drawPath(
            path = linePath,
            color = strokeColor,
            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawCircle(
            color = strokeColor,
            radius = 4.dp.toPx(),
            center = lastOffset
        )
    }
}
