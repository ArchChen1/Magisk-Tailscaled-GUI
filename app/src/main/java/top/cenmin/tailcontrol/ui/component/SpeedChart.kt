package top.cenmin.tailcontrol.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.cenmin.tailcontrol.ui.theme.LocalStatusColors

/**
 * 简单的双折线图：Canvas 实现，无第三方图表依赖。
 */
@Composable
fun SpeedChart(
    rxValues: List<Double>,
    txValues: List<Double>,
    rxLabel: String,
    txLabel: String,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
) {
    val onSurface = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    val rxColor = LocalStatusColors.current.online
    val txColor = MaterialTheme.colorScheme.primary

    val maxValue = (rxValues + txValues).maxOrNull()?.coerceAtLeast(1.0) ?: 1.0

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendDot(rxColor); Text("  $rxLabel", style = MaterialTheme.typography.labelLarge)
            Box(Modifier.size(16.dp))
            LegendDot(txColor); Text("  $txLabel", style = MaterialTheme.typography.labelLarge)
        }
        Box(
            Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(12.dp))
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                // 横向 4 条网格线
                for (i in 1..3) {
                    val y = h * i / 4
                    drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                }
                drawSeries(rxValues, maxValue, w, h, rxColor)
                drawSeries(txValues, maxValue, w, h, txColor)
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color) {
    Canvas(Modifier.size(10.dp)) { drawCircle(color) }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSeries(
    values: List<Double>,
    maxValue: Double,
    w: Float,
    h: Float,
    color: Color,
) {
    if (values.size < 2) return
    val step = w / (values.size - 1).coerceAtLeast(1)
    val path = Path().apply {
        values.forEachIndexed { idx, v ->
            val x = idx * step
            val y = h - (v.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f) * h
            if (idx == 0) moveTo(x, y) else lineTo(x, y)
        }
    }
    drawPath(path, color = color, style = Stroke(width = 4f))
}
