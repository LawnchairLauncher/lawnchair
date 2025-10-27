/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.launcher3.taskbar.bubbles

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import com.android.launcher3.icons.BaseIconFactory

/** Bubble icons factory for the bubble bar. */
class BubbleBarBubbleIconsFactory(context: Context, bubbleSize: Int) :
    BaseIconFactory(context, context.resources.configuration.densityDpi, bubbleSize) {

    /** Creates shadowed icon for the bubble bar. */
    fun createShadowedIconBitmap(
        icon: Drawable,
        scale: Float,
    ): Bitmap = super.createIconBitmap(icon, scale, MODE_WITH_SHADOW)
}
