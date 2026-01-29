package pl.poznan.put.boatcontroller.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryLightBlue, // Jaśniejszy niebieski dla tekstu w TabRow i sensorach
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = AppWhite,
    onSecondary = AppWhite,
    onTertiary = AppWhite,
    onBackground = DarkOnSurface,
    onSurface = DarkOnSurface
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLightBlue, // Jaśniejszy niebieski dla tekstu w TabRow i sensorach
    background = LightBackground,
    surface = LightSurface,
    onPrimary = AppWhite,
    onSecondary = AppWhite,
    onTertiary = AppWhite,
    onBackground = LightOnSurface,
    onSurface = LightOnSurface
)

@Composable
fun BoatControllerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Wyłączone aby używać naszych kolorów
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
