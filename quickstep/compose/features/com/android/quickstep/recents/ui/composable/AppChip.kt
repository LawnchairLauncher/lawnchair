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

package com.android.quickstep.recents.ui.composable

import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.app.animation.Interpolators
import com.android.launcher3.R
import com.android.launcher3.util.FontScalePreviews
import com.android.launcher3.util.painterResource
import com.android.launcher3.util.toComposeEasing
import com.android.quickstep.recents.ui.composable.AppChip.IconSize
import com.android.quickstep.recents.ui.composable.AppChip.IconSizeExpanded
import com.android.quickstep.recents.ui.viewmodel.TaskData
import com.android.quickstep.recents.ui.viewmodel.TaskTileUiState
import com.android.quickstep.recents.ui.viewmodel.TaskViewModel
import kotlinx.coroutines.flow.map

private data class AppChipInfo(val title: String, val icon: Drawable, val expanded: Boolean)

private fun mapAppChipInfo(state: TaskTileUiState, taskId: Int): AppChipInfo? =
    state.tasks
        .firstOrNull { it.taskId == taskId }
        .let {
            if (it is TaskData.Data && it.title != null && it.icon != null) {
                AppChipInfo(
                    title = it.title,
                    icon = it.icon,
                    expanded = false,
                    // TODO: The state needs to be implemented in the VM when this AppChip
                    // is added to the TaskView. The state will be called isMenuExpanded, and it
                    // indicates whether the menu is expanded or not. When the chip is clicked,
                    // it will notify the VM that the menu is expanded. Then, this property will
                    // be either Open or Collapsed.
                    // state.isMenuExpanded is MenuState.Open &&
                    // state.isMenuExpanded.taskId == taskId,
                )
            } else {
                null
            }
        }

@Composable
fun TaskAppChip(
    viewModel: TaskViewModel,
    taskId: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val chipState: AppChipInfo? by
        viewModel.state.map { mapAppChipInfo(it, taskId) }.collectAsState(null)

    chipState?.let {
        AppChip(
            title = it.title,
            icon = it.icon,
            expanded = it.expanded,
            onClick = {
                // viewModel.toggleMenu(taskId, !it.expanded)
                onClick()
            },
            onLongClick = {
                // viewModel.toggleMenu(taskId, !it.expanded)
                onLongClick()
            },
        )
    }
}

@Composable
fun AppChip(
    title: String,
    icon: Drawable,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    interactionSource: MutableInteractionSource? = remember { MutableInteractionSource() },
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    val style = AppChip.getStyle(expanded)
    var isFocused = remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Box(modifier = Modifier.height(52.dp), contentAlignment = Alignment.CenterStart) {
        Row(
            modifier =
                modifier
                    .offset { IntOffset(style.startOffset.roundToPx(), 0) }
                    .size(style.width, style.height)
                    .shadow(AppChip.Shadow, AppChip.ChipShape)
                    .drawBehind {
                        drawRoundRect(
                            color = style.backgroundColor,
                            size = DpSize(style.width, style.height).toSize(),
                        )
                    }
                    .focusBorder(isFocused, width = 2.dp, color = style.focusBorderColor)
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            onClick.invoke()
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongClick.invoke()
                        },
                    )
                    .clip(AppChip.ChipShape)
                    .padding(
                        start = style.startPadding,
                        top = style.topPadding,
                        end = style.endPadding,
                        bottom = style.bottomPadding,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(icon),
                modifier = Modifier.requiredSize(IconSize).scale(style.iconScale).clip(CircleShape),
                contentDescription = title,
            )
            Text(
                text = title,
                modifier =
                    Modifier.padding(start = style.dividerSize)
                        .weight(1f)
                        .fadingEdge()
                        .heightIn(max = IconSizeExpanded)
                        .then(if (expanded) Modifier.basicMarquee(1) else Modifier),
                maxLines = 1,
                overflow = TextOverflow.Visible,
                softWrap = false,
                style =
                    TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = style.contentColor,
                    ),
            )
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_chevron_down),
                contentDescription = "",
                modifier = Modifier.size(24.dp).scale(1f, style.chevronScaleY),
                tint = style.contentColor,
            )
        }
    }
}

fun Modifier.focusBorder(
    isFocused: MutableState<Boolean>,
    width: Dp = 1.dp,
    color: Color = Color.Black,
    shape: Shape = CircleShape,
): Modifier =
    this.onFocusChanged { isFocused.value = it.isFocused }
        .focusable()
        .then(if (isFocused.value) Modifier.border(width, color, shape) else Modifier)

fun Modifier.fadingEdge(): Modifier {
    val fade = Brush.horizontalGradient(.8f to Color.Red, 1f to Color.Transparent)
    return this.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
        drawContent()
        drawRect(brush = fade, blendMode = BlendMode.DstIn)
    }
}

private object AppChip {
    private const val REVEAL_DURATION = 417
    private const val HIDE_DURATION = 333

