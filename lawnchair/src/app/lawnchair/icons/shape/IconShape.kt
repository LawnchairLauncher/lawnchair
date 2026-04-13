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
 * This class provides a framework for creating various icon masks, supporting both
 * path-based shapes (via SVG data) and corner-based shapes (with customizable
 * corner styles and scales).
 *
 * Each shape is identified by a unique [key] and provides a [getMaskPath]
 * implementation for rendering the icon's outline.
 */
sealed class IconShape {
    abstract val key: String

    override fun toString(): String {
        return key
    }

    open fun getHashString(): String = key

    /** Returns the mask path normalized to a 100x100 viewport */
    abstract fun getMaskPath(): Path

    /** The icon scale used by Launcher3 */
    open val iconScale = 1f

    /** Transition radius for window animations */
    open val windowTransitionRadius = 1f

    interface DefaultShapes

    class SystemBased(
        private val iconMask: Path,
        val context: Context,
    ) : IconShape() {
        private val nearestShape: CornerBased by lazy(LazyThreadSafetyMode.PUBLICATION) {
            findNearestShape()
        }

        override fun getMaskPath(): Path = nearestShape.getMaskPath()

        override val key = "system"

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
    ) : IconShape(), DefaultShapes {
        private val cachedPath by lazy {
            PathParser.createPathFromPathData(svgPathString)
        }

        override fun getMaskPath(): Path = Path(cachedPath)
    }

