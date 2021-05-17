/*
 * Copyright 2021, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Adapted from https://gist.github.com/Gowsky/4613829b2e94c846a5bbcd41367662d9.

package app.lawnchair.util

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.Dp

private const val MAGIC_FLOAT = 1.2f
private val HairlineBorderStroke = Stroke(Stroke.HairlineWidth)

inline val Float.half: Float
    get() = this / 2

fun Modifier.smartBorder(border: BorderStroke, shape: Shape = RectangleShape) =
    smartBorder(width = border.width, brush = border.brush, shape = shape)

fun Modifier.smartBorder(width: Dp, color: Color, shape: Shape = RectangleShape) =
    smartBorder(width, SolidColor(color), shape)

fun Modifier.smartBorder(width: Dp, brush: Brush, shape: Shape): Modifier = composed(
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
) {
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
                pathClip = when (outline) {
                    is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                    is Outline.Generic -> outline.path
                    else -> null
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
                            val rRect = (insetOutline as Outline.Rounded).roundRect
                            drawRoundRect(
                                brush = brush,
                                topLeft = Offset(rRect.left, rRect.top),
                                size = Size(rRect.width, rRect.height),
                                cornerRadius = CornerRadius(
                                    rRect.topLeftCornerRadius.x - cornerCompensation,
                                    rRect.topLeftCornerRadius.y - cornerCompensation
                                ),
                                style = stroke
                            )
                        } else if (insetPath != null) {
                            drawPath(insetPath, brush, style = stroke)
                        }
                    }
                    clipRect {
                        if (isSimpleRoundRect) {
                            val rRect = (outline as Outline.Rounded).roundRect
                            drawRoundRect(
                                brush = brush,
                                topLeft = Offset(rRect.left, rRect.top),
                                size = Size(rRect.width, rRect.height),
                                cornerRadius = rRect.topLeftCornerRadius,
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
}
