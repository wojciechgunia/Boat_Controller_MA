package pl.poznan.put.boatcontroller.domain.components.poi_window

import android.annotation.SuppressLint
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
import pl.poznan.put.boatcontroller.domain.dataclass.POIObject
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

    val managerPoiIndex by POIWindowStateManager.currentPoiIndex.collectAsState()
    val managerImageIndex by POIWindowStateManager.currentImageIndex.collectAsState()
    val managerPoiId by POIWindowStateManager.currentPoiId.collectAsState()

    LaunchedEffect(poiId, poiList.size) {
        POIWindowStateManager.initializeIfNeeded(poiId, poiList.size)
    }

    LaunchedEffect(poiList.size) {
        if (poiList.isNotEmpty() && managerPoiIndex >= poiList.size) {
            POIWindowStateManager.updatePoiIndex(0)
        }
    }

    val currentIndex = if (poiList.isEmpty()) {
        0
    } else {
        managerPoiIndex.coerceIn(0, poiList.size - 1)
    }

    val currentPoi = poiList.getOrNull(currentIndex) ?: POIObject(
        id = 0,
        missionId = 0,
        lon = 0.0,
        lat = 0.0
    )

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

    LaunchedEffect(currentPoi.id) {
        if (currentPoi.id != managerPoiId) {
            POIWindowStateManager.updatePoiId(currentPoi.id)
            POIWindowStateManager.updateImageIndex(0)
        }
    }

    val currentImageIndex = managerImageIndex
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
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .border(2.dp, if (darkTheme) DarkImageFrame else LightImageFrame)
                ) {
                    if (currentImageUrl != null) {
                        SwipeableImageSection(
                            images = picturesList,
                            currentIndex = currentImageIndex,
                            onIndexChange = { newIndex ->
                                val finalIndex = when {
                                    newIndex < 0 -> picturesList.lastIndex
                                    newIndex >= picturesList.size -> 0
                                    else -> newIndex
                                }
                                POIWindowStateManager.updateImageIndex(finalIndex)
                            },
                            onExpand = { expandedImage = true },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
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
                    modifier = Modifier.width(280.dp),
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
                },
                onSaveDescription = {
                    onSaveDescription(currentPoi.id, descriptionValue.text)
                    isEditingDescription = false
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
            contentScale = ContentScale.Fit,
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

    val animatedOffset = remember { Animatable(0f) }
    val displayOffsetX = if (isAnimating) animatedOffset.value else dragOffsetX

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
                        dragOffsetX = (dragOffsetX + dragAmount.x).coerceIn(-containerWidth, containerWidth)
                    },
                    onDragEnd = {
                        val threshold = containerWidth * 0.25f
                        
                        when {
                            dragOffsetX > threshold -> {
                                targetIndex = if (currentIndex > 0) currentIndex - 1 else images.lastIndex
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
                                    onIndexChange(targetIndex)
                                    dragOffsetX = 0f
                                    animatedOffset.snapTo(0f)
                                    isAnimating = false
                                }
                            }
                            dragOffsetX < -threshold -> {
                                targetIndex = if (currentIndex < images.size - 1) currentIndex + 1 else 0
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
                                    onIndexChange(targetIndex)
                                    dragOffsetX = 0f
                                    animatedOffset.snapTo(0f)
                                    isAnimating = false
                                }
                            }
                            else -> {
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

        if (displayOffsetX != 0f && containerWidth > 0f) {
            val nextIndex = when {
                displayOffsetX > 0 -> {
                    if (currentIndex > 0) currentIndex - 1 else images.lastIndex
                }
                displayOffsetX < 0 -> {
                    if (currentIndex < images.size - 1) currentIndex + 1 else 0
                }
                else -> null
            }
            
            val nextImageUrl = nextIndex?.let { images.getOrNull(it) }
            if (nextImageUrl != null) {
                val nextOffset = if (displayOffsetX > 0) {
                    displayOffsetX - containerWidth
                } else {
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
                modifier = Modifier.size(32.dp)
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
