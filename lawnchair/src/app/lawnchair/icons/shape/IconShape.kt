/*
 *     Copyright (C) 2019 paphonb@xda
 *
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

package app.lawnchair.icons.shape

import android.content.Context
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Region
import android.util.Log
import android.util.PathParser
import com.android.launcher3.Utilities
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.icons.GraphicsUtils
import com.android.launcher3.shapes.ShapesProvider
import kotlin.apply

/**
 * Represents a base class for defining icon shapes in the launcher.
 *
 * This class provides a framework for creating various icon masks, supporting:
 * - [SystemBased]: Shapes derived from the Android system's adaptive icon mask.
 * - [PathBased]: Shapes defined via SVG path data.
 * - [CornerBased]: Shapes defined by individual styles and scales for each of the four corners.
 *
 * Persistence contract: [toString] is the serialized value stored in preferences and later
 * parsed by [fromString]. For built-in shapes this is [key], while custom shapes use a
 * versioned corner format.
 */
sealed class IconShape {
    abstract val key: String

    override fun toString(): String = key

    open fun getHashString(): String = key

    /** Returns the mask path normalized to a 100x100 viewport.
     *
     * Implementations are expected to return a new [Path] instance on each call so callers can
     * mutate the result safely.
     */
    abstract fun getMaskPath(): Path

    /**
     * Rendering scale multiplier used by Launcher3 when fitting artwork inside this mask.
     *
     * `1f` keeps default sizing; values below `1f` shrink content and values above `1f` expand it.
     */
    open val iconScale = 1f

    /**
     * Normalized corner-radius hint ([0, 1]) used for window transition animations.
     *
     * `1f` corresponds to a fully rounded transition radius for the shape's bounds.
     */
    open val windowTransitionRadius = 1f

    /** Marker interface for built-in, non-user-defined shapes. */
    interface DefaultShapes

    /**
     * Shape backed by the platform icon mask.
     *
     * We approximate the platform mask to the nearest supported [CornerBased] shape for
     * rendering consistency with the editor/customization pipeline.
     */
    class SystemBased(
        private val iconMask: Path,
        val context: Context,
    ) : IconShape() {
        private val nearestShape: CornerBased by lazy(LazyThreadSafetyMode.PUBLICATION) {
            findNearestShape()
        }

        override fun getMaskPath(): Path = nearestShape.getMaskPath()

        override val key = "system"

        /** Finds the closest supported corner-based shape by minimizing XOR region area.
         *
         * This intentionally compares only the most common preset shapes to avoid expensive
         * matching over every possible custom corner configuration.
         */
        fun findNearestShape(): CornerBased {
            val size = 200
            val clip = Region(0, 0, size, size)
            val iconR = Region().apply {
                setPath(iconMask, clip)
            }
            val shapePath = Path()
            val shapeR = Region()

            return listOf(
                Circle,
                Square,
                RoundedSquare,
                Squircle,
                Sammy,
                Teardrop,
                Cylinder,
            )
                .minByOrNull {
                    shapePath.reset()
                    it.addShape(shapePath, 0f, 0f, size / 2f)
                    shapeR.setPath(shapePath, clip)
                    shapeR.op(iconR, Region.Op.XOR)

                    GraphicsUtils.getArea(shapeR)
                }!!
        }

        /**
         * Returns a stable identifier for cache invalidation.
         *
         * Uses the configured theme resource string when available, otherwise falls back to a
         * fixed marker for raw path-based system masks.
         */
        override fun getHashString(): String {
            val resId = ThemeManager.CONFIG_ICON_MASK_RES_ID
            if (resId == 0) {
                return "system-path"
            }
            return context.getString(resId)
        }
    }

    open class PathBased(
        override val key: String,
        val svgPathString: String,
    ) : IconShape(),
        DefaultShapes {
        private val cachedPath by lazy {
            PathParser.createPathFromPathData(svgPathString)
        }

        override fun getMaskPath(): Path = Path(cachedPath)
    }

