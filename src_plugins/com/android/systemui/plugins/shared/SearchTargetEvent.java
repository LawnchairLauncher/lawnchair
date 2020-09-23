/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.systemui.plugins.shared;

import android.content.pm.ShortcutInfo;
import android.os.Bundle;

/**
 * Event used for the feedback loop to the plugin. (and future aiai)
 */
public class SearchTargetEvent {
    public static final int SELECT = 0;
    public static final int QUICK_SELECT = 1;
    public static final int LONG_PRESS = 2;
    public static final int CHILD_SELECT = 3;

    public SearchTarget.ItemType type;
    public ShortcutInfo shortcut;
    public int eventType;
    public Bundle bundle;
    public int index;
    public String sessionIdentifier;

    public SearchTargetEvent(SearchTarget.ItemType itemType, int eventType, int index,
            String sessionId) {
        this.type = itemType;
        this.eventType = eventType;
        this.index = index;
        this.sessionIdentifier = sessionId;
    }
}
