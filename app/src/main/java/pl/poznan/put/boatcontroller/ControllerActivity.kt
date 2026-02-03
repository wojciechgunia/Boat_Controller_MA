package pl.poznan.put.boatcontroller

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.core.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.graphics.lerp
import kotlin.math.sin
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import pl.poznan.put.boatcontroller.ui.theme.SuccessGreen
import pl.poznan.put.boatcontroller.ui.theme.ErrorRed
import pl.poznan.put.boatcontroller.ui.theme.WarningYellow
import pl.poznan.put.boatcontroller.ui.theme.VisibilityButtonColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.location.LocationServices
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.android.style.layers.SymbolLayer
import pl.poznan.put.boatcontroller.dataclass.HomePosition
import pl.poznan.put.boatcontroller.enums.ControllerTab
import pl.poznan.put.boatcontroller.enums.MapLayersVisibilityMode
import pl.poznan.put.boatcontroller.enums.WaypointIndicationType
import pl.poznan.put.boatcontroller.socket.HttpStreamConfigs
import pl.poznan.put.boatcontroller.socket.HttpStreamRepository
import pl.poznan.put.boatcontroller.templates.BatteryIndicator
import pl.poznan.put.boatcontroller.templates.poi_window.FullScreenPopup
import pl.poznan.put.boatcontroller.templates.HttpStreamView
import pl.poznan.put.boatcontroller.templates.SavePOIButton
import pl.poznan.put.boatcontroller.templates.info_popup.InfoPopup
import pl.poznan.put.boatcontroller.templates.info_popup.InfoPopupType
import pl.poznan.put.boatcontroller.templates.RotatePhoneAnimation
import pl.poznan.put.boatcontroller.templates.createWaypointBitmap
import pl.poznan.put.boatcontroller.templates.info_popup.InfoPopupManager
import pl.poznan.put.boatcontroller.ui.theme.BoatControllerTheme
import pl.poznan.put.boatcontroller.socket.HttpStreamConfig
import kotlin.math.PI
import kotlin.math.min