    open class CornerBased(
        override val key: String,
        open val topLeft: Corner,
        open val topRight: Corner,
        open val bottomLeft: Corner,
        open val bottomRight: Corner,
    ) : IconShape() {
        constructor(
            key: String,
            topLeftShape: IconCornerShape,
            topRightShape: IconCornerShape,
            bottomLeftShape: IconCornerShape,
            bottomRightShape: IconCornerShape,
            topLeftScale: Float,
            topRightScale: Float,
            bottomLeftScale: Float,
            bottomRightScale: Float,
        ) : this(
            key,
            Corner(topLeftShape, topLeftScale),
            Corner(topRightShape, topRightScale),
            Corner(bottomLeftShape, bottomLeftScale),
            Corner(bottomRightShape, bottomRightScale),
        )

        constructor(
            key: String,
            topLeftShape: IconCornerShape,
            topRightShape: IconCornerShape,
            bottomLeftShape: IconCornerShape,
            bottomRightShape: IconCornerShape,
            topLeftScale: PointF,
            topRightScale: PointF,
            bottomLeftScale: PointF,
            bottomRightScale: PointF,
        ) : this(
            key,
            Corner(topLeftShape, topLeftScale),
            Corner(topRightShape, topRightScale),
            Corner(bottomLeftShape, bottomLeftScale),
            Corner(bottomRightShape, bottomRightScale),
        )

        private val isCircle =
            topLeft == Corner.fullArc &&
                topRight == Corner.fullArc &&
                bottomLeft == Corner.fullArc &&
                bottomRight == Corner.fullArc

        open fun addShape(path: Path, x: Float, y: Float, radius: Float) {
            if (isCircle) {
                path.addCircle(x + radius, y + radius, radius, Path.Direction.CW)
            } else {
                val size = radius * 2
                CornerShapeCompat.addToPath(this, path, x, y, x + size, y + size, radius)
            }
        }

        override fun getMaskPath(): Path {
            return Path().also { CornerShapeCompat.addToPath(this, it, 0f, 0f, 100f, 100f, 50f) }
        }
    }

    open class SimpleCornerBased(
        override val key: String,
        shape: IconCornerShape,
        scale: PointF,
    ) : CornerBased(
        key,
        shape,
        shape,
        shape,
        shape,
        scale,
        scale,
        scale,
        scale,
    ),
        DefaultShapes {
        constructor(
            key: String,
            shape: IconCornerShape,
            scale: Float,
        ) : this(
            key,
            shape,
            PointF(scale, scale),
        )
    }

    data class CustomCornerBased(
        override val topLeft: Corner,
        override val topRight: Corner,
        override val bottomLeft: Corner,
        override val bottomRight: Corner,
        val version: Int = 1,
    ) : CornerBased(
        key = "custom",
        topLeft = topLeft,
        topRight = topRight,
        bottomLeft = bottomLeft,
        bottomRight = bottomRight,
    ) {
        constructor(iconShape: CornerBased) : this(
            topLeft = iconShape.topLeft,
            topRight = iconShape.topRight,
            bottomLeft = iconShape.bottomLeft,
            bottomRight = iconShape.bottomRight,
        )

        // Intentional, see [IconShapeCornerPreferenceGroup]
        fun copy(
            topLeftShape: IconCornerShape = topLeft.shape,
            topRightShape: IconCornerShape = topRight.shape,
            bottomLeftShape: IconCornerShape = bottomLeft.shape,
            bottomRightShape: IconCornerShape = bottomRight.shape,
            topLeftScale: Float = topLeft.scale.x,
            topRightScale: Float = topRight.scale.x,
            bottomLeftScale: Float = bottomLeft.scale.x,
            bottomRightScale: Float = bottomRight.scale.x,
        ): CustomCornerBased = CustomCornerBased(
            Corner(topLeftShape, topLeftScale),
            Corner(topRightShape, topRightScale),
            Corner(bottomLeftShape, bottomLeftScale),
            Corner(bottomRightShape, bottomRightScale),
        )

        // Format: v1|<topLeft>|<topRight>|<bottomLeft>|<bottomRight>
        override fun toString(): String = "v$version|$topLeft|$topRight|$bottomLeft|$bottomRight"

        override fun getHashString(): String = toString()

        companion object {
            /** Parses [value], returning null for invalid or unsupported serialized input. */
            fun fromStringOrNull(value: String): CustomCornerBased? {
                return runCatching { fromString(value) }.getOrNull()
            }

            /**
             * Parses `v1|<corner>|<corner>|<corner>|<corner>` custom shape strings.
             *
             * Throws when the format/version is invalid.
             */
            fun fromString(value: String): CustomCornerBased {
                val parts = value.split("|")
                check(parts[0] == "v1") { "unknown config format" }
                check(parts.size == 5) { "invalid arguments size" }
                return CustomCornerBased(
                    Corner.fromString(parts[1]),
                    Corner.fromString(parts[2]),
                    Corner.fromString(parts[3]),
                    Corner.fromString(parts[4]),
                )
            }
        }
    }

