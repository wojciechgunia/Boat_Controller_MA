package pl.poznan.put.boatcontroller.templates.poi_window

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import pl.poznan.put.boatcontroller.R
import pl.poznan.put.boatcontroller.dataclass.POIObject
import kotlin.text.Typography.nbsp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import pl.poznan.put.boatcontroller.ui.theme.DarkPlaceholder
import pl.poznan.put.boatcontroller.ui.theme.LightPlaceholder
import pl.poznan.put.boatcontroller.ui.theme.ErrorRed
import pl.poznan.put.boatcontroller.ui.theme.LightImageFrame
import pl.poznan.put.boatcontroller.ui.theme.DarkImageFrame
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.border

@SuppressLint("AutoboxingStateCreation")
@Composable
fun FullScreenPopup(
    isOpen: Boolean,
    onClose: () -> Unit,
    poiId: Int,
    poiList: List<POIObject>,
    onSaveName: (Int, String) -> Unit,
    onSaveDescription: (Int, String) -> Unit,
    onDelete: (Int) -> Unit
) {
    if (!isOpen) return

    val darkTheme = isSystemInDarkTheme()
    val backgroundColor = MaterialTheme.colorScheme.background
    val textColor = MaterialTheme.colorScheme.onBackground
    val surfaceColor = MaterialTheme.colorScheme.surface

    // Obserwuj indeks POI z managera używając StateFlow (jak SocketRepository.batteryLevel)
    val managerPoiIndex by POIWindowStateManager.currentPoiIndex.collectAsState()
    
    // Loguj wartości z managera
    LaunchedEffect(managerPoiIndex) {
        Log.d("POIWindow", "managerPoiIndex zmienił się na: $managerPoiIndex")
    }
    
    // Inicjalizacja tylko raz przy pierwszym otwarciu - jeśli poiId jest prawidłowy (kliknięcie na mapie), użyj go
    // Po inicjalizacji używamy tylko wartości z managera (przetrwa obrót ekranu)
    var initialized by rememberSaveable { mutableStateOf(false) }
    var lastPoiId by rememberSaveable { mutableIntStateOf(-1) }
    LaunchedEffect(poiId, poiList.size) {
        // Inicjalizuj tylko raz przy pierwszym otwarciu lub gdy poiId się zmienia (nowe kliknięcie na mapie)
        if ((!initialized || poiId != lastPoiId) && poiList.isNotEmpty()) {
            initialized = true
            lastPoiId = poiId
            Log.d("POIWindow", "LaunchedEffect(poiId): poiId=$poiId, managerPoiIndex=$managerPoiIndex, poiList.size=${poiList.size}, initialized=$initialized")
            if (poiId >= 0 && poiId < poiList.size) {
                // Kliknięcie na mapie - użyj poiId jako indeksu tylko jeśli różni się od wartości w managerze
                if (managerPoiIndex != poiId) {
                    Log.d("POIWindow", "Kliknięcie na mapie - ustawiam poiIndex=$poiId (było: $managerPoiIndex)")
                    POIWindowStateManager.updatePoiIndex(poiId)
                } else {
                    Log.d("POIWindow", "Kliknięcie na mapie - poiIndex już jest ustawiony na $poiId")
                }
            } else {
                // Jeśli poiId jest nieprawidłowy, nie rób nic - użyj zapamiętanego z managera
                Log.d("POIWindow", "poiId nieprawidłowy ($poiId) - używam managerPoiIndex=$managerPoiIndex")
            }
        } else {
            Log.d("POIWindow", "LaunchedEffect(poiId): pomijam - initialized=$initialized, poiId=$poiId, lastPoiId=$lastPoiId")
        }
    }
    
    // Waliduj indeks z managera tylko gdy lista się zmienia
    LaunchedEffect(poiList.size) {
        if (poiList.isNotEmpty() && managerPoiIndex >= poiList.size) {
            Log.d("POIWindow", "Lista się zmieniła - waliduję indeks: managerPoiIndex=$managerPoiIndex >= poiList.size=${poiList.size}")
            POIWindowStateManager.updatePoiIndex(0)
        }
    }
    
    // Waliduj indeks z managera względem aktualnej listy
    val currentIndex = if (poiList.isEmpty()) {
        0
    } else {
        managerPoiIndex.coerceIn(0, poiList.size - 1)
    }
    
    // Loguj currentIndex
    LaunchedEffect(currentIndex) {
        Log.d("POIWindow", "currentIndex zmienił się na: $currentIndex (managerPoiIndex=$managerPoiIndex)")
    }
    
    
    // Oblicz currentPoi z currentIndex
    val currentPoi = poiList.getOrNull(currentIndex) ?: POIObject(
        id = 0,
        missionId = 0,
        lon = 0.0,
        lat = 0.0
    )
    
    // Parsuj pictures do listy URL
    val picturesList = remember(currentPoi.pictures) {
        if (currentPoi.pictures.isNullOrBlank()) {
            emptyList()
        } else {
            try {
                val gson = Gson()
                val listType = object : TypeToken<List<String>>() {}.type
                gson.fromJson<List<String>>(currentPoi.pictures, listType) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
    
    // Obserwuj indeks zdjęcia z managera używając StateFlow
    val managerImageIndex by POIWindowStateManager.currentImageIndex.collectAsState()
    
    // Loguj wartości z managera
    LaunchedEffect(managerImageIndex) {
        Log.d("POIWindow", "managerImageIndex zmienił się na: $managerImageIndex")
    }
    
    // Lokalna zmienna do aktualizacji (synchronizowana z managerem)
    // Używamy rememberSaveable tylko dla tej zmiennej - musi przetrwać obrót ekranu
    var lastPoiIdForImage by rememberSaveable { mutableIntStateOf(-1) }
    var currentImageIndex by remember { mutableIntStateOf(managerImageIndex) }
    
    // Gdy zmienia się POI (currentPoi.id), resetuj indeks zdjęcia
    LaunchedEffect(currentPoi.id) {
        Log.d("POIWindow", "LaunchedEffect(currentPoi.id): currentPoi.id=${currentPoi.id}, lastPoiIdForImage=$lastPoiIdForImage")
        if (currentPoi.id != lastPoiIdForImage) {
            lastPoiIdForImage = currentPoi.id
            // Resetuj tylko gdy POI się zmienia
            Log.d("POIWindow", "POI się zmienił - resetuję imageIndex do 0")
            currentImageIndex = 0
            POIWindowStateManager.updateImageIndex(0)
        }
    }
    
    // Przywróć wartość z managera gdy się zmienia (po obrocie ekranu)
    // Ale tylko jeśli POI się nie zmienił (żeby nie nadpisać resetu)
    LaunchedEffect(managerImageIndex, currentPoi.id) {
        Log.d("POIWindow", "LaunchedEffect(managerImageIndex): managerImageIndex=$managerImageIndex, currentPoi.id=${currentPoi.id}, lastPoiIdForImage=$lastPoiIdForImage, currentImageIndex=$currentImageIndex")
        // Przywróć tylko jeśli POI się nie zmienił (żeby nie nadpisać resetu)
        if (currentPoi.id == lastPoiIdForImage && currentImageIndex != managerImageIndex) {
            Log.d("POIWindow", "Przywracam imageIndex z managera: $managerImageIndex")
            currentImageIndex = managerImageIndex
        }
    }
    
    // Synchronizuj zmiany indeksu zdjęcia z managerem (gdy użytkownik nawiguje)
    LaunchedEffect(currentImageIndex) {
        Log.d("POIWindow", "LaunchedEffect(currentImageIndex): currentImageIndex=$currentImageIndex, currentPoi.id=${currentPoi.id}, lastPoiIdForImage=$lastPoiIdForImage")
        // Aktualizuj managera tylko jeśli wartość się zmieniła i POI się nie zmienił
        if (currentPoi.id == lastPoiIdForImage && POIWindowStateManager.currentImageIndex.value != currentImageIndex) {
            Log.d("POIWindow", "Aktualizuję managera imageIndex: $currentImageIndex")
            POIWindowStateManager.updateImageIndex(currentImageIndex)
        }
    }
    
    val currentImageUrl = picturesList.getOrNull(currentImageIndex)

    var expandedImage by remember { mutableStateOf(false) }
    var isEditingName by remember { mutableStateOf(false) }
    var isEditingDescription by remember { mutableStateOf(false) }
    var nameValue by remember(currentPoi.id) { mutableStateOf(TextFieldValue(currentPoi.name.orEmpty())) }
    var descriptionValue by remember(currentPoi.id) { mutableStateOf(TextFieldValue(currentPoi.description.orEmpty())) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        if (expandedImage && currentImageUrl != null) {
            ExpandedImageView(
                imageUrl = currentImageUrl,
                onClose = { expandedImage = false },
            )
        } else {
            Row(Modifier.fillMaxSize()) {
                // Sekcja ze zdjęciem - zajmuje całą dostępną przestrzeń minus szerokość ControlPanel
                // Zdjęcie używa całego ekranu (bez systemBarsPadding)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f) // Zajmuje resztę przestrzeni
                        .border(2.dp, if (darkTheme) DarkImageFrame else LightImageFrame)
                ) {
                    if (currentImageUrl != null) {
                        SwipeableImageSection(
                            images = picturesList,
                            currentIndex = currentImageIndex,
                            onIndexChange = { newIndex ->
                                // Cykliczne przechodzenie między zdjęciami
                                val finalIndex = when {
                                    newIndex < 0 -> picturesList.lastIndex
                                    newIndex >= picturesList.size -> 0
                                    else -> newIndex
                                }
                                currentImageIndex = finalIndex
                                // Zapisz do managera
                                POIWindowStateManager.updateImageIndex(finalIndex)
                            },
                            onExpand = { expandedImage = true },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Brak obrazów - pokaż placeholder
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(if (darkTheme) DarkPlaceholder else LightPlaceholder),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Brak obrazów",
                                color = textColor,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
                
                // Panel kontrolny - stała szerokość 280dp jak wcześniej
                ControlPanel(
                    currentPoi = currentPoi,
                    currentImageIndex = currentImageIndex,
                    totalImages = picturesList.size,
                    onEditName = {
                        isEditingName = true
                        nameValue = TextFieldValue(currentPoi.name.orEmpty())
                    },
                    onEditDescription = {
                        isEditingDescription = true
                        descriptionValue = TextFieldValue(currentPoi.description.orEmpty())
                    },
                    onDelete = onDelete,
                    onPrev = {
                        if (poiList.isNotEmpty()) {
                            val newIndex = if (currentIndex > 0) currentIndex - 1 else poiList.lastIndex
                            POIWindowStateManager.updatePoiIndex(newIndex)
                        }
                    },
                    onNext = {
                        if (poiList.isNotEmpty()) {
                            val newIndex = if (currentIndex < poiList.lastIndex) currentIndex + 1 else 0
                            POIWindowStateManager.updatePoiIndex(newIndex)
                        }
                    },
                    textColor = textColor,
                    surfaceColor = surfaceColor,
                    modifier = Modifier.width(280.dp), // Stała szerokość jak wcześniej
                    onClose = onClose
                )
            }
        }

        if (isEditingName || isEditingDescription) {
            EditBar(
                isEditingName = isEditingName,
                isEditingDescription = isEditingDescription,
                nameValue = nameValue,
                descriptionValue = descriptionValue,
                onNameChange = { nameValue = it },
                onDescriptionChange = { descriptionValue = it },
                onSaveName = {
                    onSaveName(currentPoi.id, nameValue.text)
                    isEditingName = false
                    // currentPoi jest obliczany z poiList, więc zaktualizuje się automatycznie po zapisaniu
                },
                onSaveDescription = {
                    onSaveDescription(currentPoi.id, descriptionValue.text)
                    isEditingDescription = false
                    // currentPoi jest obliczany z poiList, więc zaktualizuje się automatycznie po zapisaniu
                },
                textColor = textColor,
                surfaceColor = surfaceColor
            )
        }
    }
}

@Composable
private fun ExpandedImageView(
    imageUrl: String,
    onClose: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offsetX += pan.x
                    offsetY += pan.y
                }
            }
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit, // Zachowaj aspect ratio, wypełnij maksymalnie dostępną przestrzeń
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

@Composable
private fun SwipeableImageSection(
    images: List<String>,
    currentIndex: Int,
    onIndexChange: (Int) -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var containerWidth by remember { mutableFloatStateOf(0f) }
    var isAnimating by remember { mutableStateOf(false) }
    var targetIndex by remember { mutableIntStateOf(currentIndex) }
    val coroutineScope = rememberCoroutineScope()
    val imageUrl = images.getOrNull(currentIndex) ?: return
    
    // Animatable do kontroli animacji - pozwala na snapTo i animateTo
    val animatedOffset = remember { Animatable(0f) }
    
    // Użyj animowanej wartości podczas animacji, w przeciwnym razie użyj dragOffsetX
    val displayOffsetX = if (isAnimating) animatedOffset.value else dragOffsetX
    
    // Resetuj offset gdy zmienia się indeks zdjęcia (np. przez strzałki)
    LaunchedEffect(currentIndex) {
        if (!isAnimating && targetIndex == currentIndex) {
            dragOffsetX = 0f
            animatedOffset.snapTo(0f)
        }
    }
    
    Box(
        modifier = modifier
            .onSizeChanged { size ->
                containerWidth = size.width.toFloat()
            }
            .pointerInput(currentIndex, containerWidth) {
                if (containerWidth <= 0f) return@pointerInput
                
                detectDragGestures(
                    onDragStart = { offset ->
                        // Zatrzymaj animację jeśli trwa i zsynchronizuj pozycję
                        if (isAnimating) {
                            dragOffsetX = animatedOffset.value
                            coroutineScope.launch {
                                animatedOffset.stop()
                            }
                            isAnimating = false
                            targetIndex = currentIndex
                        }
                    },
                    onDrag = { change, dragAmount ->
                        // Zawsze pozwól na przesuwanie (cykliczne przechodzenie)
                        dragOffsetX = (dragOffsetX + dragAmount.x).coerceIn(-containerWidth, containerWidth)
                    },
                    onDragEnd = {
                        // Po zakończeniu przeciągania sprawdź czy przesunięcie było wystarczające
                        val threshold = containerWidth * 0.25f // 25% szerokości ekranu
                        
                        when {
                            dragOffsetX > threshold -> {
                                // Przesuń w prawo - poprzednie zdjęcie (lub ostatnie jeśli jesteśmy na pierwszym)
                                targetIndex = if (currentIndex > 0) currentIndex - 1 else images.lastIndex
                                // Rozpocznij animację od aktualnej pozycji dragOffsetX do końca w prawo
                                isAnimating = true
                                coroutineScope.launch {
                                    animatedOffset.snapTo(dragOffsetX) // Ustaw początkową wartość
                                    animatedOffset.animateTo(
                                        targetValue = containerWidth,
                                        animationSpec = tween(
                                            durationMillis = 300,
                                            easing = FastOutSlowInEasing
                                        )
                                    )
                                    // Po zakończeniu animacji zmień indeks
                                    onIndexChange(targetIndex)
                                    // Zresetuj wartości
                                    dragOffsetX = 0f
                                    animatedOffset.snapTo(0f)
                                    isAnimating = false
                                }
                            }
                            dragOffsetX < -threshold -> {
                                // Przesuń w lewo - następne zdjęcie (lub pierwsze jeśli jesteśmy na ostatnim)
                                targetIndex = if (currentIndex < images.size - 1) currentIndex + 1 else 0
                                // Rozpocznij animację od aktualnej pozycji dragOffsetX do końca w lewo
                                isAnimating = true
                                coroutineScope.launch {
                                    animatedOffset.snapTo(dragOffsetX) // Ustaw początkową wartość
                                    animatedOffset.animateTo(
                                        targetValue = -containerWidth,
                                        animationSpec = tween(
                                            durationMillis = 300,
                                            easing = FastOutSlowInEasing
                                        )
                                    )
                                    // Po zakończeniu animacji zmień indeks
                                    onIndexChange(targetIndex)
                                    // Zresetuj wartości
                                    dragOffsetX = 0f
                                    animatedOffset.snapTo(0f)
                                    isAnimating = false
                                }
                            }
                            else -> {
                                // Za słabo przesunięte - animuj z powrotem do środka
                                targetIndex = currentIndex
                                isAnimating = true
                                coroutineScope.launch {
                                    animatedOffset.snapTo(dragOffsetX) // Ustaw początkową wartość
                                    animatedOffset.animateTo(
                                        targetValue = 0f,
                                        animationSpec = tween(
                                            durationMillis = 300,
                                            easing = FastOutSlowInEasing
                                        )
                                    )
                                    // Po zakończeniu animacji zresetuj wartości
                                    dragOffsetX = 0f
                                    animatedOffset.snapTo(0f)
                                    isAnimating = false
                                }
                            }
                        }
                    }
                )
            }
    ) {
        // Wyświetl aktualne zdjęcie z przesunięciem
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    translationX = displayOffsetX
                )
        )
        
        // Jeśli przesuwamy, pokaż podgląd następnego/poprzedniego zdjęcia
        if (displayOffsetX != 0f && containerWidth > 0f) {
            val nextIndex = when {
                displayOffsetX > 0 -> {
                    // Przesuwamy w prawo - pokaż poprzednie (lub ostatnie jeśli jesteśmy na pierwszym)
                    if (currentIndex > 0) currentIndex - 1 else images.lastIndex
                }
                displayOffsetX < 0 -> {
                    // Przesuwamy w lewo - pokaż następne (lub pierwsze jeśli jesteśmy na ostatnim)
                    if (currentIndex < images.size - 1) currentIndex + 1 else 0
                }
                else -> null
            }
            
            val nextImageUrl = nextIndex?.let { images.getOrNull(it) }
            if (nextImageUrl != null) {
                val nextOffset = if (displayOffsetX > 0) {
                    // Pokazuj poprzednie zdjęcie po lewej stronie
                    displayOffsetX - containerWidth
                } else {
                    // Pokazuj następne zdjęcie po prawej stronie
                    displayOffsetX + containerWidth
                }
                
                AsyncImage(
                    model = nextImageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            translationX = nextOffset
                        )
                )
            }
        }
        
        // Przycisk FAB do pełnoekranowego podglądu (bez tła, tylko ikona) - górny prawy róg
        FloatingActionButton(
            onClick = onExpand,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .alpha(0.7f),
            containerColor = Color.Transparent,
            elevation = FloatingActionButtonDefaults.elevation(0.dp)
        ) {
            Icon(
                Icons.Default.Fullscreen,
                contentDescription = "Fullscreen",
                tint = Color.White,
                modifier = Modifier.size(32.dp) // Większa ikona
            )
        }
    }
}

@Composable
private fun ControlPanel(
    currentPoi: POIObject,
    currentImageIndex: Int,
    totalImages: Int,
    onEditName: () -> Unit,
    onEditDescription: () -> Unit,
    onDelete: (Int) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    textColor: Color,
    surfaceColor: Color,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {}
) {
    var showDialog by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .background(surfaceColor)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(16.dp, top = 32.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                // Przycisk zamknięcia w górnym rogu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = textColor
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Name: ${currentPoi.name ?: "null"}",
                    color = textColor,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onEditName) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = textColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Description: ${currentPoi.description ?: "null"}",
                    color = textColor,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onEditDescription) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = textColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            // Wskaźnik zdjęć (np. "Zdjęcie 1/3")
            if (totalImages > 0) {
                Text(
                    text = "Zdjęcie ${currentImageIndex + 1}/$totalImages",
                    color = textColor.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        Column {
            Row(Modifier.padding(top = 8.dp)) {
                Text(
                    text = "Lon:${nbsp}${"%.5f".format(currentPoi.lon)} | ",
                    fontSize = 12.sp,
                    color = textColor.copy(alpha = 0.7f)
                )
                Text(
                    text = "Lat:${nbsp}${"%.5f".format(currentPoi.lat)}",
                    fontSize = 12.sp,
                    color = textColor.copy(alpha = 0.7f)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                OutlinedButton(
                    onClick = { showDialog = true },
                    border = BorderStroke(1.dp, ErrorRed),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .width(150.dp)
                        .height(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = ErrorRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete", color = ErrorRed, fontSize = 15.sp)
                }
            }
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(end = 12.dp)
            ) {
                OutlinedButton(
                    onClick = onPrev,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor)
                ) {
                    Icon(
                        Icons.Default.ChevronLeft,
                        contentDescription = "Prev",
                        tint = textColor
                    )
                }
                OutlinedButton(
                    onClick = onNext,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor)
                ) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Next",
                        tint = textColor
                    )
                }
            }
        }
        }
    }
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Are you sure?", color = textColor) },
            text = { Text("Do you really want to delete this point?", color = textColor) },
            containerColor = surfaceColor,
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    onDelete(currentPoi.id)
                }) {
                    Text("Yes", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("No", color = textColor)
                }
            }
        )
    }
}

@Composable
private fun EditBar(
    isEditingName: Boolean,
    isEditingDescription: Boolean,
    nameValue: TextFieldValue,
    descriptionValue: TextFieldValue,
    onNameChange: (TextFieldValue) -> Unit,
    onDescriptionChange: (TextFieldValue) -> Unit,
    onSaveName: () -> Unit,
    onSaveDescription: () -> Unit,
    textColor: Color,
    surfaceColor: Color
) {
    Surface(
        color = surfaceColor,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .systemBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isEditingName) {
                TextField(
                    value = nameValue,
                    onValueChange = onNameChange,
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor
                    )
                )
                IconButton(onClick = onSaveName) {
                    Icon(
                        painterResource(id = R.drawable.save),
                        contentDescription = "Save",
                        tint = textColor
                    )
                }
            }
            if (isEditingDescription) {
                TextField(
                    value = descriptionValue,
                    onValueChange = onDescriptionChange,
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor
                    )
                )
                IconButton(onClick = onSaveDescription) {
                    Icon(
                        painterResource(id = R.drawable.save),
                        contentDescription = "Save",
                        tint = textColor
                    )
                }
            }
        }
    }
}
