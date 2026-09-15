/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.monet

import android.annotation.ColorInt
import android.graphics.Color
import com.androidinternal.graphics.ColorUtils
import com.androidinternal.graphics.cam.Cam
import com.androidinternal.graphics.cam.CamUtils
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

const val TAG = "ColorScheme"
const val ACCENT1_CHROMA = 48.0f
const val GOOGLE_BLUE = 0xFF1b6ef3.toInt()
const val MIN_CHROMA = 5

internal interface Hue {
    fun get(sourceColor: Cam): Double

    /**
     * Given a hue, and a mapping of hues to hue rotations, find which hues in the mapping the hue
     * fall betweens, and use the hue rotation of the lower hue.
     *
     * @param sourceHue hue of source color
     * @param hueAndRotations list of pairs, where the first item in a pair is a hue, and the second
     *   item in the pair is a hue rotation that should be applied
     */
    fun getHueRotation(sourceHue: Float, hueAndRotations: List<Pair<Int, Int>>): Double {
        val sanitizedSourceHue = (if (sourceHue < 0 || sourceHue >= 360) 0 else sourceHue).toFloat()
        for (i in 0..hueAndRotations.size - 2) {
            val thisHue = hueAndRotations[i].first.toFloat()
            val nextHue = hueAndRotations[i + 1].first.toFloat()
            if (thisHue <= sanitizedSourceHue && sanitizedSourceHue < nextHue) {
                return ColorScheme.wrapDegreesDouble(
                    sanitizedSourceHue.toDouble() + hueAndRotations[i].second,
                )
            }
        }
        // If this statement executes, something is wrong, there should have been a rotation
        // found using the arrays.
        return sourceHue.toDouble()
    }
}

internal class HueSource : Hue {
    override fun get(sourceColor: Cam): Double {
        return sourceColor.hue.toDouble()
    }
}

internal class HueAdd(val amountDegrees: Double) : Hue {
    override fun get(sourceColor: Cam): Double {
        return ColorScheme.wrapDegreesDouble(sourceColor.hue.toDouble() + amountDegrees)
    }
}

internal class HueSubtract(val amountDegrees: Double) : Hue {
    override fun get(sourceColor: Cam): Double {
        return ColorScheme.wrapDegreesDouble(sourceColor.hue.toDouble() - amountDegrees)
    }
}

internal class HueVibrantSecondary : Hue {
    val hueToRotations =
        listOf(
            Pair(0, 18),
            Pair(41, 15),
            Pair(61, 10),
            Pair(101, 12),
            Pair(131, 15),
            Pair(181, 18),
            Pair(251, 15),
            Pair(301, 12),
            Pair(360, 12),
        )

    override fun get(sourceColor: Cam): Double {
        return getHueRotation(sourceColor.hue, hueToRotations)
    }
}

internal class HueVibrantTertiary : Hue {
    val hueToRotations =
        listOf(
            Pair(0, 35),
            Pair(41, 30),
            Pair(61, 20),
            Pair(101, 25),
            Pair(131, 30),
            Pair(181, 35),
            Pair(251, 30),
            Pair(301, 25),
            Pair(360, 25),
        )

    override fun get(sourceColor: Cam): Double {
        return getHueRotation(sourceColor.hue, hueToRotations)
    }
}

internal class HueExpressiveSecondary : Hue {
    val hueToRotations =
        listOf(
            Pair(0, 45),
            Pair(21, 95),
            Pair(51, 45),
            Pair(121, 20),
            Pair(151, 45),
            Pair(191, 90),
            Pair(271, 45),
            Pair(321, 45),
            Pair(360, 45),
        )

    override fun get(sourceColor: Cam): Double {
        return getHueRotation(sourceColor.hue, hueToRotations)
    }
}

internal class HueExpressiveTertiary : Hue {
    val hueToRotations =
        listOf(
            Pair(0, 120),
            Pair(21, 120),
            Pair(51, 20),
            Pair(121, 45),
            Pair(151, 20),
            Pair(191, 15),
            Pair(271, 20),
            Pair(321, 120),
            Pair(360, 120),
        )

    override fun get(sourceColor: Cam): Double {
        return getHueRotation(sourceColor.hue, hueToRotations)
    }
}

internal interface Chroma {
    fun get(sourceColor: Cam): Double

    companion object {
        val MAX_VALUE = 120.0
        val MIN_VALUE = 0.0
    }
}

internal class ChromaMaxOut : Chroma {
    override fun get(sourceColor: Cam): Double {
        // Intentionally high. Gamut mapping from impossible HCT to sRGB will ensure that
        // the maximum chroma is reached, even if lower than this constant.
        return Chroma.MAX_VALUE + 10.0
    }
}

