package pl.poznan.put.boatcontroller

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
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
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
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

import org.maplibre.geojson.LineString
import pl.poznan.put.boatcontroller.enums.FlagMode

class WaypointActivity : ComponentActivity() {
    private val waypointVm by viewModels<WaypointViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val colorScheme = MaterialTheme.colorScheme

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
        Box(modifier = Modifier.fillMaxSize()) {
            MapTab(waypointVm)
            SlidingToolbar(waypointVm)
        }
    }

    @SuppressLint("InflateParams")
    @Composable
    fun MapTab(waypointVm: WaypointViewModel, modifier: Modifier = Modifier) {
        val context = LocalContext.current
        val waypointScaling = 0.4f

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
                                      "coordinates": [${waypointVm.shipPosition[1]}, ${waypointVm.shipPosition[0]}]
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
                            },
                            {
                              "id": "point-layer",
                              "type": "symbol",
                              "source": "point-source",
                              "layout": {
                                "icon-image": "ship-icon",
                                "icon-size": 0.04,
                                "icon-allow-overlap": true
                              }
                            }
                          ]
                        }
                    """.trimIndent()
                        val shipDrawable = ContextCompat.getDrawable(context, R.drawable.ship)!!
                        val shipBitmap = createBitmap(
                            shipDrawable.intrinsicWidth,
                            shipDrawable.intrinsicHeight
                        ).apply {
                            val canvas = android.graphics.Canvas(this)
                            shipDrawable.setBounds(0, 0, canvas.width, canvas.height)
                            shipDrawable.draw(canvas)
                        }
                        mapboxMap.setStyle(Style.Builder().fromJson(styleJson)) { style ->
                            style.addImage("ship-icon", shipBitmap)

                            val flagsSource = GeoJsonSource(
                                "flags-source",
                                FeatureCollection.fromFeatures(emptyArray())
                            )
                            style.addSource(flagsSource)

                            val flagLayer = SymbolLayer("flags-layer", "flags-source")
                                .withProperties(
                                    iconImage(get("icon")),
                                    iconSize(waypointScaling),
                                    iconAllowOverlap(true),
                                )

                            style.addLayer(flagLayer)

                            val linesSource = GeoJsonSource(
                                "lines-source",
                                FeatureCollection.fromFeatures(emptyArray()))
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

                            val position = CameraPosition.Builder()
                                .target(
                                    LatLng(
                                        waypointVm.shipPosition[0],
                                        waypointVm.shipPosition[1]
                                    )
                                )
                                .zoom(13.0)
                                .build()
                            mapboxMap.moveCamera(CameraUpdateFactory.newCameraPosition(position))
                            mapboxMap.uiSettings.isRotateGesturesEnabled = false

                            mapboxMap.addOnMapClickListener { latLng ->
                                val mode = waypointVm.flagMode

                                when (mode) {
                                    FlagMode.ADD -> {
                                        val newWaypoint = waypointVm.addWaypoint(
                                            latLng.longitude,
                                            latLng.latitude
                                        )

                                        val combinedBitmap = createFlagWithCircleTextBitmap(
                                            context,
                                            newWaypoint.id.toString()
                                        )
                                        style.addImage(
                                            "flag-icon-${newWaypoint.id}",
                                            combinedBitmap
                                        )

                                        val features = waypointVm.waypointPositions.map {
                                            Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat))
                                                .apply {
                                                    addStringProperty("id", it.id.toString())
                                                    addStringProperty("icon", "flag-icon-${it.id}")
                                                }
                                        }
                                        flagsSource.setGeoJson(
                                            FeatureCollection.fromFeatures(
                                                features
                                            )
                                        )
                                        updateConnectionLines(waypointVm, linesSource)
                                        waypointVm.setFlagEditMode(null)
                                        true
                                    }

                                    FlagMode.REMOVE -> {
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
                                                waypointVm.removeWaypoint(id)
                                                waypointVm.reindexWaypoints()

                                                waypointVm.waypointPositions.forEach {
                                                    val bitmap = createFlagWithCircleTextBitmap(context, it.id.toString())
                                                    style.addImage("flag-icon-${it.id}", bitmap)
                                                }

                                                val updatedFeatures =
                                                    waypointVm.waypointPositions.map {
                                                        Feature.fromGeometry(
                                                            Point.fromLngLat(
                                                                it.lon,
                                                                it.lat
                                                            )
                                                        ).apply {
                                                            addStringProperty(
                                                                "id",
                                                                it.id.toString()
                                                            )
                                                            addStringProperty(
                                                                "icon",
                                                                "flag-icon-${it.id}"
                                                            )
                                                        }
                                                    }

                                                flagsSource.setGeoJson(
                                                    FeatureCollection.fromFeatures(
                                                        updatedFeatures
                                                    )
                                                )
                                            }
                                            updateConnectionLines(waypointVm, linesSource)
                                            waypointVm.setFlagEditMode(null)
                                            true
                                        } else {
                                            false
                                        }
                                    }

                                    FlagMode.MOVE -> {
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
                                                    waypointVm.flagToMoveId = id
                                                    waypointVm.removeWaypoint(id)

                                                    val updatedFeatures =
                                                        waypointVm.waypointPositions.map {
                                                            Feature.fromGeometry(
                                                                Point.fromLngLat(
                                                                    it.lon,
                                                                    it.lat
                                                                )
                                                            ).apply {
                                                                addStringProperty(
                                                                    "id",
                                                                    it.id.toString()
                                                                )
                                                                addStringProperty(
                                                                    "icon",
                                                                    "flag-icon-${it.id}"
                                                                )
                                                            }
                                                        }
                                                    flagsSource.setGeoJson(
                                                        FeatureCollection.fromFeatures(
                                                            updatedFeatures
                                                        )
                                                    )
                                                    updateConnectionLines(waypointVm, linesSource)
                                                }
                                            }
                                            true
                                        } else {
                                            val id = waypointVm.flagToMoveId!!
                                            waypointVm.flagToMoveId = null

                                            waypointVm.addWaypoint(
                                                id,
                                                latLng.longitude,
                                                latLng.latitude
                                            )

                                            val updatedFeatures = waypointVm.waypointPositions.map {
                                                Feature.fromGeometry(
                                                    Point.fromLngLat(
                                                        it.lon,
                                                        it.lat
                                                    )
                                                ).apply {
                                                    addStringProperty("id", it.id.toString())
                                                    addStringProperty("icon", "flag-icon-${it.id}")
                                                }
                                            }
                                            flagsSource.setGeoJson(
                                                FeatureCollection.fromFeatures(
                                                    updatedFeatures
                                                )
                                            )
                                            updateConnectionLines(waypointVm, linesSource)
                                            waypointVm.setFlagEditMode(null)
                                            true
                                        }
                                    }

                                    else -> false
                                }
                            }
                        }
                    }
                }
            },
            modifier = modifier.fillMaxSize()
        )
    }

    fun createFlagWithCircleTextBitmap(context: Context, text: String): Bitmap {
        val flagDrawable = ContextCompat.getDrawable(context, R.drawable.ic_flag)!!

        val cx = flagDrawable.intrinsicWidth + 0f
        val cy = flagDrawable.intrinsicHeight + 0f
        val radius = 60f

        val minWidth = (cx + radius).toInt()
        val minHeight = (cy + radius).toInt()

        val width = maxOf(flagDrawable.intrinsicWidth, minWidth)
        val height = maxOf(flagDrawable.intrinsicHeight, minHeight)

        val paintCircle = android.graphics.Paint().apply {
            color = android.graphics.Color.RED
            isAntiAlias = true
        }

        val paintText = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 45f
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
            isAntiAlias = true
        }
        val textY = cy - (paintText.descent() + paintText.ascent()) / 2

        val flagBitmap = createBitmap(width, height).apply {
            val canvas = android.graphics.Canvas(this)
            flagDrawable.setBounds(0, 0, flagDrawable.intrinsicWidth, flagDrawable.intrinsicHeight)
            flagDrawable.draw(canvas)
            canvas.drawCircle(cx, cy, radius, paintCircle)
            canvas.drawText(text, cx, textY, paintText)
        }
        return flagBitmap
    }

    @Composable
    fun SlidingToolbar(waypointVm: WaypointViewModel) {
        val density = LocalDensity.current

        val screenWidth = LocalWindowInfo.current.containerSize.width
        val screenWidthDp = with(density) { screenWidth.toDp() }
        val screenHeight = LocalWindowInfo.current.containerSize.height
        val screenHeightDp = with(density) { screenHeight.toDp() }

        val toolbarWidth = screenWidthDp * 0.2f
        val arrowBoxWidth = screenWidthDp * 0.05f
        val arrowBoxHeight = screenHeightDp * 0.2f
        val arrowBoxOffset =
            if (waypointVm.isExpanded) toolbarWidth - (arrowBoxWidth / 2) else toolbarWidth - (arrowBoxWidth / 4)

        val animatedOffset by animateDpAsState(
            targetValue = if (waypointVm.isExpanded) 0.dp else -toolbarWidth,
            animationSpec = tween(300),
            label = "ToolbarOffset"
        )

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .offset(x = animatedOffset)
                .width(toolbarWidth + arrowBoxWidth * 0.8f)
                .zIndex(1f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(toolbarWidth)
                    .background(Color.Black)
            ) {
                if (waypointVm.isExpanded) {
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
                        Row(
                            modifier = Modifier
                                .padding(8.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    waypointVm.setFlagEditMode(FlagMode.ADD)
                                    waypointVm.closeToolbar()
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                            }
                            IconButton(
                                onClick = {
                                    waypointVm.setFlagEditMode(FlagMode.REMOVE)
                                    waypointVm.closeToolbar()
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                            IconButton(
                                onClick = {
                                    waypointVm.setFlagEditMode(FlagMode.MOVE)
                                    waypointVm.closeToolbar()
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White)
                            }
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
                    .clickable { waypointVm.isExpanded = !waypointVm.isExpanded },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (waypointVm.isExpanded) Icons.AutoMirrored.Default.KeyboardArrowLeft else Icons.AutoMirrored.Default.KeyboardArrowRight,
                    contentDescription = "Toggle",
                    tint = Color.White
                )
            }
        }
    }

    fun updateConnectionLines(
        waypointVm: WaypointViewModel,
        linesSource: GeoJsonSource
    ) {
        val lines = mutableListOf<Feature>()
        val waypoints = waypointVm.waypointPositions.sortedBy { it.id }

        for (i in 0 until waypoints.size - 1) {
            val start = waypoints[i]
            val end = waypoints[i + 1]
            val line = LineString.fromLngLats(
                listOf(
                    Point.fromLngLat(start.lon, start.lat),
                    Point.fromLngLat(end.lon, end.lat)
                )
            )
            lines.add(Feature.fromGeometry(line))
        }

        linesSource.setGeoJson(FeatureCollection.fromFeatures(lines))
    }

    @Preview(showBackground = true)
    @Composable
    fun PreviewPhoneAnimationScreen() {

    }
}