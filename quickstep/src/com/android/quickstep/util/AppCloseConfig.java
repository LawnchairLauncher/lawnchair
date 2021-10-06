/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.quickstep.util;

import android.annotation.FloatRange;
import android.annotation.IntRange;

/*
 * Adds getter methods to {@link MultiValueUpdateListener} specific to app close animation,
 * so that the entire animation can be defined in one place.
 */
public abstract class AppCloseConfig extends MultiValueUpdateListener {

    /**
     * Returns the translation y of the workspace contents.
     */
    public abstract float getWorkspaceTransY();

    /*
     * Returns the scale of the workspace contents.
     */
    public abstract float getWorkspaceScale();

    /*
     * Returns the alpha of the window.
     */
    public abstract @FloatRange(from = 0, to = 1) float getWindowAlpha();

    /*
     * Returns the alpha of the foreground layer of an adaptive icon.
     */
    public abstract @IntRange(from = 0, to = 255) int getFgAlpha();

    /*
     * Returns the corner radius of the window and icon.
     */
    public abstract float getCornerRadius();

    /*
     * Returns the interpolated progress of the animation.
     */
    public abstract float getInterpolatedProgress();

}
