/*
 * Copyright (C) 2022 The Android Open Source Project
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

/** {@link Launchable} that can serve as a source for dragging and dropping to splitscreen. */
interface SplitscreenDragSource {

    /**
     * Drags this app icon to the left (landscape) or bottom (portrait) of the screen, launching it
     * in splitscreen.
     *
     * @param expectedNewPackageName package name of the app being dragged
     * @param expectedExistingPackageName package name of the already-launched app
     */
    default void dragToSplitscreen(
            String expectedNewPackageName, String expectedExistingPackageName) {
        Launchable launchable = getLaunchable();
        LauncherInstrumentation launcher = launchable.mLauncher;
        try (LauncherInstrumentation.Closable e = launcher.eventsCheck()) {
            LaunchedAppState.dragToSplitscreen(
                    launcher, launchable, expectedNewPackageName, expectedExistingPackageName);
        }
    }

    /** This method requires public access, however should not be called in tests. */
    Launchable getLaunchable();
}
