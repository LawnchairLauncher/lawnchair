/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.plugins.statusbar;

import android.service.notification.SnoozeCriterion;
import android.service.notification.StatusBarNotification;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;

import com.android.systemui.plugins.annotations.DependsOn;
import com.android.systemui.plugins.annotations.ProvidesInterface;
import com.android.systemui.plugins.statusbar.NotificationSwipeActionHelper.SnoozeOption;

@ProvidesInterface(version = NotificationSwipeActionHelper.VERSION)
@DependsOn(target = SnoozeOption.class)
public interface NotificationSwipeActionHelper {
    public static final String ACTION = "com.android.systemui.action.PLUGIN_NOTIFICATION_SWIPE_ACTION";

    public static final int VERSION = 1;

    /**
     * Call this to dismiss a notification.
     */
    public void dismiss(View animView, float velocity);

    /**
     * Call this to snap a notification to provided {@code targetLeft}.
     */
    public void snapOpen(View animView, int targetLeft, float velocity);

    /**
     * Call this to snooze a notification based on the provided {@link SnoozeOption}.
     */
    public void snooze(StatusBarNotification sbn, SnoozeOption snoozeOption);

    public float getMinDismissVelocity();

    public boolean isDismissGesture(MotionEvent ev);

    /** Returns true if the gesture should be rejected. */
    boolean isFalseGesture();

    @ProvidesInterface(version = SnoozeOption.VERSION)
    public interface SnoozeOption {
        public static final int VERSION = 2;

        public SnoozeCriterion getSnoozeCriterion();

        public CharSequence getDescription();

        public CharSequence getConfirmation();

        public int getMinutesToSnoozeFor();

        public AccessibilityAction getAccessibilityAction();
    }
}
