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

import android.content.Context
import android.content.res.Resources
import android.util.DisplayMetrics
import android.util.TypedValue

/** Convert [this] number of dps to device pixels. */
fun Number.dpToPx(context: Context): Float = dpToPx(resources = context.resources)

/** Convert [this] number of dps to device pixels. */
fun Number.dpToPx(resources: Resources): Float = dpToPx(displayMetrics = resources.displayMetrics)

/** Convert [this] number of dps to device pixels. */
fun Number.dpToPx(displayMetrics: DisplayMetrics): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, toFloat(), displayMetrics)
