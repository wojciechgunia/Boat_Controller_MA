package pl.poznan.put.boatcontroller

import android.content.res.Configuration
import android.os.Bundle
import android.util.Base64
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import pl.poznan.put.boatcontroller.templates.RotatePhoneTutorialAnimation
import kotlin.getValue

class VRCamActivity : ComponentActivity() {
    private val vrcamVm by viewModels<VRCamViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
                VRCamControlScreen(vrcamVm)
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
fun VRCamControlScreen(
    vrcamVm: VRCamViewModel,
) {
    val videoUrl = vrcamVm.videoUrl.collectAsState().value

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        CameraStreamView(
            streamUrl = videoUrl,
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
        )
    }
}

@Composable
fun CameraStreamView(
    streamUrl: String,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.loadsImagesAutomatically = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                setBackgroundColor(android.graphics.Color.BLACK)
                webViewClient = WebViewClient()

                loadUrl(streamUrl)
            }
        },
        modifier = modifier
    )
}

