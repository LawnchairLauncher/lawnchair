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
 * limitations under the License
 */

package com.android.systemui.shared.system;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

public class MetricsLoggerCompat {

    private final MetricsLogger mMetricsLogger;
    public static final int OVERVIEW_ACTIVITY = MetricsEvent.OVERVIEW_ACTIVITY;

    public MetricsLoggerCompat() {
        mMetricsLogger = new MetricsLogger();
    }

    public void action(int category) {
        mMetricsLogger.action(category);
    }

    public void action(int category, int value) {
        mMetricsLogger.action(category, value);
    }

    public void visible(int category) {
        mMetricsLogger.visible(category);
    }

    public void hidden(int category) {
        mMetricsLogger.hidden(category);
    }

    public void visibility(int category, boolean visible) {
        mMetricsLogger.visibility(category, visible);
    }
}
