package pl.poznan.put.boatcontroller.templates.info_popup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import pl.poznan.put.boatcontroller.ui.theme.SuccessGreen
import pl.poznan.put.boatcontroller.ui.theme.WarningYellow
import pl.poznan.put.boatcontroller.ui.theme.ErrorRed

enum class InfoPopupType {
    SUCCESS,
    WARNING,
    ERROR
}

@Composable
fun InfoPopup(modifier: Modifier = Modifier) {
    val message by InfoPopupManager.message.collectAsState()
    val type by InfoPopupManager.type.collectAsState()

    if (message == null || type == null) return

    InfoPopupContent(
        message = message!!,
        type = type!!,
        isVisible = true,
        modifier = modifier
    )
}

@Composable
fun InfoPopupContent(
    message: String,
    type: InfoPopupType,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val screenWidthPx = LocalWindowInfo.current.containerSize.width
    val screenWidthDp = with(density) { screenWidthPx.toDp() }
    val maxWidth = screenWidthDp * 0.5f
    val maxHeight = 125.dp

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
        modifier = modifier.zIndex(1000f)
    ) {
        Box(
            modifier = Modifier
                .width(maxWidth)
                .wrapContentHeight()
                .heightIn(max = maxHeight)
                .clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .blur(16.dp)
                    .background(Color.Gray.copy(alpha = 0.9f))
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = when (type) {
                        InfoPopupType.SUCCESS -> Icons.Default.CheckCircle
                        InfoPopupType.WARNING -> Icons.Default.Warning
                        InfoPopupType.ERROR -> Icons.Default.Error
                    },
                    contentDescription = null,
                    tint = when (type) {
                        InfoPopupType.SUCCESS -> SuccessGreen
                        InfoPopupType.WARNING -> WarningYellow
                        InfoPopupType.ERROR -> ErrorRed
                    },
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = message,
                    color = Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}