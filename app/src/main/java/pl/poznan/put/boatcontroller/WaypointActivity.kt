package pl.poznan.put.boatcontroller

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import pl.poznan.put.boatcontroller.templates.RotatePhoneTutorialAnimation
import androidx.core.graphics.createBitmap
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.LineLayer
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
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import pl.poznan.put.boatcontroller.enums.ShipDirection
import pl.poznan.put.boatcontroller.enums.WaypointMode

class WaypointActivity : ComponentActivity() {
    private val waypointVm by viewModels<WaypointViewModel>()
    val cameraZoomAnimationTime = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val colorScheme = MaterialTheme.colorScheme

            MapLibre.getInstance(
                applicationContext
            )
//            Log.d("WAYPOINTS", waypointVm.flagPositions.toString())
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
                RotatePhoneTutorialAnimation(colorScheme, image)
            } else {
                WaypointControlScreen(waypointVm)
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
            snapshotFlow { waypointVm.flagPositions.toList() to shipPos.value }
                .collect {
                    val (flags, ship) = it
                    val flagsSource = mapLibreMapState.value?.style?.getSourceAs<GeoJsonSource>("flags-source")
                    val linesSource = mapLibreMapState.value?.style?.getSourceAs<GeoJsonSource>("lines-source")
                    val shipSource = mapLibreMapState.value?.style?.getSourceAs<GeoJsonSource>("ship-source")

                    if (flagsSource != null && linesSource != null && shipSource != null) {
                        waypointVm.updateMapSources(flagsSource, linesSource, shipSource)
                    }
                }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            MapTab(
                waypointVm = waypointVm,
                onMapReady = { map -> mapLibreMapState.value = map }
            )

            SlidingToolbar(waypointVm)

            FloatingActionButton(
                onClick = {
                    val shipPosition = waypointVm.shipPosition.value
                    val zoom = 13.0
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
        }
    }

    @SuppressLint("InflateParams")
    @Composable
    fun MapTab(waypointVm: WaypointViewModel, onMapReady: (MapLibreMap) -> Unit) {
        val context = LocalContext.current
        val flagSizeScaling = 0.45f
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
                        val flagsSource = GeoJsonSource(
                            "flags-source",
                            FeatureCollection.fromFeatures(emptyArray())
                        )
                        style.addSource(flagsSource)

                        val flagLayer = SymbolLayer("flags-layer", "flags-source")
                            .withProperties(
                                iconImage(get("icon")),
                                iconSize(flagSizeScaling),
                                iconAllowOverlap(true),
                            )

                        style.addLayer(flagLayer)

                        val linesSource = GeoJsonSource(
                            "lines-source",
                            FeatureCollection.fromFeatures(emptyArray())
                        )
                        style.addSource(linesSource)

                        val lineLayer = LineLayer("lines-layer", "lines-source")
                            .withProperties(
                                lineColor(Color.Black.toArgb()),
                                lineWidth(2.0f),
                                lineDasharray(arrayOf(2f, 2f)),
                                lineCap(Property.LINE_CAP_ROUND),
                                lineJoin(Property.LINE_JOIN_ROUND)
                            )
                        style.addLayerBelow(lineLayer, "flags-layer")

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
                        waypointVm.updateMapSources(flagsSource, linesSource, shipSource)

                        mapboxMap.addOnMapClickListener { latLng ->
                            val mode = waypointVm.waypointMode
                            Log.d("FLAGMODE", "Current mode: $mode")
                            when (mode) {
                                WaypointMode.FLAG_ADD -> {
                                    val newWaypoint = waypointVm.addFlag(
                                        latLng.longitude,
                                        latLng.latitude
                                    )

                                    val combinedBitmap = createFlagBitmap(
                                        context,
                                        newWaypoint.id.toString()
                                    )
                                    waypointVm.setFlagBitmap(newWaypoint.id, combinedBitmap)

                                    if (style.getImage("flag-icon-${newWaypoint.id}") == null) {
                                        style.addImage(
                                            "flag-icon-${newWaypoint.id}",
                                            combinedBitmap
                                        )
                                    }
                                    true
                                }

                                WaypointMode.FLAG_DELETE -> {
                                    val screenPoint =
                                        mapboxMap.projection.toScreenLocation(latLng)

                                    val features = mapboxMap.queryRenderedFeatures(
                                        screenPoint,
                                        "flags-layer"
                                    )

                                    if (features.isNotEmpty()) {
                                        val clickedFeature = features.first()
                                        val id = clickedFeature.getStringProperty("id")
                                            ?.toIntOrNull()

                                        if (id != null) {
                                            waypointVm.removeFlag(id)
                                            updateFlagBitmaps(style)
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                }

                                WaypointMode.FLAG_MOVE -> {
                                    val movingId = waypointVm.flagToMoveId
                                    if (movingId == null) {
                                        val screenPoint =
                                            mapboxMap.projection.toScreenLocation(latLng)
                                        val features = mapboxMap.queryRenderedFeatures(
                                            screenPoint,
                                            "flags-layer"
                                        )

                                        if (features.isNotEmpty()) {
                                            val clickedFeature = features.first()
                                            val id = clickedFeature.getStringProperty("id")
                                                ?.toIntOrNull()
                                            if (id != null) {
                                                if(waypointVm.getWaypointById(id)?.isCompleted == false) {
                                                    waypointVm.flagToMoveId = id
                                                    val bitmap = waypointVm.flagBitmaps[id]!!
                                                    val selectedBitmap =
                                                        bitmap.scale(
                                                            (bitmap.width * 1.2f).toInt(),
                                                            (bitmap.height * 1.2f).toInt()
                                                        )
                                                    style.addImage("flag-icon-$id", selectedBitmap)
                                                }
                                            }
                                        }
                                        true
                                    } else {
                                        waypointVm.moveFlag(
                                            movingId,
                                            latLng.longitude,
                                            latLng.latitude
                                        )

                                        val bitmap = waypointVm.flagBitmaps[movingId]!!
                                        style.addImage("flag-icon-$movingId", bitmap)

                                        waypointVm.flagToMoveId = null
                                        true
                                    }
                                }

                                else -> false
                            }
                        }
                        updateFlagBitmaps(style)
                    }
                    onMapReady(mapboxMap)
                }
            }
        )
    }

    fun createFlagBitmap(context: Context, text: String): Bitmap {
        val flagDrawable = ContextCompat.getDrawable(context, R.drawable.ic_flag)!!

        val cx = flagDrawable.intrinsicWidth + 0f
        val cy = flagDrawable.intrinsicHeight + 0f
        val radius = 60f

        val minWidth = (cx + radius).toInt()
        val minHeight = (cy + radius).toInt()

        val width = maxOf(flagDrawable.intrinsicWidth, minWidth)
        val height = maxOf(flagDrawable.intrinsicHeight, minHeight)

        val paintCircle = Paint().apply {
            color = Color.Red.toArgb()
            isAntiAlias = true
        }

        val paintCircleBorder = Paint().apply {
            color = Color.Black.toArgb()
            style = Paint.Style.STROKE
            strokeWidth = 6f
            isAntiAlias = true
        }

        val paintText = Paint().apply {
            color = Color.White.toArgb()
            textSize = 45f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            isAntiAlias = true
        }
        val textY = cy - (paintText.descent() + paintText.ascent()) / 2

        val flagBitmap = createBitmap(
            width.toInt() + 50,
            height.toInt() + 50,
        ).apply {
            val canvas = Canvas(this)
            flagDrawable.setBounds(0, 0, flagDrawable.intrinsicWidth, flagDrawable.intrinsicHeight)
            flagDrawable.draw(canvas)
            canvas.drawCircle(cx, cy, radius, paintCircle)
            canvas.drawCircle(cx, cy, radius, paintCircleBorder)
            canvas.drawText(text, cx, textY, paintText)
        }
        return flagBitmap
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
                                waypointMode = WaypointMode.FLAG_ADD,
                                onClick = {
//                                    waypointVm.setFlagEditMode(WaypointMode.FLAG_ADD)
                                    waypointVm.toggleFlagEditMode(WaypointMode.FLAG_ADD)
                                },
                                isEnabled = !waypointVm.isShipMoving.value
                            )
                            IconWithEffectButton(
                                drawableId = R.drawable.waypoint_delete,
                                waypointMode = WaypointMode.FLAG_DELETE,
                                onClick = {
                                    waypointVm.toggleFlagEditMode(WaypointMode.FLAG_DELETE)
                                },
                                isEnabled = !waypointVm.isShipMoving.value
                            )
                            IconWithEffectButton(
                                drawableId = R.drawable.waypoint_move,
                                waypointMode = WaypointMode.FLAG_MOVE,
                                onClick = {
                                    waypointVm.toggleFlagEditMode(WaypointMode.FLAG_MOVE)
                                },
                                isEnabled = !waypointVm.isShipMoving.value
                            )
                            IconWithEffectButton(
                                drawableId = if (waypointVm.isShipMoving.value) R.drawable.pause else R.drawable.start,
                                waypointMode = WaypointMode.SHIP_DEFAULT_MOVE,
                                onClick = {
                                    waypointVm.toggleFlagEditMode(WaypointMode.SHIP_DEFAULT_MOVE)
                                    waypointVm.toggleSimulation()
                                },
                            )
                            IconWithEffectButton(
                                drawableId = R.drawable.back_to_home,
                                waypointMode = WaypointMode.SHIP_REVERSE_MOVE,
                                onClick = {
                                    waypointVm.currentShipDirection.value = ShipDirection.REVERSE
                                    waypointVm.startShipSimulation()
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

    fun updateFlagBitmaps(style: Style) {
        waypointVm.flagBitmaps.forEach { (idx, bitmap) ->
            style.addImage(
                "flag-icon-$idx",
                bitmap
            )
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun PreviewPhoneAnimationScreen() {

    }
}