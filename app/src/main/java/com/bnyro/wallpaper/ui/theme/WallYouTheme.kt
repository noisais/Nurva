/* Nurva palette customization for the GPL-3.0 Wall You derivative. Retain upstream notices. */
package com.bnyro.wallpaper.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val NurvaDark = darkColorScheme(
    primary=Color(0xFFB6A7FF), onPrimary=Color(0xFF201247), secondary=Color(0xFF4DE4D2),
    onSecondary=Color(0xFF003730), tertiary=Color(0xFFD8C7FF), background=Color(0xFF111321),
    onBackground=Color(0xFFF5F6FA), surface=Color(0xFF191C2E), onSurface=Color(0xFFF5F6FA),
    surfaceVariant=Color(0xFF282D45), onSurfaceVariant=Color(0xFFC5CBE0), outline=Color(0xFF8188A8),
)
private val NurvaLight = lightColorScheme(
    primary=Color(0xFF5843B5), onPrimary=Color.White, secondary=Color(0xFF006B62), onSecondary=Color.White,
    tertiary=Color(0xFF6750A4), background=Color(0xFFF5F6FA), onBackground=Color(0xFF171927),
    surface=Color.White, onSurface=Color(0xFF171927), surfaceVariant=Color(0xFFE5E7F1),
    onSurfaceVariant=Color(0xFF45495B), outline=Color(0xFF74788B),
)

@Composable
fun WallYouTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val context= LocalContext.current
    val scheme=when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> NurvaDark
        else -> NurvaLight
    }
    MaterialTheme(colorScheme=scheme, typography=Typography, content=content)
}
