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
     * Returns the width of this icon view.
     */
    int getWidth();

    /**
     * Returns the height of this icon view.
     */
    int getHeight();

    /**
     * Sets the opacity of the view.
     */
    void setContentAlpha(float alpha);

    /**
     * Returns this icon view's drawable.
     */
    @Nullable Drawable getDrawable();

    /**
     * Sets a {@link Drawable} to be displayed.
     */
    void setDrawable(@Nullable Drawable icon);

    /**
     * Register a callback to be invoked when this view is clicked.
     */
    void setOnClickListener(@Nullable View.OnClickListener l);

    /**
     * Register a callback to be invoked when this view is clicked and held.
     */
    void setOnLongClickListener(@Nullable View.OnLongClickListener l);

    /**
     * Returns the LayoutParams associated with this view.
     */
    ViewGroup.LayoutParams getLayoutParams();

    /**
     * Sets the layout parameters associated with this view.
     */
    void setLayoutParams(ViewGroup.LayoutParams params);

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
     * Sets the visibility state of this view.
     */
    void setVisibility(int visibility);

    /**
     * Sets the tint color of the icon, useful for scrimming or dimming.
     *
     * @param color to blend in.
     * @param amount [0,1] 0 no tint, 1 full tint
     */
    void setIconColorTint(int color, float amount);

    /**
     * Gets the opacity of the view.
     */
    float getAlpha();

    /**
     * Returns the width of this icon view's drawable.
     */
    int getDrawableWidth();

    /**
     * Returns the height of this icon view's drawable.
     */
    int getDrawableHeight();

    /**
     * Directly calls any attached OnClickListener.
     */
    boolean callOnClick();

    /**
     * Calls this view's OnLongClickListener.
     */
    boolean performLongClick();

    /**
     * Sets the text for this icon view if any text view is associated.
     */
    default void setText(CharSequence text) {}

    /**
     * Returns this icon view cast as a View.
     */
    View asView();
}
