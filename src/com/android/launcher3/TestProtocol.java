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

package com.android.launcher3;

/**
 * Protocol for custom accessibility events for communication with UI Automation tests.
 */
public final class TestProtocol {
    public static final String GET_SCROLL_MESSAGE = "TAPL_GET_SCROLL";
    public static final String SCROLL_Y_FIELD = "scrollY";
    public static final String STATE_FIELD = "state";
    public static final String SWITCHED_TO_STATE_MESSAGE = "TAPL_SWITCHED_TO_STATE";
    public static final String SCROLL_FINISHED_MESSAGE = "TAPL_SCROLL_FINISHED";
    public static final String RESPONSE_MESSAGE_POSTFIX = "_RESPONSE";
    public static final int NORMAL_STATE_ORDINAL = 0;
    public static final int SPRING_LOADED_STATE_ORDINAL = 1;
    public static final int OVERVIEW_STATE_ORDINAL = 2;
    public static final int OVERVIEW_PEEK_STATE_ORDINAL = 3;
    public static final int QUICK_SWITCH_STATE_ORDINAL = 4;
    public static final int ALL_APPS_STATE_ORDINAL = 5;
    public static final int BACKGROUND_APP_STATE_ORDINAL = 6;

    public static String stateOrdinalToString(int ordinal) {
        switch (ordinal) {
            case NORMAL_STATE_ORDINAL:
                return "Normal";
            case SPRING_LOADED_STATE_ORDINAL:
                return "SpringLoaded";
            case OVERVIEW_STATE_ORDINAL:
                return "Overview";
            case OVERVIEW_PEEK_STATE_ORDINAL:
                return "OverviewPeek";
            case QUICK_SWITCH_STATE_ORDINAL:
                return "QuickSwitch";
            case ALL_APPS_STATE_ORDINAL:
                return "AllApps";
            case BACKGROUND_APP_STATE_ORDINAL:
                return "Background";
            default:
                return null;
        }
    }

    public static final String TEST_INFO_RESPONSE_FIELD = "response";
    public static final String REQUEST_HOME_TO_OVERVIEW_SWIPE_HEIGHT =
            "home-to-overview-swipe-height";
    public static final String REQUEST_BACKGROUND_TO_OVERVIEW_SWIPE_HEIGHT =
            "background-to-overview-swipe-height";
    public static final String REQUEST_ALL_APPS_TO_OVERVIEW_SWIPE_HEIGHT =
            "all-apps-to-overview-swipe-height";
    public static final String REQUEST_HOME_TO_ALL_APPS_SWIPE_HEIGHT =
            "home-to-all-apps-swipe-height";
}