    object Circle : SimpleCornerBased(
        key = "circle",
        IconCornerShape.arc,
        1f,
    )

    object Square : SimpleCornerBased(
        key = "square",
        IconCornerShape.arc,
        .16f,
    ) {
        override val windowTransitionRadius = .16f
    }

    object SharpSquare : SimpleCornerBased(
        key = "sharpSquare",
        IconCornerShape.arc,
        0f,
    ) {
        override val windowTransitionRadius = 0f
    }

    object RoundedSquare : SimpleCornerBased(
        key = "roundedSquare",
        IconCornerShape.arc,
        .6f,
    ) {
        override val windowTransitionRadius = .6f
    }

    object Squircle : SimpleCornerBased(
        key = "squircle",
        IconCornerShape.Squircle,
        1f,
    )

    object Sammy : SimpleCornerBased(
        key = "sammy",
        IconCornerShape.Sammy,
        1f,
    )

    object Teardrop : SimpleCornerBased(
        key = "teardrop",
        IconCornerShape.arc,
        .3f,
    )

    object Cylinder : SimpleCornerBased(
        key = "cylinder",
        IconCornerShape.arc,
        PointF(1f, .6f),
    )

    object Cupertino : SimpleCornerBased(
        key = "cupertino",
        IconCornerShape.Cupertino,
        1f,
    ) {
        override val windowTransitionRadius = .45f
    }

    object Octagon : SimpleCornerBased(
        key = "octagon",
        IconCornerShape.Cut,
        .5f,
    )

    object Hexagon : SimpleCornerBased(
        key = "hexagon",
        IconCornerShape.CutHex,
        PointF(1f, .5f),
    )

    object Diamond : SimpleCornerBased(
        key = "diamond",
        IconCornerShape.Cut,
        1f,
    ) {
        override val windowTransitionRadius = 0f
    }

    object Egg :
        CornerBased(
            key = "egg",
            IconCornerShape.arc,
            IconCornerShape.arc,
            IconCornerShape.arc,
            IconCornerShape.arc,
            1f, 1f, 0.75f, 0.75f,
        ),
        DefaultShapes {
        override val windowTransitionRadius = 0.85f
    }

    object VerySunny : PathBased(
        key = "verysunny",
        svgPathString = "M42.3337 4.6379C45.5777 -0.914451 53.4223 -0.914461 56.6663 4.6379L60.3144 10.882C62.2063 14.12 65.9414 15.7068 69.5115 14.7892L76.396 13.0198C82.5178 11.4463 88.0648 17.1355 86.5307 23.4143L84.8055 30.4753C83.9108 34.137 85.4579 37.9679 88.615 39.9082L94.703 43.6499C100.117 46.977 100.117 55.0228 94.703 58.35L88.615 62.0917C85.4579 64.032 83.9108 67.8629 84.8055 71.5246L86.5307 78.5856C88.0648 84.8644 82.5178 90.5536 76.396 88.9801L69.5115 87.2107C65.9414 86.2931 62.2063 87.8798 60.3144 91.1179L56.6663 97.362C53.4223 102.914 45.5777 102.914 42.3337 97.362L38.6856 91.1179C36.7937 87.8798 33.0586 86.2931 29.4884 87.2107L22.604 88.9801C16.4822 90.5536 10.9352 84.8644 12.4693 78.5856L14.1945 71.5246C15.0892 67.8629 13.5421 64.032 10.3849 62.0917L4.29698 58.35C-1.11657 55.0229 -1.11658 46.9771 4.29697 43.6499L10.3849 39.9082C13.5421 37.9679 15.0892 34.137 14.1945 30.4753L12.4693 23.4143C10.9352 17.1355 16.4822 11.4463 22.604 13.0197L29.4884 14.7892C33.0586 15.7068 36.7937 14.12 38.6856 10.882L42.3337 4.6379Z",
    )

