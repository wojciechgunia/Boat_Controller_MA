package pl.poznan.put.boatcontroller.domain.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import kotlinx.coroutines.launch

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
    val isSnapshotCapturing by HttpStreamRepository.isSnapshotCapturing.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Renderujemy przycisk zawsze gdy jest połączenie, ale ukrywamy go wizualnie (alpha=0) podczas robienia zrzutu.
    // Dzięki temu coroutine scope nie jest anulowany w trakcie operacji (co stałoby się przy użyciu if(!isSnapshotCapturing)).
    if (connectionState == ConnectionState.Connected && isWebViewReady) {
        FloatingActionButton(
            onClick = {
                val activity = context as? Activity
                if (activity != null) {
                    scope.launch {
                        val bitmap = HttpStreamRepository.captureSnapshot(activity.window)
                        
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
                    }
                } else {
                    InfoPopupManager.show(
                        message = "Błąd wewnętrzny: brak dostępu do okna aplikacji.",
                        type = InfoPopupType.ERROR
                    )
                }
            },
            shape = CircleShape,
            modifier = modifier
                .shadow(16.dp, CircleShape, clip = false)
                .clip(CircleShape)
                .alpha(if (isSnapshotCapturing) 0f else 1f),
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

