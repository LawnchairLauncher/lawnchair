package dev.kdrag0n.android12ext.monet.colors

import kotlin.math.roundToInt
import dev.kdrag0n.android12ext.monet.colors.LinearSrgb.Companion.toLinearSrgb as realToLinearSrgb

data class Srgb(
    val r: Double,
    val g: Double,
    val b: Double,
) : Color {
    // Convenient constructors for quantized values
    constructor(r: Int, g: Int, b: Int) : this(r.toDouble() / 255.0, g.toDouble() / 255.0, b.toDouble() / 255.0)
    constructor(color: Int) : this(
        android.graphics.Color.red(color),
        android.graphics.Color.green(color),
        android.graphics.Color.blue(color),
    )

    override fun toLinearSrgb() = realToLinearSrgb()

    override fun toSrgb() = this

    fun quantize8(): Int {
        return android.graphics.Color.rgb(
            quantize8(r),
            quantize8(g),
            quantize8(b),
        )
    }

    companion object {
        // Clamp out-of-bounds values
        private fun quantize8(n: Double) = (n * 255.0).roundToInt().coerceIn(0..255)
    }
}
