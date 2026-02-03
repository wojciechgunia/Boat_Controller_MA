package pl.poznan.put.boatcontroller.domain.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import pl.poznan.put.boatcontroller.ui.theme.LightGreen
import pl.poznan.put.boatcontroller.ui.theme.ErrorRed
import pl.poznan.put.boatcontroller.ui.theme.WarningYellow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun BatteryIndicator(
    level: Int,
    isCharging: Boolean,
    modifier: Modifier = Modifier,
    showPercentage: Boolean = true
) {
    val color = when {
        level < 11 -> ErrorRed
        level < 41 -> WarningYellow
        else -> LightGreen
    }

    val infiniteTransition = rememberInfiniteTransition(label = "")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = ""
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .height(20.dp)
                .width(38.dp)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Transparent, RoundedCornerShape(5.dp))
                    .border(2.dp, Color.Gray, RoundedCornerShape(5.dp))
            )

            Box(
                modifier = Modifier
                    .padding(2.dp)
                    .fillMaxHeight()
                    .fillMaxWidth(level.coerceIn(0, 100) / 100f)
                    .background(color, RoundedCornerShape(5.dp))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (-4).dp)
                    .width(6.dp)
                    .height(9.dp)
                    .background(Color.Gray, RoundedCornerShape(1.dp))
            )

            if (isCharging) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = "Ładowanie",
                    tint = Color.Gray,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(14.dp)
                )
            }
        }

        if (showPercentage) {
            Text(
                text = "$level%",
                modifier = Modifier.padding(start = 6.dp),
                color = Color.DarkGray,
                fontWeight = FontWeight.Normal
            )
        }

        if (level in 11..20) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Niski poziom baterii",
                tint = WarningYellow,
                modifier = Modifier.size(24.dp),
            )
        } else if (level <= 10) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Bardzo niski poziom baterii",
                tint = ErrorRed.copy(alpha = alphaAnim),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}