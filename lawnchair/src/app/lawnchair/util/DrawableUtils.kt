package app.lawnchair.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import app.lawnchair.theme.color.generateColor

fun GradientDrawable.getCornerRadiiCompat(): FloatArray? = try {
    cornerRadii
} catch (_: NullPointerException) {
    null
}

fun createTextBitmap(context: Context, text: String): Bitmap {
    val iconSize = context.resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
    val bitmap = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Set up paint for drawing text
    val paint = Paint().apply {
        color = generateColor(context)
        textSize = context.resources.getDimensionPixelSize(android.R.dimen.app_icon_size).toFloat()
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    // Draw text in the center of the bitmap
    val x = (bitmap.width / 2).toFloat()
    val y = (bitmap.height / 2 - (paint.descent() + paint.ascent()) / 2)
    canvas.drawText(text, x, y, paint)
    return bitmap
}
