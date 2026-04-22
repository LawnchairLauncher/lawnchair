/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.launcher3.views;

import android.view.View;

/**
 * A view that can be drawn (in some capacity) via) {@link FloatingIconView}.
 * This interface allows us to hide certain properties of the view that the FloatingIconView
 * cannot draw, which allows us to make a seamless handoff between the FloatingIconView and
 * the companion view.
 */
public interface FloatingIconViewCompanion {
    void setIconVisible(boolean visible);
    void setForceHideDot(boolean hide);
    default void setForceHideRing(boolean hide) {}

    /**
     * Sets the visibility of icon and dot of the view
     */
    static void setPropertiesVisible(View view, boolean visible) {
        if (view instanceof FloatingIconViewCompanion) {
            ((FloatingIconViewCompanion) view).setIconVisible(visible);
            ((FloatingIconViewCompanion) view).setForceHideDot(!visible);
            ((FloatingIconViewCompanion) view).setForceHideRing(!visible);
        } else {
            view.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        }
    }
}
