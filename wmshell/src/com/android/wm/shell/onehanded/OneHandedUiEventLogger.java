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

package com.android.wm.shell.onehanded;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.logging.UiEvent;
import com.android.internal.logging.UiEventLogger;

/**
 *  Helper class that ends OneHanded mode log to UiEvent, see also go/uievent
 */
public class OneHandedUiEventLogger {
    private static final String TAG = "OneHandedUiEventLogger";
    private final UiEventLogger mUiEventLogger;

    /**
     * One-Handed event types
     */
    // Triggers
    public static final int EVENT_ONE_HANDED_TRIGGER_GESTURE_IN = 0;
    public static final int EVENT_ONE_HANDED_TRIGGER_GESTURE_OUT = 1;
    public static final int EVENT_ONE_HANDED_TRIGGER_OVERSPACE_OUT = 2;
    public static final int EVENT_ONE_HANDED_TRIGGER_POP_IME_OUT = 3;
    public static final int EVENT_ONE_HANDED_TRIGGER_ROTATION_OUT = 4;
    public static final int EVENT_ONE_HANDED_TRIGGER_APP_TAPS_OUT = 5;
    public static final int EVENT_ONE_HANDED_TRIGGER_TIMEOUT_OUT = 6;
    public static final int EVENT_ONE_HANDED_TRIGGER_SCREEN_OFF_OUT = 7;
    // Settings toggles
    public static final int EVENT_ONE_HANDED_SETTINGS_ENABLED_ON = 8;
    public static final int EVENT_ONE_HANDED_SETTINGS_ENABLED_OFF = 9;
    public static final int EVENT_ONE_HANDED_SETTINGS_APP_TAPS_EXIT_ON = 10;
    public static final int EVENT_ONE_HANDED_SETTINGS_APP_TAPS_EXIT_OFF = 11;
    public static final int EVENT_ONE_HANDED_SETTINGS_TIMEOUT_EXIT_ON = 12;
    public static final int EVENT_ONE_HANDED_SETTINGS_TIMEOUT_EXIT_OFF = 13;
    public static final int EVENT_ONE_HANDED_SETTINGS_TIMEOUT_SECONDS_NEVER = 14;
    public static final int EVENT_ONE_HANDED_SETTINGS_TIMEOUT_SECONDS_4 = 15;
    public static final int EVENT_ONE_HANDED_SETTINGS_TIMEOUT_SECONDS_8 = 16;
    public static final int EVENT_ONE_HANDED_SETTINGS_TIMEOUT_SECONDS_12 = 17;
    public static final int EVENT_ONE_HANDED_SETTINGS_SHOW_NOTIFICATION_ENABLED_ON = 18;
    public static final int EVENT_ONE_HANDED_SETTINGS_SHOW_NOTIFICATION_ENABLED_OFF = 19;
    public static final int EVENT_ONE_HANDED_SETTINGS_SHORTCUT_ENABLED_ON = 20;
    public static final int EVENT_ONE_HANDED_SETTINGS_SHORTCUT_ENABLED_OFF = 21;

    private static final String[] EVENT_TAGS = {
            "one_handed_trigger_gesture_in",
            "one_handed_trigger_gesture_out",
            "one_handed_trigger_overspace_out",
            "one_handed_trigger_pop_ime_out",
            "one_handed_trigger_rotation_out",
            "one_handed_trigger_app_taps_out",
            "one_handed_trigger_timeout_out",
            "one_handed_trigger_screen_off_out",
            "one_handed_settings_enabled_on",
            "one_handed_settings_enabled_off",
            "one_handed_settings_app_taps_exit_on",
            "one_handed_settings_app_taps_exit_off",
            "one_handed_settings_timeout_exit_on",
            "one_handed_settings_timeout_exit_off",
            "one_handed_settings_timeout_seconds_never",
            "one_handed_settings_timeout_seconds_4",
            "one_handed_settings_timeout_seconds_8",
            "one_handed_settings_timeout_seconds_12",
            "one_handed_settings_show_notification_enabled_on",
            "one_handed_settings_show_notification_enabled_off",
            "one_handed_settings_shortcut_enabled_on",
            "one_handed_settings_shortcut_enabled_off"
    };

