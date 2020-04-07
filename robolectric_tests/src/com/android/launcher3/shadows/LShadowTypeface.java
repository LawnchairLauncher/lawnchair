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
package com.android.launcher3.shadows;

import android.graphics.Typeface;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowTypeface;

/**
 * Extension of {@link ShadowTypeface} with missing shadow methods
 */
@Implements(Typeface.class)
public class LShadowTypeface extends ShadowTypeface {

    @Implementation
    public static Typeface create(Typeface family, int weight, boolean italic) {
        int style = italic ? Typeface.ITALIC : Typeface.NORMAL;
        if (weight >= 400) {
            style |= Typeface.BOLD;
        }
        return create(family, style);
    }
}
