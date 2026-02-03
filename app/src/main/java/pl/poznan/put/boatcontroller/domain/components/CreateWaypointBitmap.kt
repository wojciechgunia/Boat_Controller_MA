package pl.poznan.put.boatcontroller.domain.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import pl.poznan.put.boatcontroller.domain.models.WaypointIndication

fun createWaypointBitmap(
    waypointDrawable: Drawable,
    indicationDrawable: WaypointIndication?,
    waypointNumber: String = ""
): Bitmap {
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