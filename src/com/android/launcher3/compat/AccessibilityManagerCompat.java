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

package com.android.launcher3.compat;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;

import com.android.launcher3.testing.TestProtocol;
import com.android.launcher3.Utilities;

import java.util.function.Consumer;

public class AccessibilityManagerCompat {

    public static boolean isAccessibilityEnabled(Context context) {
        return getManager(context).isEnabled();
    }

    public static boolean isObservedEventType(Context context, int eventType) {
        // TODO: Use new API once available
        return isAccessibilityEnabled(context);
    }

    public static void sendCustomAccessibilityEvent(View target, int type, String text) {
        if (isObservedEventType(target.getContext(), type)) {
            AccessibilityEvent event = AccessibilityEvent.obtain(type);
            target.onInitializeAccessibilityEvent(event);
            event.getText().add(text);
            getManager(target.getContext()).sendAccessibilityEvent(event);
        }
    }

    private static AccessibilityManager getManager(Context context) {
        return (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
    }

    public static void sendStateEventToTest(Context context, int stateOrdinal) {
        final AccessibilityManager accessibilityManager = getAccessibilityManagerForTest(context);
        if (accessibilityManager == null) return;

        final Bundle parcel = new Bundle();
        parcel.putInt(TestProtocol.STATE_FIELD, stateOrdinal);

        sendEventToTest(accessibilityManager, TestProtocol.SWITCHED_TO_STATE_MESSAGE, parcel);
    }

    public static void sendScrollFinishedEventToTest(Context context) {
        final AccessibilityManager accessibilityManager = getAccessibilityManagerForTest(context);
        if (accessibilityManager == null) return;

        sendEventToTest(accessibilityManager, TestProtocol.SCROLL_FINISHED_MESSAGE, null);
    }

    public static void sendPauseDetectedEventToTest(Context context) {
        final AccessibilityManager accessibilityManager = getAccessibilityManagerForTest(context);
        if (accessibilityManager == null) return;

        sendEventToTest(accessibilityManager, TestProtocol.PAUSE_DETECTED_MESSAGE, null);
    }

    private static void sendEventToTest(
            AccessibilityManager accessibilityManager, String eventTag, Bundle data) {
        final AccessibilityEvent e = AccessibilityEvent.obtain(
                AccessibilityEvent.TYPE_ANNOUNCEMENT);
        e.setClassName(eventTag);
        e.setParcelableData(data);
        accessibilityManager.sendAccessibilityEvent(e);
    }

    /**
     * Returns accessibility manager to be used for communication with UI Automation tests.
     * The tests may exchange custom accessibility messages with the launcher; the accessibility
     * manager is used in these communications.
     *
     * If the launcher runs not under a test, the return is null, and no attempt to process or send
     * custom accessibility messages should be made.
     */
    private static AccessibilityManager getAccessibilityManagerForTest(Context context) {
        // If not running in a test harness, don't participate in test exchanges.
        if (!Utilities.IS_RUNNING_IN_TEST_HARNESS) return null;

        final AccessibilityManager accessibilityManager = getManager(context);
        if (!accessibilityManager.isEnabled()) return null;

        return accessibilityManager;
    }

    public static boolean processTestRequest(Context context, String eventTag, int action,
            Bundle request, Consumer<Bundle> responseFiller) {
        final AccessibilityManager accessibilityManager = getAccessibilityManagerForTest(context);
        if (accessibilityManager == null) return false;

        // The test sends a request via a ACTION_SET_TEXT.
        if (action == AccessibilityNodeInfo.ACTION_SET_TEXT &&
                eventTag.equals(request.getCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE))) {
            final Bundle response = new Bundle();
            responseFiller.accept(response);
            AccessibilityManagerCompat.sendEventToTest(
                    accessibilityManager, eventTag + TestProtocol.RESPONSE_MESSAGE_POSTFIX, response);
            return true;
        }
        return false;
    }

    public static int getRecommendedTimeoutMillis(Context context, int originalTimeout, int flags) {
        if (Utilities.ATLEAST_Q) {
            return getManager(context).getRecommendedTimeoutMillis(originalTimeout, flags);
        }
        return originalTimeout;
    }
}
