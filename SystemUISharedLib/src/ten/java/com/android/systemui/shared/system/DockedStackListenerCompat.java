/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.systemui.shared.system;

import android.view.IDockedStackListener;

/**
 * An interface to track docked stack changes.
 */
public class DockedStackListenerCompat {

    IDockedStackListener.Stub mListener = new IDockedStackListener.Stub() {
        @Override
        public void onDividerVisibilityChanged(boolean visible) {}

        @Override
        public void onDockedStackExistsChanged(boolean exists) {
            DockedStackListenerCompat.this.onDockedStackExistsChanged(exists);
        }

        @Override
        public void onDockedStackMinimizedChanged(boolean minimized, long animDuration,
                boolean isHomeStackResizable) {
            DockedStackListenerCompat.this.onDockedStackMinimizedChanged(minimized, animDuration,
                    isHomeStackResizable);
        }

        @Override
        public void onAdjustedForImeChanged(boolean adjustedForIme, long animDuration) {}

        @Override
        public void onDockSideChanged(final int newDockSide) {
            DockedStackListenerCompat.this.onDockSideChanged(newDockSide);
        }
    };

    public void onDockedStackExistsChanged(boolean exists) {
        // To be overridden
    }

    public void onDockedStackMinimizedChanged(boolean minimized, long animDuration,
            boolean isHomeStackResizable) {
        // To be overridden
    }

    public void onDockSideChanged(final int newDockSide) {
        // To be overridden
    }
}
