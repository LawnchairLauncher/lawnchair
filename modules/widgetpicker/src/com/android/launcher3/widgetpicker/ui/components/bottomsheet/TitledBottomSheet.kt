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

package com.android.launcher3.widgetpicker.ui.components.bottomsheet

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import com.android.launcher3.widgetpicker.R
import com.android.launcher3.widgetpicker.shared.model.CloseBehavior
import com.android.launcher3.widgetpicker.ui.components.accessibility.LocalAccessibilityState
import com.android.launcher3.widgetpicker.ui.components.bottomsheet.TitledBottomSheetDimens.contentWindowInsets
import com.android.launcher3.widgetpicker.ui.components.bottomsheet.TitledBottomSheetDimens.headerBottomMargin
import com.android.launcher3.widgetpicker.ui.components.bottomsheet.TitledBottomSheetDimens.sheetInnerHorizontalPadding
import com.android.launcher3.widgetpicker.ui.components.bottomsheet.TitledBottomSheetDimens.sheetInnerTopPadding
import com.android.launcher3.widgetpicker.ui.components.bottomsheet.TitledBottomSheetDimens.sheetShape
import com.android.launcher3.widgetpicker.ui.components.bottomsheet.TitledBottomSheetDimens.sheetWindowInsets
import com.android.launcher3.widgetpicker.ui.theme.WidgetPickerTheme
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * A bottom sheet with title and description on the top. Intended to serve as a common container
 * structure for different types of widget pickers.
 *
 * @param modifier modifier to be applies to the bottom sheet container.
 * @param title A top level title for the bottom sheet. If title is absent, top header isn't shown.
 * @param description an optional short (1-2 line - max 80 char) description that can be shown below
 *   the title. At max font+display size it might overflow to 3 lines.
 * @param heightStyle indicates how much vertical space should the bottom sheet take; see
 *   [ModalBottomSheetHeightStyle].
 * @param showDragHandle whether to show drag handle; e.g. if the content doesn't need scrolling set
 *   this to false.
 * @param enableSwipeUpToDismiss whether to handle swipe up from bottom of sheet to close it.
 *   Setting this to true doesn't exclude the gesture nav stealing the touches automatically, the
 *   host need to ensure it has disabled gesture nav when passing true here.
 * @param onDismissSheet callback to be invoked when the bottom sheet is closed
 * @param content the content to be displayed below the [title] and [description]
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TitledBottomSheet(
    modifier: Modifier = Modifier,
    title: String?,
    description: String?,
    heightStyle: ModalBottomSheetHeightStyle,
    closeBehavior: CloseBehavior = CloseBehavior.DRAG_HANDLE,
    enforceStaticMaxSizes: Boolean = false,
    enableSwipeUpToDismiss: Boolean = false,
    onSheetOpen: () -> Unit,
    onDismissSheet: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        val density = LocalDensity.current
        val accessibilityState = LocalAccessibilityState.current
        val closeSheetLabel = stringResource(R.string.widget_picker_collapse_sheet_label)

        val sizeModifier =
            if (enforceStaticMaxSizes) {
                val sheetMaxWidth = dimensionResource(id = R.dimen.bottom_sheet_max_width)
                val sheetMaxHeight = dimensionResource(id = R.dimen.bottom_sheet_max_height)
                Modifier.widthIn(max = sheetMaxWidth).heightIn(max = sheetMaxHeight)
            } else {
                Modifier
            }

        BoxWithConstraints(
            modifier = modifier.then(sizeModifier).windowInsetsPadding(sheetWindowInsets)
        ) {
            val animSpec: AnimationSpec<Float> = MaterialTheme.motionScheme.slowSpatialSpec()
            val sheetState = remember {
                BottomSheetDismissState(expandCollapseAnimationSpec = animSpec)
            }

            Surface(
                modifier =
                    Modifier.semantics {
                            isTraversalGroup = true
                            customActions =
                                listOf(
                                    CustomAccessibilityAction(label = closeSheetLabel) {
                                        onDismissSheet()
                                        true
                                    }
                                )
                        }
                        .fillMaxSize()
                        .dismissibleBottomSheet(
                            sheetState = sheetState,
                            onSheetOpen = onSheetOpen,
                            onDismissSheet = onDismissSheet,
                            maxHeight = with(density) { maxHeight.toPx() },
                            enableNestedScrolling = !accessibilityState.isEnabled,
                        ),
                color = WidgetPickerTheme.colors.sheetBackground,
                shape = sheetShape,
                content = {
                    Column(
                        modifier =
                            Modifier.imePadding()
                                .windowInsetsPadding(contentWindowInsets)
                                .sheetContentHeight(heightStyle, maxHeight)
                                .padding(horizontal = sheetInnerHorizontalPadding)
                                .padding(
                                    top =
                                        sheetInnerTopPadding.takeIf {
                                            closeBehavior != CloseBehavior.DRAG_HANDLE
                                        } ?: 0.dp
                                )
                                .dismissableBottomSheetContent(sheetState)
                    ) {
                        if (closeBehavior == CloseBehavior.DRAG_HANDLE) {
                            DecorativeDragHandle(
                                modifier =
                                    Modifier.align(alignment = Alignment.CenterHorizontally)
                                        .padding(
                                            top = sheetInnerTopPadding,
                                            bottom = headerBottomMargin,
                                        )
                            )
                        }
                        title?.let {
                            Header(
                                title = title,
                                description = description,
                                closeBehavior = closeBehavior,
                                onDismissSheet = onDismissSheet,
                            )
                        }
                        content()
                    }
                },
            )

            if (enableSwipeUpToDismiss) {
                val scope = rememberCoroutineScope()

                SwipeUpToDismissHandler(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    contentHeight = maxHeight,
                    onProgress = { scope.launch { sheetState.backProgress.snapTo(it) } },
                    onCancel = { scope.launch { sheetState.settleProgress() } },
                    onClose = { scope.launch { sheetState.collapse() } },
                )
            }
        }
    }
}

