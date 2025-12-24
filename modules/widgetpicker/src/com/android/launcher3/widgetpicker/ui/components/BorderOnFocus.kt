/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3.widgetpicker.ui.components

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Provides a rounded rect border when the element is focused.
 *
 * Same as system ui's `borderOnFocus`.
 */
internal fun Modifier.borderOnFocus(
    enabled: Boolean,
    color: Color,
    cornerSize: CornerSize,
    strokeWidth: Dp = 3.dp,
    padding: Dp = 2.dp,
) = this then BorderOnFocusElement(enabled, color, cornerSize, strokeWidth, padding)

private class BorderOnFocusNode(
    var enabled: Boolean,
    var color: Color,
    var cornerSize: CornerSize,
    var strokeWidth: Dp,
    var padding: Dp,
) : FocusEventModifierNode, DrawModifierNode, Modifier.Node() {

    private var focused by mutableStateOf(false)

    override fun onFocusEvent(focusState: FocusState) {
        focused = focusState.isFocused
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val focusOutline = Rect(Offset.Zero, size).inflate(padding.toPx())
        if (enabled && focused) {
            drawRoundRect(
                color = color,
                topLeft = focusOutline.topLeft,
                size = focusOutline.size,
                cornerRadius = CornerRadius(cornerSize.toPx(focusOutline.size, this)),
                style = Stroke(strokeWidth.toPx()),
            )
        }
    }
}

private data class BorderOnFocusElement(
    val enabled: Boolean,
    val color: Color,
    val cornerSize: CornerSize,
    val strokeWidth: Dp,
    val padding: Dp,
) : ModifierNodeElement<BorderOnFocusNode>() {
    override fun create(): BorderOnFocusNode {
        return BorderOnFocusNode(enabled, color, cornerSize, strokeWidth, padding)
    }

    override fun update(node: BorderOnFocusNode) {
        node.enabled = enabled
        node.color = color
        node.cornerSize = cornerSize
        node.strokeWidth = strokeWidth
        node.padding = padding
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "borderOnFocus"
        properties["enabled"] = enabled
        properties["color"] = color
        properties["cornerSize"] = cornerSize
        properties["strokeWidth"] = strokeWidth
        properties["padding"] = padding
    }
}
