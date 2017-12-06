package ch.deletescape.lawnchair.snow

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.VectorDrawable
import android.os.Build

/**
 * Copyright (C) 2016 JetRadar, licensed under Apache License 2.0
 * https://github.com/JetradarMobile/android-snowfall/
 */

@SuppressLint("NewApi")
internal fun Drawable.toBitmap(): Bitmap {
    return when (this) {
        is BitmapDrawable -> bitmap
        is VectorDrawable -> toBitmap()
        else -> throw IllegalArgumentException("Unsupported drawable type")
    }
}

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
internal fun VectorDrawable.toBitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}