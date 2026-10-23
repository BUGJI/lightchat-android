package com.benton.lightchat.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

enum class ThemeMode(val value: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色"),
    ;

    companion object {
        fun fromValue(value: String?): ThemeMode =
            entries.firstOrNull { it.value == value } ?: SYSTEM
    }
}

data class AccentColors(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
)

enum class AccentColor(
    val value: String,
    val label: String,
    val light: AccentColors,
    val dark: AccentColors,
) {
    DEFAULT(
        "default",
        "默认",
        AccentColors(Purple40, Color.White, Color(0xFFEADDFF), Color(0xFF21005D)),
        AccentColors(Purple80, Color(0xFF381E72), Color(0xFF4F378B), Color(0xFFEADDFF)),
    ),
    BLUE(
        "blue",
        "蓝色",
        AccentColors(Color(0xFF1565C0), Color.White, Color(0xFFD6E3FF), Color(0xFF001B3E)),
        AccentColors(Color(0xFF9FC9FF), Color(0xFF00325B), Color(0xFF1E4976), Color(0xFFD6E3FF)),
    ),
    GREEN(
        "green",
        "绿色",
        AccentColors(Color(0xFF2E7D32), Color.White, Color(0xFFC8E6C9), Color(0xFF0A2E0C)),
        AccentColors(Color(0xFF81C784), Color(0xFF00390A), Color(0xFF1B5E20), Color(0xFFC8E6C9)),
    ),
    TEAL(
        "teal",
        "青色",
        AccentColors(Color(0xFF00695C), Color.White, Color(0xFFB2DFDB), Color(0xFF00201C)),
        AccentColors(Color(0xFF80CBC4), Color(0xFF003731), Color(0xFF004D45), Color(0xFFB2DFDB)),
    ),
    ORANGE(
        "orange",
        "橙色",
        AccentColors(Color(0xFFEF6C00), Color.White, Color(0xFFFFDCC2), Color(0xFF2C1600)),
        AccentColors(Color(0xFFFFB77C), Color(0xFF4A2800), Color(0xFF6A3C00), Color(0xFFFFDCC2)),
    ),
    PINK(
        "pink",
        "粉色",
        AccentColors(Color(0xFFC2185B), Color.White, Color(0xFFFFD9E2), Color(0xFF3E0021)),
        AccentColors(Color(0xFFFFB1C8), Color(0xFF5E1133), Color(0xFF7D2948), Color(0xFFFFD9E2)),
    ),
    RED(
        "red",
        "红色",
        AccentColors(Color(0xFFC62828), Color.White, Color(0xFFFFDAD6), Color(0xFF410002)),
        AccentColors(Color(0xFFFFB4AB), Color(0xFF690005), Color(0xFF93000A), Color(0xFFFFDAD6)),
    ),
    INDIGO(
        "indigo",
        "靛蓝",
        AccentColors(Color(0xFF303F9F), Color.White, Color(0xFFE0E0FF), Color(0xFF00105C)),
        AccentColors(Color(0xFFBAC3FF), Color(0xFF08218A), Color(0xFF293CA0), Color(0xFFE0E0FF)),
    ),
    ;

    companion object {
        fun fromValue(value: String?): AccentColor =
            entries.firstOrNull { it.value == value } ?: DEFAULT
    }
}

const val MIN_FONT_SCALE = 0.85f
const val MAX_FONT_SCALE = 1.3f
const val DEFAULT_FONT_SCALE = 1f
const val DEFAULT_CORNER_RADIUS = 16
const val MAX_CORNER_RADIUS = 28

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
)

private fun accentScheme(accent: AccentColor, darkTheme: Boolean): ColorScheme {
    val base = if (darkTheme) DarkColorScheme else LightColorScheme
    val colors = if (darkTheme) accent.dark else accent.light
    return base.copy(
        primary = colors.primary,
        onPrimary = colors.onPrimary,
        primaryContainer = colors.primaryContainer,
        onPrimaryContainer = colors.onPrimaryContainer,
        secondary = colors.primary,
        onSecondary = colors.onPrimary,
        secondaryContainer = colors.primaryContainer,
        onSecondaryContainer = colors.onPrimaryContainer,
        tertiary = colors.primary,
        onTertiary = colors.onPrimary,
        tertiaryContainer = colors.primaryContainer,
        onTertiaryContainer = colors.onPrimaryContainer,
    )
}

private fun ColorScheme.amoled(): ColorScheme = copy(
    background = Color.Black,
    onBackground = Color(0xFFE6E1E5),
    surface = Color.Black,
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF1C1B1F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF1C1B1F),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF222222),
)

private fun lightchatShapes(cornerRadius: Int): Shapes {
    val scale = cornerRadius.coerceIn(0, MAX_CORNER_RADIUS) / DEFAULT_CORNER_RADIUS.toFloat()
    fun shape(base: Int) = RoundedCornerShape((base * scale).dp)
    return Shapes(
        extraSmall = shape(4),
        small = shape(8),
        medium = shape(12),
        large = shape(16),
        extraLarge = shape(28),
    )
}

@Composable
fun LightchatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    amoled: Boolean = false,
    accentColor: AccentColor = AccentColor.DEFAULT,
    cornerRadius: Int = DEFAULT_CORNER_RADIUS,
    fontScale: Float = DEFAULT_FONT_SCALE,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> accentScheme(accentColor, darkTheme = true)
        else -> accentScheme(accentColor, darkTheme = false)
    }.let { if (amoled && darkTheme) it.amoled() else it }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    val density = LocalDensity.current
    val userFontScale = fontScale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
    val scaledDensity = Density(density.density, density.fontScale * userFontScale)

    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = lightchatShapes(cornerRadius),
            content = content,
        )
    }
}
