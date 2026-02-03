package pl.poznan.put.boatcontroller.domain.components

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
import pl.poznan.put.boatcontroller.domain.enums.ConnectionState
import pl.poznan.put.boatcontroller.ui.controller.ControllerViewModel
import pl.poznan.put.boatcontroller.R
import pl.poznan.put.boatcontroller.backend.remote.http.HttpStreamRepository
import pl.poznan.put.boatcontroller.domain.components.info_popup.InfoPopupManager
import pl.poznan.put.boatcontroller.domain.components.info_popup.InfoPopupType

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
    val isWebViewReady by HttpStreamRepository.isWebViewReady.collectAsState()

    if (connectionState == ConnectionState.Connected && isWebViewReady) {
        FloatingActionButton(
            onClick = {
                val bitmap = HttpStreamRepository.captureWebViewBitmap()
                
                if (bitmap != null) {
                    viewModel.createPoiWithImage(
                        bitmap = bitmap,
                        name = name,
                        description = description
                    )
                } else {
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
            containerColor = MaterialTheme.colorScheme.primary
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