    val Shadow = 4.dp
    val IconSize = 24.dp
    val IconSizeExpanded = 32.dp
    val ChipShape = RoundedCornerShape(100.dp)

    private val Collapsed =
        AppChipSizes(
            size = DpSize(156.dp, 36.dp),
            startPadding = 6.dp,
            endPadding = 8.dp,
            topPadding = 6.dp,
            bottomPadding = 6.dp,
            offset = DpOffset(6.dp, 12.dp),
            dividerSize = 8.dp,
            chevronScaleY = 1f,
            iconScale = 1f,
        )

    private val Expanded =
        AppChipSizes(
            size = DpSize(216.dp, 52.dp),
            startPadding = 10.dp,
            endPadding = 10.dp,
            topPadding = 10.dp,
            bottomPadding = 10.dp,
            offset = DpOffset(0.dp, 4.dp),
            dividerSize = 12.dp,
            chevronScaleY = -1f,
            iconScale = IconSizeExpanded / IconSize,
        )

    @Composable
    fun getStyle(isExpanded: Boolean): AppChipStyle {
        val backgroundColor = MaterialTheme.colorScheme.surfaceBright
        val contentColor = MaterialTheme.colorScheme.contentColorFor(backgroundColor)
        val focusBorderColor = MaterialTheme.colorScheme.secondary

        val transition =
            updateTransition(if (isExpanded) Expanded else Collapsed, label = "AppChip State")
        val emphasizedEasing = Interpolators.EMPHASIZED.toComposeEasing()
        val duration = if (isExpanded) HIDE_DURATION else REVEAL_DURATION
        val animationSpecInDp = tween<Dp>(durationMillis = duration, easing = emphasizedEasing)
        val animationSpecInFloat =
            tween<Float>(durationMillis = duration, easing = emphasizedEasing)

        val width = transition.animateDp({ animationSpecInDp }) { it.size.width }
        val height = transition.animateDp({ animationSpecInDp }) { it.size.height }
        val iconScale = transition.animateFloat({ animationSpecInFloat }) { it.iconScale }
        val dividerSize = transition.animateDp({ animationSpecInDp }) { it.dividerSize }
        val chevronScaleY = transition.animateFloat({ animationSpecInFloat }) { it.chevronScaleY }
        val animatedStartOffset = transition.animateDp({ animationSpecInDp }) { it.offset.x }

        val startPadding = transition.animateDp({ animationSpecInDp }) { it.startPadding }
        val endPadding = transition.animateDp({ animationSpecInDp }) { it.endPadding }
        val topPadding = transition.animateDp({ animationSpecInDp }) { it.topPadding }
        val bottomPadding = transition.animateDp({ animationSpecInDp }) { it.bottomPadding }

        return remember(transition) {
            AppChipStyle(
                backgroundColor = backgroundColor,
                contentColor = contentColor,
                focusBorderColor = focusBorderColor,
                width = width,
                height = height,
                iconScale = iconScale,
                dividerSize = dividerSize,
                chevronScaleY = chevronScaleY,
                animatedStartOffset = animatedStartOffset,
                startPadding = startPadding,
                endPadding = endPadding,
                topPadding = topPadding,
                bottomPadding = bottomPadding,
            )
        }
    }

    private data class AppChipSizes(
        val size: DpSize,
        val startPadding: Dp,
        val endPadding: Dp,
        val topPadding: Dp,
        val bottomPadding: Dp,
        val offset: DpOffset,
        val dividerSize: Dp,
        val chevronScaleY: Float,
        val iconScale: Float,
    )
}

private class AppChipStyle(
    val backgroundColor: Color,
    val contentColor: Color,
    val focusBorderColor: Color,
    width: State<Dp>,
    height: State<Dp>,
    iconScale: State<Float>,
    dividerSize: State<Dp>,
    chevronScaleY: State<Float>,
    animatedStartOffset: State<Dp>,
    startPadding: State<Dp>,
    endPadding: State<Dp>,
    topPadding: State<Dp>,
    bottomPadding: State<Dp>,
) {
    val width by width
    val height by height
    val iconScale by iconScale
    val dividerSize by dividerSize
    val chevronScaleY by chevronScaleY
    val startOffset by animatedStartOffset
    val startPadding by startPadding
    val endPadding by endPadding
    val topPadding by topPadding
    val bottomPadding by bottomPadding
}

@Composable
@PreviewLightDark
@FontScalePreviews
private fun ChipPreview() {
    val (expanded, setExpanded) = remember { mutableStateOf(false) }
    val appName = "Very long-name-for the app that won't fit in the chip"
    val appIcon =
        AppCompatResources.getDrawable(
            LocalContext.current,
            R.drawable.ic_conversations_widget_category,
        )

    MaterialTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Box(modifier = Modifier.widthIn(170.dp)) {
                AppChip(
                    title = appName,
                    icon = appIcon!!,
                    expanded = expanded,
                    onClick = { setExpanded(!expanded) },
                )
            }
        }
    }
}
