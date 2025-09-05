package pl.poznan.put.boatcontroller.enums

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import pl.poznan.put.boatcontroller.R

enum class FlagIndicationType(@DrawableRes val resId: Int) {
    STAR(R.drawable.ic_indication_star),
    COMPASS(R.drawable.ic_indication_compass);

    fun toFlagIndication(context: Context): FlagIndication {
        val drawable: Drawable = ContextCompat.getDrawable(context, resId)!!
        return FlagIndication(drawable, this)
    }
}

data class FlagIndication(
    val drawable: Drawable,
    val type: FlagIndicationType
)