internal class ChromaMultiple(val multiple: Double) : Chroma {
    override fun get(sourceColor: Cam): Double {
        return sourceColor.chroma * multiple
    }
}

internal class ChromaAdd(val amount: Double) : Chroma {
    override fun get(sourceColor: Cam): Double {
        return sourceColor.chroma + amount
    }
}

internal class ChromaBound(
    val baseChroma: Chroma,
    val minVal: Double,
    val maxVal: Double,
) : Chroma {
    override fun get(sourceColor: Cam): Double {
        val result = baseChroma.get(sourceColor)
        return min(max(result, minVal), maxVal)
    }
}

internal class ChromaConstant(val chroma: Double) : Chroma {
    override fun get(sourceColor: Cam): Double {
        return chroma
    }
}

internal class ChromaSource : Chroma {
    override fun get(sourceColor: Cam): Double {
        return sourceColor.chroma.toDouble()
    }
}

internal class TonalSpec(val hue: Hue = HueSource(), val chroma: Chroma) {
    fun shades(sourceColor: Cam): List<Int> {
        val hue = hue.get(sourceColor)
        val chroma = chroma.get(sourceColor)
        return Shades.of(hue.toFloat(), chroma.toFloat()).toList()
    }

    fun getAtTone(sourceColor: Cam, tone: Float): Int {
        val hue = hue.get(sourceColor)
        val chroma = chroma.get(sourceColor)
        return ColorUtils.CAMToColor(hue.toFloat(), chroma.toFloat(), (1000f - tone) / 10f)
    }
}

internal class CoreSpec(
    val a1: TonalSpec,
    val a2: TonalSpec,
    val a3: TonalSpec,
    val n1: TonalSpec,
    val n2: TonalSpec,
)

enum class Style(internal val coreSpec: CoreSpec) {
    SPRITZ(
        CoreSpec(
            a1 = TonalSpec(HueSource(), ChromaConstant(12.0)),
            a2 = TonalSpec(HueSource(), ChromaConstant(8.0)),
            a3 = TonalSpec(HueSource(), ChromaConstant(16.0)),
            n1 = TonalSpec(HueSource(), ChromaConstant(2.0)),
            n2 = TonalSpec(HueSource(), ChromaConstant(2.0)),
        ),
    ),
    TONAL_SPOT(
        CoreSpec(
            a1 = TonalSpec(HueSource(), ChromaConstant(36.0)),
            a2 = TonalSpec(HueSource(), ChromaConstant(16.0)),
            a3 = TonalSpec(HueAdd(60.0), ChromaConstant(24.0)),
            n1 = TonalSpec(HueSource(), ChromaConstant(6.0)),
            n2 = TonalSpec(HueSource(), ChromaConstant(8.0)),
        ),
    ),
    VIBRANT(
        CoreSpec(
            a1 = TonalSpec(HueSource(), ChromaMaxOut()),
            a2 = TonalSpec(HueVibrantSecondary(), ChromaConstant(24.0)),
            a3 = TonalSpec(HueVibrantTertiary(), ChromaConstant(32.0)),
            n1 = TonalSpec(HueSource(), ChromaConstant(10.0)),
            n2 = TonalSpec(HueSource(), ChromaConstant(12.0)),
        ),
    ),
    EXPRESSIVE(
        CoreSpec(
            a1 = TonalSpec(HueAdd(240.0), ChromaConstant(40.0)),
            a2 = TonalSpec(HueExpressiveSecondary(), ChromaConstant(24.0)),
            a3 = TonalSpec(HueExpressiveTertiary(), ChromaConstant(32.0)),
            n1 = TonalSpec(HueAdd(15.0), ChromaConstant(8.0)),
            n2 = TonalSpec(HueAdd(15.0), ChromaConstant(12.0)),
        ),
    ),
    RAINBOW(
        CoreSpec(
            a1 = TonalSpec(HueSource(), ChromaConstant(48.0)),
            a2 = TonalSpec(HueSource(), ChromaConstant(16.0)),
            a3 = TonalSpec(HueAdd(60.0), ChromaConstant(24.0)),
            n1 = TonalSpec(HueSource(), ChromaConstant(0.0)),
            n2 = TonalSpec(HueSource(), ChromaConstant(0.0)),
        ),
    ),
    FRUIT_SALAD(
        CoreSpec(
            a1 = TonalSpec(HueSubtract(50.0), ChromaConstant(48.0)),
            a2 = TonalSpec(HueSubtract(50.0), ChromaConstant(36.0)),
            a3 = TonalSpec(HueSource(), ChromaConstant(36.0)),
            n1 = TonalSpec(HueSource(), ChromaConstant(10.0)),
            n2 = TonalSpec(HueSource(), ChromaConstant(16.0)),
        ),
    ),
    CONTENT(
        CoreSpec(
            a1 = TonalSpec(HueSource(), ChromaSource()),
            a2 = TonalSpec(HueSource(), ChromaMultiple(0.33)),
            a3 = TonalSpec(HueSource(), ChromaMultiple(0.66)),
            n1 = TonalSpec(HueSource(), ChromaMultiple(0.0833)),
            n2 = TonalSpec(HueSource(), ChromaMultiple(0.1666)),
        ),
    ),
    MONOCHROMATIC(
        CoreSpec(
            a1 = TonalSpec(HueSource(), ChromaConstant(.0)),
            a2 = TonalSpec(HueSource(), ChromaConstant(.0)),
            a3 = TonalSpec(HueSource(), ChromaConstant(.0)),
            n1 = TonalSpec(HueSource(), ChromaConstant(.0)),
            n2 = TonalSpec(HueSource(), ChromaConstant(.0)),
        ),
    ),
    CLOCK(
        CoreSpec(
            a1 = TonalSpec(HueSource(), ChromaBound(ChromaSource(), 20.0, Chroma.MAX_VALUE)),
            a2 = TonalSpec(HueAdd(10.0), ChromaBound(ChromaMultiple(0.85), 17.0, 40.0)),
            a3 = TonalSpec(HueAdd(20.0), ChromaBound(ChromaAdd(20.0), 50.0, Chroma.MAX_VALUE)),
            // Not Used
            n1 = TonalSpec(HueSource(), ChromaConstant(0.0)),
            n2 = TonalSpec(HueSource(), ChromaConstant(0.0)),
        ),
    ),
    CLOCK_VIBRANT(
        CoreSpec(
            a1 = TonalSpec(HueSource(), ChromaBound(ChromaSource(), 70.0, Chroma.MAX_VALUE)),
            a2 = TonalSpec(HueAdd(20.0), ChromaBound(ChromaSource(), 70.0, Chroma.MAX_VALUE)),
            a3 = TonalSpec(HueAdd(60.0), ChromaBound(ChromaSource(), 70.0, Chroma.MAX_VALUE)),
            // Not Used
            n1 = TonalSpec(HueSource(), ChromaConstant(0.0)),
            n2 = TonalSpec(HueSource(), ChromaConstant(0.0)),
        ),
    ),
}

