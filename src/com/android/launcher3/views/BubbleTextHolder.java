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
package com.android.launcher3.views;

import com.android.launcher3.BubbleTextView;

/**
 * Views that contain {@link BubbleTextView} should implement this interface.
 */
public interface BubbleTextHolder extends FloatingIconViewCompanion {
    BubbleTextView getBubbleText();

    @Override
    default void setIconVisible(boolean visible) {
        getBubbleText().setIconVisible(visible);
    }

    @Override
    default void setForceHideDot(boolean hide) {
        getBubbleText().setForceHideDot(hide);
    }
}
