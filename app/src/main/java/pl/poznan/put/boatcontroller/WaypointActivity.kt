package pl.poznan.put.boatcontroller

import android.Manifest
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
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
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.core.graphics.scale
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.location.LocationServices
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import pl.poznan.put.boatcontroller.enums.ShipDirection
import pl.poznan.put.boatcontroller.enums.WaypointIndicationType
import pl.poznan.put.boatcontroller.enums.WaypointMode
import pl.poznan.put.boatcontroller.templates.FullScreenPopup
import pl.poznan.put.boatcontroller.templates.createWaypointBitmap
import pl.poznan.put.boatcontroller.ui.theme.BoatControllerTheme

class WaypointActivity : ComponentActivity() {
    private val waypointVm by viewModels<WaypointViewModel>()
    val cameraZoomAnimationTime = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        val intent = getIntent()
        val selectedMission = intent.getIntExtra("selectedMission", -1)
        waypointVm.updateMissionId(selectedMission)
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

    @OptIn(ExperimentalPermissionsApi::class)
    @Composable
    fun WaypointControlScreen(waypointVm: WaypointViewModel) {
        val map = waypointVm.mapLibreMapState.value
        val context = LocalContext.current
        val waypoints = waypointVm.waypointPositions.toList()
        val poi = waypointVm.poiPositions.toList()
        val poiToggle = waypointVm.arePoiVisible
        val phonePosition = waypointVm.phonePosition.value
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
                                waypointVm.setPhonePosition(location.latitude, location.longitude)
                                waypointVm.saveCameraPosition(location.latitude, location.longitude, 13.0)
                                Log.d("PHONE_LOCATION", "Lat: ${location.latitude}, Lon: ${location.longitude}")
                            } else {
                                waypointVm.setPhonePositionFallback()
                                Log.d("PHONE_LOCATION", "Fallback to ship")
                            }
                        }
                        .addOnFailureListener {
                            waypointVm.setPhonePositionFallback()
                            Log.d("PHONE_LOCATION", "Fallback to ship on failure")
                        }
                } catch (e: SecurityException) {
                    waypointVm.setPhonePositionFallback()
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

            val saved = waypointVm.cameraPosition.value

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
                addWaypointBitmapToStyleIfNeeded(wp.no, bitmap, style)
            }
            refreshMapFeatures(style)
        }

        LaunchedEffect(poi) {
            val mapboxMap = map ?: return@LaunchedEffect
            val style = mapboxMap.style ?: return@LaunchedEffect

            refreshMapFeatures(style)
        }

        LaunchedEffect(poiToggle) {
            val mapboxMap = map ?: return@LaunchedEffect
            val style = mapboxMap.style ?: return@LaunchedEffect

            style.getLayer("poi-layer")?.setProperties(
                visibility(if (poiToggle) Property.VISIBLE else Property.NONE)
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            MapTab(
                waypointVm = waypointVm,
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
                        waypointVm.arePoiVisible = !waypointVm.arePoiVisible
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
                        imageVector = if (!waypointVm.arePoiVisible)
                            Icons.Default.Visibility
                        else
                            Icons.Default.VisibilityOff,
                        contentDescription = "POI Visibility",
                        tint = Color.White
                    )
                }
            }
        }

        FullScreenPopup(waypointVm.openPOIDialog, { waypointVm.openPOIDialog = false }, waypointVm.poiId, waypointVm.poiPositions, { id: Int, name: String -> waypointVm.updatePoiData(id, name, waypointVm.poiPositions.firstOrNull{ it.id == id }?.description.orEmpty()) }, { id: Int, description: String -> waypointVm.updatePoiData(id, waypointVm.poiPositions.firstOrNull{ it.id == id }?.name.orEmpty(), description)}, { id -> waypointVm.deletePoi(id)
            waypointVm.openPOIDialog = false
        })
    }

    @Composable
    fun MapTab(waypointVm: WaypointViewModel) {
        val context = LocalContext.current
        val mapView = remember { MapView(context) }

        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { mapView ->
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

                mapView.getMapAsync { mapboxMap ->
                    mapboxMap.setStyle(Style.Builder().fromJson(styleJson)) { style ->
                        waypointVm.setMapReady(mapboxMap)
                        initializeMapSources(style)
                        updateBitmaps(style, context)

                        mapboxMap.uiSettings.isRotateGesturesEnabled = false
                        refreshMapFeatures(style)

                        mapboxMap.addOnMapClickListener { latLng ->
                            val screenPoint = mapboxMap.projection.toScreenLocation(latLng)
                            val features = mapboxMap.queryRenderedFeatures(screenPoint, "waypoint-layer")

                            val clickedNo = features.firstOrNull()
                                ?.getStringProperty("no")
                                ?.toIntOrNull()

                            when (waypointVm.waypointMode) {
                                WaypointMode.WAYPOINT_ADD -> {
                                    val waypointNo = waypointVm.getNextAvailableWaypointNo()

                                    waypointVm.addWaypoint(latLng.longitude, latLng.latitude)
                                    val bitmap = getOrCreateWaypointBitmap(waypointNo, context)
                                    addWaypointBitmapToStyleIfNeeded(waypointNo, bitmap, style)
                                    true
                                }

                                WaypointMode.WAYPOINT_DELETE -> {
                                    if (clickedNo != null) {
                                        waypointVm.removeWaypoint(clickedNo)
                                        true
                                    } else false
                                }

                                WaypointMode.WAYPOINT_MOVE -> {
                                    val toMoveNo = waypointVm.waypointToMoveNo
                                    if (toMoveNo == null) {
                                        if (clickedNo != null && waypointVm.getWaypointByNo(clickedNo)?.isCompleted == false) {
                                            waypointVm.waypointToMoveNo = clickedNo

                                            val bitmap = waypointVm.waypointBitmaps[clickedNo]!!
                                            val selectedBitmap = bitmap.scale(
                                                (bitmap.width * 1.2f).toInt(),
                                                (bitmap.height * 1.2f).toInt()
                                            )
                                            style.addImage("waypoint-icon-$clickedNo", selectedBitmap)
                                        }
                                    } else {
                                        waypointVm.moveWaypoint(toMoveNo, latLng.longitude, latLng.latitude)
                                        waypointVm.waypointToMoveNo = null

                                        val bitmap = waypointVm.waypointBitmaps[toMoveNo]!!
                                        style.addImage("waypoint-icon-$toMoveNo", bitmap)
                                    }
                                    true
                                }

                                else -> false
                            }
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
                                    waypointVm.poiId = waypointVm.poiPositions.indexOfFirst { it.id == id }
                                    waypointVm.openPOIDialog = true
                                }
                                true
                            } else {
                                false
                            }
                        }
                        refreshMapFeatures(style)
                    }
                    waypointVm.mapLibreMapState.value = mapboxMap
                }
            }
        )
    }

    fun initializeMapSources(style: Style) {
        val waypointSizeScaling = 0.45f

        // Waypoint connections
        style.addSource(GeoJsonSource("waypoint-connections-source", FeatureCollection.fromFeatures(emptyArray())))
        val connectionLayer = LineLayer("waypoint-connections-layer", "waypoint-connections-source")
            .withProperties(
                lineColor(Color.Black.toArgb()),
                lineWidth(2.0f),
                lineDasharray(arrayOf(2f, 2f)),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND)
            )
        style.addLayer(connectionLayer)

        // POI Waypoints
        style.addSource(GeoJsonSource("poi-source", FeatureCollection.fromFeatures(emptyArray())))
        val poiLayer = SymbolLayer("poi-layer", "poi-source")
            .withProperties(
                iconImage("poi-icon"),
                iconSize(waypointSizeScaling),
                iconAllowOverlap(true),
                visibility(if (waypointVm.arePoiVisible) Property.VISIBLE else Property.NONE)
            )
        style.addLayer(poiLayer)

        // Waypoints
        style.addSource(GeoJsonSource("waypoint-source", FeatureCollection.fromFeatures(emptyArray())))
        val waypointLayer = SymbolLayer("waypoint-layer", "waypoint-source")
            .withProperties(
                iconImage(get("icon")),
                iconSize(waypointSizeScaling),
                iconAllowOverlap(true),
            )
        style.addLayer(waypointLayer)

        // Ship
        style.addSource(GeoJsonSource("ship-source", waypointVm.getShipFeature()))
        val shipLayer = SymbolLayer("ship-layer", "ship-source")
            .withProperties(
                iconImage("ship-icon"),
                iconSize(0.07f),
                iconAllowOverlap(true)
            )
        style.addLayer(shipLayer)


        // Phone location
        style.addSource(GeoJsonSource("phone-source", FeatureCollection.fromFeatures(emptyArray())))
        val phoneLayer = SymbolLayer("phone-layer", "phone-source")
            .withProperties(
                iconImage("phone-icon"),
                iconSize(0.03f),
                iconAllowOverlap(true)
            )
        style.addLayer(phoneLayer)
    }

    fun refreshMapFeatures(style: Style) {
        style.getSourceAs<GeoJsonSource>("waypoint-connections-source")
            ?.setGeoJson(FeatureCollection.fromFeatures(waypointVm.getConnectionLinesFeature()))
        style.getSourceAs<GeoJsonSource>("poi-source")
            ?.setGeoJson(FeatureCollection.fromFeatures(waypointVm.getPoiFeature()))
        style.getSourceAs<GeoJsonSource>("waypoint-source")
            ?.setGeoJson(FeatureCollection.fromFeatures(waypointVm.getWaypointsFeature()))
        style.getSourceAs<GeoJsonSource>("ship-source")
            ?.setGeoJson(waypointVm.getShipFeature())
        style.getSourceAs<GeoJsonSource>("phone-source")
            ?.setGeoJson(waypointVm.getPhoneLocationFeature())
//        Log.d("WAYPOINT_BITMAPS_CACHE", "Aktualna zawartość waypointBitmaps:")
//        waypointVm.waypointBitmaps.forEach { (no, bitmap) ->
//            Log.d(
//                "WAYPOINT_BITMAPS_CACHE",
//                "no=$no | bitmap=${bitmap.width}x${bitmap.height}"
//            )
//        }
    }

    fun getOrCreateWaypointBitmap(no: Int, context: Context): Bitmap {
        waypointVm.waypointBitmaps[no]?.let { return it }

        val waypointDrawable = ContextCompat.getDrawable(context, R.drawable.ic_waypoint)!!
        val indicationDrawable = WaypointIndicationType.COMPASS.toWaypointIndication(context)

        val bitmap = createWaypointBitmap(
            waypointDrawable,
            indicationDrawable,
            no.toString()
        )
        waypointVm.setWaypointBitmap(no, bitmap)
        return bitmap
    }

    fun addWaypointBitmapToStyleIfNeeded(no: Int, bitmap: Bitmap, style: Style) {
        val bitmapId = "waypoint-icon-$no"
        if (style.getImage(bitmapId) == null) {
            style.addImage(bitmapId, bitmap)
        }
    }

    fun updateBitmaps(style: Style, context: Context) {
        val phoneDrawable = ContextCompat.getDrawable(context, R.drawable.steering_wheel)!!
        val phoneBitmap = createBitmap(
            phoneDrawable.intrinsicWidth,
            phoneDrawable.intrinsicHeight,
        ).apply {
            val canvas = Canvas(this)
            phoneDrawable.setBounds(0, 0, canvas.width, canvas.height)
            phoneDrawable.draw(canvas)
        }

        val shipDrawable = ContextCompat.getDrawable(context, R.drawable.ship)!!
        val shipBitmap = createBitmap(
            shipDrawable.intrinsicWidth,
            shipDrawable.intrinsicHeight
        ).apply {
            val canvas = Canvas(this)
            shipDrawable.setBounds(0, 0, canvas.width, canvas.height)
            shipDrawable.draw(canvas)
        }

        val waypointDrawable = ContextCompat.getDrawable(context, R.drawable.ic_waypoint)!!
        val indicationDrawable = WaypointIndicationType.STAR.toWaypointIndication(context)

        val poiBitmap = createWaypointBitmap(
            waypointDrawable,
            indicationDrawable,
        )

        style.addImage("phone-icon", phoneBitmap)
        style.addImage("ship-icon", shipBitmap)
        style.addImage("poi-icon", poiBitmap)

        waypointVm.waypointPositions.forEach { wp ->
            val bitmap = waypointVm.waypointBitmaps[wp.no]
                ?: getOrCreateWaypointBitmap(wp.no, context)
            addWaypointBitmapToStyleIfNeeded(wp.no, bitmap, style)
        }
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

    @Preview(showBackground = true)
    @Composable
    fun PreviewPhoneAnimationScreen() {

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
}
