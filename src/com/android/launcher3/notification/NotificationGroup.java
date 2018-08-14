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

package com.android.launcher3.notification;

import java.util.HashSet;
import java.util.Set;

/**
 * Contains data related to a group of notifications, like the group summary key and the child keys.
 */
public class NotificationGroup {
    private String mGroupSummaryKey;
    private Set<String> mChildKeys;

    public NotificationGroup() {
        mChildKeys = new HashSet<>();
    }

    public void setGroupSummaryKey(String groupSummaryKey) {
        mGroupSummaryKey = groupSummaryKey;
    }

    public String getGroupSummaryKey() {
        return mGroupSummaryKey;
    }

    public void addChildKey(String childKey) {
        mChildKeys.add(childKey);
    }

    public void removeChildKey(String childKey) {
        mChildKeys.remove(childKey);
    }

    public boolean isEmpty() {
        return mChildKeys.isEmpty();
    }
}
