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

package com.android.launcher3.widgetpicker.theme

import androidx.compose.runtime.Composable
import com.android.launcher3.R
import com.android.launcher3.util.textStyleFromResource
import com.android.launcher3.widgetpicker.ui.theme.WidgetPickerTextStyles

/**
 * Parses styles from resources (R.style) to prepare [WidgetPickerTextStyles] for use in compose
 * theme for widget picker.
 */
@Composable
fun launcherWidgetPickerTextStyles(): WidgetPickerTextStyles {
    val searchBarTextStyle = textStyleFromResource(R.style.WidgetSearchBarText)
    val widgetHeaderTitleStyle = textStyleFromResource(R.style.WidgetListHeader_Title)
    val widgetHeaderSubTitleStyle = textStyleFromResource(R.style.WidgetListHeader_SubTitle)

    return WidgetPickerTextStyles(
        sheetTitle = textStyleFromResource(R.style.WidgetsTitle),
        sheetDescription = textStyleFromResource(R.style.WidgetPickerDescription),
        expandableListHeaderTitle = widgetHeaderTitleStyle,
        expandableListHeaderSubTitle = widgetHeaderSubTitleStyle,
        unSelectedListHeaderTitle = widgetHeaderTitleStyle,
        selectedListHeaderTitle = textStyleFromResource(R.style.WidgetListHeader_Title_Selected),
        unSelectedListHeaderSubTitle = widgetHeaderSubTitleStyle,
        selectedListHeaderSubTitle =
            textStyleFromResource(R.style.WidgetListHeader_SubTitle_Selected),
        widgetLabel = textStyleFromResource(R.style.WidgetLabel),
        widgetDescription = textStyleFromResource(R.style.WidgetDescription),
        widgetSpanText = textStyleFromResource(R.style.WidgetDims),
        addWidgetButtonLabel = textStyleFromResource(R.style.WidgetAddButton),
        toolbarUnSelectedTabLabel = textStyleFromResource(R.style.WidgetToolbarTabLabel),
        toolbarSelectedTabLabel = textStyleFromResource(R.style.WidgetToolbarTabLabel_Selected),
        searchBarText = searchBarTextStyle,
        searchBarPlaceholderText = searchBarTextStyle,
        noWidgetsErrorText = textStyleFromResource(R.style.WidgetErrorText),
    )
}
