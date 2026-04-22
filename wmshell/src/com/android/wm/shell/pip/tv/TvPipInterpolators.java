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

package com.android.wm.shell.pip.tv;

import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

/**
 * All interpolators needed for TV specific Pip animations
 */
public class TvPipInterpolators {

    /**
     * A standard ease-in-out curve reserved for moments of interaction (button and card states).
     */
    public static final Interpolator STANDARD = new PathInterpolator(0.2f, 0.1f, 0f, 1f);

    /**
     * A sharp ease-out-expo curve created for snappy but fluid browsing between cards and clusters.
     */
    public static final Interpolator BROWSE = new PathInterpolator(0.18f, 1f, 0.22f, 1f);

    /**
     * A smooth ease-out-expo curve created for incoming elements (forward, back, overlay).
     */
    public static final Interpolator ENTER = new PathInterpolator(0.12f, 1f, 0.4f, 1f);

    /**
     * A smooth ease-in-out-expo curve created for outgoing elements (forward, back, overlay).
     */
    public static final Interpolator EXIT = new PathInterpolator(0.4f, 1f, 0.12f, 1f);

}
