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

package com.android.keyguard;

import android.view.View;

/**
 * A common interface for classes that provide functionality for the PIN type view
 */
public interface PinShapeInput {

    /**
     * This is the method that is triggered when user types in a character
     */
    void append();

    /**
     * This is the method that is triggered when user deletes a character
     */
    void delete();

    /**
     * This is the method that is triggered for setting the color of the view
     */
    void setDrawColor(int color);

    /**
     * This is the method that is triggered for resetting the view
     */
    void reset();

    /**
     * This is the method that is triggered for resetting the view with error If it doesn't have to
     * show something regarding error, just reset
     */
    default void resetWithError() {
        reset();
    }

    /**
     * This is the method that is triggered for getting the view
     */
    View getView();
}
