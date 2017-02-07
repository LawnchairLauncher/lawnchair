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

package com.android.launcher3.badge;

import com.android.launcher3.Utilities;

import static com.android.launcher3.Utilities.boundToRange;

/**
 * Subclass of BadgeInfo that only contains the badge count,
 * which is the sum of all the Folder's items' counts.
 */
public class FolderBadgeInfo extends BadgeInfo {

    private static final int MIN_COUNT = 0;
    private static final int MAX_COUNT = 999;

    private int mTotalNotificationCount;

    public FolderBadgeInfo() {
        super(null);
    }

    public void addBadgeInfo(BadgeInfo badgeToAdd) {
        if (badgeToAdd == null) {
            return;
        }
        mTotalNotificationCount += badgeToAdd.getNotificationCount();
        mTotalNotificationCount = Utilities.boundToRange(
                mTotalNotificationCount, MIN_COUNT, MAX_COUNT);
    }

    public void subtractBadgeInfo(BadgeInfo badgeToSubtract) {
        if (badgeToSubtract == null) {
            return;
        }
        mTotalNotificationCount -= badgeToSubtract.getNotificationCount();
        mTotalNotificationCount = Utilities.boundToRange(
                mTotalNotificationCount, MIN_COUNT, MAX_COUNT);
    }

    @Override
    public int getNotificationCount() {
        return mTotalNotificationCount;
    }
}
