package pl.poznan.put.boatcontroller

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.FeatureCollection
import pl.poznan.put.boatcontroller.templates.RotatePhoneAnimation
import androidx.core.graphics.createBitmap
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import androidx.core.graphics.scale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import pl.poznan.put.boatcontroller.enums.ShipDirection
import pl.poznan.put.boatcontroller.enums.WaypointIndication
import pl.poznan.put.boatcontroller.enums.WaypointIndicationType
import pl.poznan.put.boatcontroller.enums.WaypointMode
import pl.poznan.put.boatcontroller.ui.theme.BoatControllerTheme

class WaypointActivity : ComponentActivity() {
    private val waypointVm by viewModels<WaypointViewModel>()
    val cameraZoomAnimationTime = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_BoatController)
        super.onCreate(savedInstanceState)
        setContent {
            BoatControllerTheme {
                val context = LocalContext.current
                val colorScheme = MaterialTheme.colorScheme

                waypointVm.shouldFinish.observe(this) { shouldFinish ->
                    if (shouldFinish == true) {
                        finish()
                    }
                }

                onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        waypointVm.stopShipSimulation()
                        waypointVm.onSimulationFinished()
                        waypointVm.sendMessage("SCM:MEN")
                        finish()
                    }
                })

                MapLibre.getInstance(
                    applicationContext
                )
                val image = remember {
                    ResourcesCompat.getDrawable(
                        context.resources,
                        R.drawable.phone_android_2,
                        context.theme
                    )
                        ?.toBitmap()
                        ?.asImageBitmap()
                }
                if (!isLandscape()) {
                    RotatePhoneAnimation(colorScheme, image)
                } else {
                    WaypointControlScreen(waypointVm)
                }
            }
        }
    }

    @Composable
    fun isLandscape(): Boolean {
        val configuration = LocalConfiguration.current
        return configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    @Composable
    fun WaypointControlScreen(waypointVm: WaypointViewModel) {
        val mapLibreMapState = remember { mutableStateOf<MapLibreMap?>(null) }
        val shipPos = waypointVm.shipPosition

        LaunchedEffect(Unit) {
            snapshotFlow { waypointVm.waypointPositions.toList() to shipPos.value }
                .collect {
                    val waypointsSource = mapLibreMapState.value?.style?.getSourceAs<GeoJsonSource>("waypoint-source")
                    val waypointConnectionsSource = mapLibreMapState.value?.style?.getSourceAs<GeoJsonSource>("waypoint-connections-source")
                    val shipSource = mapLibreMapState.value?.style?.getSourceAs<GeoJsonSource>("ship-source")

                    if (waypointsSource != null && waypointConnectionsSource != null && shipSource != null) {
                        waypointVm.updateMapSources(waypointsSource, waypointConnectionsSource, shipSource)
                    }
                }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            MapTab(
                waypointVm = waypointVm,
                onMapReady = { map -> mapLibreMapState.value = map }
            )

            SlidingToolbar(waypointVm)

            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                FloatingActionButton(
                    onClick = {
                        val shipPosition = waypointVm.shipPosition.value
                        val zoom = 16.0
                        mapLibreMapState.value?.animateCamera(
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
                    containerColor = colorResource(id = R.color.blue)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.current_location_tracker),
                        contentDescription = "Center Map",
                        tint = Color.White
                    )
                }

                FloatingActionButton(
                    onClick = {
                        val shipPosition = waypointVm.shipPosition.value
                        val zoom = 32.0
                        mapLibreMapState.value?.animateCamera(
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
                        .padding(end = 88.dp, bottom = 16.dp)
                        .shadow(16.dp, CircleShape, clip = false)
                        .clip(CircleShape),
                    containerColor = colorResource(id = R.color.teal_700)
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "Center Map",
                        tint = Color.White
                    )
                }
            }
        }
    }

    @SuppressLint("InflateParams")
    @Composable
    fun MapTab(waypointVm: WaypointViewModel, onMapReady: (MapLibreMap) -> Unit) {
        val context = LocalContext.current
        val waypointSizeScaling = 0.45f
        val lifecycleOwner = LocalLifecycleOwner.current
        val mapView = remember { MapView(context) }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE) {
                    mapView.getMapAsync { map ->
                        map.cameraPosition.let { pos ->
                            val target = pos.target
                            if (target != null) {
                                waypointVm.saveCameraPosition(
                                    target.latitude,
                                    target.longitude,
                                    pos.zoom
                                )
                            }
                        }
                    }
                }
            }

            lifecycleOwner.lifecycle.addObserver(observer)

            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { mapView ->
                mapView.getMapAsync { mapboxMap ->
                    val styleJson = """
                        {
                          "version": 8,
                          "sources": {
                            "osm": {
                              "type": "raster",
                              "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
                              "tileSize": 256
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

                    val shipDrawable = ContextCompat.getDrawable(context, R.drawable.ship)!!
                    val shipBitmap = createBitmap(
                        shipDrawable.intrinsicWidth,
                        shipDrawable.intrinsicHeight
                    ).apply {
                        val canvas = Canvas(this)
                        shipDrawable.setBounds(0, 0, canvas.width, canvas.height)
                        shipDrawable.draw(canvas)
                    }

                    mapboxMap.setStyle(Style.Builder().fromJson(styleJson)) { style ->
                        // ===========================
                        // SHIP SOURCE & LAYER
                        // ===========================

                        style.addImage("ship-icon", shipBitmap)
                        val shipCoordinates = Point.fromLngLat(
                            waypointVm.shipPosition.value.lon,
                            waypointVm.shipPosition.value.lat
                        )

                        val shipFeature = Feature.fromGeometry(shipCoordinates).apply {
                            addStringProperty("title", "Ship")
                        }

                        val shipSource = GeoJsonSource(
                            "ship-source",
                            FeatureCollection.fromFeatures(listOf(shipFeature))
                        )
                        style.addSource(shipSource)

                        val shipLayer = SymbolLayer("ship-layer", "ship-source")
                            .withProperties(
                                iconImage("ship-icon"),
                                iconSize(0.07f),
                                iconAllowOverlap(true)
                            )
                        style.addLayer(shipLayer)

                        // ===========================
                        // WAYPOINT SOURCE & LAYER
                        // ===========================

                        val waypointsSource = GeoJsonSource(
                            "waypoint-source",
                            FeatureCollection.fromFeatures(emptyArray())
                        )
                        style.addSource(waypointsSource)

                        val waypointLayer = SymbolLayer("waypoint-layer", "waypoint-source")
                            .withProperties(
                                iconImage(get("icon")),
                                iconSize(waypointSizeScaling),
                                iconAllowOverlap(true),
                            )
                        style.addLayer(waypointLayer)

                        // ===========================
                        // POINTS OF INTEREST (POI) SOURCE & LAYER
                        // ===========================

                        val poiSource = GeoJsonSource(
                            "poi-source",
                            FeatureCollection.fromFeatures(emptyArray())
                        )
                        style.addSource(poiSource)

                        val poiLayer = SymbolLayer("poi-layer", "poi-source")
                            .withProperties(
                                iconImage("poi-icon"),
                                iconSize(waypointSizeScaling),
                                iconAllowOverlap(true),
                            )
                        style.addLayer(poiLayer)

                        // ===========================
                        // WAYPOINT CONNECTIONS SOURCE & LAYER
                        // ===========================

                        val waypointConnectionsSource = GeoJsonSource(
                            "waypoint-connections-source",
                            FeatureCollection.fromFeatures(emptyArray())
                        )
                        style.addSource(waypointConnectionsSource)

                        val waypointConnectionsLayer = LineLayer("waypoint-connections-layer", "waypoint-connections-source")
                            .withProperties(
                                lineColor(Color.Black.toArgb()),
                                lineWidth(2.0f),
                                lineDasharray(arrayOf(2f, 2f)),
                                lineCap(Property.LINE_CAP_ROUND),
                                lineJoin(Property.LINE_JOIN_ROUND)
                            )
                        style.addLayerBelow(waypointConnectionsLayer, "waypoint-layer")

                        val savedCamPos = waypointVm.cameraPosition.value

                        if (savedCamPos != null) {
                            mapboxMap.moveCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(savedCamPos.lon, savedCamPos.lat),
                                    savedCamPos.zoom
                                )
                            )
                        } else {
                            val defaultPosition = CameraPosition.Builder()
                                .target(
                                    LatLng(
                                        waypointVm.shipPosition.value.lat,
                                        waypointVm.shipPosition.value.lon
                                    )
                                )
                                .zoom(13.0)
                                .build()

                            mapboxMap.moveCamera(
                                CameraUpdateFactory.newCameraPosition(
                                    defaultPosition
                                )
                            )
                        }

                        mapboxMap.uiSettings.isRotateGesturesEnabled = false
                        waypointVm.updateMapSources(waypointsSource, waypointConnectionsSource, shipSource)
                        waypointVm.showMapPoiSources(poiSource)

                        createPoiWaypoints(context, style)

                        mapboxMap.addOnMapClickListener { latLng ->
                            val mode = waypointVm.waypointMode
                            Log.d("WAYPOINTMODE", "Current mode: $mode")
                            when (mode) {
                                WaypointMode.WAYPOINT_ADD -> {
                                    val newWaypoint = waypointVm.addWaypoint(
                                        latLng.longitude,
                                        latLng.latitude
                                    )

                                    val waypointDrawable = ContextCompat.getDrawable(context, R.drawable.ic_waypoint)!!
                                    val indicationDrawable = WaypointIndicationType.STAR.toWaypointIndication(context)

                                    val combinedBitmap = createWaypointBitmap(
                                        waypointDrawable,
                                        indicationDrawable,
                                        newWaypoint.id.toString()
                                    )
                                    waypointVm.setWaypointBitmap(newWaypoint.id, combinedBitmap)

                                    if (style.getImage("waypoint-icon-${newWaypoint.id}") == null) {
                                        style.addImage(
                                            "waypoint-icon-${newWaypoint.id}",
                                            combinedBitmap
                                        )
                                    }
                                    Log.d("WAYPOINTS_COORDINATES_${newWaypoint.id}",
                                        "(" + waypointVm.getWaypointById(newWaypoint.id)?.lat.toString() + ", " +
                                                waypointVm.getWaypointById(newWaypoint.id)?.lon.toString() + ")"
                                    )
                                    true
                                }

                                WaypointMode.WAYPOINT_DELETE -> {
                                    val screenPoint =
                                        mapboxMap.projection.toScreenLocation(latLng)

                                    val features = mapboxMap.queryRenderedFeatures(
                                        screenPoint,
                                        "waypoint-layer"
                                    )

                                    if (features.isNotEmpty()) {
                                        val clickedFeature = features.first()
                                        val id = clickedFeature.getStringProperty("id")
                                            ?.toIntOrNull()

                                        if (id != null) {
                                            waypointVm.removeWaypoint(id)
                                            updateWaypointBitmaps(style)
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                }

                                WaypointMode.WAYPOINT_MOVE -> {
                                    val movingId = waypointVm.waypointToMoveId
                                    if (movingId == null) {
                                        val screenPoint =
                                            mapboxMap.projection.toScreenLocation(latLng)
                                        val features = mapboxMap.queryRenderedFeatures(
                                            screenPoint,
                                            "waypoint-layer"
                                        )

                                        if (features.isNotEmpty()) {
                                            val clickedFeature = features.first()
                                            val id = clickedFeature.getStringProperty("id")
                                                ?.toIntOrNull()
                                            if (id != null) {
                                                if(waypointVm.getWaypointById(id)?.isCompleted == false) {
                                                    waypointVm.waypointToMoveId = id
                                                    val bitmap = waypointVm.waypointBitmaps[id]!!
                                                    val selectedBitmap =
                                                        bitmap.scale(
                                                            (bitmap.width * 1.2f).toInt(),
                                                            (bitmap.height * 1.2f).toInt()
                                                        )
                                                    style.addImage("waypoint-icon-$id", selectedBitmap)
                                                }
                                            }
                                        }
                                        true
                                    } else {
                                        waypointVm.moveWaypoint(
                                            movingId,
                                            latLng.longitude,
                                            latLng.latitude
                                        )

                                        val bitmap = waypointVm.waypointBitmaps[movingId]!!
                                        style.addImage("waypoint-icon-$movingId", bitmap)

                                        waypointVm.waypointToMoveId = null
                                        true
                                    }
                                }

                                else -> false
                            }
                        }
                        updateWaypointBitmaps(style)
                    }
                    onMapReady(mapboxMap)
                }
            }
        )
    }

    fun createWaypointBitmap(
        waypointDrawable: Drawable,
        indicationDrawable: WaypointIndication?,
        waypointNumber: String = ""
    ): Bitmap {
        // Canvas is creating image on (0,0) left/top so additional
        // pixels are going on bottom/right
        val extraTop = 30
        val extraRight = 30

        val waypointScale = 0.20f
        val waypointWidth = (waypointDrawable.intrinsicWidth * waypointScale).toInt()
        val waypointHeight = (waypointDrawable.intrinsicHeight * waypointScale).toInt()

        val width = waypointWidth + extraRight
        val height = waypointHeight + extraTop

        return createBitmap(
            width,
            height
        ).apply {
            val canvas = Canvas(this)

            waypointDrawable.setBounds(0, extraTop, waypointWidth, extraTop + waypointHeight)
            waypointDrawable.draw(canvas)

            indicationDrawable?.let { ind ->
                val indicationScale = 0.08f
                val dw = (ind.drawable.intrinsicWidth * indicationScale).toInt()
                val dh = (ind.drawable.intrinsicHeight * indicationScale).toInt()

                val indLeft = width - dw
                val indTop = 0
                val indRight = indLeft + dw
                val indBottom = indTop + dh

                ind.drawable.setBounds(indLeft, indTop, indRight, indBottom)
                ind.drawable.draw(canvas)
            }

            if(waypointNumber != "") {
                val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.White.toArgb()
                    textSize = 76f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                    isFakeBoldText = true
                }

                val textX = waypointWidth / 2f
                val textY = waypointHeight * 0.5f - (paintText.descent() + paintText.ascent()) / 2f

                canvas.drawText(waypointNumber, textX, textY, paintText)
            }
        }
    }

    fun createPoiWaypoints(context: Context, style: Style) {
        val waypointDrawable = ContextCompat.getDrawable(context, R.drawable.ic_waypoint)!!
        val indicationDrawable = WaypointIndicationType.COMPASS.toWaypointIndication(context)

        val combinedBitmap = createWaypointBitmap(
            waypointDrawable,
            indicationDrawable,
        )

        if (style.getImage("poi-icon") == null) {
            style.addImage(
                "poi-icon",
                combinedBitmap
            )
        }
    }

    // Canvas for tests in preview without compiling whole app
    @Preview(showBackground = true)
    @Composable
    fun WaypointBitmapPreview() {
        val context = LocalContext.current
        val waypointDrawable = ContextCompat.getDrawable(context, R.drawable.ic_waypoint)!!
        val indicationDrawable = WaypointIndicationType.COMPASS.toWaypointIndication(context)

        val bitmap = remember { createWaypointBitmap(waypointDrawable, indicationDrawable, "29") }

        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.padding(16.dp)
        )
    }

    @Composable
    fun SlidingToolbar(
        waypointVm: WaypointViewModel,
    ) {
        val density = LocalDensity.current

        val screenWidth = LocalWindowInfo.current.containerSize.width
        val screenWidthDp = with(density) { screenWidth.toDp() }
        val screenHeight = LocalWindowInfo.current.containerSize.height
        val screenHeightDp = with(density) { screenHeight.toDp() }

        val toolbarWidth = screenWidthDp * 0.15f
        val arrowBoxWidth = screenWidthDp * 0.05f
        val arrowBoxHeight = screenHeightDp * 0.2f
        val arrowBoxOffset =
            if (waypointVm.isToolbarOpened) toolbarWidth - (arrowBoxWidth / 2) else toolbarWidth - (arrowBoxWidth / 4)

        val animatedOffset by animateDpAsState(
            targetValue = if (waypointVm.isToolbarOpened) 0.dp else -toolbarWidth,
            animationSpec = tween(300),
            label = "ToolbarOffset"
        )

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .offset(x = animatedOffset)
                .width(toolbarWidth + arrowBoxWidth * 0.8f)
                .zIndex(1f)
                .pointerInput(Unit) {
                    detectTapGestures {
                        Log.d("MAP", "Kliknięcie w obszar toolbara zablokowane podczas operacji")
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(toolbarWidth)
                    .background(Color.Black)
            ) {
                if (waypointVm.isToolbarOpened) {
                    Column(
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Waypoints",
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Normal,
                                fontSize = 3.5.em,
                                color = Color.White
                            )
                        }
                        Column(
                            modifier = Modifier
                                .padding(8.dp)
                                .fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceAround,
                        ) {
                            IconWithEffectButton(
                                drawableId = R.drawable.waypoint_add,
                                waypointMode = WaypointMode.WAYPOINT_ADD,
                                onClick = {
//                                    waypointVm.setWaypointEditMode(WaypointMode.WAYPOINT_ADD)
                                    waypointVm.toggleWaypointEditMode(WaypointMode.WAYPOINT_ADD)
                                },
                                isEnabled = !waypointVm.isShipMoving.value
                            )
                            IconWithEffectButton(
                                drawableId = R.drawable.waypoint_delete,
                                waypointMode = WaypointMode.WAYPOINT_DELETE,
                                onClick = {
                                    waypointVm.toggleWaypointEditMode(WaypointMode.WAYPOINT_DELETE)
                                },
                                isEnabled = !waypointVm.isShipMoving.value
                            )
                            IconWithEffectButton(
                                drawableId = R.drawable.waypoint_move,
                                waypointMode = WaypointMode.WAYPOINT_MOVE,
                                onClick = {
                                    waypointVm.toggleWaypointEditMode(WaypointMode.WAYPOINT_MOVE)
                                },
                                isEnabled = !waypointVm.isShipMoving.value
                            )
                            IconWithEffectButton(
                                drawableId = if (waypointVm.isShipMoving.value) R.drawable.pause else R.drawable.start,
                                waypointMode = WaypointMode.SHIP_DEFAULT_MOVE,
                                onClick = {
                                    waypointVm.toggleWaypointEditMode(WaypointMode.SHIP_DEFAULT_MOVE)
                                    waypointVm.toggleSimulation()
                                },
                            )
                            IconWithEffectButton(
                                drawableId = R.drawable.back_to_home,
                                waypointMode = WaypointMode.SHIP_REVERSE_MOVE,
                                onClick = {
                                    waypointVm.goToHome()
                                    waypointVm.stopShipSimulation()

                                    CoroutineScope(Dispatchers.Main).launch {
                                        delay(300)
                                        waypointVm.startShipSimulation()
                                    }
                                },
                                isEnabled = waypointVm.currentShipDirection.value == ShipDirection.DEFAULT
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(width = arrowBoxWidth, height = arrowBoxHeight)
                    .align(Alignment.CenterStart)
                    .offset(x = arrowBoxOffset)
                    .background(Color.DarkGray, shape = RoundedCornerShape(8.dp))
                    .clickable { waypointVm.isToolbarOpened = !waypointVm.isToolbarOpened },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (waypointVm.isToolbarOpened) Icons.AutoMirrored.Default.KeyboardArrowLeft else Icons.AutoMirrored.Default.KeyboardArrowRight,
                    contentDescription = "Toggle",
                    tint = Color.White
                )
            }
        }
    }

    @Composable
    fun IconWithEffectButton(
        drawableId: Int,
        waypointMode: WaypointMode?,
        onClick: () -> Unit,
        isEnabled: Boolean = true
    ) {
        val borderColor = when {
            !isEnabled -> colorResource(id = R.color.DARK_RED)
            isEnabled && waypointMode == waypointVm.waypointMode -> Color.Green
            isEnabled && waypointMode != waypointVm.waypointMode -> Color.Transparent
            else -> Color.Transparent
        }

        val shadowColor = when {
            !isEnabled -> colorResource(id = R.color.DARK_RED)
            isEnabled && waypointMode == waypointVm.waypointMode -> Color.Green
            isEnabled && waypointMode != waypointVm.waypointMode -> Color.Transparent
            else -> Color.Transparent
        }

        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .shadow(
                    elevation = if (waypointMode == waypointVm.waypointMode) 8.dp else 0.dp,
                    shape = CircleShape,
                    ambientColor = shadowColor,
                    spotColor = shadowColor
                )
                .border(width = 2.dp, color = borderColor, shape = CircleShape)
                .clip(CircleShape)
                .background(Color.DarkGray),
            enabled = isEnabled
        ) {
            Icon(
                painter = painterResource(drawableId),
                contentDescription = null,
                tint = Color.White
            )
        }
    }

    fun updateWaypointBitmaps(style: Style) {
        waypointVm.waypointBitmaps.forEach { (idx, bitmap) ->
            style.addImage(
                "waypoint-icon-$idx",
                bitmap
            )
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun PreviewPhoneAnimationScreen() {

    }
}