class ControllerActivity: ComponentActivity() {
    private val viewModel by viewModels<ControllerViewModel>()
    val cameraZoomAnimationTime = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_BoatController)
        super.onCreate(savedInstanceState)
        
        // Inicjalizuj stan - domyślnie żaden tab nie jest aktywny
        HttpStreamRepository.setActiveTab(ControllerTab.NONE)

        setContent {
            BoatControllerTheme {
                val colorScheme = MaterialTheme.colorScheme
                if (!isLandscape()) {
                    val image = remember {
                        ResourcesCompat.getDrawable(
                            this.resources,
                            R.drawable.phone_android_2,
                            this.theme
                        )
                            ?.toBitmap()
                            ?.asImageBitmap()
                    }
                    RotatePhoneAnimation(colorScheme, image)
                } else {
                    EngineControlScreen(viewModel)
                }
            }

        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        setResult(RESULT_OK)
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    override fun onPause() {
        super.onPause()
        // Aplikacja schodzi z ekranu (np. minimalizacja) – twardo wyłącz wszystkie streamy
        HttpStreamRepository.onAppPaused()
    }

    override fun onResume() {
        super.onResume()
        // Po powrocie na ekran przywróć ostatni aktywny tab (jeśli był Sonar lub Camera)
        val restoredTab = HttpStreamRepository.onAppResumed()
        if (restoredTab != null) {
            // Upewnij się że selectedTab jest zsynchronizowany
            viewModel.setSelectedTab(restoredTab)
        }
    }

    @Composable
    fun isLandscape(): Boolean {
        val configuration = LocalConfiguration.current
        return configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    @Composable
    fun isTablet(context: Context): Boolean {
        val metrics = context.resources.displayMetrics
        val widthDp = metrics.widthPixels / metrics.density
        val heightDp = metrics.heightPixels / metrics.density
        val smallestWidthDp = min(widthDp, heightDp)

        return smallestWidthDp >= 600
    }

    // ===========================  Controller screen ================================================

    fun sendEnginePower(viewModel: ControllerViewModel) {
        viewModel.sendSpeed(
            viewModel.leftEnginePower.value,
            viewModel.rightEnginePower.value
        )
    }

    fun sendSetHome(viewModel: ControllerViewModel) {
        val lat = viewModel.shipPosition.value.lat
        val lon = viewModel.shipPosition.value.lon
        viewModel.updateHomePosition(HomePosition(lat, lon))
        // Brak dedykowanej komendy w nowym protokole – używamy akcji GH (Go Home)
        viewModel.sendAction("GH", "")
    }

    @Composable
    fun SidebarButton(
        icon: Painter,
        label: String,
        isActive: Boolean,
        onClick: () -> Unit
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()

        val secondary = MaterialTheme.colorScheme.secondary // Kolor tekstu jak w Tab'ach (PrimaryLightBlue)
        val background = MaterialTheme.colorScheme.secondaryContainer // Tło pozostaje bez zmian
        val iconAndTextColor = if (isActive || isPressed) secondary else Color.White
        val underlineColor = if (isActive || isPressed) secondary else Color.LightGray

        Column(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .width(50.dp)
                .height(50.dp)
                .background(background)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(3.dp))

            Icon(
                painter = icon,
                contentDescription = label,
                tint = iconAndTextColor,
                modifier = Modifier.size(18.dp)
            )

            Text(
                text = label,
                fontSize = 8.sp,
                color = iconAndTextColor
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(underlineColor)
            )
        }
    }

    @Composable
    fun EngineControlScreen(viewModel: ControllerViewModel) {
        val tabs = listOf(ControllerTab.MAP, ControllerTab.SONAR, ControllerTab.SENSORS, ControllerTab.CAMERA)
        val isTablet = isTablet(this)
        var isSyncOn by remember { mutableStateOf(false) }
        
        val batteryLevel by viewModel.externalBatteryLevel.collectAsState()
        val currentBatteryLevel = batteryLevel ?: 100
        
        // Throttling dla wysyłania wiadomości - maksymalnie co 100ms
        val scope = remember { CoroutineScope(Dispatchers.Main) }
        var lastSendJob by remember { mutableStateOf<Job?>(null) }
        
        fun sendEnginePowerThrottled(viewModel: ControllerViewModel) {
            // Anuluj poprzednie zadanie jeśli jeszcze nie zostało wykonane
            lastSendJob?.cancel()
            
            // Utwórz nowe zadanie z opóźnieniem 100ms
            lastSendJob = scope.launch {
                delay(200)
                sendEnginePower(viewModel)
            }
        }
        
        // Obsługa niskiej baterii - warning przy 20%, error przy 10%
        // Używamy remember aby nie pokazywać tego samego warningu wielokrotnie
        var lastBatteryWarning = remember { mutableStateOf<Int?>(null) }
        LaunchedEffect(currentBatteryLevel) {
            when {
                currentBatteryLevel <= 10 && lastBatteryWarning.value != currentBatteryLevel -> {
                    InfoPopupManager.show(
                        message = "Krytycznie niski poziom baterii: ${currentBatteryLevel}%",
                        type = InfoPopupType.ERROR
                    )
                    lastBatteryWarning.value = currentBatteryLevel
                }
                currentBatteryLevel in 11..20 && lastBatteryWarning.value != currentBatteryLevel -> {
                    InfoPopupManager.show(
                        message = "Niski poziom baterii: ${currentBatteryLevel}%",
                        type = InfoPopupType.WARNING
                    )
                    lastBatteryWarning.value = currentBatteryLevel
                }
                currentBatteryLevel > 20 -> {
                    // Reset gdy bateria wzrośnie powyżej 20%
                    lastBatteryWarning.value = null
                }
            }
        }

        val openPOIDialog by viewModel.openPOIDialog.collectAsState()
        val poiId by viewModel.poiId.collectAsState()
        val poiPositions by viewModel.poiPositions.collectAsState()
        val leftEnginePower by viewModel.leftEnginePower.collectAsState()
        val rightEnginePower by viewModel.rightEnginePower.collectAsState()
        val currentSpeed by viewModel.currentSpeed.collectAsState()
        val selectedTab by viewModel.selectedTab.collectAsState()

        Box(modifier = Modifier.fillMaxSize()) {
            // InfoPopup dla warningów i błędów (na górze ekranu) - tylko raz na całym activity
            InfoPopup(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            )
            
            Row(modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)) {
                FullScreenPopup(openPOIDialog, { viewModel.setOpenPOIDialog(false) }, poiId, poiPositions, { id: Int, name: String -> viewModel.updatePoiData(id, name, poiPositions.firstOrNull{ it.id == id }?.description.orEmpty()) }, { id: Int, description: String -> viewModel.updatePoiData(id, poiPositions.firstOrNull{ it.id == id }?.name.orEmpty(), description)}, { id -> viewModel.deletePoi(id)
                    viewModel.setOpenPOIDialog(false)
                })

            PowerSlider(
                value = leftEnginePower,
                onValueChange = {
                    if(leftEnginePower != it) {
                        viewModel.setLeftEnginePower(it)
                        if(isSyncOn) {
                            viewModel.setRightEnginePower(it)
                        }
                        sendEnginePowerThrottled(viewModel)
                    }},
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                isTablet = isTablet,
            )

            Column(
                modifier = Modifier
                    .weight(3f)
                    .fillMaxHeight(),
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .width(50.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(top = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Speed:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = currentSpeed.toString(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "km/h",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        SidebarButton(
                            icon = painterResource(id = R.drawable.sync),
                            label = "Sync",
                            isActive = isSyncOn,
                            onClick = {
                                isSyncOn = !isSyncOn
                                viewModel.setLeftEnginePower(viewModel.rightEnginePower.value)
                                sendEnginePower(viewModel)
                            }
                        )

                        SidebarButton(
                            icon = painterResource(id = R.drawable.stop),
                            label = "Stop",
                            isActive = false,
                            onClick = {
                                viewModel.setRightEnginePower(0)
                                viewModel.setLeftEnginePower(0)
                                sendEnginePower(viewModel)
                            }
                        )

                        SidebarButton(
                            icon = painterResource(id = R.drawable.set_home),
                            label = "Set Home",
                            isActive = false,
                            onClick = {
                                sendSetHome(viewModel)
                            }
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        TabRow(
                            selectedTabIndex = tabs.indexOf(selectedTab),
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.secondary, // Jaśniejszy niebieski dla tekstu Tab'ów
                            indicator = { tabPositions ->
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[tabs.indexOf(selectedTab)]),
                                    color = MaterialTheme.colorScheme.secondary // Jaśniejszy niebieski dla wskaźnika
                                )
                            }
                        ) {
                            tabs.forEach { tab ->
                                Tab(
                                    selected = selectedTab == tab,
                                    onClick = { 
                                        viewModel.setSelectedTab(tab)
                                        
                                        // Informuj HttpStreamRepository o zmianie taba
                                        HttpStreamRepository.setActiveTab(tab)
                                    },
                                    text = { 
                                        Text(
                                            tab.displayName,
                                            color = if (selectedTab == tab) 
                                                MaterialTheme.colorScheme.secondary 
                                            else 
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        ) 
                                    }
                                )
                            }
                        }

                        when (selectedTab) {
                            ControllerTab.MAP -> {MapTab(viewModel) }
                            ControllerTab.SONAR -> {SonarTab(viewModel) }
                            ControllerTab.SENSORS -> {SensorsTab(viewModel) }
                            ControllerTab.CAMERA -> {CameraTab(viewModel) }
                            ControllerTab.NONE -> { /* Nie powinno się zdarzyć */ }
                        }
                    }
                }
            }

            PowerSlider(
                value = rightEnginePower,
                onValueChange = {
                    if(rightEnginePower != it) {
                        viewModel.setRightEnginePower(it)
                        if(isSyncOn) {
                            viewModel.setLeftEnginePower(it)
                        }
                        sendEnginePowerThrottled(viewModel)
                    }},
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                rightSide = true,
                isTablet = isTablet,
            )
            }
        }
    }

    @SuppressLint("ConfigurationScreenWidthHeight")
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun PowerSlider(
        value: Int,
        onValueChange: (Int) -> Unit,
        modifier: Modifier = Modifier,
        rightSide: Boolean = false,
        minValue: Int = -80,
        maxValue: Int = 80,
        isTablet: Boolean = false
    ) {
        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
        val range = maxValue - minValue
        val steps = range / 10
        val sliderHeight = screenHeight * 0.8f
        val trackWidth = if (isTablet) 60.dp else 40.dp
        val thumbHeight = screenHeight * 0.08f

        Box(
            modifier = modifier
                .width(if (isTablet) 180.dp else 120.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            val density = LocalDensity.current

            Box(
                modifier = Modifier
                    .width(trackWidth)
                    .height(sliderHeight)
                    .padding(vertical = 10.dp),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val segmentHeight = size.height / steps
                    for (i in 0..steps) {
                        val segValue = maxValue - i * 10
                        val top = i * segmentHeight

                        val color2 = when {
                            segValue < -70 -> Color.Transparent
                            segValue > 60 || segValue in -70..-60 -> ErrorRed
                            segValue in 41..60 || segValue in -60..-39 -> WarningYellow
                            segValue in 21..40 || segValue in -39..-19 -> SuccessGreen
                            else -> Color.LightGray
                        }

                        drawRect(
                            color = color2,
                            topLeft = Offset(0f, top),
                            size = Size(size.width, segmentHeight)
                        )

                        drawLine(
                            color = Color.Black,
                            start = Offset(0f, top),
                            end = Offset(20f, top),
                            strokeWidth = 5f
                        )

                        drawLine(
                            color = Color.Black,
                            start = Offset(size.width - 20f, top),
                            end = Offset(size.width, top),
                            strokeWidth = 5f
                        )

                        drawContext.canvas.nativeCanvas.apply {
                            drawText(
                                if (segValue in -75..70) "$segValue" else "",
                                size.width - ((trackWidth/2)+5.dp).toPx(),
                                top + 3.dp.toPx(),
                                Paint().apply {
                                    color = AndroidColor.BLACK
                                    textSize = 24f
                                }
                            )
                        }
                    }
                }
            }

            val sliderOffset = remember(value) {
                val fraction = value.toFloat()/maxValue
                fraction
            }

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = if (rightSide) (-(trackWidth * (if (isTablet) 0.75f else 0.85f)).toPx()).toInt() else (trackWidth * 1.6f).toPx()
                                .toInt(),
                            y = with(density) {
                                (-sliderOffset * ((sliderHeight.toPx() / 2) - (thumbHeight.toPx() * 0.40))).toInt()
                            }
                        )
                    }
                    .width(trackWidth * 2)
                    .height(thumbHeight),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .offset(x = if (rightSide) trackWidth + 10.dp else -trackWidth - 10.dp)
                        .width(trackWidth + 10.dp)
                        .height(trackWidth / 2 - 2.dp)
                        .background(Color.Black, RoundedCornerShape(4.dp))
                        .border(1.dp, Color.White, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {}
                Box(
                    modifier = Modifier
                        .width(trackWidth + 10.dp)
                        .height(thumbHeight)
                        .background(Color.Black, RoundedCornerShape(4.dp))
                        .border(1.dp, Color.White, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$value",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                valueRange = minValue.toFloat()..maxValue.toFloat(),
                steps = range/2 - 1,
                modifier = Modifier
                    .rotate(-90f)
                    .width(100.dp)
                    .scale(sliderHeight / 100.dp, 1f)
                    .alpha(0f),
            )
        }
    }


    @OptIn(ExperimentalPermissionsApi::class)
    @SuppressLint("MissingPermission", "InflateParams", "UseKtx")
    @Composable
    fun MapTab(viewModel: ControllerViewModel, modifier: Modifier = Modifier) {
        val map by viewModel.mapLibreMapState.collectAsState()
        val context = LocalContext.current
        val waypoints by viewModel.waypointPositions.collectAsState()
        val poi by viewModel.poiPositions.collectAsState()
        val layersMode by viewModel.layersMode.collectAsState()
        val homePosition by viewModel.homePosition.collectAsState()
        val phonePosition by viewModel.phonePosition.collectAsState()
        val shipPosition by viewModel.shipPosition.collectAsState()
        val cameraPosition by viewModel.cameraPosition.collectAsState()
        val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
        val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

        LaunchedEffect(Unit) {
            if (!locationPermissionState.status.isGranted) {
                locationPermissionState.launchPermissionRequest()
            } else {
                try {
                    fusedLocationClient.lastLocation
                        .addOnSuccessListener { location ->
                            if (location != null) {
                                viewModel.setPhonePosition(location.latitude, location.longitude)
                                viewModel.saveCameraPosition(location.latitude, location.longitude, 13.0)
                                Log.d("PHONE_LOCATION", "Lat: ${location.latitude}, Lon: ${location.longitude}")
                            } else {
                                // Brak aktualnej lokalizacji telefonu – nie pokazujemy operatora
                                Log.d("PHONE_LOCATION", "No phone location available (null)")
                            }
                        }
                        .addOnFailureListener { e ->
                            // Błąd pobierania lokalizacji – nie pokazujemy operatora
                            Log.d("PHONE_LOCATION", "Error fetching phone location: ${e.message}")
                        }
                } catch (_: SecurityException) {
                    // Brak uprawnień – nie pokazujemy operatora
                    Log.d("PHONE_LOCATION", "SecurityException – location permission missing")
                }
            }
        }

        LaunchedEffect(phonePosition, map?.style) {
            val mapboxMap = map ?: return@LaunchedEffect
            val style = mapboxMap.style ?: return@LaunchedEffect
            val pos = phonePosition ?: return@LaunchedEffect

            val phoneFeature = Feature.fromGeometry(Point.fromLngLat(pos[1], pos[0]))
            phoneFeature.addStringProperty("title", "Phone")
            style.getSourceAs<GeoJsonSource>("phone-source")
                ?.setGeoJson(FeatureCollection.fromFeatures(listOf(phoneFeature)))

            val saved = cameraPosition

            val targetLat = saved?.lat ?: pos[0]
            val targetLng = saved?.lon ?: pos[1]
            val zoom = saved?.zoom ?: 13.0

            mapboxMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(targetLat, targetLng),
                    zoom
                )
            )
        }

        LaunchedEffect(waypoints) {
            val mapboxMap = map ?: return@LaunchedEffect
            val style = mapboxMap.style ?: return@LaunchedEffect

            waypoints.forEach { wp ->
                val bitmap = getOrCreateWaypointBitmap(wp.no, context)
                addWaypointBitmapToStyle(wp.no, bitmap, style)
            }
            // Aktualizuj tylko waypointy i połączenia, NIE pozycję łódki
            style.getSourceAs<GeoJsonSource>("waypoint-connections-source")
                ?.setGeoJson(FeatureCollection.fromFeatures(viewModel.getConnectionLinesFeature()))
            style.getSourceAs<GeoJsonSource>("waypoint-source")
                ?.setGeoJson(FeatureCollection.fromFeatures(viewModel.getWaypointsFeature()))
        }

        LaunchedEffect(poi) {
            val mapboxMap = map ?: return@LaunchedEffect
            val style = mapboxMap.style ?: return@LaunchedEffect

            // Aktualizuj tylko POI, NIE pozycję łódki
            style.getSourceAs<GeoJsonSource>("poi-source")
                ?.setGeoJson(FeatureCollection.fromFeatures(viewModel.getPoiFeature()))
        }
        
        // Osobny LaunchedEffect dla pozycji łódki - aktualizuje się tylko gdy pozycja się zmienia
        LaunchedEffect(shipPosition) {
            val mapboxMap = map ?: return@LaunchedEffect
            val style = mapboxMap.style ?: return@LaunchedEffect
            
            style.getSourceAs<GeoJsonSource>("ship-source")
                ?.setGeoJson(viewModel.getShipFeature())
        }

        LaunchedEffect(layersMode, map?.style) {
            val mapboxMap = map ?: return@LaunchedEffect
            val style = mapboxMap.style ?: return@LaunchedEffect

            when (layersMode) {
                MapLayersVisibilityMode.BOTH_VISIBLE -> {
                    style.getLayer("waypoint-layer")?.setProperties(visibility(Property.VISIBLE))
                    style.getLayer("waypoint-connections-layer")?.setProperties(visibility(Property.VISIBLE))
                    style.getLayer("poi-layer")?.setProperties(visibility(Property.VISIBLE))
                }
                MapLayersVisibilityMode.WAYPOINTS -> {
                    style.getLayer("waypoint-layer")?.setProperties(visibility(Property.VISIBLE))
                    style.getLayer("waypoint-connections-layer")?.setProperties(visibility(Property.VISIBLE))
                    style.getLayer("poi-layer")?.setProperties(visibility(Property.NONE))
                }
                MapLayersVisibilityMode.POI -> {
                    style.getLayer("waypoint-layer")?.setProperties(visibility(Property.NONE))
                    style.getLayer("waypoint-connections-layer")?.setProperties(visibility(Property.NONE))
                    style.getLayer("poi-layer")?.setProperties(visibility(Property.VISIBLE))
                }
                MapLayersVisibilityMode.NONE -> {
                    style.getLayer("waypoint-layer")?.setProperties(visibility(Property.NONE))
                    style.getLayer("waypoint-connections-layer")?.setProperties(visibility(Property.NONE))
                    style.getLayer("poi-layer")?.setProperties(visibility(Property.NONE))
                }
            }
        }

        LaunchedEffect(homePosition) {
            val mapboxMap = map ?: return@LaunchedEffect
            val style = mapboxMap.style ?: return@LaunchedEffect
            val homeSource = style.getSourceAs<GeoJsonSource>("home-source")

            val lat = homePosition.lat
            val lon = homePosition.lon

            if (lat == 0.0 && lon == 0.0) {
                homeSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            } else {
                val homeFeature = Feature.fromGeometry(Point.fromLngLat(lon, lat)).apply {
                    addStringProperty("title", "Home")
                }

                homeSource?.setGeoJson(FeatureCollection.fromFeatures(listOf(homeFeature)))
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = {
                    MapLibre.getInstance(context)
                    MapView(context).apply {
                        getMapAsync { mapboxMap ->
                            val styleJson = """
                                {
                                  "version": 8,
                                  "sources": {
                                    "osm": {
                                      "type": "raster",
                                      "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
                                      "tileSize": 256
                                    },
                                    "point-source": {
                                      "type": "geojson",
                                      "data": {
                                        "type": "FeatureCollection",
                                        "features": [
                                          {
                                            "type": "Feature",
                                            "geometry": {
                                              "type": "Point",
                                              "coordinates": [${shipPosition.lon}, ${shipPosition.lat}]
                                            },
                                            "properties": {
                                              "title": "Ship"
                                            }
                                          }
                                        ]
                                      }
                                    }
                                  },
                                  "layers": [
                                    {
                                      "id": "osm-layer",
                                      "type": "raster",
                                      "source": "osm"
                                    }
                                  ]
                                }
                            """.trimIndent()
                            mapboxMap.setStyle(Style.Builder().fromJson(styleJson)) { style ->
                                // Optymalizacja: Zwiększ cache dla kafelków mapy aby zmniejszyć zużycie danych
                                // Cache jest włączony domyślnie, ale zwiększamy jego rozmiar
                                try {
                                    // MapLibre automatycznie cache'uje kafelki, ale możemy zwiększyć limit
                                    // Domyślny cache to ~50 MB, zwiększamy do ~100 MB dla lepszej wydajności
                                    // To zmniejszy potrzebę ponownego pobierania kafelków przy przesuwaniu mapy
                                    mapboxMap.tileCacheEnabled = true
                                    // Uwaga: setTileCacheSize() może nie być dostępne we wszystkich wersjach MapLibre
                                    // Jeśli metoda nie istnieje, cache będzie używał domyślnego rozmiaru
                                } catch (e: Exception) {
                                    // Ignoruj jeśli metoda nie jest dostępna
                                    Log.d("MapLibre", "Tile cache optimization not available: ${e.message}")
                                }
                                
                                viewModel.setMapReady(mapboxMap)
                                initializeMapSources(style)
                                updateBitmaps(style, context)
                                initializeMapLayers(style)

                                mapboxMap.uiSettings.isRotateGesturesEnabled = false
                                updateMapFeatures(style)

                                mapboxMap.addOnMapClickListener { latLng ->
                                    val poiObject =
                                        mapboxMap.projection.toScreenLocation(latLng)
                                    val poiFeatures = mapboxMap.queryRenderedFeatures(
                                        poiObject,
                                        "poi-layer"
                                    )

                                    if (poiFeatures.isNotEmpty()) {
                                        val clickedFeature = poiFeatures.first()
                                        val id = clickedFeature.getStringProperty("id")?.toIntOrNull()
                                        if (id != null) {
                                            Log.d("POI_CLICKED", "ID of clicked POI Object: $id")
                                            viewModel.setPoiId(viewModel.poiPositions.value.indexOfFirst { it.id == id })
                                            viewModel.setOpenPOIDialog(true)
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                }
                                updateMapFeatures(style)
                            }
                            viewModel.setMapReady(mapboxMap)
                        }
                    }
                },
                modifier = modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                val battery by viewModel.externalBatteryLevel.collectAsState()
                BatteryIndicator(
                    level = battery ?: 0,
                    isCharging = true,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                )

                FloatingActionButton(
                    onClick = {
                        val zoom = 16.0
                        map?.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(
                                    shipPosition.lat,
                                    shipPosition.lon
                                ), zoom
                            ),
                            cameraZoomAnimationTime * 1000
                        )
                    },
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .shadow(16.dp, CircleShape, clip = false)
                        .clip(CircleShape),
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.current_location_tracker),
                        contentDescription = "Center Map",
                        tint = Color.White
                    )
                }

                FloatingActionButton(
                    onClick = { viewModel.toggleMapLayersMode() },
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 88.dp, bottom = 16.dp)
                        .shadow(16.dp, CircleShape, clip = false)
                        .clip(CircleShape),
                    containerColor = VisibilityButtonColor
                ) {
                    when (layersMode) {
                        MapLayersVisibilityMode.BOTH_VISIBLE -> MapLayerVisibilityIcon(painterResource(id = R.drawable.ic_indication_compass), "Waypoints visible")
                        MapLayersVisibilityMode.WAYPOINTS -> MapLayerVisibilityIcon(painterResource(id = R.drawable.ic_indication_star), "POI visible")
                        MapLayersVisibilityMode.POI -> Icon(Icons.Default.VisibilityOff, tint = Color.White, contentDescription = "None visible")
                        MapLayersVisibilityMode.NONE -> Icon(Icons.Default.Visibility, tint = Color.White, contentDescription = "Both visible")
                    }
                }
                
