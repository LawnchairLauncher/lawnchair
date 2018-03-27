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
package com.android.quickstep.util;

import android.metrics.LogMaker;
import android.util.EventLog;

/**
 * Utility class for writing logs on behalf of systemUI
 */
public class SysuiEventLogger {

    /** 524292 sysui_multi_action (content|4) */
    public static final int SYSUI_MULTI_ACTION = 524292;

    private static void write(LogMaker content) {
        if (content.getType() == 0/*MetricsEvent.TYPE_UNKNOWN*/) {
            content.setType(4/*MetricsEvent.TYPE_ACTION*/);
        }
        EventLog.writeEvent(SYSUI_MULTI_ACTION, content.serialize());
    }

    public static void writeDummyRecentsTransition(long transitionDelay) {
        // Mimic ActivityMetricsLogger.logAppTransitionMultiEvents() logging for
        // "Recents" activity for app transition tests for the app-to-recents case.
        final LogMaker builder = new LogMaker(761/*APP_TRANSITION*/);
        builder.setPackageName("com.android.systemui");
        builder.addTaggedData(871/*FIELD_CLASS_NAME*/,
                "com.android.systemui.recents.RecentsActivity");
        builder.addTaggedData(319/*APP_TRANSITION_DELAY_MS*/,
                transitionDelay);
        write(builder);
    }
}
