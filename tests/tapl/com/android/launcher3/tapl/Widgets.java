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

package com.android.launcher3.tapl;

import android.support.annotation.NonNull;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiObject2;

/**
 * All widgets container.
 */
public final class Widgets {
    private static final int FLING_SPEED = 12000;

    private final Launcher mLauncher;

    Widgets(Launcher launcher) {
        mLauncher = launcher;
        assertState();
    }

    /**
     * Flings forward (down) and waits the fling's end.
     */
    public void flingForward() {
        final UiObject2 widgetsContainer = assertState();
        widgetsContainer.fling(Direction.DOWN, FLING_SPEED);
        mLauncher.waitForIdle();
        assertState();
    }

    /**
     * Flings backward (up) and waits the fling's end.
     */
    public void flingBackward() {
        final UiObject2 widgetsContainer = assertState();
        widgetsContainer.fling(Direction.UP, FLING_SPEED);
        mLauncher.waitForIdle();
        assertState();
    }

    /**
     * Asserts that we are in widgets.
     *
     * @return Widgets container.
     */
    @NonNull
    private UiObject2 assertState() {
        return mLauncher.assertState(Launcher.State.WIDGETS);
    }
}
