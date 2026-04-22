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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.android.launcher3.widgetpicker.ui.components.TwoPaneLayoutDimensions.LEFT_PANE_WEIGHT
import com.android.launcher3.widgetpicker.ui.components.TwoPaneLayoutDimensions.RIGHT_PANE_WEIGHT
import com.android.launcher3.widgetpicker.ui.components.TwoPaneLayoutDimensions.horizontalPadding
import com.android.launcher3.widgetpicker.ui.components.TwoPaneLayoutDimensions.paneSpacing
import com.android.launcher3.widgetpicker.ui.components.TwoPaneLayoutDimensions.searchBarBottomMargin
import com.android.launcher3.widgetpicker.ui.theme.WidgetPickerTheme

/**
 * A layout that splits the widget picker content into two panes where the left pane takes about
 * [LEFT_PANE_WEIGHT] space while the right pane takes the remaining.
 *
 * The left pane always has a [searchBar] on the top and the left and right panes contain dynamic
 * content based on the user's actions.
 *
 * @param searchBar A sticky search bar shown on top in the left pane.
 * @param leftContent list options available for user to select that will be shown on left
 * @param rightContent content for the currently selected list option
 * @param rightPaneTitle when a user selects an option on left pane, changing this title for right
 *   pane guides the user that content for selected option is now visible on right. When using
 *   accessibility services like talkback, after selecting an option on left, the users can use four
 *   finger swipe down to move focus to the right pane.
 */
@Composable
fun TwoPaneLayout(
    searchBar: @Composable () -> Unit,
    leftContent: @Composable () -> Unit,
    rightContent: @Composable () -> Unit,
    rightPaneTitle: String?,
) {
    val rightPaneModifier =
        if (rightPaneTitle != null) {
            Modifier.semantics { paneTitle = rightPaneTitle }
        } else Modifier

    val leftPane: @Composable RowScope.() -> Unit = {
        Column(
            modifier =
                Modifier.semantics { isTraversalGroup = true }
                    .fillMaxHeight()
                    .padding(end = paneSpacing)
                    .weight(LEFT_PANE_WEIGHT)
        ) {
            searchBar()
            Spacer(modifier = Modifier.height(searchBarBottomMargin).fillMaxWidth())
            leftContent()
        }
    }

    val rightPane: @Composable RowScope.() -> Unit = {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                rightPaneModifier
                    .fillMaxHeight()
                    .weight(RIGHT_PANE_WEIGHT)
                    .clip(TwoPaneLayoutDimensions.rightPaneShape)
                    .background(WidgetPickerTheme.colors.widgetsContainerBackground)
                    .verticalScroll(rememberScrollState()),
        ) {
            rightContent()
        }
    }

    Row(modifier = Modifier.padding(horizontal = horizontalPadding).fillMaxSize()) {
        leftPane()
        rightPane()
    }
}

private object TwoPaneLayoutDimensions {
    const val LEFT_PANE_WEIGHT = 0.37f
    const val RIGHT_PANE_WEIGHT = 1 - LEFT_PANE_WEIGHT

    val horizontalPadding = 14.dp
    val paneSpacing = 16.dp

    val searchBarBottomMargin = 16.dp

    val rightPaneShape = RoundedCornerShape(28.dp)
}