class TonalPalette
internal constructor(
    private val spec: TonalSpec,
    seedColor: Int,
) {
    val seedCam: Cam = Cam.fromInt(seedColor)
    val allShades: List<Int> = spec.shades(seedCam)
    val allShadesMapped: Map<Int, Int> = SHADE_KEYS.zip(allShades).toMap()
    val baseColor: Int

    init {
        val h = spec.hue.get(seedCam).toFloat()
        val c = spec.chroma.get(seedCam).toFloat()
        baseColor = ColorUtils.CAMToColor(h, c, CamUtils.lstarFromInt(seedColor))
    }

    // Dynamically computed tones across the full range from 0 to 1000
    fun getAtTone(tone: Float) = spec.getAtTone(seedCam, tone)

    // Predefined & precomputed tones
    val s10: Int
        get() = this.allShades[0]
    val s50: Int
        get() = this.allShades[1]
    val s100: Int
        get() = this.allShades[2]
    val s200: Int
        get() = this.allShades[3]
    val s300: Int
        get() = this.allShades[4]
    val s400: Int
        get() = this.allShades[5]
    val s500: Int
        get() = this.allShades[6]
    val s600: Int
        get() = this.allShades[7]
    val s700: Int
        get() = this.allShades[8]
    val s800: Int
        get() = this.allShades[9]
    val s900: Int
        get() = this.allShades[10]
    val s1000: Int
        get() = this.allShades[11]

    companion object {
        val SHADE_KEYS = listOf(10, 50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000)
    }
}

