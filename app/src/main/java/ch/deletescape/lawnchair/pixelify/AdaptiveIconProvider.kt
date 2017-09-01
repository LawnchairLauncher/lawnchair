package ch.deletescape.lawnchair.pixelify

import android.content.res.Resources
import android.graphics.drawable.Drawable
import ch.deletescape.lawnchair.util.DrawableUtils
import ch.deletescape.lawnchair.util.drawableInflater
import ch.deletescape.lawnchair.util.overrideSdk

class AdaptiveIconProvider {

    companion object {

        const val TAG = "AdaptiveIconProvider"

        fun getDrawableForDensity(res: Resources, id: Int, density: Int): Drawable {
            var drawable: Drawable? = null
            res.overrideSdk(26) {
                drawable = try {
                    res.getDrawableForDensity(id, density)
                } catch (e: Resources.NotFoundException) {
                    val drawableInflater = res.drawableInflater
                    val parser = res.getXml(id)
                    DrawableUtils.inflateFromXml(drawableInflater, parser)
                }
            }
            return drawable!!
        }
    }
}