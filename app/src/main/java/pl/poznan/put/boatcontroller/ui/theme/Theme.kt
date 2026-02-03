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
    primary = PrimaryBlue, // Główny kolor aplikacji - używany dla wszystkich głównych przycisków
    onPrimary = AppWhite, // Tekst/ikony na przyciskach primary
    secondary = PrimaryLightBlue, // Jaśniejszy niebieski dla tekstu w Tab'ach i tytułów sekcji
    background = DarkBackground, // Tło całej aplikacji
    onBackground = DarkOnSurface, // Tekst na tle aplikacji
    surface = DarkSurface, // Powierzchnie (karty, panele)
    onSurface = DarkOnSurface, // Tekst na powierzchniach
    surfaceVariant = DarkPlaceholder, // Wariant powierzchni (panele zwijarki, sekcje)
    onSurfaceVariant = DarkOnSurface, // Tekst na surfaceVariant
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue, // Główny kolor aplikacji - używany dla wszystkich głównych przycisków
    onPrimary = AppWhite, // Tekst/ikony na przyciskach primary
    secondary = PrimaryLightBlue, // Jaśniejszy niebieski dla tekstu w Tab'ach i tytułów sekcji
    background = LightBackground, // Tło całej aplikacji
    onBackground = LightOnSurface, // Tekst na tle aplikacji
    surface = LightSurface, // Powierzchnie (karty, panele)
    onSurface = LightOnSurface, // Tekst na powierzchniach
    surfaceVariant = LightPlaceholder, // Wariant powierzchni (panele zwijarki, sekcje)
    onSurfaceVariant = LightOnSurface, // Tekst na surfaceVariant
)

@Composable
fun BoatControllerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
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
