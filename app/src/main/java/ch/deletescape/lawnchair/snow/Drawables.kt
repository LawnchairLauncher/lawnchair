package ch.deletescape.lawnchair.snow

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.VectorDrawable
import android.os.Build

import ch.deletescape.lawnchair.util.Randomizer

/**
 * Copyright (C) 2016 JetRadar, licensed under Apache License 2.0
 * https://github.com/JetradarMobile/android-snowfall/
 */

@SuppressLint("NewApi")
internal fun Drawable.toBitmap(rotation: Float): Bitmap {
    return when (this) {
        is BitmapDrawable -> bitmap
        is VectorDrawable -> toBitmap(rotation)
        else -> throw IllegalArgumentException("Unsupported drawable type")
    }
}

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
internal fun VectorDrawable.toBitmap(rotation: Float): Bitmap {
    val bitmap = Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    if (rotation != 0f) {
        canvas.rotate(rotation, intrinsicWidth / 2f, intrinsicHeight / 2f);
    }

    setBounds(0, 0, canvas.width, canvas.height)
    setTint(Color.WHITE)
    draw(canvas)
    return bitmap
}