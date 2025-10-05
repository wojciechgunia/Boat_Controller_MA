package pl.poznan.put.boatcontroller

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
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
import pl.poznan.put.boatcontroller.enums.MapLayersVisibilityMode
import pl.poznan.put.boatcontroller.enums.WaypointIndicationType
import pl.poznan.put.boatcontroller.templates.BatteryIndicator
import pl.poznan.put.boatcontroller.templates.FullScreenPopup
import pl.poznan.put.boatcontroller.templates.RotatePhoneAnimation
import pl.poznan.put.boatcontroller.templates.createWaypointBitmap
import pl.poznan.put.boatcontroller.ui.theme.BoatControllerTheme
import kotlin.math.min

class ControllerActivity: ComponentActivity() {
    private val viewModel by viewModels<ControllerViewModel>()
    val cameraZoomAnimationTime = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        val intent = getIntent()
        val selectedMission = intent.getIntExtra("selectedMission", -1)
        viewModel.updateMissionId(selectedMission)
        setTheme(R.style.Theme_BoatController)
        super.onCreate(savedInstanceState)

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
        sendMode("MEN")
        setResult(Activity.RESULT_OK)
        @Suppress("DEPRECATION")
        super.onBackPressed()
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
        val message = "SES:${viewModel.leftEnginePower}:${viewModel.rightEnginePower}"
        println("Sending via socket: $message")
        viewModel.sendMessage(message)
    }

    fun sendSetHome(viewModel: ControllerViewModel) {
        val lat = viewModel.shipPosition.value.lat
        val lon = viewModel.shipPosition.value.lon
        val message = "SHM:${lat}:${lon}"
        viewModel.updateHomePosition(HomePosition(lat, lon))
        println("Sending via socket: $message")
        viewModel.sendMessage(message)
    }

    fun sendMode(mode: String) {
        val message = "SCM:${mode}"
        println("Sending via socket: $message")
        viewModel.sendMessage(message)
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

        val primary = MaterialTheme.colorScheme.primary
        val onSurface = MaterialTheme.colorScheme.onSecondaryContainer
        val background = MaterialTheme.colorScheme.secondaryContainer
        val iconAndTextColor = if (isActive || isPressed) primary else onSurface
        val underlineColor = if (isActive || isPressed) primary else Color.LightGray

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
        val tabs = listOf("Mapa", "Sonar", "Czujniki", "Kamera")
        val isTablet = isTablet(this)
        var isSyncOn by remember { mutableStateOf(false) }

        Row(modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)) {
            FullScreenPopup(viewModel.openPOIDialog, { viewModel.openPOIDialog = false }, viewModel.poiId, viewModel.poiPositions, { id: Int, name: String -> viewModel.updatePoiData(id, name, viewModel.poiPositions.firstOrNull{ it.id == id }?.description.orEmpty()) }, { id: Int, description: String -> viewModel.updatePoiData(id, viewModel.poiPositions.firstOrNull{ it.id == id }?.name.orEmpty(), description)}, { id -> viewModel.deletePoi(id)
                viewModel.openPOIDialog = false
            })

            PowerSlider(
                value = viewModel.leftEnginePower,
                onValueChange = {
                    if(viewModel.leftEnginePower != it) {
                        viewModel.leftEnginePower = it
                        sendEnginePower(viewModel)
                        isSyncOn = false
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
                            text = viewModel.currentSpeed.toString(),
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
                                viewModel.leftEnginePower = viewModel.rightEnginePower
                                sendEnginePower(viewModel)
                            }
                        )

                        SidebarButton(
                            icon = painterResource(id = R.drawable.stop),
                            label = "Stop",
                            isActive = false,
                            onClick = {
                                viewModel.rightEnginePower = 0
                                viewModel.leftEnginePower = 0
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
                        TabRow(selectedTabIndex = viewModel.selectedTab) {
                            tabs.forEachIndexed { index, title ->
                                Tab(
                                    selected = viewModel.selectedTab == index,
                                    onClick = { viewModel.selectedTab = index
                                        when (index) {
                                            0 -> { sendMode("MAP") }
                                            1 -> { sendMode("SNR") }
                                            2 -> { sendMode("SEN") }
                                            3 -> { sendMode("CAM") }
                                        }
                                    },
                                    text = { Text(title) }
                                )
                            }
                        }

                        when (viewModel.selectedTab) {
                            0 -> {MapTab(viewModel) }
                            1 -> {SonarTab(viewModel) }
                            2 -> {SensorsTab(viewModel) }
                            3 -> {CameraTab(viewModel) }
                        }
                    }
                }
            }

            PowerSlider(
                value = viewModel.rightEnginePower,
                onValueChange = {
                    if(viewModel.rightEnginePower != it) {
                        viewModel.rightEnginePower = it
                        if(isSyncOn) {
                            viewModel.leftEnginePower = it
                        }
                        sendEnginePower(viewModel)
                    }},
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                rightSide = true,
                isTablet = isTablet,
            )
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
        val sliderHeight = screenHeight * 0.9f
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
                            segValue > 60 || segValue in -70..-60 -> Color.Red
                            segValue in 41..60 || segValue in -60..-39 -> Color.Yellow
                            segValue in 21..40 || segValue in -39..-19 -> Color.Green
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
                                android.graphics.Paint().apply {
                                    color = android.graphics.Color.BLACK
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
        val map = viewModel.mapLibreMapState.value
        val context = LocalContext.current
        val waypoints = viewModel.waypointPositions.toList()
        val poi = viewModel.poiPositions.toList()
        val layersMode = viewModel.layersMode.value
        val homePosition = viewModel.homePosition
        val phonePosition = viewModel.phonePosition.value
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
                                viewModel.setPhonePositionFallback()
                                Log.d("PHONE_LOCATION", "Fallback to ship")
                            }
                        }
                        .addOnFailureListener {
                            viewModel.setPhonePositionFallback()
                            Log.d("PHONE_LOCATION", "Fallback to ship on failure")
                        }
                } catch (e: SecurityException) {
                    viewModel.setPhonePositionFallback()
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

            val saved = viewModel.cameraPosition.value

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

        LaunchedEffect(waypoints.toList()) {
            val mapboxMap = map ?: return@LaunchedEffect
            val style = mapboxMap.style ?: return@LaunchedEffect

            waypoints.forEach { wp ->
                val bitmap = getOrCreateWaypointBitmap(wp.no, context)
                addWaypointBitmapToStyle(wp.no, bitmap, style)
            }
            updateMapFeatures(style)
        }

        LaunchedEffect(poi) {
            val mapboxMap = map ?: return@LaunchedEffect
            val style = mapboxMap.style ?: return@LaunchedEffect

            updateMapFeatures(style)
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

            val lat = viewModel.homePosition.value.lat
            val lon = viewModel.homePosition.value.lon

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
                                              "coordinates": [${viewModel.shipPosition.value.lon}, ${viewModel.shipPosition.value.lat}]
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
                                            viewModel.poiId = viewModel.poiPositions.indexOfFirst { it.id == id }
                                            viewModel.openPOIDialog = true
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                }
                                updateMapFeatures(style)
                            }
                            viewModel.mapLibreMapState.value = mapboxMap
                        }
                    }
                },
                modifier = modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                BatteryIndicator(
                    level = viewModel.externalBatteryLevel.value ?: 0,
                    isCharging = true,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                )

                FloatingActionButton(
                    onClick = {
                        val shipPosition = viewModel.shipPosition
                        val zoom = 16.0
                        map?.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(
                                    shipPosition.value.lat,
                                    shipPosition.value.lon
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
                    containerColor = colorResource(id = R.color.blue)
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
                    containerColor = colorResource(id = R.color.teal_700)
                ) {
                    when (viewModel.layersMode.value) {
                        MapLayersVisibilityMode.BOTH_VISIBLE -> MapLayerVisibilityIcon(painterResource(id = R.drawable.ic_indication_compass), "Waypoints visible")
                        MapLayersVisibilityMode.WAYPOINTS -> MapLayerVisibilityIcon(painterResource(id = R.drawable.ic_indication_star), "POI visible")
                        MapLayersVisibilityMode.POI -> Icon(Icons.Default.VisibilityOff, tint = Color.White, contentDescription = "None visible")
                        MapLayersVisibilityMode.NONE -> Icon(Icons.Default.Visibility, tint = Color.White, contentDescription = "Both visible")
                    }
                }
            }
        }
    }


    @Composable
    fun SonarTab(viewModel: ControllerViewModel) {
        Box(modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(), contentAlignment = Alignment.Center) {
            viewModel.sonarData.let {
                val bitmap = remember(viewModel.sonarData) {
                    BitmapFactory.decodeByteArray(viewModel.sonarData, 0, viewModel.sonarData.size)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Głębokość: ${viewModel.sensorsData.depth}m", fontSize = 20.sp, color = MaterialTheme.colorScheme.onBackground)
                    bitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "Zdjęcie z kamery",
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(4f / 3f),
                            contentScale = ContentScale.Crop
                        )
                    } ?: Text("Nie można odczytać obrazu", color = MaterialTheme.colorScheme.onBackground)}
            }
        }
    }

    @Composable
    fun SensorsTab(viewModel: ControllerViewModel) {
        val data = viewModel.sensorsData
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Temperatura: ${data.temperature}°C\n", fontSize = 20.sp, color = MaterialTheme.colorScheme.onBackground)
                Text(text = "Wilgotność: ${data.humidity}%\n", fontSize = 20.sp, color = MaterialTheme.colorScheme.onBackground)
                Text(text = "Głębokość: ${data.depth}m\n", fontSize = 20.sp, color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }

    fun byteArrayToImageBitmap(bytes: ByteArray): ImageBitmap? {
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }

    @Composable
    fun CameraTab(viewModel: ControllerViewModel) {
        Box(modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(), contentAlignment = Alignment.Center) {
            viewModel.cameraFeed.let {
                val imageBitmap = byteArrayToImageBitmap(it)
                imageBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Zdjęcie z kamery",
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(4f / 3f),
                        contentScale = ContentScale.Crop
                    )
                } ?: Text("Nie można odczytać obrazu", color = MaterialTheme.colorScheme.onBackground)
            }
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
            val canvas = android.graphics.Canvas(this)
            phoneDrawable.setBounds(0, 0, canvas.width, canvas.height)
            phoneDrawable.draw(canvas)
        }

        val shipDrawable = ContextCompat.getDrawable(context, R.drawable.ship)!!
        val shipBitmap = createBitmap(
            shipDrawable.intrinsicWidth,
            shipDrawable.intrinsicHeight
        ).apply {
            val canvas = android.graphics.Canvas(this)
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
            val canvas = android.graphics.Canvas(this)
            homeDrawable.setBounds(0, 0, canvas.width, canvas.height)
            homeDrawable.draw(canvas)
        }

        style.addImage("phone-icon", phoneBitmap)
        style.addImage("ship-icon", shipBitmap)
        style.addImage("home-icon", homeBitmap)
        style.addImage("poi-icon", poiBitmap)

        viewModel.waypointPositions.forEach { wp ->
            val bitmap = viewModel.waypointBitmaps[wp.no]
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
        viewModel.waypointBitmaps[no]?.let { return it }

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

    @Preview(showBackground = true)
    @Composable
    fun PreviewPhoneAnimationScreen() {

    }
}
