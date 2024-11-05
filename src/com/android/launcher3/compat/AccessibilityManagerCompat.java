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
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

import androidx.annotation.Nullable;

import com.android.launcher3.Utilities;
import com.android.launcher3.testing.shared.TestProtocol;

public class AccessibilityManagerCompat {

    public static boolean isAccessibilityEnabled(Context context) {
        return getManager(context).isEnabled();
    }

    public static boolean isObservedEventType(Context context, int eventType) {
        // TODO: Use new API once available
        return isAccessibilityEnabled(context);
    }

    /**
     * @param target The view the accessibility event is initialized on.
     *               If null, this method has no effect.
     * @param type   See TYPE_ constants defined in {@link AccessibilityEvent}.
     * @param text   Optional text to add to the event, which will be announced to the user.
     */
    public static void sendCustomAccessibilityEvent(@Nullable View target, int type,
            @Nullable String text) {
        if (target != null && isObservedEventType(target.getContext(), type)) {
            AccessibilityEvent event = AccessibilityEvent.obtain(type);
            target.onInitializeAccessibilityEvent(event);
            if (!TextUtils.isEmpty(text)) {
                event.getText().add(text);
            }
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

        sendEventToTest(
                accessibilityManager, context, TestProtocol.SWITCHED_TO_STATE_MESSAGE, parcel);
        Log.d(TestProtocol.PERMANENT_DIAG_TAG, "sendStateEventToTest: " + stateOrdinal);
    }

    public static void sendTestProtocolEventToTest(Context context, String testProtocolEvent) {
        final AccessibilityManager accessibilityManager = getAccessibilityManagerForTest(context);
        if (accessibilityManager == null) return;

        sendEventToTest(accessibilityManager, context, testProtocolEvent, null);
    }
    private static void sendEventToTest(
            AccessibilityManager accessibilityManager,
            Context context, String eventTag, Bundle data) {
        final AccessibilityEvent e = AccessibilityEvent.obtain(
                AccessibilityEvent.TYPE_ANNOUNCEMENT);
        e.setClassName(eventTag);
        e.setParcelableData(data);
        e.setPackageName(context.getApplicationContext().getPackageName());
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
        if (!Utilities.isRunningInTestHarness()) return null;

        final AccessibilityManager accessibilityManager = getManager(context);
        if (!accessibilityManager.isEnabled()) return null;

        return accessibilityManager;
    }

    public static int getRecommendedTimeoutMillis(Context context, int originalTimeout, int flags) {
        return getManager(context).getRecommendedTimeoutMillis(originalTimeout, flags);
    }
}