    object ComplexClover : PathBased(
        key = "complexclover",
        svgPathString = "M 49.85 6.764 L 50.013 6.971 L 50.175 6.764 C 53.422 2.635 58.309 0.207 63.538 0.207 C 65.872 0.207 68.175 0.692 70.381 1.648 L 71.79 2.264 L 71.792 2.265 A 3.46 3.46 0 0 0 74.515 2.265 L 74.517 2.264 L 75.926 1.652 A 17.1 17.1 0 0 1 82.769 0.207 C 88.495 0.207 93.824 3.117 97.022 7.989 C 100.21 12.848 100.697 18.712 98.36 24.087 L 97.749 25.496 V 25.497 A 3.45 3.45 0 0 0 97.749 28.222 V 28.223 L 98.36 29.632 C 100.697 35.007 100.207 40.871 97.022 45.73 A 17.5 17.5 0 0 1 93.264 49.838 L 93.06 50 L 93.264 50.162 A 17.5 17.5 0 0 1 97.022 54.27 C 100.21 59.129 100.697 64.993 98.36 70.368 V 71.778 A 3.45 3.45 0 0 0 97.749 74.503 V 74.504 L 98.36 75.913 C 100.697 81.288 100.207 87.152 97.022 92.011 C 93.824 96.883 88.495 99.793 82.769 99.793 C 80.435 99.793 78.132 99.308 75.926 98.348 L 74.517 97.736 H 74.515 A 3.5 3.5 0 0 0 73.153 97.455 C 72.682 97.455 72.225 97.552 71.792 97.736 H 71.79 L 70.381 98.348 A 17.1 17.1 0 0 1 63.538 99.793 C 58.309 99.793 53.422 97.365 50.175 93.236 L 50.013 93.029 L 49.85 93.236 C 46.603 97.365 41.717 99.793 36.488 99.793 C 34.154 99.793 31.851 99.308 29.645 98.348 L 28.236 97.736 H 28.234 A 3.5 3.5 0 0 0 26.872 97.455 C 26.401 97.455 25.944 97.552 25.511 97.736 H 25.509 L 24.1 98.348 A 17.1 17.1 0 0 1 17.257 99.793 C 11.53 99.793 6.202 96.883 3.004 92.011 C -0.181 87.152 -0.671 81.288 1.661 75.913 L 2.277 74.504 V 74.503 A 3.45 3.45 0 0 0 2.277 71.778 V 71.777 L 1.665 70.368 C -0.671 64.993 -0.181 59.129 3.004 54.274 A 17.5 17.5 0 0 1 6.761 50.162 L 6.965 50 L 6.761 49.838 A 17.5 17.5 0 0 1 3.004 45.73 C -0.181 40.871 -0.671 35.007 1.665 29.632 L 2.277 28.223 V 28.222 A 3.45 3.45 0 0 0 2.277 25.497 V 25.496 L 1.665 24.087 C -0.671 18.712 -0.181 12.848 3.004 7.994 V 7.993 C 6.202 3.117 11.53 0.207 17.257 0.207 C 19.591 0.207 21.894 0.692 24.1 1.652 L 25.509 2.264 L 25.511 2.265 A 3.46 3.46 0 0 0 28.234 2.265 L 28.236 2.264 L 29.645 1.652 A 17.1 17.1 0 0 1 36.488 0.207 C 41.717 0.207 46.603 2.635 49.85 6.764 Z",
    )

    object FourSidedCookie : PathBased(
        key = "foursidedcookie",
        svgPathString = ShapesProvider.FOUR_SIDED_COOKIE_PATH,
    ) {
        override val iconScale = 72f / 83.4f
    }

