/* Nurva visual system for the GPL-3.0 Wall You derivative. Retain upstream notices. */
package com.bnyro.wallpaper.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val NurvaDark = darkColorScheme(
    primary = Color(0xFFB9A8FF),
    onPrimary = Color(0xFF25164D),
    primaryContainer = Color(0xFF362766),
    onPrimaryContainer = Color(0xFFE9E2FF),
    secondary = Color(0xFF56E0D0),
    onSecondary = Color(0xFF003A36),
    secondaryContainer = Color(0xFF174B49),
    onSecondaryContainer = Color(0xFFB5F5ED),
    tertiary = Color(0xFFFFC98D),
    onTertiary = Color(0xFF482900),
    background = Color(0xFF0B1020),
    onBackground = Color(0xFFF3F3FC),
    surface = Color(0xFF121A2D),
    onSurface = Color(0xFFF3F3FC),
    surfaceVariant = Color(0xFF202B43),
    onSurfaceVariant = Color(0xFFC1CBE0),
    outline = Color(0xFF7E8AA8),
    outlineVariant = Color(0xFF3A4661),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val NurvaLight = lightColorScheme(
    primary = Color(0xFF5945B8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8E1FF),
    onPrimaryContainer = Color(0xFF21105F),
    secondary = Color(0xFF006D64),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFA9F1E8),
    onSecondaryContainer = Color(0xFF00201D),
    tertiary = Color(0xFF88520E),
    onTertiary = Color.White,
    background = Color(0xFFF7F7FC),
    onBackground = Color(0xFF171927),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171927),
    surfaceVariant = Color(0xFFE9EAF4),
    onSurfaceVariant = Color(0xFF45495D),
    outline = Color(0xFF74788C),
    outlineVariant = Color(0xFFD0D2E0),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val NurvaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun NurvaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Nurva keeps its own recognizable palette instead of changing colors with the wallpaper.
    val colorScheme = if (darkTheme) NurvaDark else NurvaLight
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = NurvaShapes,
        content = content
    )
}
