package pl.poznan.put.boatcontroller.templates

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import pl.poznan.put.boatcontroller.R
import pl.poznan.put.boatcontroller.dataclass.POIObject

@SuppressLint("AutoboxingStateCreation")
@Composable
fun FullScreenPopup(
    isOpen: Boolean,
    onClose: () -> Unit,
    poiId: Int,
    poiList: List<POIObject>,
    onSaveName: (String) -> Unit,
    onSaveDescription: (String) -> Unit,
) {
    var currentIndex by remember { mutableIntStateOf(poiId) }
    val currentPoi = poiList.getOrNull(currentIndex)

    if (!isOpen) return

    var expandedImage by remember { mutableStateOf(false) }
    var isEditingName by remember { mutableStateOf(false) }
    var isEditingDescription by remember { mutableStateOf(false) }
    var nameValue by remember { mutableStateOf(TextFieldValue(currentPoi?.name.toString())) }
    var descriptionValue by remember { mutableStateOf(TextFieldValue(currentPoi?.description.toString())) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
    ) {
        if (expandedImage) {
            ExpandedImageView(
                imageUrl = currentPoi?.pictures?.replace("[","")?.replace("]","")?.split(",")?.get(0).toString(),
                onClose = { expandedImage = false }
            )
        } else {
            Row(Modifier.fillMaxSize()) {
                ImageSection(
                    imageUrl = currentPoi?.pictures.toString().replace("[","").replace("]","").split(",")[0],
                    onExpand = { expandedImage = true },
                    modifier = Modifier.weight(2f)
                )
                ControlPanel(
                    nameValue = nameValue,
                    descriptionValue = descriptionValue,
                    lon = currentPoi?.lon,
                    lat = currentPoi?.lat,
                    onEditName = { isEditingName = true },
                    onEditDescription = { isEditingDescription = true },
                    onPrev = { if (currentIndex > 0) currentIndex-- else currentIndex = poiList.lastIndex;},
                    onNext = { if (currentIndex < poiList.lastIndex) currentIndex++ else currentIndex = 0; },
                    modifier = Modifier.weight(1f)
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
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
                    onSaveName(nameValue.text)
                    isEditingName = false
                },
                onSaveDescription = {
                    onSaveDescription(descriptionValue.text)
                    isEditingDescription = false
                }
            )
        }
    }
}

@Composable
private fun ExpandedImageView(imageUrl: String, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = rememberAsyncImagePainter(imageUrl),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
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
private fun ImageSection(imageUrl: String, onExpand: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable { onExpand() }
    ) {
        Image(
            painter = rememberAsyncImagePainter(imageUrl),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun ControlPanel(
    nameValue: TextFieldValue,
    descriptionValue: TextFieldValue,
    lon: Double?,
    lat: Double?,
    onEditName: () -> Unit,
    onEditDescription: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(Color.White)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Name: ${nameValue.text}", modifier = Modifier.weight(1f))
                IconButton(onClick = onEditName) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Description: ${descriptionValue.text}", modifier = Modifier.weight(1f))
                IconButton(onClick = onEditDescription) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
            }
            Text(text = "Lon: $lon")
            Text(text = "Lat: $lat")
        }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(onClick = onPrev) { Text("← Prev") }
            Button(onClick = onNext) { Text("Next →") }
        }
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

@Preview(showBackground = true)
@Composable
fun PreviewScreen() {
    FullScreenPopup(
        isOpen = true,
        onClose = { },
        poiId = 0,
        poiList = listOf(POIObject(0, 1, 52.432423, 12.32423, "Name", "Description", "https://galeria-mad.pl/wp-content/uploads/2023/03/Klaudiusz-Kolodziejski-41x33-Zachod-Slonca--scaled.jpg")),
        onSaveName = {},
        onSaveDescription = {}
    )
}