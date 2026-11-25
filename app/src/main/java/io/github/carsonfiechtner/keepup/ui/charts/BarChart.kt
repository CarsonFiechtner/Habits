package io.github.carsonfiechtner.keepup.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class Bar(val value: Float, val color: Color)

/**
 * A simple bar chart with an optional dashed reference line (the goal). Bars are scaled to the larger
 * of the highest bar and the reference line.
 */
@Composable
fun BarChart(
    bars: List<Bar>,
    reference: Float?,
    startLabel: String,
    endLabel: String,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
) {
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val baseline = MaterialTheme.colorScheme.outlineVariant
    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .semantics { contentDescription = description },
        ) {
            if (bars.isEmpty()) return@Canvas
            val max = maxOf(bars.maxOf { it.value }, reference ?: 0f).takeIf { it > 0f } ?: 1f
            val slot = size.width / bars.size
            val barWidth = (slot * 0.7f).coerceAtMost(28.dp.toPx())
            bars.forEachIndexed { i, bar ->
                val h = (bar.value / max) * size.height
                val minVisible = if (bar.value > 0f) 2.dp.toPx() else 0f
                val barHeight = maxOf(h, minVisible)
                drawRoundRect(
                    color = bar.color,
                    topLeft = Offset(i * slot + (slot - barWidth) / 2, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                )
            }
            drawLine(baseline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            reference?.let { ref ->
                val y = size.height - (ref / max) * size.height
                drawLine(
                    color = lineColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(startLabel, style = MaterialTheme.typography.labelSmall, color = lineColor)
            Spacer(Modifier.weight(1f))
            Text(endLabel, style = MaterialTheme.typography.labelSmall, color = lineColor)
        }
    }
}