    object SevenSidedCookie : PathBased(
        key = "sevensidedcookie",
        svgPathString = ShapesProvider.SEVEN_SIDED_COOKIE_PATH,
    ) {
        override val iconScale = 72f / 80f
    }

    object Arch : PathBased(
        key = "arch",
        svgPathString = ShapesProvider.ARCH_PATH,
    ) {
        override val windowTransitionRadius = 0.16f
    }

    // Additional shapes from https://github.com/Evolution-X/vendor_extras/blob/bq2/themes/iconshapes/

    object Cloudy : PathBased(
        key = "cloudy",
        svgPathString = "M4,50 C2,45 0,39 0,33 C0,15 15,0 33,0 C39,0 45,2 50,4 C55,2 61,0 67,0 C85,0 100,15 100,33 C100,39 98,45 96,50 C98,55 100,61 100,66 C100,85 85,100 66,100 C61,100 55,98 50,96 C45,98 39,100 33,100 C15,100 0,85 0,66 C0,61 2,55 3,50 Z",
    )

    object Flower : PathBased(
        key = "flower",
        svgPathString = "M50,0 C60.6,0 69.9,5.3 75.6,13.5 78.5,17.8 82.3,21.5 86.6,24.5 94.7,30.1 100,39.4 100,50 100,60.6 94.7,69.9 86.5,75.6 82.2,78.5 78.5,82.3 75.5,86.6 69.9,94.7 60.6,100 50,100 39.4,100 30.1,94.7 24.4,86.5 21.5,82.2 17.7,78.5 13.4,75.5 5.3,69.9 0,60.6 0,50 0,39.4 5.3,30.1 13.5,24.4 17.8,21.5 21.5,17.7 24.5,13.4 30.1,5.3 39.4,0 50,0 Z",
    )

    object Heart : PathBased(
        key = "heart",
        svgPathString = "M50,20 C45,0 30,0 25,0 20,0 0,5 0,34 0,72 40,97 50,100 60,97 100,72 100,34 100,5 80,0 75,0 70,0 55,0 50,20 Z",
    )

    object Leaf : PathBased(
        key = "leaf",
        svgPathString = "M-0.06,0.07h67.37C85.36,0.07,100,14.71,100,32.76v67.37H32.63c-18.06,0-32.69-14.64-32.69-32.69L-0.06,0.07z",
    )

    object Meow : PathBased(
        key = "meow",
        svgPathString = "M 4.432 31.65 C 4.656 31.126 4.736 30.551 4.663 29.985 C 4.026 26.269 1.314 11.591 0.116 4.701 C -0.235 2.992 0.443 0.912 1.895 0.145 C 2.336 -0.072 2.839 -0.014 3.293 0.112 C 4.378 0.473 5.381 1.08 6.357 1.718 C 10.502 4.416 14.621 7.167 18.732 9.933 C 19.823 10.666 21.252 10.658 22.334 9.911 C 30.185 4.6 39.651 1.503 49.833 1.503 C 60.18 1.503 69.786 4.701 77.716 10.161 C 78.797 10.922 80.235 10.937 81.331 10.199 C 86.109 6.997 90.875 3.776 95.646 0.564 C 96.99 -0.438 99.098 0.259 99.76 1.907 C 100.193 3.164 99.88 4.529 99.634 5.789 C 98.325 13.019 95.754 27.409 95.207 30.68 C 95.169 31.181 95.249 31.684 95.441 32.148 C 97.788 37.889 99.082 44.17 99.082 50.752 C 99.082 77.932 77.014 100 49.833 100 C 22.653 100 0.585 77.932 0.585 50.752 C 0.585 43.98 1.955 37.526 4.432 31.65 Z",
    )

    object Pebble : PathBased(
        key = "pebble",
        svgPathString = "M55,0 C25,0 0,25 0,50 0,78 28,100 55,100 85,100 100,85 100,58 100,30 86,0 55,0 Z",
    )

