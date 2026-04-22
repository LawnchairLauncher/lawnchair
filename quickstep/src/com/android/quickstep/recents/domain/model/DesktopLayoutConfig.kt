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

package com.android.quickstep.recents.domain.model

/**
 * Holds pre-scaled configuration values related to desktop task layout dimensions. These values are
 * typically derived from resources and then scaled according to the current view and screen
 * dimensions.
 *
 * @property topBottomMarginOneRow Scaled margin for top/bottom when one row is shown.
 * @property topMarginMultiRows Scaled top margin when multiple rows are shown.
 * @property bottomMarginMultiRows Scaled bottom margin when multiple rows are shown.
 * @property leftRightMarginOneRow Scaled margin for left/right when one row is shown.
 * @property leftRightMarginMultiRows Scaled margin for left/right when multiple rows are shown.
 * @property horizontalPaddingBetweenTasks Scaled horizontal padding between tasks.
 * @property verticalPaddingBetweenTasks Scaled vertical padding between tasks.
 */
data class DesktopLayoutConfig(
    val topBottomMarginOneRow: Int,
    val topMarginMultiRows: Int,
    val bottomMarginMultiRows: Int,
    val leftRightMarginOneRow: Int,
    val leftRightMarginMultiRows: Int,
    val horizontalPaddingBetweenTasks: Int,
    val verticalPaddingBetweenTasks: Int,
)
