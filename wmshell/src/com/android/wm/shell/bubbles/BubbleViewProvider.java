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

package com.android.wm.shell.bubbles;

import android.graphics.Bitmap;
import android.graphics.Path;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.wm.shell.bubbles.bar.BubbleBarExpandedView;

/**
 * Interface to represent actual Bubbles and UI elements that act like bubbles, like BubbleOverflow.
 */
public interface BubbleViewProvider {

    /**
     * Returns the icon view used for a bubble (the click target when collapsed). This is populated
     * when bubbles are floating, i.e. when {@link BubbleController#isShowingAsBubbleBar()} is
     * false.
     */
    @Nullable
    View getIconView();

    /**
     * Returns the expanded view used for a bubble. This is populated when bubbles are floating,
     * i.e. when {@link BubbleController#isShowingAsBubbleBar()} is false.
     */
    @Nullable
    BubbleExpandedView getExpandedView();

    /**
     * Returns the expanded view used for a bubble being show in the bubble bar. This is populated
     * when {@link BubbleController#isShowingAsBubbleBar()} is true.
     */
    @Nullable
    BubbleBarExpandedView getBubbleBarExpandedView();

    /**
     * Sets whether the contents of the bubble's TaskView should be visible.
     */
    void setTaskViewVisibility(boolean visible);


    String getKey();

    /** Bubble icon bitmap with no badge and no dot. */
    Bitmap getBubbleIcon();

    /** App badge drawable to draw above bubble icon. */
    @Nullable Bitmap getAppBadge();

    /** Base app badge drawable without any markings. */
    @Nullable Bitmap getRawAppBadge();

    /** Path of normalized bubble icon to draw dot on. */
    Path getDotPath();

    int getDotColor();

    boolean showDot();

    int getTaskId();
}