    object RoundedHexagon : PathBased(
        key = "roundedhexagon",
        svgPathString = "M4.8 33V67c0 5.8 3 11 8 13.7l29.4 17c4.9 2.7 11 2.7 15.9 0l29.4 -17c4.9 -2.7 8 -8 8 -13.7V33c0 -5.8 -3 -11 -8 -13.7l-29.4 -17c-4.9 -2.7 -11 -2.7 -15.9 0l-29.7 17C7.8 22.2 4.8 27.5 4.8 33z",
    )

    object Stretched : PathBased(
        key = "stretched",
        svgPathString = "M100,50 C100,77 77,100 50,100 L10,100 C4,100 0,96 0,90 L0,50 C0,22 22,0 50,0 L90,0 C96,0 100,4 100,10 L100,50 Z",
    )

    object Vessel : PathBased(
        key = "vessel",
        svgPathString = "M12.97,0 C8.41,0 4.14,2.55 2.21,6.68 -1.03,13.61 -0.71,21.78 3.16,28.46 4.89,31.46 4.89,35.2 3.16,38.2 -1.05,45.48 -1.05,54.52 3.16,61.8 4.89,64.8 4.89,68.54 3.16,71.54 -0.71,78.22 -1.03,86.39 2.21,93.32 4.14,97.45 8.41,100 12.97,100 21.38,100 78.62,100 87.03,100 91.59,100 95.85,97.45 97.79,93.32 101.02,86.39 100.71,78.22 96.84,71.54 95.1,68.54 95.1,64.8 96.84,61.8 101.05,54.52 101.05,45.48 96.84,38.2 95.1,35.2 95.1,31.46 96.84,28.46 100.71,21.78 101.02,13.61 97.79,6.68 95.85,2.55 91.59,0 87.03,0 78.62,0 21.38,0 12.97,0 Z",
    )

    companion object {

        /**
         * Parses a persisted icon shape token.
         *
         * Accepted values:
         * - `system`: resolve the platform mask via [IconShapeManager.getSystemIconShape]
         * - built-in keys such as `circle`, `squircle`, `arch`, etc.
         * - custom serialized shapes in `v1|...` format
         *
         * If resolving `system` fails, parsing falls back to non-system tokens.
         */
        fun fromString(value: String, context: Context): IconShape? {
            if (value == "system") {
                runCatching {
                    return IconShapeManager.getSystemIconShape(context = context)
                }
            }
            return fromStringWithoutContext(value = value)
        }

        /**
         * Parses non-system shape tokens.
         *
         * Returns `null` for empty/unknown values or malformed custom `v1|...` payloads.
         */
        private fun fromStringWithoutContext(value: String): IconShape? = when (value) {
            "circle" -> Circle
            "square" -> Square
            "sharpSquare" -> SharpSquare
            "roundedSquare" -> RoundedSquare
            "squircle" -> Squircle
            "sammy" -> Sammy
            "teardrop" -> Teardrop
            "cylinder" -> Cylinder
            "cupertino" -> Cupertino
            "octagon" -> Octagon
            "hexagon" -> Hexagon
            "diamond" -> Diamond
            "egg" -> Egg
            "verysunny" -> VerySunny
            "complexclover" -> ComplexClover
            "foursidedcookie" -> FourSidedCookie
            "sevensidedcookie" -> SevenSidedCookie
            "arch" -> Arch
            "cloudy" -> Cloudy
            "flower" -> Flower
            "heart" -> Heart
            "leaf" -> Leaf
            "meow" -> Meow
            "pebble" -> Pebble
            "roundedhexagon" -> RoundedHexagon
            "stretched" -> Stretched
            "vessel" -> Vessel
            "" -> null
            else -> CustomCornerBased.fromStringOrNull(value)
        }

        /**
         * Returns `true` when [iconShape] serializes as a valid custom `v1|...` corner shape.
         *
         * This is serialization-based detection; parse failures are logged and return `false`.
         */
        fun isCustomShape(iconShape: IconShape): Boolean {
            return try {
                CustomCornerBased.fromString(iconShape.toString())
                true
            } catch (e: Exception) {
                Log.e("IconShape", "Error creating shape $iconShape", e)
                false
            }
        }
    }

    data class Corner(val shape: IconCornerShape, val scale: PointF) {

        constructor(shape: IconCornerShape, scale: Float) : this(shape, PointF(scale, scale))

