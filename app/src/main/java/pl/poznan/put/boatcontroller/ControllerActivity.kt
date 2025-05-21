package pl.poznan.put.boatcontroller

import android.Manifest
import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import kotlin.getValue
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.android.style.layers.SymbolLayer

class ControllerActivity: ComponentActivity() {

    private val viewModel by viewModels<ControllerViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val colorScheme = MaterialTheme.colorScheme
            if (!isLandscape()) {
                RotatePhoneAnimation(colorScheme)
            } else {
                EngineControlScreen(viewModel)
            }
        }
    }

    @Composable
    fun isLandscape(): Boolean {
        val configuration = LocalConfiguration.current
        return configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    // ===========================  Rotation screen ================================================
    @SuppressLint("UseCompatLoadingForDrawables", "AutoboxingStateCreation")
    @Composable
    fun RotatePhoneAnimation(colorScheme: ColorScheme) {

        var sweepAngle by remember { mutableFloatStateOf(0f) }

        var phoneRotation by remember { mutableFloatStateOf(0f) }

        var startPhoneRotation by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            while (true) {
                sweepAngle = 0f
                startPhoneRotation = false

                animate(
                    initialValue = 0f,
                    targetValue = 250f,
                    animationSpec = tween(durationMillis = 4000, easing = LinearOutSlowInEasing)
                ) { value, _ ->
                    sweepAngle = value
                }

                startPhoneRotation = true

                delay(54000)
            }
        }

        val phoneRotationAnim by animateFloatAsState(
            targetValue = if (startPhoneRotation) 90f else 0f,
            animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            label = "PhoneRotation"
        )

        phoneRotation = phoneRotationAnim

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(200.dp)) {
                val strokeWidth = 8.dp.toPx()
                val radius = size.minDimension / 2 - strokeWidth / 2
                val center = Offset(size.width / 2, size.height / 2)

                drawArc(
                    color = colorScheme.onBackground,
                    startAngle = 10f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                if (sweepAngle > 5f) {
                    val endAngle = Math.toRadians((20 + sweepAngle).toDouble())
                    val arrowLength = 20.dp.toPx()
                    val arrowWidth = 15.dp.toPx()

                    val endX = center.x + radius * cos(endAngle).toFloat()
                    val endY = center.y + radius * sin(endAngle).toFloat()
                    val endPoint = Offset(endX, endY)

                    val tangentAngle = endAngle + Math.PI / 2

                    val p1 = endPoint
                    val p2 = Offset(
                        (endX - arrowLength * cos(tangentAngle) - arrowWidth * sin(tangentAngle)).toFloat(),
                        (endY - arrowLength * sin(tangentAngle) + arrowWidth * cos(tangentAngle)).toFloat()
                    )
                    val p3 = Offset(
                        (endX - arrowLength * cos(tangentAngle) + arrowWidth * sin(tangentAngle)).toFloat(),
                        (endY - arrowLength * sin(tangentAngle) - arrowWidth * cos(tangentAngle)).toFloat()
                    )

                    drawPath(
                        path = Path().apply {
                            moveTo(p1.x, p1.y)
                            lineTo(p2.x, p2.y)
                            lineTo(p3.x, p3.y)
                            close()
                        },
                        color = colorScheme.onBackground
                    )
                }
            }


            Box(
                modifier = Modifier
                    .size(100.dp)
                    .graphicsLayer {
                        rotationZ = phoneRotation
                    }
            ) {
                Icon(
                    bitmap = resources.getDrawable(R.drawable.phone_android_2, null).toBitmap().asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(100.dp)
                )
            }
        }
    }

    // ===========================  Controller screen ================================================

    @Composable
    fun EngineControlScreen(viewModel: ControllerViewModel) {
        val tabs = listOf("Mapa", "Sonar", "Czujniki", "Kamera")

        Row(modifier = Modifier.fillMaxSize()) {
            PowerSlider(
                value = viewModel.leftEnginePower,
                onValueChange = { viewModel.leftEnginePower = it },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )

            Column(
                modifier = Modifier
                    .weight(3f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TabRow(selectedTabIndex = viewModel.selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = viewModel.selectedTab == index,
                            onClick = { viewModel.selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }

                when (viewModel.selectedTab) {
                    0 -> MapTab(viewModel.shipPosition)
                    1 -> SonarTab(viewModel.sonarData)
                    2 -> SensorsTab(viewModel.sensorsData)
                    3 -> CameraTab(viewModel.cameraFeedUrl)
                }
            }

            PowerSlider(
                value = viewModel.rightEnginePower,
                onValueChange = { viewModel.rightEnginePower = it },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                rightSide = true
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun PowerSlider(
        value: Int,
        onValueChange: (Int) -> Unit,
        modifier: Modifier = Modifier,
        rightSide: Boolean = false,
        minValue: Int = -80,
        maxValue: Int = 80
    ) {
        val range = maxValue - minValue
        val steps = range / 10
        val sliderHeight = 400.dp
        val trackWidth = 40.dp
        val thumbHeight = 30.dp

        Box(
            modifier = modifier
                .width(120.dp)
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
                            segValue < -70 -> Color.White
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
                                size.width - 25.dp.toPx(),
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
                            x = if (rightSide) -120 else 120,
                            y = with(density) {
                                (-sliderOffset * (((sliderHeight.toPx()) / 2) - 100)).toInt()
                            }
                        )
                    }
                    .width(trackWidth)
                    .height(thumbHeight),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .offset(x = if (rightSide) 40.dp else (-40).dp)
                        .width(50.dp)
                        .height(18.dp)
                        .background(Color.Black, RoundedCornerShape(4.dp))
                        .border(1.dp, Color.White, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {}
                Box(
                    modifier = Modifier
                        .width(50.dp)
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
                    .scale(2.2f, 1f)
                    .alpha(0f),
            )
        }
    }


    @OptIn(ExperimentalPermissionsApi::class)
    @SuppressLint("MissingPermission", "InflateParams", "UseKtx")
    @Composable
    fun MapTab(shipPosition: DoubleArray, modifier: Modifier = Modifier) {
        val context = LocalContext.current
        var phonePosition by remember { mutableStateOf<DoubleArray?>(null) }
        val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

        val locationManager = context.getSystemService(LOCATION_SERVICE) as LocationManager

        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

        var mapboxMapRef by remember { mutableStateOf<MapLibreMap?>(null) }
        var phoneSourceRef by remember { mutableStateOf<GeoJsonSource?>(null) }

        LaunchedEffect(Unit) {
            if (!locationPermissionState.status.isGranted) {
                locationPermissionState.launchPermissionRequest()
            } else {
                val provider = if (isGpsEnabled) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER

                locationManager.requestSingleUpdate(provider, object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        phonePosition = doubleArrayOf(location.latitude, location.longitude)
                        Log.d("Location", "Lat: ${location.latitude}, Lon: ${location.longitude}")
                    }

                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }, null)
            }
        }

        LaunchedEffect(phonePosition) {
            val phoneSource = phoneSourceRef ?: return@LaunchedEffect
            phonePosition?.let {
                val phoneFeature = Feature.fromGeometry(Point.fromLngLat(it[1], it[0]))
                phoneFeature.addStringProperty("title", "Phone")
                val featureCollection = FeatureCollection.fromFeatures(listOf(phoneFeature))
                phoneSource.setGeoJson(featureCollection)
            }
        }

        AndroidView(
            factory = {
                MapLibre.getInstance(context)
                MapView(context).apply {
                    getMapAsync { mapboxMap ->

                        mapboxMapRef = mapboxMap

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
                                      "coordinates": [${shipPosition[1]}, ${shipPosition[0]}]
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
                        val shipBitmap = Bitmap.createBitmap(
                            shipDrawable.intrinsicWidth,
                            shipDrawable.intrinsicHeight,
                            Bitmap.Config.ARGB_8888
                        ).apply {
                            val canvas = android.graphics.Canvas(this)
                            shipDrawable.setBounds(0, 0, canvas.width, canvas.height)
                            shipDrawable.draw(canvas)
                        }

                        val phoneDrawable = ContextCompat.getDrawable(context, R.drawable.steering_wheel)!!
                        val phoneBitmap = Bitmap.createBitmap(
                            phoneDrawable.intrinsicWidth,
                            phoneDrawable.intrinsicHeight,
                            Bitmap.Config.ARGB_8888
                        ).apply {
                            val canvas = android.graphics.Canvas(this)
                            phoneDrawable.setBounds(0, 0, canvas.width, canvas.height)
                            phoneDrawable.draw(canvas)
                        }

                        mapboxMap.setStyle(Style.Builder().fromJson(styleJson)) { style ->
                            style.addImage("ship-icon", shipBitmap)
                            style.addImage("phone-icon", phoneBitmap)

                            val phoneSource = GeoJsonSource("phone-source", FeatureCollection.fromFeatures(emptyArray()))
                            phoneSourceRef = phoneSource
                            style.addSource(phoneSource)

                            val phoneLayer = SymbolLayer("phone-layer", "phone-source").withProperties(
                                iconImage("phone-icon"),
                                iconSize(0.03f),
                                iconAllowOverlap(true)
                            )
                            style.addLayer(phoneLayer)

                            val position = CameraPosition.Builder()
                                .target(LatLng(shipPosition[0], shipPosition[1]))
                                .zoom(13.0)
                                .build()
                            mapboxMap.moveCamera(CameraUpdateFactory.newCameraPosition(position))
                            mapboxMap.uiSettings.isRotateGesturesEnabled = false
                        }
                    }
                }
            },
            modifier = modifier.fillMaxSize()
        )
    }


    @Composable
    fun SonarTab(data: String) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = data, fontSize = 24.sp)
        }
    }

    @Composable
    fun SensorsTab(data: String) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = data, fontSize = 20.sp)
        }
    }

    @Composable
    fun CameraTab(feedUrl: String) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = feedUrl,
                contentDescription = "Podgląd z kamery",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }



    @Preview(showBackground = true)
    @Composable
    fun PreviewPhoneAnimationScreen() {

    }
}
