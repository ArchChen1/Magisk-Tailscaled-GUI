package top.cenmin.tailcontrol.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import top.cenmin.tailcontrol.core.model.StatusTone

@Immutable
data class StatusColors(
    val online: Color,
    val warning: Color,
    val offline: Color,
    val error: Color,
    val unknown: Color,
)

fun statusColorsFor(scheme: ColorScheme, dark: Boolean): StatusColors = StatusColors(
    online = if (dark) Color(0xFF63D08C) else Color(0xFF1B873F),
    warning = if (dark) Color(0xFFFFD17A) else Color(0xFFB76A00),
    offline = scheme.onSurfaceVariant,
    error = scheme.error,
    unknown = scheme.outline,
)

val LocalStatusColors = staticCompositionLocalOf {
    StatusColors(
        online = Color(0xFF1B873F),
        warning = Color(0xFFB76A00),
        offline = Color(0xFF74777F),
        error = Color(0xFFBA1A1A),
        unknown = Color(0xFF8E9099),
    )
}

@Composable
fun StatusColors.colorFor(tone: StatusTone): Color = when (tone) {
    StatusTone.ONLINE -> online
    StatusTone.WARNING -> warning
    StatusTone.OFFLINE -> offline
    StatusTone.ERROR -> error
    StatusTone.UNKNOWN -> unknown
}