        // Format: <shape>,<scaleX>,<scaleY>
        override fun toString(): String {
            return "$shape,${scale.x},${scale.y}"
        }

        companion object {

            val fullArc = Corner(IconCornerShape.arc, 1f)

            /**
             * Parses `<shape>,<scaleX>[,<scaleY>]` where omitted `scaleY` reuses `scaleX`.
             *
             * Both scales must be in [0, 1]. Invalid input throws.
             */
            fun fromString(value: String): Corner {
                val parts = value.split(",")
                val scaleX = parts[1].toFloat()
                val scaleY = if (parts.size >= 3) parts[2].toFloat() else scaleX
                check(scaleX in 0f..1f) { "scaleX must be in [0, 1]" }
                check(scaleY in 0f..1f) { "scaleY must be in [0, 1]" }
                return Corner(IconCornerShape.fromString(parts[0]), PointF(scaleX, scaleY))
            }
        }
    }
}

object CornerShapeCompat {
    @JvmOverloads
    fun addToPath(
        shape: IconShape.CornerBased,
        path: Path,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        size: Float = 50f,
        endSize: Float = size,
        progress: Float = 0f,
    ) {
        // Interpolates each corner size from its configured value toward [endSize] by [progress].
        val tmpPoint = PointF()

        val topLeft = shape.topLeft
        val topRight = shape.topRight
        val bottomLeft = shape.bottomLeft
        val bottomRight = shape.bottomRight

        val topLeftSizeX = Utilities.mapRange(progress, topLeft.scale.x * size, endSize)
        val topLeftSizeY = Utilities.mapRange(progress, topLeft.scale.y * size, endSize)
        val topRightSizeX = Utilities.mapRange(progress, topRight.scale.x * size, endSize)
        val topRightSizeY = Utilities.mapRange(progress, topRight.scale.y * size, endSize)
        val bottomLeftSizeX = Utilities.mapRange(progress, bottomLeft.scale.x * size, endSize)
        val bottomLeftSizeY = Utilities.mapRange(progress, bottomLeft.scale.y * size, endSize)
        val bottomRightSizeX = Utilities.mapRange(progress, bottomRight.scale.x * size, endSize)
        val bottomRightSizeY = Utilities.mapRange(progress, bottomRight.scale.y * size, endSize)

        // Start from the bottom right corner
        path.moveTo(right, bottom - bottomRightSizeY)
        bottomRight.shape.addCorner(
            path,
            IconCornerShape.Position.BottomRight,
            tmpPoint.apply {
                x = bottomRightSizeX
                y = bottomRightSizeY
            },
            progress,
            right - bottomRightSizeX,
            bottom - bottomRightSizeY,
        )

        // Move to bottom left
        addLine(
            path,
            right - bottomRightSizeX,
            bottom,
            left + bottomLeftSizeX,
            bottom,
        )
        bottomLeft.shape.addCorner(
            path,
            IconCornerShape.Position.BottomLeft,
            tmpPoint.apply {
                x = bottomLeftSizeX
                y = bottomLeftSizeY
            },
            progress,
            left,
            bottom - bottomLeftSizeY,
        )

        // Move to top left
        addLine(
            path,
            left,
            bottom - bottomLeftSizeY,
            left,
            top + topLeftSizeY,
        )
        topLeft.shape.addCorner(
            path,
            IconCornerShape.Position.TopLeft,
            tmpPoint.apply {
                x = topLeftSizeX
                y = topLeftSizeY
            },
            progress,
            left,
            top,
        )

        // And then finally top right
        addLine(
            path,
            left + topLeftSizeX,
            top,
            right - topRightSizeX,
            top,
        )
        topRight.shape.addCorner(
            path,
            IconCornerShape.Position.TopRight,
            tmpPoint.apply {
                x = topRightSizeX
                y = topRightSizeY
            },
            progress,
            right - topRightSizeX,
            top,
        )

        path.close()
    }

    private fun addLine(path: Path, x1: Float, y1: Float, x2: Float, y2: Float) {
        if (x1 == x2 && y1 == y2) return
        path.lineTo(x2, y2)
    }
}
