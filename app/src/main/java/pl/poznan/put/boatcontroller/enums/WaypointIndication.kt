package pl.poznan.put.boatcontroller.enums

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import pl.poznan.put.boatcontroller.R

enum class WaypointIndicationType(@DrawableRes val resId: Int) {
    STAR(R.drawable.ic_indication_star),
    COMPASS(R.drawable.ic_indication_compass);

    fun toWaypointIndication(context: Context): WaypointIndication {
        val drawable: Drawable = ContextCompat.getDrawable(context, resId)!!
        return WaypointIndication(drawable, this)
    }
}

data class WaypointIndication(
    val drawable: Drawable,
    val type: WaypointIndicationType
)