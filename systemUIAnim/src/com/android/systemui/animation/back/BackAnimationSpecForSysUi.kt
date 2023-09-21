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

package com.android.systemui.animation.back

import android.util.DisplayMetrics

/**
 * SysUI transitions - Dismiss app (ST1) Return to launching surface or place of origin
 * https://carbon.googleplex.com/predictive-back-for-apps/pages/st-1-dismiss-app
 */
fun BackAnimationSpec.Companion.dismissAppForSysUi(
    displayMetrics: DisplayMetrics,
): BackAnimationSpec =
    BackAnimationSpec.createFloatingSurfaceAnimationSpec(
        displayMetrics = displayMetrics,
        maxMarginXdp = 8f,
        maxMarginYdp = 8f,
        minScale = 0.8f,
    )

/**
 * SysUI transitions - Cross task (ST2) Return to previous task/app, keeping the current one open
 * https://carbon.googleplex.com/predictive-back-for-apps/pages/st-2-cross-task
 */
fun BackAnimationSpec.Companion.crossTaskForSysUi(
    displayMetrics: DisplayMetrics,
): BackAnimationSpec =
    BackAnimationSpec.createFloatingSurfaceAnimationSpec(
        displayMetrics = displayMetrics,
        maxMarginXdp = 8f,
        maxMarginYdp = 8f,
        minScale = 0.8f,
    )

/**
 * SysUI transitions - Inner area dismiss (ST3) Dismiss non-detachable surface
 * https://carbon.googleplex.com/predictive-back-for-apps/pages/st-3-inner-area-dismiss
 */
fun BackAnimationSpec.Companion.innerAreaDismissForSysUi(
    displayMetrics: DisplayMetrics,
): BackAnimationSpec =
    BackAnimationSpec.createFloatingSurfaceAnimationSpec(
        displayMetrics = displayMetrics,
        maxMarginXdp = 0f,
        maxMarginYdp = 0f,
        minScale = 0.9f,
    )

/**
 * SysUI transitions - Floating system surfaces (ST4)
 * https://carbon.googleplex.com/predictive-back-for-apps/pages/st-4-floating-system-surfaces
 */
fun BackAnimationSpec.Companion.floatingSystemSurfacesForSysUi(
    displayMetrics: DisplayMetrics,
): BackAnimationSpec =
    BackAnimationSpec.createFloatingSurfaceAnimationSpec(
        displayMetrics = displayMetrics,
        maxMarginXdp = 8f,
        maxMarginYdp = 8f,
        minScale = 0.8f,
    )