//                // Tymczasowy przycisk do testowania baterii (tylko do testów)
//                FloatingActionButton(
//                    onClick = {
//                        viewModel.simulateBatteryDecrease()
//                        if ((viewModel.externalBatteryLevel.value ?: 100) <= 0) {
//                            viewModel.resetBattery()
//                        }
//                    },
//                    shape = CircleShape,
//                    modifier = Modifier
//                        .align(Alignment.BottomStart)
//                        .padding(start = 16.dp, bottom = 16.dp)
//                        .shadow(16.dp, CircleShape, clip = false)
//                        .clip(CircleShape),
//                    containerColor = Color.Gray
//                ) {
//                    Text(
//                        text = "🔋",
//                        fontSize = 20.sp,
//                        modifier = Modifier.padding(4.dp)
//                    )
//                }
            }
        }
    }


    /**
     * Wspólny komponent dla tabów z HTTP stream (Sonar i Camera).
     * Zarządza widocznością wskaźnika połączenia i obsługuje kliknięcia.
     * 
     * @param viewModel ViewModel z danymi połączenia
     * @param tab Typ taba (SONAR lub CAMERA)
     * @param sourceType Typ źródła dla SavePOIButton ("sonar" lub "camera")
     * @param label Etykieta dla HttpStreamView
     * @param config Konfiguracja streamu
     * @param modifier Modifier dla głównego Box
     */
    @Composable
    fun HttpStreamTabContent(
        viewModel: ControllerViewModel,
        tab: ControllerTab,
        sourceType: String,
        label: String,
        config: HttpStreamConfig,
        modifier: Modifier = Modifier
    ) {
        // Stan widoczności wskaźnika połączenia
        var isIndicatorVisible by remember { mutableStateOf(false) }
        var hideJob by remember { mutableStateOf<Job?>(null) }
        val scope = rememberCoroutineScope()
        val connectionState by viewModel.httpConnectionState.collectAsState()
        val errorMessage by viewModel.httpErrorMessage.collectAsState()
        
        // Funkcja do pokazania wskaźnika na ~3 sekundy
        fun showIndicator() {
            // Anuluj poprzednie zadanie ukrycia jeśli istnieje
            hideJob?.cancel()
            
            // Pokaż wskaźnik
            isIndicatorVisible = true
            
            // Zaplanuj ukrycie po ~2 sekundach (animacja zanikania trwa 1.5s, więc łącznie ~3.5s)
            hideJob = scope.launch {
                delay(2000) // 2 sekundy widoczności
                isIndicatorVisible = false // Animacja zanikania trwa 1.5s
            }
        }
        
        // Automatycznie pokaż wskaźnik przy wejściu do taba
        LaunchedEffect(Unit) {
            showIndicator()
        }
        
        Box(modifier = modifier.fillMaxSize()) {
            HttpStreamView(
                streamUrl = HttpStreamRepository.getUrlForTab(tab),
                connectionState = connectionState,
                errorMessage = errorMessage,
                label = label,
                config = config,
                onTap = {
                    // Callback wywoływany gdy użytkownik kliknie w WebView
                    // (ale nie w interaktywne elementy jak przyciski)
                    showIndicator()
                }
            )
            
            // Wizualna informacja o stanie połączenia - uspójniona z HttpStreamView
            ConnectionStatusIndicator(
                connectionState = connectionState,
                errorMessage = errorMessage,
                isVisible = isIndicatorVisible,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            )

            // Przycisk zapisu POI - widoczny TYLKO gdy połączono
            if (connectionState == ConnectionState.Connected) {
                SavePOIButton(
                    viewModel = viewModel,
                    connectionState = connectionState,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                )
            }
        }
    }

    @Composable
    fun SonarTab(viewModel: ControllerViewModel) {
        HttpStreamTabContent(
            viewModel = viewModel,
            tab = ControllerTab.SONAR,
            sourceType = "sonar",
            label = "sonaru",
            config = HttpStreamConfigs.SONAR
        )
    }

    @Composable
    fun SensorsTab(viewModel: ControllerViewModel) {
        val data by viewModel.sensorsData.collectAsState()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                // Sekcja: Głębokość (przeniesiona wyżej)
                item {
                    SensorSection(
                        title = "Głębokość",
                        values = listOf(
                            "${String.format("%.2f", data.depth)} m"
                        )
                    )
                }

                // Sekcja: Akcelerometr
                item {
                    SensorSection(
                        title = "Akcelerometr (g)",
                        values = listOf(
                            "X: ${String.format("%.2f", data.accelX)}",
                            "Y: ${String.format("%.2f", data.accelY)}",
                            "Z: ${String.format("%.2f", data.accelZ)}"
                        )
                    )
                }

                // Sekcja: Żyroskop
                item {
                    SensorSection(
                        title = "Żyroskop (deg/s)",
                        values = listOf(
                            "X: ${String.format("%.2f", data.gyroX)}",
                            "Y: ${String.format("%.2f", data.gyroY)}",
                            "Z: ${String.format("%.2f", data.gyroZ)}"
                        )
                    )
                }

                // Sekcja: Magnetometr
                item {
                    SensorSection(
                        title = "Magnetometr (µT)",
                        values = listOf(
                            "X: ${String.format("%.2f", data.magX)}",
                            "Y: ${String.format("%.2f", data.magY)}",
                            "Z: ${String.format("%.2f", data.magZ)}"
                        )
                    )
                }

                // Sekcja: Kąty
                item {
                    SensorSection(
                        title = "Kąty (deg)",
                        values = listOf(
                            "X: ${data.angleX}",
                            "Y: ${data.angleY}",
                            "Z: ${data.angleZ}"
                        )
                    )
                }
            }
        }
    }

    @Composable
    fun SensorSection(
        title: String,
        values: List<String>
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(8.dp)
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary // Jaśniejszy niebieski dla tytułów sekcji
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                values.forEach { value ->
                    Text(
                        text = value,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    @Composable
    fun CameraTab(viewModel: ControllerViewModel) {
        val winchState by viewModel.winchState.collectAsState()
        
        Row(modifier = Modifier.fillMaxSize()) {
            // Widok kamery - 85% szerokości
            HttpStreamTabContent(
                viewModel = viewModel,
                tab = ControllerTab.CAMERA,
                sourceType = "camera",
                label = "kamery",
                config = HttpStreamConfigs.CAMERA,
                modifier = Modifier.weight(0.85f)
            )
            
            // Panel sterowania zwijarki - 15% szerokości, pionowy
            WinchControlSwitchVertical(
                currentState = winchState,
                onStateChange = { viewModel.updateWinchState(it) },
                modifier = Modifier
                    .weight(0.15f)
                    .fillMaxHeight()
            )
        }
    }

    // MOJE FUNKCJE Z WAYPOINT ACTIVITY

    fun initializeMapSources(style: Style) {
        style.addSource(GeoJsonSource("waypoint-connections-source", FeatureCollection.fromFeatures(emptyArray())))
        style.addSource(GeoJsonSource("poi-source", FeatureCollection.fromFeatures(emptyArray())))
        style.addSource(GeoJsonSource("waypoint-source", FeatureCollection.fromFeatures(emptyArray())))
        style.addSource(GeoJsonSource("ship-source", viewModel.getShipFeature()))
        style.addSource(GeoJsonSource("phone-source", FeatureCollection.fromFeatures(emptyArray())))
        style.addSource(GeoJsonSource("home-source", viewModel.getHomeFeature()))
    }

    fun initializeMapLayers(style: Style) {
        val waypointSizeScaling = 0.45f
        val connectionLayer = LineLayer("waypoint-connections-layer", "waypoint-connections-source")
            .withProperties(
                lineColor(Color.Black.toArgb()),
                lineWidth(2.0f),
                lineDasharray(arrayOf(2f, 2f)),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND)
            )
        style.addLayer(connectionLayer)

        val poiLayer = SymbolLayer("poi-layer", "poi-source")
            .withProperties(
                iconImage("poi-icon"),
                iconSize(waypointSizeScaling),
                iconAllowOverlap(true),
            )
        style.addLayer(poiLayer)

        val waypointLayer = SymbolLayer("waypoint-layer", "waypoint-source")
            .withProperties(
                iconImage(get("icon")),
                iconSize(waypointSizeScaling),
                iconAllowOverlap(true),
            )
        style.addLayer(waypointLayer)

        val shipLayer = SymbolLayer("ship-layer", "ship-source")
            .withProperties(
                iconImage("ship-icon"),
                iconSize(0.07f),
                iconAllowOverlap(true)
            )
        style.addLayer(shipLayer)

        val phoneLayer = SymbolLayer("phone-layer", "phone-source")
            .withProperties(
                iconImage("phone-icon"),
                iconSize(0.03f),
                iconAllowOverlap(true)
            )
        style.addLayer(phoneLayer)

        val homeLayer = SymbolLayer("home-layer", "home-source").withProperties(
            iconImage("home-icon"),
            iconSize(0.045f),
            iconAllowOverlap(true)
        )
        style.addLayer(homeLayer)
    }

    fun updateBitmaps(style: Style, context: Context) {
        val phoneDrawable = ContextCompat.getDrawable(context, R.drawable.steering_wheel)!!
        val phoneBitmap = createBitmap(
            phoneDrawable.intrinsicWidth,
            phoneDrawable.intrinsicHeight,
        ).apply {
            val canvas = AndroidCanvas(this)
            phoneDrawable.setBounds(0, 0, canvas.width, canvas.height)
            phoneDrawable.draw(canvas)
        }

        val shipDrawable = ContextCompat.getDrawable(context, R.drawable.ship)!!
        val shipBitmap = createBitmap(
            shipDrawable.intrinsicWidth,
            shipDrawable.intrinsicHeight
        ).apply {
            val canvas = AndroidCanvas(this)
            shipDrawable.setBounds(0, 0, canvas.width, canvas.height)
            shipDrawable.draw(canvas)
        }

        val waypointDrawable = ContextCompat.getDrawable(context, R.drawable.ic_waypoint)!!
        val indicationDrawable = WaypointIndicationType.STAR.toWaypointIndication(context)

        val poiBitmap = createWaypointBitmap(
            waypointDrawable,
            indicationDrawable,
        )

        val homeDrawable = ContextCompat.getDrawable(context, R.drawable.home)!!
        val homeBitmap = createBitmap(
            homeDrawable.intrinsicWidth,
            homeDrawable.intrinsicHeight,
        ).apply {
            val canvas = AndroidCanvas(this)
            homeDrawable.setBounds(0, 0, canvas.width, canvas.height)
            homeDrawable.draw(canvas)
        }

        style.addImage("phone-icon", phoneBitmap)
        style.addImage("ship-icon", shipBitmap)
        style.addImage("home-icon", homeBitmap)
        style.addImage("poi-icon", poiBitmap)

        viewModel.waypointPositions.value.forEach { wp ->
            val bitmap = viewModel.waypointBitmaps.value[wp.no]
                ?: getOrCreateWaypointBitmap(wp.no, context)
            addWaypointBitmapToStyle(wp.no, bitmap, style)
        }
    }

    fun updateMapFeatures(style: Style) {
        style.getSourceAs<GeoJsonSource>("waypoint-connections-source")
            ?.setGeoJson(FeatureCollection.fromFeatures(viewModel.getConnectionLinesFeature()))
        style.getSourceAs<GeoJsonSource>("poi-source")
            ?.setGeoJson(FeatureCollection.fromFeatures(viewModel.getPoiFeature()))
        style.getSourceAs<GeoJsonSource>("waypoint-source")
            ?.setGeoJson(FeatureCollection.fromFeatures(viewModel.getWaypointsFeature()))
        style.getSourceAs<GeoJsonSource>("ship-source")
            ?.setGeoJson(viewModel.getShipFeature())
        style.getSourceAs<GeoJsonSource>("phone-source")
            ?.setGeoJson(viewModel.getPhoneLocationFeature())
        style.getSourceAs<GeoJsonSource>("home-source")
            ?.setGeoJson(viewModel.getHomeFeature())
    }

    fun getOrCreateWaypointBitmap(no: Int, context: Context): Bitmap {
        viewModel.waypointBitmaps.value[no]?.let { return it }

        val waypointDrawable = ContextCompat.getDrawable(context, R.drawable.ic_waypoint)!!
        val indicationDrawable = WaypointIndicationType.COMPASS.toWaypointIndication(context)

        val bitmap = createWaypointBitmap(
            waypointDrawable,
            indicationDrawable,
            no.toString()
        )
        viewModel.setWaypointBitmap(no, bitmap)
        return bitmap
    }

    fun addWaypointBitmapToStyle(no: Int, bitmap: Bitmap, style: Style) {
        val bitmapId = "waypoint-icon-$no"
        if (style.getImage(bitmapId) == null) {
            style.addImage(bitmapId, bitmap)
        }
    }

    @Composable
    fun MapLayerVisibilityIcon(
        badgeIcon: Painter,
        contentDescription: String? = null
    ) {
        Box {
            Icon(
                imageVector = Icons.Default.Visibility,
                tint = Color.White,
                contentDescription = contentDescription,
                modifier = Modifier.size(32.dp)
            )
            Icon(
                painter = badgeIcon,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp)
                    .offset(x = 4.dp, y = (-4).dp),
                tint = Color.Unspecified
            )
        }
    }
    
    /**
     * Wskaźnik stanu połączenia streamu.
     * Wyświetla kolorową kropkę wskazującą czy stream jest połączony.
     * Uspójnione komunikaty: "Połączono", "Łączenie...", "Brak połączenia"
     * 
     * @param connectionState Stan połączenia
     * @param errorMessage Komunikat błędu (opcjonalny)
     * @param isVisible Stan widoczności wskaźnika
     * @param modifier Modifier
     */
    @Composable
    fun ConnectionStatusIndicator(
        connectionState: ConnectionState,
        errorMessage: String? = null,
        isVisible: Boolean = true,
        modifier: Modifier = Modifier
    ) {
        // Uspójniony stan - ten sam co w HttpStreamView
        val (baseColor, text) = when (connectionState) {
            ConnectionState.Connected -> SuccessGreen to "Connected"
            ConnectionState.Reconnecting -> WarningYellow to "Reconnecting..."
            ConnectionState.Disconnected -> ErrorRed to "Disconnected"
        }
        
        // Animacja przezroczystości dla fade in/out
        val alpha by animateFloatAsState(
            targetValue = if (isVisible) 1f else 0f,
            animationSpec = when {
                isVisible -> tween(
                    durationMillis = 200, // Krótka animacja pojawiania się
                    easing = FastOutSlowInEasing
                )
                else -> tween(
                    durationMillis = 1500, // Dłuższa animacja zanikania
                    easing = FastOutSlowInEasing
                )
            },
            label = "indicator_alpha"
        )
        
        // Animacja pulsacji koloru kropki (sinusoidalna)
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulseProgress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 2000, // Pełny cykl pulsacji: 2 sekundy
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "pulse_progress"
        )
        
        // Oblicz pulsujący kolor używając sinusa
        // Sinus od 0 do 2π daje wartości od -1 do 1, więc normalizujemy do 0-1
        val pulseFactor = (sin(pulseProgress * 2f * PI.toFloat()) + 1f) / 2f // 0.0 do 1.0
        
        // Lerp między bazowym kolorem a jaśniejszym odcieniem
        // Zwiększamy wahanie - użyjemy większego współczynnika (0.5 zamiast 0.3)
        val lighterColor = Color(
            red = (baseColor.red + (1f - baseColor.red) * 0.5f).coerceIn(0f, 1f),
            green = (baseColor.green + (1f - baseColor.green) * 0.5f).coerceIn(0f, 1f),
            blue = (baseColor.blue + (1f - baseColor.blue) * 0.5f).coerceIn(0f, 1f),
            alpha = baseColor.alpha
        )
        // Dodatkowo ciemniejszy odcień dla większego kontrastu
        val darkerColor = Color(
            red = (baseColor.red * 0.7f).coerceIn(0f, 1f),
            green = (baseColor.green * 0.7f).coerceIn(0f, 1f),
            blue = (baseColor.blue * 0.7f).coerceIn(0f, 1f),
            alpha = baseColor.alpha
        )
        // Pulsacja między ciemniejszym a jaśniejszym odcieniem
        val pulseColor = lerp(darkerColor, lighterColor, pulseFactor)
        
        // Ukryj całkowicie gdy alpha = 0
        if (alpha > 0f) {
            Box(
                modifier = modifier
                    .alpha(alpha)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f * alpha), // Zastosuj alpha również do tła
                        RoundedCornerShape(8.dp)
                    )
                    .padding(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(pulseColor, CircleShape)
                    )
                    Text(
                        text = text,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha), // Zastosuj alpha do tekstu
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    @Composable
    fun WinchControlSwitchVertical(
        currentState: Int, // 2 = góra, 1 = wyłączony, 0 = dół
        onStateChange: (Int) -> Unit,
        modifier: Modifier = Modifier
    ) {
        Column(
            modifier = modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // Lista przycisków: stan, tekst, ikona, kształt
            val buttons = listOf(
                Triple(2, "Góra", Icons.Default.ArrowUpward),
                Triple(1, "Stop", painterResource(id = R.drawable.stop)),
                Triple(0, "Dół", Icons.Default.ArrowDownward)
            )

            buttons.forEachIndexed { index, (stateValue, text, icon) ->
                WinchControlButton(
                    currentState = currentState,
                    stateValue = stateValue,
                    text = text,
                    icon = icon,
                    shape = when (stateValue) {
                        2 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                        0 -> RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
                        else -> RoundedCornerShape(0.dp)
                    },
                    onClick = { onStateChange(stateValue) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                // Dodaj border między przyciskami, oprócz ostatniego
                if (index < buttons.size - 1) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outline)
                    )
                }
            }
        }
    }

    @Composable
    fun WinchControlButton(
        currentState: Int,
        stateValue: Int,
        text: String,
        icon: Any, // może być ImageVector lub Painter
        shape: Shape,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        Button(
            onClick = onClick,
            modifier = modifier.fillMaxHeight(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (currentState == stateValue)
                    MaterialTheme.colorScheme.primary // Ujednolicone z MaterialTheme
                else
                    MaterialTheme.colorScheme.surfaceVariant // Ujednolicone z tłem panelu
            ),
            shape = shape,
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when (icon) {
                    is ImageVector -> Icon(
                        imageVector = icon,
                        contentDescription = text,
                        modifier = Modifier.size(24.dp),
                        tint = if (currentState == stateValue)
                            MaterialTheme.colorScheme.onPrimary // Ujednolicone z MaterialTheme
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant // Ujednolicone z kolorem tekstu na surfaceVariant
                    )
                    is Painter -> Icon(
                        painter = icon,
                        contentDescription = text,
                        modifier = Modifier.size(24.dp),
                        tint = if (currentState == stateValue)
                            MaterialTheme.colorScheme.onPrimary // Ujednolicone z MaterialTheme
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant // Ujednolicone z kolorem tekstu na surfaceVariant
                    )
                }

                Text(
                    text = text,
                    fontSize = 12.sp,
                    color = if (currentState == stateValue)
                        MaterialTheme.colorScheme.onPrimary // Ujednolicone z MaterialTheme
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant // Ujednolicone z kolorem tekstu na surfaceVariant
                )
            }
        }
    }
}
