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
@file:JvmName("DismissViewUtils")

package com.android.wm.shell.bubbles

import androidx.annotation.DimenRes
import com.android.wm.shell.R
import com.android.wm.shell.shared.bubbles.DismissView
import com.android.wm.shell.shared.R as SharedR

private val defaultConfig =
    DismissView.Config(
        dismissViewResId = R.id.dismiss_view,
        targetSizeResId = SharedR.dimen.floating_dismiss_background_size,
        iconSizeResId = SharedR.dimen.floating_dismiss_icon_size,
        bottomMarginResId = R.dimen.floating_dismiss_bottom_margin,
        floatingGradientHeightResId = R.dimen.floating_dismiss_gradient_height,
        floatingGradientColorResId = android.R.color.system_neutral1_900,
        backgroundResId = SharedR.drawable.floating_dismiss_background,
        iconResId = SharedR.drawable.floating_dismiss_ic_close,
        applyMarginOverNavBarInset = true,
    )

fun DismissView.setup() {
    setup(defaultConfig)
}

fun DismissView.setupWithMarginIgnoringNavBarInset(@DimenRes bottomMarginResId: Int) {
    setup(
        defaultConfig.copy(
            bottomMarginResId = bottomMarginResId,
            applyMarginOverNavBarInset = false,
        )
    )
}