    open class CornerBased(
        override val key: String,
        val topLeft: Corner,
        val topRight: Corner,
        val bottomLeft: Corner,
        val bottomRight: Corner,
    ) : IconShape(), DefaultShapes {
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

        fun copy(
            key: String = this.key,
            topLeftShape: IconCornerShape = topLeft.shape,
            topRightShape: IconCornerShape = topRight.shape,
            bottomLeftShape: IconCornerShape = bottomLeft.shape,
            bottomRightShape: IconCornerShape = bottomRight.shape,
            topLeftScale: Float = topLeft.scale.x,
            topRightScale: Float = topRight.scale.x,
            bottomLeftScale: Float = bottomLeft.scale.x,
            bottomRightScale: Float = bottomRight.scale.x,
        ): CornerBased = CornerBased(
            key = key,
            topLeftShape = topLeftShape,
            topRightShape = topRightShape,
            bottomLeftShape = bottomLeftShape,
            bottomRightShape = bottomRightShape,
            topLeftScale = topLeftScale,
            topRightScale = topRightScale,
            bottomLeftScale = bottomLeftScale,
            bottomRightScale = bottomRightScale,
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

    object Circle : CornerBased(
        key = "circle",
        IconCornerShape.arc,
        IconCornerShape.arc,
        IconCornerShape.arc,
        IconCornerShape.arc,
        1f,
        1f,
        1f,
        1f,
    )

    object Square : CornerBased(
        key = "square",
        IconCornerShape.arc,
        IconCornerShape.arc,
        IconCornerShape.arc,
        IconCornerShape.arc,
        .16f,
        .16f,
        .16f,
        .16f,
    ) {
        override val windowTransitionRadius = .16f
    }

    object SharpSquare : CornerBased(
        key = "sharpSquare",
        IconCornerShape.arc,
        IconCornerShape.arc,
        IconCornerShape.arc,
        IconCornerShape.arc,
        0f,
        0f,
        0f,
        0f,
    ) {
        override val windowTransitionRadius = 0f
    }

    object RoundedSquare : CornerBased(
        key = "roundedSquare",
        IconCornerShape.arc,
        IconCornerShape.arc,
        IconCornerShape.arc,
        IconCornerShape.arc,
        .6f,
        .6f,
        .6f,
        .6f,
    ) {

        override val windowTransitionRadius = .6f
    }

    object Squircle : CornerBased(
        key = "squircle",
        IconCornerShape.Squircle,
        IconCornerShape.Squircle,
        IconCornerShape.Squircle,
        IconCornerShape.Squircle,
        1f, 1f, 1f, 1f,
    )

    object Sammy : CornerBased(
        key = "sammy",
        IconCornerShape.Sammy,
        IconCornerShape.Sammy,
        IconCornerShape.Sammy,
        IconCornerShape.Sammy,
        1f, 1f, 1f, 1f,
    )

    object Teardrop : CornerBased(
        key = "teardrop",
        IconCornerShape.arc,
        IconCornerShape.arc,
        IconCornerShape.arc,
        IconCornerShape.arc,
        1f, 1f, 1f, .3f,
    )

    object Cylinder : CornerBased(
        key = "cylinder",
        IconCornerShape.arc,
        IconCornerShape.arc,
        IconCornerShape.arc,
        IconCornerShape.arc,
        PointF(1f, .6f),
        PointF(1f, .6f),
        PointF(1f, .6f),
        PointF(1f, .6f),
    )

    object Cupertino : CornerBased(
        key = "cupertino",
        IconCornerShape.Cupertino,
        IconCornerShape.Cupertino,
        IconCornerShape.Cupertino,
        IconCornerShape.Cupertino,
        1f, 1f, 1f, 1f,
    ) {
        override val windowTransitionRadius = .45f
    }

    object Octagon : CornerBased(
        key = "octagon",
        IconCornerShape.Cut,
        IconCornerShape.Cut,
        IconCornerShape.Cut,
        IconCornerShape.Cut,
        .5f, .5f, .5f, .5f,
    )

    object Hexagon : CornerBased(
        key = "hexagon",
        IconCornerShape.CutHex,
        IconCornerShape.CutHex,
        IconCornerShape.CutHex,
        IconCornerShape.CutHex,
        PointF(1f, .5f),
        PointF(1f, .5f),
        PointF(1f, .5f),
        PointF(1f, .5f),
    )

    object Diamond : CornerBased(
        key = "diamond",
        IconCornerShape.Cut,
        IconCornerShape.Cut,
        IconCornerShape.Cut,
        IconCornerShape.Cut,
        1f, 1f, 1f, 1f,
    ) {
        override val windowTransitionRadius = 0f
    }

    object Egg : CornerBased(
        key = "egg",
        IconCornerShape.arc,
        IconCornerShape.arc,
        IconCornerShape.arc,
        IconCornerShape.arc,
        1f, 1f, 0.75f, 0.75f,
    ) {
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

    companion object {

        fun fromString(value: String, context: Context): IconShape? {
            if (value == "system") {
                runCatching {
                    return IconShapeManager.getSystemIconShape(context = context)
                }
            }
            return fromStringWithoutContext(value = value)
        }

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
            "" -> null
            else -> runCatching { parseCustomShape(value) }.getOrNull()
        }

        fun parseCustomShape(value: String): CornerBased {
            val parts = value.split("|")
            check(parts[0] == "v1") { "unknown config format" }
            check(parts.size == 5) { "invalid arguments size" }
            return CornerBased(
                "v1|${parts[1]}|${parts[2]}|${parts[3]}|${parts[4]}",
                Corner.fromString(parts[1]),
                Corner.fromString(parts[2]),
                Corner.fromString(parts[3]),
                Corner.fromString(parts[4]),
            )
        }

        fun isCustomShape(iconShape: IconShape): Boolean {
            return try {
                parseCustomShape(iconShape.toString())
                true
            } catch (e: Exception) {
                Log.e("IconShape", "Error creating shape $iconShape", e)
                false
            }
        }
    }

    data class Corner(val shape: IconCornerShape, val scale: PointF) {

        constructor(shape: IconCornerShape, scale: Float) : this(shape, PointF(scale, scale))

        override fun toString(): String {
            return "$shape,${scale.x},${scale.y}"
        }

        companion object {

            val fullArc = Corner(IconCornerShape.arc, 1f)

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
