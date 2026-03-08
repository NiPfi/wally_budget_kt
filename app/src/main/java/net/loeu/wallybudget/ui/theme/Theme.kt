package net.loeu.wallybudget.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Sage80,
    secondary = Moss80,
    tertiary = Amber80,
    background = Color(0xFF101513),
    surface = Color(0xFF101513),
    onPrimary = Color(0xFF0F372A),
    onSecondary = Color(0xFF1C211A),
    onTertiary = Color(0xFF352400),
    onBackground = Color(0xFFE2E7E1),
    onSurface = Color(0xFFE2E7E1),
    surfaceVariant = Color(0xFF24312B),
    onSurfaceVariant = Color(0xFFB8C5BC),
    primaryContainer = Color(0xFF1A4C3A),
    onPrimaryContainer = Color(0xFFCDEBDD),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    tertiaryContainer = Color(0xFF5A430C),
    onTertiaryContainer = Color(0xFFFFDEAA)
)

private val LightColorScheme = lightColorScheme(
    primary = Sage40,
    secondary = Moss40,
    tertiary = Amber40,
    background = Color(0xFFF4F1E8),
    surface = Color(0xFFF8F6F0),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF181D19),
    onSurface = Color(0xFF181D19),
    surfaceVariant = Color(0xFFDCE4D9),
    onSurfaceVariant = Color(0xFF404942),
    primaryContainer = Color(0xFFCDEBDD),
    onPrimaryContainer = Color(0xFF0A2118),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    tertiaryContainer = Color(0xFFF2D9A6),
    onTertiaryContainer = Color(0xFF2A1C00)
)

@Composable
fun WallyBudgetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
