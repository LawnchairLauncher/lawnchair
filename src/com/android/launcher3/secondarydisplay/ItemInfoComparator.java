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
package com.android.launcher3.secondarydisplay;

import android.content.Context;
import android.os.Process;
import android.os.UserHandle;

import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.util.LabelComparator;

import java.util.Comparator;

/**
 * A comparator to arrange items based on user profiles.
 */
final class ItemInfoComparator implements Comparator<ItemInfo> {
    private final UserCache mUserManager;
    private final UserHandle mMyUser;
    private final LabelComparator mLabelComparator;

    ItemInfoComparator(Context context) {
        mUserManager = UserCache.INSTANCE.get(context);
        mMyUser = Process.myUserHandle();
        mLabelComparator = new LabelComparator();
    }

    @Override
    public int compare(ItemInfo a, ItemInfo b) {
        // Order by the title in the current locale.
        int result = mLabelComparator.compare(
                a.title == null ? "" : a.title.toString(),
                b.title == null ? "" : b.title.toString());
        if (result != 0) {
            return result;
        }

        // If labels are same, compare target components.
        result = a.getTargetComponent().compareTo(b.getTargetComponent());
        if (result != 0) {
            return result;
        }

        // Otherwise, order by user.
        if (mMyUser.equals(a.user)) {
            return -1;
        } else {
            Long aUserSerial = mUserManager.getSerialNumberForUser(a.user);
            Long bUserSerial = mUserManager.getSerialNumberForUser(b.user);
            return aUserSerial.compareTo(bUserSerial);
        }
    }
}
