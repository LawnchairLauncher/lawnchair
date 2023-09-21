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

package com.android.systemui.util

import android.app.Dialog
import android.view.View
import android.window.OnBackInvokedDispatcher
import com.android.systemui.animation.back.BackAnimationSpec
import com.android.systemui.animation.back.BackTransformation
import com.android.systemui.animation.back.applyTo
import com.android.systemui.animation.back.floatingSystemSurfacesForSysUi
import com.android.systemui.animation.back.onBackAnimationCallbackFrom
import com.android.systemui.animation.back.registerOnBackInvokedCallbackOnViewAttached

/**
 * Register on the Dialog's [OnBackInvokedDispatcher] an animation using the [BackAnimationSpec].
 * The [BackTransformation] will be applied on the [targetView].
 */
@JvmOverloads
fun Dialog.registerAnimationOnBackInvoked(
    targetView: View,
    backAnimationSpec: BackAnimationSpec =
        BackAnimationSpec.floatingSystemSurfacesForSysUi(
            displayMetrics = targetView.resources.displayMetrics,
        ),
) {
    targetView.registerOnBackInvokedCallbackOnViewAttached(
        onBackInvokedDispatcher = onBackInvokedDispatcher,
        onBackAnimationCallback =
            onBackAnimationCallbackFrom(
                backAnimationSpec = backAnimationSpec,
                displayMetrics = targetView.resources.displayMetrics,
                onBackProgressed = { backTransformation -> backTransformation.applyTo(targetView) },
                onBackInvoked = { dismiss() },
            ),
    )
}
