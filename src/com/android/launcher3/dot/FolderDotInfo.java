/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3.dot;

import android.view.ViewDebug;

import com.android.launcher3.Utilities;

/**
 * Subclass of DotInfo that only contains the dot count, which is
 * the sum of all the Folder's items' notifications (each counts as 1).
 */
public class FolderDotInfo extends DotInfo {

    private static final int MIN_COUNT = 0;

    private int mNumNotifications;

    public void addDotInfo(DotInfo dotToAdd) {
        if (dotToAdd == null) {
            return;
        }
        mNumNotifications += dotToAdd.getNotificationKeys().size();
        mNumNotifications = Utilities.boundToRange(
                mNumNotifications, MIN_COUNT, DotInfo.MAX_COUNT);
    }

    public void subtractDotInfo(DotInfo dotToSubtract) {
        if (dotToSubtract == null) {
            return;
        }
        mNumNotifications -= dotToSubtract.getNotificationKeys().size();
        mNumNotifications = Utilities.boundToRange(
                mNumNotifications, MIN_COUNT, DotInfo.MAX_COUNT);
    }

    @Override
    public int getNotificationCount() {
        return mNumNotifications;
    }

    @ViewDebug.ExportedProperty(category = "launcher")
    public boolean hasDot() {
        return mNumNotifications > 0;
    }
}
