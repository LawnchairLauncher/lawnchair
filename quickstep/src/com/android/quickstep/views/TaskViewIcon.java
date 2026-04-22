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
package com.android.quickstep.views;

import android.annotation.Nullable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

import com.android.quickstep.util.RecentsOrientedState;

/**
 * Interface defining an object which can be used as a TaskView's icon.
 */
public interface TaskViewIcon {

    /**
     * Sets the opacity of the view.
     */
    void setContentAlpha(float alpha);

    /**
     * Sets the opacity of the view for modal state.
     */
    void setModalAlpha(float alpha);

    /**
     * Sets the opacity of the view for flex split state.
     */
    void setFlexSplitAlpha(float alpha);

    /**
     * Returns this icon view's drawable.
     */
    @Nullable Drawable getDrawable();

    /**
     * Sets a {@link Drawable} to be displayed.
     */
    void setDrawable(@Nullable Drawable icon);

    /**
     * Sets the degrees that the view is rotated around the pivot point.
     */
    void setRotation(float rotation);

    /**
     * Sets the size of the icon drawable.
     */
    void setDrawableSize(int iconWidth, int iconHeight);

    /**
     * Sets the orientation of this icon view based on the provided orientationState.
     */
    void setIconOrientation(RecentsOrientedState orientationState, boolean isGridTask);

    /**
     * Sets the tint color of the icon, useful for scrimming or dimming.
     *
     * @param color to blend in.
     * @param amount [0,1] 0 no tint, 1 full tint
     */
    void setIconColorTint(int color, float amount);

    /**
     * Returns the width of this icon view's drawable.
     */
    int getDrawableWidth();

    /**
     * Returns the height of this icon view's drawable.
     */
    int getDrawableHeight();

    /**
     * Sets the text for this icon view if any text view is associated.
     */
    default void setText(CharSequence text) {}

    /**
     * Returns this icon view cast as a View.
     */
    View asView();
}
