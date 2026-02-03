package pl.poznan.put.boatcontroller.domain.components

import android.annotation.SuppressLint
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@SuppressLint("UseCompatLoadingForDrawables", "AutoboxingStateCreation")
@Composable
fun RotatePhoneAnimation(colorScheme: ColorScheme, image: ImageBitmap?) {

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
            .background(colorScheme.background)
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
                bitmap = image!!,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(100.dp),
                tint = colorScheme.onBackground,
            )
        }
    }
}