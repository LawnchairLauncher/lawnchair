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

package com.android.launcher3

import com.android.launcher3.AbstractFloatingView.FloatingViewType
import com.android.launcher3.views.ActivityContext

/**
 * Helper class for manaing AbstractFloatingViews which shows a floating UI on top of the launcher
 * UI.
 */
class AbstractFloatingViewHelper {
    fun closeOpenViews(activity: ActivityContext, animate: Boolean, @FloatingViewType type: Int) {
        val dragLayer = activity.getDragLayer()
        // Iterate in reverse order. AbstractFloatingView is added later to the dragLayer,
        // and will be one of the last views.
        for (i in dragLayer.getChildCount() - 1 downTo 0) {
            val child = dragLayer.getChildAt(i)
            if (child is AbstractFloatingView && child.isOfType(type)) {
                child.close(animate)
            }
        }
    }
}
