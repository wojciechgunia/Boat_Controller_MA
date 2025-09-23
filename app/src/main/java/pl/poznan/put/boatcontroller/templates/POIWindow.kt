package pl.poznan.put.boatcontroller.templates

import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import pl.poznan.put.boatcontroller.R
import pl.poznan.put.boatcontroller.dataclass.POIObject

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
    var currentIndex by remember { mutableIntStateOf(poiId) }
    var currentPoi  by remember { mutableStateOf<POIObject>(POIObject(
        id = 0,
        missionId = 0,
        lon = 0.0,
        lat = 0.0
    )) }
    val poi = poiList.getOrNull(currentIndex)
    if (poi != null) {
        currentPoi = poi
    }

    if (!isOpen) return

    var expandedImage by remember { mutableStateOf(false) }
    var isEditingName by remember { mutableStateOf(false) }
    var isEditingDescription by remember { mutableStateOf(false) }
    var nameValue by remember { mutableStateOf(TextFieldValue(currentPoi.name.orEmpty())) }
    var descriptionValue by remember { mutableStateOf(TextFieldValue(currentPoi.description.orEmpty())) }

    LaunchedEffect(poiId) {
        currentIndex = poiId
        val poi = poiList.getOrNull(currentIndex)
        currentPoi = poi
            ?: POIObject(
                id = 0,
                missionId = 0,
                lon = 0.0,
                lat = 0.0)
        nameValue = TextFieldValue(currentPoi.name.orEmpty())
        descriptionValue = TextFieldValue(currentPoi.description.orEmpty())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
    ) {
        if (expandedImage) {
            ExpandedImageView(
                imageUrl = currentPoi.pictures?.replace("[","")?.replace("]","")?.split(",")?.get(0).toString(),
                onClose = { expandedImage = false }
            )
        } else {
            Row(Modifier.fillMaxSize()) {
                ImageSection(
                    imageUrl = currentPoi.pictures.toString().replace("[", "").replace("]", "")
                        .split(",")[0],
                    onExpand = { expandedImage = true },
                    modifier = Modifier.weight(2f)
                )
                ControlPanel(
                    currentPoi = currentPoi,
                    onEditName = { isEditingName = true; nameValue = TextFieldValue(currentPoi.name.orEmpty()) },
                    onEditDescription = { isEditingDescription = true; descriptionValue = TextFieldValue(currentPoi.description.orEmpty()) },
                    onDelete = onDelete,
                    onPrev = {
                        if (currentIndex > 0) currentIndex-- else currentIndex = poiList.lastIndex
                    },
                    onNext = {
                        if (currentIndex < poiList.lastIndex) currentIndex++ else currentIndex = 0
                    },
                    modifier = Modifier.weight(1f)
                )

            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Black)
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
                    onSaveName(currentPoi.id,nameValue.text)
                    isEditingName = false
                    currentPoi = currentPoi.copy(name = nameValue.text)
                },
                onSaveDescription = {
                    onSaveDescription(currentPoi.id,descriptionValue.text)
                    isEditingDescription = false
                    currentPoi = currentPoi.copy(description = descriptionValue.text)
                }
            )
        }
    }
}

@Composable
private fun ExpandedImageView(imageUrl: String, onClose: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
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

        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }
    }
}


@Composable
private fun ImageSection(
    imageUrl: String,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable { onExpand() }
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun ControlPanel(
    currentPoi: POIObject,
    onEditName: () -> Unit,
    onEditDescription: () -> Unit,
    onDelete: (Int) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(Color.White)
            .padding(16.dp, top = 36.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Name: ${currentPoi.name}", modifier = Modifier.weight(1f))
                IconButton(onClick = onEditName) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Description: ${currentPoi.description}", modifier = Modifier.weight(1f))
                IconButton(onClick = onEditDescription) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                }
            }
        }
        Column {
            Row(Modifier.padding(top = 8.dp)) {
                Text(text = "Lon: ${"%.5f".format(currentPoi.lon)}", fontSize = 14.sp)
                Text(text = " | Lat: ${"%.5f".format(currentPoi.lat)}", fontSize = 14.sp)
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
                    border = BorderStroke(1.dp, Color.Red),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .width(150.dp).height(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.Red,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete", color = Color.Red, fontSize = 16.sp)
                }
            }
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(end=12.dp)
            ) {
                OutlinedButton(onClick = onPrev, shape = RoundedCornerShape(6.dp)) {
                    Icon(
                        Icons.Default.ChevronLeft,
                        contentDescription = "Prev",
                        tint = Color.Black
                    )
                }
                OutlinedButton(onClick = onNext, shape = RoundedCornerShape(6.dp)) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Next",
                        tint = Color.Black
                    )
                }
            }
        }
    }
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Are you sure?") },
            text = { Text("Do you really want to delete this point?") },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    onDelete(currentPoi.id)
                }) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("No")
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
    onSaveDescription: () -> Unit
) {
    Surface(
        color = Color.White,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
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
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onSaveName) {
                    Icon(painterResource(id = R.drawable.save), contentDescription = "Save")
                }
            }
            if (isEditingDescription) {
                TextField(
                    value = descriptionValue,
                    onValueChange = onDescriptionChange,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onSaveDescription) {
                    Icon(painterResource(id = R.drawable.save), contentDescription = "Save")
                }
            }
        }
    }
}