/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.deletescape.lawnchair.iconpack

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import ch.deletescape.lawnchair.toBitmap
import com.android.launcher3.FastBitmapDrawable
import com.android.launcher3.LauncherAppState
import com.android.launcher3.Utilities
import com.android.launcher3.graphics.FixedScaleDrawable

class IconMask {
    var hasMask: Boolean = false
    var onlyMaskLegacy: Boolean = false
    var scale: Float = 1.0f
        get() = if (Utilities.ATLEAST_OREO && iconBack?.drawable is AdaptiveIconDrawable) {
            field - (1f - FixedScaleDrawable.LEGACY_ICON_SCALE)
        } else field
    var iconBack: IconPack.Entry? = null
    var iconMask: IconPack.Entry? = null
    var iconUpon: IconPack.Entry? = null
    val matrix = Matrix()
    val paint = Paint()

    fun getIcon(context: Context, baseIcon: Drawable): Drawable {
        var adaptiveBackground: Drawable? = null
        // Some random magic to get an acceptable resolution
        var size = (LauncherAppState.getIDP(context).iconBitmapSize * (3 - scale)).toInt()
        if (Utilities.ATLEAST_OREO && iconBack?.drawable is AdaptiveIconDrawable) {
            size += (size * AdaptiveIconDrawable.getExtraInsetFraction()).toInt()
        }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (iconBack != null) {
            val drawable = iconBack!!.drawable
            if (Utilities.ATLEAST_OREO && drawable is AdaptiveIconDrawable) {
                adaptiveBackground = drawable.background
            } else {
                val b = drawable.toBitmap()
                matrix.setScale(size.toFloat() / b.width, size.toFloat() / b.height)
                canvas.drawBitmap(b, matrix, paint)
                matrix.reset()
            }
        }
        var bb = baseIcon.toBitmap()
        if (!bb.isMutable) bb = bb.copy(bb.config, true)
        if (iconMask != null) {
            val tmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val saveCount = canvas.save()
            canvas.setBitmap(tmp)
            matrix.setScale((size * scale) / bb.width, (size * scale) / bb.height)
            matrix.postTranslate((size / 2) * (1 - scale), (size / 2) * (1 - scale))
            canvas.drawBitmap(bb, matrix, paint)
            matrix.reset()
            val b = iconMask!!.drawable.toBitmap()
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            matrix.setScale(size.toFloat() / b.width, size.toFloat() / b.height)
            canvas.drawBitmap(b, matrix, paint)
            matrix.reset()
            paint.reset()
            canvas.restoreToCount(saveCount)
            canvas.setBitmap(bitmap)
            canvas.drawBitmap(tmp, matrix, paint)
        } else {
            matrix.setScale((size * scale) / bb.width, (size * scale) / bb.height)
            matrix.postTranslate((size / 2) * (1 - scale), (size / 2) * (1 - scale))
            canvas.drawBitmap(bb, matrix, paint)
            matrix.reset()
        }
        if (iconUpon != null) {
            val b = iconUpon!!.drawable.toBitmap()
            matrix.setScale(size.toFloat() / b.width, size.toFloat() / b.height)
            canvas.drawBitmap(b, matrix, paint)
            matrix.reset()
        }
        if (adaptiveBackground != null) {
            if (onlyMaskLegacy && baseIcon is AdaptiveIconDrawable) {
                return baseIcon
            }
            return AdaptiveIconDrawable(adaptiveBackground, FastBitmapDrawable(bitmap))
        }
        return FastBitmapDrawable(bitmap)
    }
}