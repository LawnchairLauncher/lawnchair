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

package com.android.wm.shell.bubbles;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.logging.UiEvent;
import com.android.internal.logging.UiEventLogger;
import com.android.internal.util.FrameworkStatsLog;

/**
 * Implementation of UiEventLogger for logging bubble UI events.
 *
 * See UiEventReported atom in atoms.proto for more context.
 */
public class BubbleLogger {

    private final UiEventLogger mUiEventLogger;

    /**
     * Bubble UI event.
     */
    @VisibleForTesting
    public enum Event implements UiEventLogger.UiEventEnum {

        @UiEvent(doc = "User dismissed the bubble via gesture, add bubble to overflow.")
        BUBBLE_OVERFLOW_ADD_USER_GESTURE(483),

        @UiEvent(doc = "No more space in top row, add bubble to overflow.")
        BUBBLE_OVERFLOW_ADD_AGED(484),

        @UiEvent(doc = "No more space in overflow, remove bubble from overflow")
        BUBBLE_OVERFLOW_REMOVE_MAX_REACHED(485),

        @UiEvent(doc = "Notification canceled, remove bubble from overflow.")
        BUBBLE_OVERFLOW_REMOVE_CANCEL(486),

        @UiEvent(doc = "Notification group canceled, remove bubble for child notif from overflow.")
        BUBBLE_OVERFLOW_REMOVE_GROUP_CANCEL(487),

        @UiEvent(doc = "Notification no longer bubble, remove bubble from overflow.")
        BUBBLE_OVERFLOW_REMOVE_NO_LONGER_BUBBLE(488),

        @UiEvent(doc = "User tapped overflow bubble. Promote bubble back to top row.")
        BUBBLE_OVERFLOW_REMOVE_BACK_TO_STACK(489),

        @UiEvent(doc = "User blocked notification from bubbling, remove bubble from overflow.")
        BUBBLE_OVERFLOW_REMOVE_BLOCKED(490),

        @UiEvent(doc = "User selected the overflow.")
        BUBBLE_OVERFLOW_SELECTED(600),

        @UiEvent(doc = "Restore bubble to overflow after phone reboot.")
        BUBBLE_OVERFLOW_RECOVER(691);

        private final int mId;

        Event(int id) {
            mId = id;
        }

        @Override
        public int getId() {
            return mId;
        }
    }

    public BubbleLogger(UiEventLogger uiEventLogger) {
        mUiEventLogger = uiEventLogger;
    }

    /**
     * @param b Bubble involved in this UI event
     * @param e UI event
     */
    public void log(Bubble b, UiEventLogger.UiEventEnum e) {
        mUiEventLogger.logWithInstanceId(e, b.getAppUid(), b.getPackageName(), b.getInstanceId());
    }

    /**
     * @param b Bubble removed from overflow
     * @param r Reason that bubble was removed
     */
    public void logOverflowRemove(Bubble b, @Bubbles.DismissReason int r) {
        if (r == Bubbles.DISMISS_NOTIF_CANCEL) {
            log(b, BubbleLogger.Event.BUBBLE_OVERFLOW_REMOVE_CANCEL);
        } else if (r == Bubbles.DISMISS_GROUP_CANCELLED) {
            log(b, BubbleLogger.Event.BUBBLE_OVERFLOW_REMOVE_GROUP_CANCEL);
        } else if (r == Bubbles.DISMISS_NO_LONGER_BUBBLE) {
            log(b, BubbleLogger.Event.BUBBLE_OVERFLOW_REMOVE_NO_LONGER_BUBBLE);
        } else if (r == Bubbles.DISMISS_BLOCKED) {
            log(b, BubbleLogger.Event.BUBBLE_OVERFLOW_REMOVE_BLOCKED);
        }
    }

    /**
     * @param b Bubble added to overflow
     * @param r Reason that bubble was added to overflow
     */
    public void logOverflowAdd(Bubble b, @Bubbles.DismissReason int r) {
        if (r == Bubbles.DISMISS_AGED) {
            log(b, Event.BUBBLE_OVERFLOW_ADD_AGED);
        } else if (r == Bubbles.DISMISS_USER_GESTURE) {
            log(b, Event.BUBBLE_OVERFLOW_ADD_USER_GESTURE);
        } else if (r == Bubbles.DISMISS_RELOAD_FROM_DISK) {
            log(b, Event.BUBBLE_OVERFLOW_RECOVER);
        }
    }

    void logStackUiChanged(String packageName, int action, int bubbleCount, float normalX,
            float normalY) {
        FrameworkStatsLog.write(FrameworkStatsLog.BUBBLE_UI_CHANGED,
                packageName,
                null /* notification channel */,
                0 /* notification ID */,
                0 /* bubble position */,
                bubbleCount,
                action,
                normalX,
                normalY,
                false /* unread bubble */,
                false /* on-going bubble */,
                false /* isAppForeground (unused) */);
    }

    void logShowOverflow(String packageName, int currentUserId) {
        mUiEventLogger.log(BubbleLogger.Event.BUBBLE_OVERFLOW_SELECTED, currentUserId,
                packageName);
    }

    void logBubbleUiChanged(Bubble bubble, String packageName, int action, int bubbleCount,
            float normalX, float normalY, int index) {
        FrameworkStatsLog.write(FrameworkStatsLog.BUBBLE_UI_CHANGED,
                packageName,
                bubble.getChannelId() /* notification channel */,
                bubble.getNotificationId() /* notification ID */,
                index,
                bubbleCount,
                action,
                normalX,
                normalY,
                bubble.showInShade() /* isUnread */,
                false /* isOngoing (unused) */,
                false /* isAppForeground (unused) */);
    }
}