@Composable
private fun SwipeUpToDismissHandler(
    modifier: Modifier,
    contentHeight: Dp,
    onProgress: (Float) -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
) {
    val density = LocalDensity.current

    var currentDragDistanceY by remember { mutableFloatStateOf(0f) }
    val targetDistanceY = remember { with(density) { contentHeight.toPx() / 2 } }
    // Distance user should have swiped up when releasing the drag that should lead to closing the
    // sheet.
    val swipeUpDistanceToClosePx = remember {
        with(density) { SwipeUpToDismissHandlerDimens.swipeUpDistanceToClose.toPx() }
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(SwipeUpToDismissHandlerDimens.gestureBoxHeight)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { currentDragDistanceY = it.y },
                        onDragCancel = { onCancel() },
                        onDragEnd = {
                            if (currentDragDistanceY < -swipeUpDistanceToClosePx) {
                                onClose()
                            } else {
                                onCancel()
                            }
                        },
                    ) { _, dragAmount ->
                        currentDragDistanceY += dragAmount.y
                        onProgress(abs(currentDragDistanceY / targetDistanceY).coerceIn(0f, 1f))
                    }
                }
    )
}

@Composable
private fun Header(
    title: String,
    description: String?,
    closeBehavior: CloseBehavior,
    onDismissSheet: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(bottom = headerBottomMargin).fillMaxWidth(),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                modifier = Modifier.align(Alignment.Center),
                maxLines = 1,
                text = title,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = WidgetPickerTheme.typography.sheetTitle,
                color = WidgetPickerTheme.colors.sheetTitle,
            )
            if (closeBehavior == CloseBehavior.CLOSE_BUTTON) {
                IconButton(
                    onClick = onDismissSheet,
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription =
                            stringResource(R.string.widget_picker_collapse_sheet_label),
                        tint = WidgetPickerTheme.colors.sheetTitle,
                    )
                }
            }
        }
        description?.let {
            Text(
                text = it,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = WidgetPickerTheme.typography.sheetDescription,
                color = WidgetPickerTheme.colors.sheetDescription,
            )
        }
    }
}

@Composable
private fun Modifier.sheetContentHeight(
    style: ModalBottomSheetHeightStyle,
    maxHeight: Dp,
): Modifier {
    val heightModifier =
        when (style) {
            ModalBottomSheetHeightStyle.FILL_HEIGHT -> this.fillMaxHeight()

            ModalBottomSheetHeightStyle.WRAP_CONTENT -> this.wrapContentHeight()
        }

    return if (maxHeight > 1200.dp) {
        // Cap the height to max 2/3 of total window height; so the bottom sheet doesn't feel too
        // huge.
        heightModifier.heightIn(max = 2 * maxHeight / 3)
    } else {
        heightModifier
    }
}

@Composable
private fun DecorativeDragHandle(modifier: Modifier) {
    Box(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.outline)
                .size(
                    width = DragHandleDimens.dragHandleWidth,
                    height = DragHandleDimens.dragHandleHeight,
                )
    )
}

/**
 * Describes how should the default height of the bottom sheet look like (excluding the insets such
 * as status bar).
 */
enum class ModalBottomSheetHeightStyle {
    /**
     * Fills the available height; capped to a max for extra tall cases. Useful for cases where
     * irrespective of content, we want it to be expanded fully.
     */
    FILL_HEIGHT,

    /**
     * Wraps the content's height; capped to a max for extra tall cases. Set up vertical scrolling
     * if the content can be longer than the available height. Useful for cases like single app
     * widget picker or pin widget picker that don't need to expand fully.
     */
    WRAP_CONTENT,
}

private object DragHandleDimens {
    val dragHandleHeight = 4.dp
    val dragHandleWidth = 32.dp
}

private object SwipeUpToDismissHandlerDimens {
    val gestureBoxHeight = 20.dp
    val swipeUpDistanceToClose = 28.dp
}

private object TitledBottomSheetDimens {
    val sheetInnerTopPadding = 16.dp
    val sheetInnerHorizontalPadding = 10.dp
    val headerBottomMargin = 16.dp

    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    val sheetWindowInsets: WindowInsets
        @Composable
        get() =
            WindowInsets.statusBars.union(
                WindowInsets.displayCutout.only(
                    sides = WindowInsetsSides.Horizontal + WindowInsetsSides.Top
                )
            )

    val contentWindowInsets: WindowInsets
        @Composable
        get() =
            WindowInsets.safeDrawing.only(sides = WindowInsetsSides.Bottom + WindowInsetsSides.Top)
}
