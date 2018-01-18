package ch.deletescape.lawnchair.snow

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Paint.Style

import ch.deletescape.lawnchair.util.Randomizer

import java.lang.Math.cos
import java.lang.Math.sin
import java.lang.Math.toRadians

/**
 * Copyright (C) 2016 JetRadar, licensed under Apache License 2.0
 * https://github.com/JetradarMobile/android-snowfall/
 */
internal class Snowflake(val params: Params) {
    private var size: Int = 0
    private var alpha: Int = 255
    private var bitmap: Bitmap? = null
    private var speedX: Double = 0.0
    private var speedY: Double = 0.0
    private var positionX: Double = 0.0
    private var positionY: Double = 0.0

    private val paint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 255, 255)
            style = Style.FILL
        }
    }

    private val randomizer by lazy { Randomizer() }

    init {
        reset()
    }

    internal fun reset(positionY: Double? = null) {
        size = randomizer.randomInt(params.sizeMinInPx, params.sizeMaxInPx, gaussian = true)
        if (params.image != null) {
            bitmap = Bitmap.createScaledBitmap(params.image, size, size, false)
        }

        val speed = ((size - params.sizeMinInPx).toFloat() / (params.sizeMaxInPx - params.sizeMinInPx) * (params.speedMax - params.speedMin) + params.speedMin)
        val angle = toRadians(randomizer.randomDouble(params.angleMax) * randomizer.randomSignum())
        speedX = speed * sin(angle)
        speedY = speed * cos(angle)

        alpha = randomizer.randomInt(params.alphaMin, params.alphaMax)
        paint.alpha = alpha

        positionX = randomizer.randomDouble(params.parentWidth)
        if (positionY != null) {
            this.positionY = positionY
        } else {
            this.positionY = randomizer.randomDouble(params.parentHeight)
            if (!params.alreadyFalling) {
                this.positionY = this.positionY - params.parentHeight - size
            }
        }
    }

    fun update() {
        positionX += speedX
        positionY += speedY

        if (positionY > params.parentHeight) {
            reset(positionY = -size.toDouble())
        }

        if (params.fadingEnabled) {
            paint.alpha = (alpha * ((params.parentHeight - positionY).toFloat() / params.parentHeight)).toInt()
        }
    }

    fun draw(canvas: Canvas) {
        if (bitmap != null) {
            canvas.drawBitmap(bitmap, positionX.toFloat(), positionY.toFloat(), paint)
        } else {
            canvas.drawCircle(positionX.toFloat(), positionY.toFloat(), size.toFloat(), paint)
        }
    }

    data class Params(
            val parentWidth: Int,
            val parentHeight: Int,
            val image: Bitmap?,
            val alphaMin: Int,
            val alphaMax: Int,
            val angleMax: Int,
            val sizeMinInPx: Int,
            val sizeMaxInPx: Int,
            val speedMin: Int,
            val speedMax: Int,
            val fadingEnabled: Boolean,
            val alreadyFalling: Boolean)
}