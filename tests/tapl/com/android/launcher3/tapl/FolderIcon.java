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

package com.android.launcher3.tapl;

import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.test.uiautomator.UiObject2;

import com.android.launcher3.testing.shared.TestProtocol;

/**
 * Folder Icon, an app folder in workspace.
 */
public class FolderIcon implements IconDragTarget {

    protected final UiObject2 mObject;
    protected final LauncherInstrumentation mLauncher;

    FolderIcon(LauncherInstrumentation launcher, UiObject2 icon) {
        mObject = icon;
        mLauncher = launcher;
    }

    /**
     * Open and return a folder or raise assertion error.
     */
    @NonNull
    public Folder open() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer("open folder")) {
            mLauncher.executeAndWaitForLauncherEvent(() -> mLauncher.clickLauncherObject(mObject),
                    event -> TestProtocol.FOLDER_OPENED_MESSAGE.equals(
                            event.getClassName().toString()),
                    () -> "Fail to open folder.",
                    "open folder");
        }
        return new Folder(mLauncher);
    }

    /** This method requires public access, however should not be called in tests. */
    @Override
    public Rect getDropLocationBounds() {
        return mLauncher.getVisibleBounds(mObject.getParent());
    }

    /** This method requires public access, however should not be called in tests. */
    @Override
    public FolderIcon getTargetIcon(Rect bounds) {
        return this;
    }
}