    public OneHandedUiEventLogger(UiEventLogger uiEventLogger) {
        mUiEventLogger = uiEventLogger;
    }

    /**
     * Events definition that related to One-Handed gestures.
     */
    @VisibleForTesting
    public enum OneHandedTriggerEvent implements UiEventLogger.UiEventEnum {
        INVALID(0),
        @UiEvent(doc = "One-Handed trigger in via NavigationBar area")
        ONE_HANDED_TRIGGER_GESTURE_IN(366),

        @UiEvent(doc = "One-Handed trigger out via NavigationBar area")
        ONE_HANDED_TRIGGER_GESTURE_OUT(367),

        @UiEvent(doc = "One-Handed trigger out via Overspace area")
        ONE_HANDED_TRIGGER_OVERSPACE_OUT(368),

        @UiEvent(doc = "One-Handed trigger out while IME pop up")
        ONE_HANDED_TRIGGER_POP_IME_OUT(369),

        @UiEvent(doc = "One-Handed trigger out while device rotation to landscape")
        ONE_HANDED_TRIGGER_ROTATION_OUT(370),

        @UiEvent(doc = "One-Handed trigger out when an Activity is launching")
        ONE_HANDED_TRIGGER_APP_TAPS_OUT(371),

        @UiEvent(doc = "One-Handed trigger out when one-handed mode times up")
        ONE_HANDED_TRIGGER_TIMEOUT_OUT(372),

        @UiEvent(doc = "One-Handed trigger out when screen off")
        ONE_HANDED_TRIGGER_SCREEN_OFF_OUT(449);

        private final int mId;

        OneHandedTriggerEvent(int id) {
            mId = id;
        }

        @Override
        public int getId() {
            return mId;
        }
    }

    /**
     * Events definition that related to Settings toggles.
     */
    @VisibleForTesting
    public enum OneHandedSettingsTogglesEvent implements UiEventLogger.UiEventEnum {
        INVALID(0),
        @UiEvent(doc = "One-Handed mode enabled toggle on")
        ONE_HANDED_SETTINGS_TOGGLES_ENABLED_ON(356),

        @UiEvent(doc = "One-Handed mode enabled toggle off")
        ONE_HANDED_SETTINGS_TOGGLES_ENABLED_OFF(357),

        @UiEvent(doc = "One-Handed mode app-taps-exit toggle on")
        ONE_HANDED_SETTINGS_TOGGLES_APP_TAPS_EXIT_ON(358),

        @UiEvent(doc = "One-Handed mode app-taps-exit toggle off")
        ONE_HANDED_SETTINGS_TOGGLES_APP_TAPS_EXIT_OFF(359),

        @UiEvent(doc = "One-Handed mode timeout-exit toggle on")
        ONE_HANDED_SETTINGS_TOGGLES_TIMEOUT_EXIT_ON(360),

        @UiEvent(doc = "One-Handed mode timeout-exit toggle off")
        ONE_HANDED_SETTINGS_TOGGLES_TIMEOUT_EXIT_OFF(361),

        @UiEvent(doc = "One-Handed mode timeout value changed to never timeout")
        ONE_HANDED_SETTINGS_TOGGLES_TIMEOUT_SECONDS_NEVER(362),

        @UiEvent(doc = "One-Handed mode timeout value changed to 4 seconds")
        ONE_HANDED_SETTINGS_TOGGLES_TIMEOUT_SECONDS_4(363),

        @UiEvent(doc = "One-Handed mode timeout value changed to 8 seconds")
        ONE_HANDED_SETTINGS_TOGGLES_TIMEOUT_SECONDS_8(364),

