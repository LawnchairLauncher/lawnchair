/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.launcher3;

import android.graphics.PointF;
import android.view.View;

public interface Reorderable {

    /**
     * Set the offset related to reorder hint and bounce animations
     */
    void setReorderBounceOffset(float x, float y);

    void getReorderBounceOffset(PointF offset);

    /**
     * Set the offset related to previewing the new reordered position
     */
    void setReorderPreviewOffset(float x, float y);

    void getReorderPreviewOffset(PointF offset);

    /**
     * Set the scale related to reorder hint and "bounce" animations
     */
    void setReorderBounceScale(float scale);
    float getReorderBounceScale();

    /**
     * Get the com.android.view related to this object
     */
    View getView();
}
