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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import com.android.launcher3.widgetpicker.ui.components.DragHandleDimens.dragHandleHeight
import com.android.launcher3.widgetpicker.ui.components.DragHandleDimens.dragHandleWidth
import com.android.launcher3.widgetpicker.ui.components.TitledBottomSheetDimens.headerBottomMargin
import com.android.launcher3.widgetpicker.ui.components.TitledBottomSheetDimens.sheetInnerHorizontalPadding
import com.android.launcher3.widgetpicker.ui.components.TitledBottomSheetDimens.sheetInnerTopPadding
import com.android.launcher3.widgetpicker.ui.theme.WidgetPickerTheme
import com.android.launcher3.widgetpicker.ui.windowsizeclass.WindowInfo
import com.android.launcher3.widgetpicker.ui.windowsizeclass.calculateWindowInfo
import com.android.launcher3.widgetpicker.ui.windowsizeclass.isExtraTall

/**
 * A bottom sheet with title and description on the top. Intended to serve as a common container
 * structure for different types of widget pickers.
 *
 * @param modifier modifier to be applies to the bottom sheet container.
 * @param title A top level title for the bottom sheet. If title is absent, top header isn't shown.
 * @param description an optional short (1-2 line) description that can be shown below the title.
 * @param heightStyle indicates how much vertical space should the bottom sheet take; see
 *   [ModalBottomSheetHeightStyle].
 * @param showDragHandle whether to show drag handle; e.g. if the content doesn't need scrolling set
 *   this to false.
 * @param onDismissRequest callback to be invoked when the bottom sheet is closed
 * @param content the content to be displayed below the [title] and [description]
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TitledBottomSheet(
    modifier: Modifier = Modifier,
    title: String?,
    description: String?,
    heightStyle: ModalBottomSheetHeightStyle,
    showDragHandle: Boolean = true,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    val modalBottomSheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { value -> value != SheetValue.PartiallyExpanded },
        )
    val windowInfo = LocalConfiguration.current.calculateWindowInfo()

    val dragHandle: (@Composable () -> Unit)? =
        remember(showDragHandle) {
            if (showDragHandle) {
                {
                    DragHandle(
                        modifier =
                            Modifier.padding(
                                top = sheetInnerTopPadding,
                                bottom = headerBottomMargin,
                            )
                    )
                }
            } else null
        }

    ModalBottomSheet(
        sheetState = modalBottomSheetState,
        sheetGesturesEnabled = false,
        sheetMaxWidth = Dp.Unspecified,
        containerColor = WidgetPickerTheme.colors.sheetBackground,
        onDismissRequest = onDismissRequest,
        dragHandle = dragHandle,
        modifier = modifier.windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Column(
            modifier =
                Modifier.sheetContentHeight(heightStyle, windowInfo)
                    .padding(horizontal = sheetInnerHorizontalPadding)
                    .padding(top = sheetInnerTopPadding.takeIf { !showDragHandle } ?: 0.dp)
        ) {
            title?.let { Header(title = title, description = description) }
            Box(modifier = Modifier.windowInsetsPadding(WindowInsets.displayCutout)) { content() }
        }
    }
}

@Composable
private fun DragHandle(modifier: Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.outline,
        shape = MaterialTheme.shapes.medium,
    ) {
        Box(Modifier.size(width = dragHandleWidth, height = dragHandleHeight))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Header(title: String, description: String?) {
    Column(modifier = Modifier.padding(bottom = headerBottomMargin).fillMaxWidth()) {
        Text(
            modifier = Modifier.wrapContentHeight().fillMaxWidth(),
            maxLines = 1,
            text = title,
            textAlign = TextAlign.Center,
            style = WidgetPickerTheme.typography.sheetTitle,
            color = WidgetPickerTheme.colors.sheetTitle,
        )
        description?.let {
            Text(
                modifier = Modifier.wrapContentHeight().fillMaxWidth(),
                maxLines = 2,
                text = it,
                textAlign = TextAlign.Center,
                style = WidgetPickerTheme.typography.sheetDescription,
                color = WidgetPickerTheme.colors.sheetDescription,
            )
        }
    }
}

private object TitledBottomSheetDimens {
    val sheetInnerTopPadding = 16.dp
    val sheetInnerHorizontalPadding = 10.dp
    val headerBottomMargin = 16.dp
}

private object DragHandleDimens {
    val dragHandleHeight = 4.dp
    val dragHandleWidth = 32.dp
}

/** Default values for the [TitledBottomSheet] component. */
object TitledBottomSheetDefaults {
    /** Animation duration for the bottom sheet to fully expand. */
    const val SLIDE_IN_ANIMATION_DURATION: Long = 400L
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

@Composable
private fun Modifier.sheetContentHeight(
    style: ModalBottomSheetHeightStyle,
    windowInfo: WindowInfo,
): Modifier {
    val heightModifier =
        when (style) {
            ModalBottomSheetHeightStyle.FILL_HEIGHT -> this.fillMaxHeight()

            ModalBottomSheetHeightStyle.WRAP_CONTENT -> this.wrapContentHeight()
        }

    return if (windowInfo.isExtraTall()) {
        // Cap the height to max 2/3 of total window height; so the bottom sheet doesn't feel too
        // huge.
        heightModifier.heightIn(max = 2 * windowInfo.size.height / 3)
    } else {
        heightModifier
    }
}