        @UiEvent(doc = "One-Handed mode timeout value changed to 12 seconds")
        ONE_HANDED_SETTINGS_TOGGLES_TIMEOUT_SECONDS_12(365),

        @UiEvent(doc = "One-Handed mode show notification toggle on")
        ONE_HANDED_SETTINGS_TOGGLES_SHOW_NOTIFICATION_ENABLED_ON(847),

        @UiEvent(doc = "One-Handed mode show notification toggle off")
        ONE_HANDED_SETTINGS_TOGGLES_SHOW_NOTIFICATION_ENABLED_OFF(848),

        @UiEvent(doc = "One-Handed mode shortcut toggle on")
        ONE_HANDED_SETTINGS_TOGGLES_SHORTCUT_ENABLED_ON(870),

        @UiEvent(doc = "One-Handed mode shortcut toggle off")
        ONE_HANDED_SETTINGS_TOGGLES_SHORTCUT_ENABLED_OFF(871);

        private final int mId;

        OneHandedSettingsTogglesEvent(int id) {
            mId = id;
        }

        @Override
        public int getId() {
            return mId;
        }
    }


    /**
     * Logs an event to the system log, to sCallback if present, and to the logEvent destinations.
     * @param tag One of the EVENT_* codes above.
     */
    public void writeEvent(int tag) {
        logEvent(tag);
    }

