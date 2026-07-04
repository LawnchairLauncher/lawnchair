package app.lawnchair.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import app.lawnchair.theme.color.tokens.ColorTokens
import com.android.launcher3.R

fun GradientDrawable.getCornerRadiiCompat(): FloatArray? = try {
    cornerRadii
} catch (_: NullPointerException) {
    null
}

/** Create a plain icon with [drawable] in the centre as fallback for file preview.
 *
 * For small icon search targets, use [createSmallSearchResultBitmap] instead. */
fun createFilePreviewFallbackBitmap(context: Context, @DrawableRes drawable: Int): Bitmap {
    val width = context.resources.getDimensionPixelSize(R.dimen.search_row_files_preview_width)
    val height = context.resources.getDimensionPixelSize(R.dimen.search_row_files_preview_height)
    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)

    val backgroundColor = ColorTokens.SearchResultSmallIcon.resolveColor(context)
    canvas.drawColor(backgroundColor)

    val iconDrawable = ContextCompat.getDrawable(context, drawable)
    iconDrawable?.let { d ->
        d.mutate()
        d.setTint(ColorTokens.TextColorPrimary.resolveColor(context))
        val iconSize = (height * .6f).toInt()
        val left = (width - iconSize) / 2
        val top = (height - iconSize) / 2
        d.setBounds(left, top, left + iconSize, top + iconSize)
        d.draw(canvas)
    }
    return bitmap
}

/** Create a plain icon with [drawable] in the centre as small icon for search target.
 *
 * For file preview, use [createFilePreviewFallbackBitmap] instead. */
fun createSmallSearchResultBitmap(context: Context, @DrawableRes drawable: Int): Bitmap {
    val iconSize = context.resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
    val bitmap = createBitmap(iconSize, iconSize)
    val canvas = Canvas(bitmap)

    val backgroundColor = ColorTokens.SearchResultSmallIcon.resolveColor(context)
    canvas.drawColor(backgroundColor)

    val iconDrawable = ContextCompat.getDrawable(context, drawable)
    iconDrawable?.let { drawable ->
        drawable.mutate()
        drawable.setTint(ColorTokens.TextColorPrimary.resolveColor(context))
        val inset = (iconSize * .27f).toInt()
        drawable.setBounds(inset, inset, iconSize - inset, iconSize - inset)
        drawable.draw(canvas)
    }
    return bitmap
}

/** Create a plain icon with [text] in the centre as fallback for contacts search. */
fun createTextBitmap(context: Context, text: String): Bitmap {
    val iconSize = context.resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
    val bitmap = createBitmap(iconSize, iconSize)
    val canvas = Canvas(bitmap)

    val backgroundColor = ColorTokens.SearchResultSmallIcon.resolveColor(context)
    canvas.drawColor(backgroundColor)

    // Set up paint for drawing text
    val paint = Paint().apply {
        color = ColorTokens.TextColorPrimary.resolveColor(context)
        textSize = iconSize * .4f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    // Draw text in the center of the bitmap
    val x = (bitmap.width / 2).toFloat()
    val y = (bitmap.height / 2 - (paint.descent() + paint.ascent()) / 2)
    canvas.drawText(text, x, y, paint)
    return bitmap
}
