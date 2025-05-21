package pl.poznan.put.boatcontroller

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import pl.poznan.put.boatcontroller.templates.RotatePhoneTutorialAnimation

class WaypointActivity: ComponentActivity() {
    private val waypointVm by viewModels<WaypointViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val colorScheme = MaterialTheme.colorScheme

            val image = remember {
                ResourcesCompat.getDrawable(context.resources, R.drawable.phone_android_2, context.theme)
                    ?.toBitmap()
                    ?.asImageBitmap()
            }
            if (!isLandscape()) {
                RotatePhoneTutorialAnimation(colorScheme, image)
            }
            else {
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
        Row(modifier = Modifier.fillMaxSize()) {
            MapTab(waypointVm.shipPosition)
        }
    }

    @SuppressLint("InflateParams")
    @Composable
    fun MapTab(shipPosition: DoubleArray, modifier: Modifier = Modifier) {
        val context = LocalContext.current
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
                                      "coordinates": [${shipPosition[0]}, ${shipPosition[1]}]
                                    },
                                    "properties": {
                                      "title": "Poznań"
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
                                "icon-image": "marker-icon",
                                "icon-size": 1.0,
                                "icon-allow-overlap": true
                              }
                            }
                          ]
                        }
                    """.trimIndent()
                        mapboxMap.setStyle(Style.Builder().fromJson(styleJson))
                    }
                }
            },
            modifier = modifier.fillMaxSize()
        )
    }

    @Preview(showBackground = true)
    @Composable
    fun PreviewPhoneAnimationScreen() {

    }
}