/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.launcher3.widget;

import android.os.Process;
import android.os.UserHandle;

import com.android.launcher3.model.WidgetItem;

import java.text.Collator;
import java.util.Comparator;

/**
 * Comparator for sorting WidgetItem based on their user, title and size.
 */
public class WidgetItemComparator implements Comparator<WidgetItem> {

    private final UserHandle mMyUserHandle = Process.myUserHandle();
    private final Collator mCollator = Collator.getInstance();

    @Override
    public int compare(WidgetItem a, WidgetItem b) {
        // Independent of how the labels compare, if only one of the two widget info belongs to
        // work profile, put that one in the back.
        boolean thisWorkProfile = !mMyUserHandle.equals(a.user);
        boolean otherWorkProfile = !mMyUserHandle.equals(b.user);
        if (thisWorkProfile ^ otherWorkProfile) {
            return thisWorkProfile ? 1 : -1;
        }

        int labelCompare = mCollator.compare(a.label, b.label);
        if (labelCompare != 0) {
            return labelCompare;
        }

        // If the label is same, put the smaller widget before the larger widget. If the area is
        // also same, put the widget with smaller height before.
        int thisArea = a.spanX * a.spanY;
        int otherArea = b.spanX * b.spanY;
        return thisArea == otherArea
                ? Integer.compare(a.spanY, b.spanY)
                : Integer.compare(thisArea, otherArea);
    }
}
