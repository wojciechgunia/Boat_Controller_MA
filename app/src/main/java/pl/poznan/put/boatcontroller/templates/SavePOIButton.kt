package pl.poznan.put.boatcontroller.templates

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import pl.poznan.put.boatcontroller.ConnectionState
import pl.poznan.put.boatcontroller.ControllerViewModel
import pl.poznan.put.boatcontroller.R
import pl.poznan.put.boatcontroller.socket.HttpStreamRepository
import pl.poznan.put.boatcontroller.templates.info_popup.InfoPopupManager
import pl.poznan.put.boatcontroller.templates.info_popup.InfoPopupType

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/**
 * Przycisk do zapisu POI z obrazem z kamery lub sonaru.
 * 
 * @param viewModel ViewModel zawierający logikę zapisu POI
 * @param connectionState Aktualny stan połączenia HTTP stream
 * @param modifier Modifier do zastosowania
 * @param name Opcjonalna nazwa POI
 * @param description Opcjonalny opis POI
 */
@Composable
fun SavePOIButton(
    viewModel: ControllerViewModel,
    connectionState: ConnectionState,
    modifier: Modifier = Modifier,
    name: String? = null,
    description: String? = null
) {
    // Obserwuj czy WebView jest gotowy do interakcji
    val isWebViewReady by HttpStreamRepository.isWebViewReady.collectAsState()
    
    // Przycisk widoczny tylko gdy mamy połączenie i WebView jest gotowy
    if (connectionState == ConnectionState.Connected && isWebViewReady) {
        FloatingActionButton(
            onClick = {
                // Synchroniczne przechwytywanie bitmapy (uproszczone, bez PixelCopy)
                // Dzięki temu nie przechwytujemy elementów interfejsu (przycisków, wskaźników stanu)
                val bitmap = HttpStreamRepository.captureWebViewBitmap()
                
                if (bitmap != null) {
                    // Wywołaj funkcję zapisu POI z obrazem
                    viewModel.createPoiWithImage(
                        bitmap = bitmap,
                        name = name,
                        description = description
                    )
                } else {
                    // Jeśli nie udało się przechwycić bitmapy
                    InfoPopupManager.show(
                        message = "Nie można przechwycić obrazu. Upewnij się, że stream jest w pełni załadowany.",
                        type = InfoPopupType.ERROR
                    )
                }
            },
            shape = CircleShape,
            modifier = modifier
                .shadow(16.dp, CircleShape, clip = false)
                .clip(CircleShape),
            containerColor = MaterialTheme.colorScheme.primary // Ujednolicone z MaterialTheme
        ) {
            Icon(
                painter = painterResource(id = R.drawable.save),
                contentDescription = "Save POI",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