class ColorScheme(
    @ColorInt val seed: Int,
    val style: Style = Style.TONAL_SPOT,
) {
    val accent1: TonalPalette
    val accent2: TonalPalette
    val accent3: TonalPalette
    val neutral1: TonalPalette
    val neutral2: TonalPalette

    constructor(@ColorInt seed: Int) : this(seed, Style.TONAL_SPOT)

    val allHues: List<TonalPalette>
        get() {
            return listOf(accent1, accent2, accent3, neutral1, neutral2)
        }
    val allAccentColors: List<Int>
        get() {
            val allColors = mutableListOf<Int>()
            allColors.addAll(accent1.allShades)
            allColors.addAll(accent2.allShades)
            allColors.addAll(accent3.allShades)
            return allColors
        }
    val allNeutralColors: List<Int>
        get() {
            val allColors = mutableListOf<Int>()
            allColors.addAll(neutral1.allShades)
            allColors.addAll(neutral2.allShades)
            return allColors
        }

    init {
        val proposedSeedCam = Cam.fromInt(seed)
        val seedArgb =
            if (seed == Color.TRANSPARENT) {
                GOOGLE_BLUE
            } else if (style != Style.CONTENT && proposedSeedCam.chroma < 5) {
                GOOGLE_BLUE
            } else {
                seed
            }
        accent1 = TonalPalette(style.coreSpec.a1, seedArgb)
        accent2 = TonalPalette(style.coreSpec.a2, seedArgb)
        accent3 = TonalPalette(style.coreSpec.a3, seedArgb)
        neutral1 = TonalPalette(style.coreSpec.n1, seedArgb)
        neutral2 = TonalPalette(style.coreSpec.n2, seedArgb)
    }

    val shadeCount
        get() = this.accent1.allShades.size
    val seedTone: Float
        get() = 1000f - CamUtils.lstarFromInt(seed) * 10f

    override fun toString(): String {
        return "ColorScheme {\n" +
            "  seed color: ${stringForColor(seed)}\n" +
            "  style: $style\n" +
            "  palettes: \n" +
            "  ${humanReadable("PRIMARY", accent1.allShades)}\n" +
            "  ${humanReadable("SECONDARY", accent2.allShades)}\n" +
            "  ${humanReadable("TERTIARY", accent3.allShades)}\n" +
            "  ${humanReadable("NEUTRAL", neutral1.allShades)}\n" +
            "  ${humanReadable("NEUTRAL VARIANT", neutral2.allShades)}\n" +
            "}"
    }

    companion object {
        private fun wrapDegrees(degrees: Int): Int {
            return when {
                degrees < 0 -> {
                    (degrees % 360) + 360
                }

                degrees >= 360 -> {
                    degrees % 360
                }

                else -> {
                    degrees
                }
            }
        }

        public fun wrapDegreesDouble(degrees: Double): Double {
            return when {
                degrees < 0 -> {
                    (degrees % 360) + 360
                }

                degrees >= 360 -> {
                    degrees % 360
                }

                else -> {
                    degrees
                }
            }
        }

        private fun hueDiff(a: Float, b: Float): Float {
            return 180f - ((a - b).absoluteValue - 180f).absoluteValue
        }

        private fun stringForColor(color: Int): String {
            val width = 4
            val hct = Cam.fromInt(color)
            val h = "H${hct.hue.roundToInt().toString().padEnd(width)}"
            val c = "C${hct.chroma.roundToInt().toString().padEnd(width)}"
            val t = "T${CamUtils.lstarFromInt(color).roundToInt().toString().padEnd(width)}"
            val hex = Integer.toHexString(color and 0xffffff).padStart(6, '0').uppercase()
            return "$h$c$t = #$hex"
        }

        private fun humanReadable(paletteName: String, colors: List<Int>): String {
            return "$paletteName\n" +
                colors.map { stringForColor(it) }.joinToString(separator = "\n") { it }
        }

        private fun score(cam: Cam, proportion: Double): Double {
            val proportionScore = 0.7 * 100.0 * proportion
            val chromaScore =
                if (cam.chroma < ACCENT1_CHROMA) {
                    0.1 * (cam.chroma - ACCENT1_CHROMA)
                } else {
                    0.3 * (cam.chroma - ACCENT1_CHROMA)
                }
            return chromaScore + proportionScore
        }

        private fun huePopulations(
            camByColor: Map<Int, Cam>,
            populationByColor: Map<Int, Double>,
            filter: Boolean = true,
        ): List<Double> {
            val huePopulation = List(size = 360, init = { 0.0 }).toMutableList()
            for (entry in populationByColor.entries) {
                val population = populationByColor[entry.key]!!
                val cam = camByColor[entry.key]!!
                val hue = cam.hue.roundToInt() % 360
                if (filter && cam.chroma <= MIN_CHROMA) {
                    continue
                }
                huePopulation[hue] = huePopulation[hue] + population
            }
            return huePopulation
        }
    }
}
