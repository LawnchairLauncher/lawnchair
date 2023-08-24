/*
 * Copyright (C) 2023 The Android Open Source Project
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
@file:JvmName("BubbleDismissViewUtils")

package com.android.launcher3.taskbar.bubbles

import com.android.launcher3.R
import com.android.wm.shell.common.bubbles.DismissView

/**
 * Dismiss view is shared from WMShell. It requires setup with local resources.
 *
 * Usage:
 * - Kotlin `dismissView.setup()`
 * - Java `BubbleDismissViewUtils.setup(dismissView)`
 */
fun DismissView.setup() {
    setup(
        DismissView.Config(
            dismissViewResId = R.id.dismiss_view,
            targetSizeResId = R.dimen.bubblebar_dismiss_target_size,
            iconSizeResId = R.dimen.bubblebar_dismiss_target_icon_size,
            bottomMarginResId = R.dimen.bubblebar_dismiss_target_bottom_margin,
            floatingGradientHeightResId = R.dimen.bubblebar_dismiss_floating_gradient_height,
            floatingGradientColorResId = android.R.color.system_neutral1_900,
            backgroundResId = R.drawable.bg_bubble_dismiss_circle,
            iconResId = R.drawable.ic_bubble_dismiss_white
        )
    )
}
