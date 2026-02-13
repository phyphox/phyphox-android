package de.rwth_aachen.phyphox.ui.theme

import android.app.Activity
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Immutable
data class PhyphoxColors(
    val primary: Color = PhyphoxPrimary,
    val primaryWeak: Color = PhyphoxPrimaryWeak,
    val rwthPrimary: Color = PhyphoxRwthPrimary,
    val white100: Color = PhyphoxWhite100,
    val white90: Color = PhyphoxWhite90,
    val white80: Color = PhyphoxWhite80,
    val white70: Color = PhyphoxWhite70,
    val white60: Color = PhyphoxWhite60,
    val white50Black50: Color = PhyphoxWhite50Black50,
    val black40: Color = PhyphoxBlack40,
    val black50: Color = PhyphoxBlack50,
    val black60: Color = PhyphoxBlack60,
    val black80: Color = PhyphoxBlack80,
    val black100: Color = PhyphoxBlack100,
    val blue100: Color = PhyphoxBlue100,
    val blue60: Color = PhyphoxBlue60,
    val blue40: Color = PhyphoxBlue40,
    val blueStrong: Color = PhyphoxBlueStrong,
    val red: Color = PhyphoxRed,
    val redWeak: Color = PhyphoxRedWeak,
    val magenta: Color = PhyphoxMagenta,
    val magentaWeak: Color = PhyphoxMagentaWeak,
    val green: Color = PhyphoxGreen,
    val greenWeak: Color = PhyphoxGreenWeak,
    val greenStrong: Color = PhyphoxGreenStrong,
    val yellowWeak: Color = PhyphoxYellowWeak,
    val yellow: Color = PhyphoxYellow,
    val yellowStrong: Color = PhyphoxYellowStrong,
)

val LocalPhyphoxColors = staticCompositionLocalOf { PhyphoxColors() }

@Suppress("Unused", "UnusedReceiverParameter")
val MaterialTheme.customColors: PhyphoxColors
    @Composable
    @ReadOnlyComposable
    get() = LocalPhyphoxColors.current

private val DarkColorScheme = darkColorScheme(
    primary = PhyphoxPrimary,
    secondary = PhyphoxPrimaryWeak,
    tertiary = PhyphoxBlue,
    background = PhyphoxBlack60,
    surface = PhyphoxBlack50,
    onPrimary = PhyphoxWhite100,
    onSecondary = PhyphoxBlack100,
    onTertiary = PhyphoxWhite100,
    onBackground = PhyphoxWhite100,
    onSurface = PhyphoxWhite100,
    surfaceVariant = PhyphoxBlack80,
    onSurfaceVariant = PhyphoxWhite80
)

private val LightColorScheme = lightColorScheme(
    primary = PhyphoxPrimary,
    secondary = PhyphoxPrimaryWeak,
    tertiary = PhyphoxBlue,
    background = PhyphoxWhite100,
    surface = PhyphoxWhite100,
    onPrimary = PhyphoxWhite100,
    onSecondary = PhyphoxBlack100,
    onTertiary = PhyphoxWhite100,
    onBackground = PhyphoxBlack60,
    onSurface = PhyphoxBlack60,
    surfaceVariant = PhyphoxWhite90,
    onSurfaceVariant = PhyphoxBlack60
)

@Composable
fun PhyphoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalPhyphoxColors provides PhyphoxColors()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
