package ch.deletescape.lawnchair.util

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.Dp

// Adapted from https://gist.github.com/Gowsky/4613829b2e94c846a5bbcd41367662d9.

private const val MAGIC_FLOAT = 1.2f
private val HairlineBorderStroke = Stroke(Stroke.HairlineWidth)

inline val Float.half: Float
    get() = this / 2

fun Modifier.smartBorder(border: BorderStroke, shape: Shape = RectangleShape) =
    smartBorder(width = border.width, brush = border.brush, shape = shape)

fun Modifier.smartBorder(width: Dp, color: Color, shape: Shape = RectangleShape) =
    smartBorder(width, SolidColor(color), shape)

fun Modifier.smartBorder(width: Dp, brush: Brush, shape: Shape): Modifier = composed(
    factory = {
        this.then(
            Modifier.drawWithCache {
                val outline: Outline = shape.createOutline(size, layoutDirection, this)
                val borderSize = if (width == Dp.Hairline) 1f else width.toPx()

                var insetOutline: Outline? = null
                var stroke: Stroke? = null
                var pathClip: Path? = null
                var inset = 0f
                var insetPath: Path? = null


                val cornerCompensation = width.toPx().half * MAGIC_FLOAT
                if (borderSize > 0 && size.minDimension > 0f) {
                    if (outline is Outline.Rectangle) {
                        stroke = Stroke(borderSize)
                    } else {
                        val strokeWidth = MAGIC_FLOAT * borderSize
                        inset = borderSize - strokeWidth / 2
                        val insetSize = Size(
                            size.width - inset * 2,
                            size.height - inset * 2
                        )
                        insetOutline = shape.createOutline(insetSize, layoutDirection, this)
                        stroke = Stroke(strokeWidth)
                        pathClip = if (outline is Outline.Rounded) {
                            Path().apply { addRoundRect(outline.roundRect) }
                        } else if (outline is Outline.Generic) {
                            outline.path
                        } else {
                            null
                        }

                        insetPath =
                            if (insetOutline is Outline.Rounded &&
                                !insetOutline.roundRect.isSimple
                            ) {
                                Path().apply {
                                    val rect = insetOutline.roundRect
                                    addRoundRect(
                                        RoundRect(
                                            rect.left, rect.top, rect.right, rect.bottom,
                                            CornerRadius(
                                                rect.topLeftCornerRadius.x - cornerCompensation,
                                                rect.topLeftCornerRadius.y - cornerCompensation
                                            )
                                        )
                                    )
                                    translate(Offset(inset, inset))
                                }
                            } else if (insetOutline is Outline.Generic) {
                                Path().apply {
                                    addPath(insetOutline.path, Offset(inset, inset))
                                }
                            } else {
                                null
                            }
                    }
                }

                onDrawWithContent {
                    drawContent()
                    if (stroke != null) {
                        if (insetOutline != null && pathClip != null) {
                            val isSimpleRoundRect = insetOutline is Outline.Rounded &&
                                    insetOutline.roundRect.isSimple
                            withTransform({
                                clipPath(pathClip)
                                if (isSimpleRoundRect) {
                                    translate(inset, inset)
                                }
                            }) {
                                if (isSimpleRoundRect) {
                                    val rrect = (insetOutline as Outline.Rounded).roundRect
                                    drawRoundRect(
                                        brush = brush,
                                        topLeft = Offset(rrect.left, rrect.top),
                                        size = Size(rrect.width, rrect.height),
                                        cornerRadius = CornerRadius(
                                            rrect.topLeftCornerRadius.x - cornerCompensation,
                                            rrect.topLeftCornerRadius.y - cornerCompensation
                                        ),
                                        style = stroke
                                    )
                                } else if (insetPath != null) {
                                    drawPath(insetPath, brush, style = stroke)
                                }
                            }
                            clipRect {
                                if (isSimpleRoundRect) {
                                    val rrect = (outline as Outline.Rounded).roundRect
                                    drawRoundRect(
                                        brush = brush,
                                        topLeft = Offset(rrect.left, rrect.top),
                                        size = Size(rrect.width, rrect.height),
                                        cornerRadius = rrect.topLeftCornerRadius,
                                        style = HairlineBorderStroke
                                    )
                                } else {
                                    drawPath(pathClip, brush = brush, style = HairlineBorderStroke)
                                }
                            }
                        } else {
                            val strokeWidth = stroke.width
                            val halfStrokeWidth = strokeWidth / 2
                            drawRect(
                                brush = brush,
                                topLeft = Offset(halfStrokeWidth, halfStrokeWidth),
                                size = Size(
                                    size.width - strokeWidth,
                                    size.height - strokeWidth
                                ),
                                style = stroke
                            )
                        }
                    }
                }
            }
        )
    },
    inspectorInfo = debugInspectorInfo {
        name = "border"
        properties["width"] = width
        if (brush is SolidColor) {
            properties["color"] = brush.value
            value = brush.value
        } else {
            properties["brush"] = brush
        }
        properties["shape"] = shape
    }
)