    /**
     * Logs an event to the UiEvent (statsd) logging.
     * @param event One of the EVENT_* codes above.
     * @return String a readable description of the event.  Begins "writeEvent <tag_description>"
     * if the tag is valid.
     */
    private void logEvent(int event) {
        switch (event) {
            case EVENT_ONE_HANDED_TRIGGER_GESTURE_IN:
                mUiEventLogger.log(OneHandedTriggerEvent.ONE_HANDED_TRIGGER_GESTURE_IN);
                break;
            case EVENT_ONE_HANDED_TRIGGER_GESTURE_OUT:
                mUiEventLogger.log(OneHandedTriggerEvent.ONE_HANDED_TRIGGER_GESTURE_OUT);
                break;
            case EVENT_ONE_HANDED_TRIGGER_OVERSPACE_OUT:
                mUiEventLogger.log(OneHandedTriggerEvent.ONE_HANDED_TRIGGER_OVERSPACE_OUT);
                break;
            case EVENT_ONE_HANDED_TRIGGER_POP_IME_OUT:
                mUiEventLogger.log(OneHandedTriggerEvent.ONE_HANDED_TRIGGER_POP_IME_OUT);
                break;
            case EVENT_ONE_HANDED_TRIGGER_ROTATION_OUT:
                mUiEventLogger.log(OneHandedTriggerEvent.ONE_HANDED_TRIGGER_ROTATION_OUT);
                break;
            case EVENT_ONE_HANDED_TRIGGER_APP_TAPS_OUT:
                mUiEventLogger.log(OneHandedTriggerEvent.ONE_HANDED_TRIGGER_APP_TAPS_OUT);
                break;
            case EVENT_ONE_HANDED_TRIGGER_TIMEOUT_OUT:
                mUiEventLogger.log(OneHandedTriggerEvent.ONE_HANDED_TRIGGER_TIMEOUT_OUT);
                break;
            case EVENT_ONE_HANDED_TRIGGER_SCREEN_OFF_OUT:
                mUiEventLogger.log(OneHandedTriggerEvent.ONE_HANDED_TRIGGER_SCREEN_OFF_OUT);
                break;
            case EVENT_ONE_HANDED_SETTINGS_ENABLED_ON:
                mUiEventLogger.log(OneHandedSettingsTogglesEvent
                        .ONE_HANDED_SETTINGS_TOGGLES_ENABLED_ON);
                break;
            case EVENT_ONE_HANDED_SETTINGS_ENABLED_OFF:
                mUiEventLogger.log(OneHandedSettingsTogglesEvent
                        .ONE_HANDED_SETTINGS_TOGGLES_ENABLED_OFF);
                break;
            case EVENT_ONE_HANDED_SETTINGS_APP_TAPS_EXIT_ON:
                mUiEventLogger.log(OneHandedSettingsTogglesEvent
                        .ONE_HANDED_SETTINGS_TOGGLES_APP_TAPS_EXIT_ON);
                break;
            case EVENT_ONE_HANDED_SETTINGS_APP_TAPS_EXIT_OFF:
                mUiEventLogger.log(OneHandedSettingsTogglesEvent
                        .ONE_HANDED_SETTINGS_TOGGLES_APP_TAPS_EXIT_OFF);
                break;
            case EVENT_ONE_HANDED_SETTINGS_TIMEOUT_EXIT_ON:
                mUiEventLogger.log(OneHandedSettingsTogglesEvent
                        .ONE_HANDED_SETTINGS_TOGGLES_TIMEOUT_EXIT_ON);
                break;
            case EVENT_ONE_HANDED_SETTINGS_TIMEOUT_EXIT_OFF:
                mUiEventLogger.log(OneHandedSettingsTogglesEvent
                        .ONE_HANDED_SETTINGS_TOGGLES_TIMEOUT_EXIT_OFF);
                break;
            case EVENT_ONE_HANDED_SETTINGS_TIMEOUT_SECONDS_NEVER:
                mUiEventLogger.log(OneHandedSettingsTogglesEvent
                        .ONE_HANDED_SETTINGS_TOGGLES_TIMEOUT_SECONDS_NEVER);
                break;
            case EVENT_ONE_HANDED_SETTINGS_TIMEOUT_SECONDS_4:
                mUiEventLogger.log(OneHandedSettingsTogglesEvent
                        .ONE_HANDED_SETTINGS_TOGGLES_TIMEOUT_SECONDS_4);
                break;
            case EVENT_ONE_HANDED_SETTINGS_TIMEOUT_SECONDS_8:
                mUiEventLogger.log(OneHandedSettingsTogglesEvent
                        .ONE_HANDED_SETTINGS_TOGGLES_TIMEOUT_SECONDS_8);
                break;
            case EVENT_ONE_HANDED_SETTINGS_TIMEOUT_SECONDS_12:
                mUiEventLogger.log(OneHandedSettingsTogglesEvent
                        .ONE_HANDED_SETTINGS_TOGGLES_TIMEOUT_SECONDS_12);
                break;
            case EVENT_ONE_HANDED_SETTINGS_SHOW_NOTIFICATION_ENABLED_ON:
                mUiEventLogger.log(OneHandedSettingsTogglesEvent
                        .ONE_HANDED_SETTINGS_TOGGLES_SHOW_NOTIFICATION_ENABLED_ON);
                break;
            case EVENT_ONE_HANDED_SETTINGS_SHOW_NOTIFICATION_ENABLED_OFF:
                mUiEventLogger.log(OneHandedSettingsTogglesEvent
                        .ONE_HANDED_SETTINGS_TOGGLES_SHOW_NOTIFICATION_ENABLED_OFF);
                break;
            case EVENT_ONE_HANDED_SETTINGS_SHORTCUT_ENABLED_ON:
                mUiEventLogger.log(OneHandedSettingsTogglesEvent
                        .ONE_HANDED_SETTINGS_TOGGLES_SHORTCUT_ENABLED_ON);
                break;
            case EVENT_ONE_HANDED_SETTINGS_SHORTCUT_ENABLED_OFF:
                mUiEventLogger.log(OneHandedSettingsTogglesEvent
                        .ONE_HANDED_SETTINGS_TOGGLES_SHORTCUT_ENABLED_OFF);
                break;
            default:
                // Do nothing
                break;
        }
    }
}
