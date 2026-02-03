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

import com.android.internal.logging.UiEvent;
import com.android.internal.logging.UiEventLogger;
import com.android.internal.util.FrameworkStatsLog;

import javax.inject.Inject;

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
    public enum Event implements UiEventLogger.UiEventEnum {

        // region bubble events

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
        BUBBLE_OVERFLOW_RECOVER(691),

        // endregion

        // region bubble bar events

        @UiEvent(doc = "new bubble posted")
        BUBBLE_BAR_BUBBLE_POSTED(1927),

        @UiEvent(doc = "existing bubble updated")
        BUBBLE_BAR_BUBBLE_UPDATED(1928),

        @UiEvent(doc = "expanded a bubble from bubble bar")
        BUBBLE_BAR_EXPANDED(1929),

        @UiEvent(doc = "bubble bar collapsed")
        BUBBLE_BAR_COLLAPSED(1930),

        @UiEvent(doc = "dismissed single bubble from bubble bar by dragging it to dismiss target")
        BUBBLE_BAR_BUBBLE_DISMISSED_DRAG_BUBBLE(1931),

        @UiEvent(doc = "dismissed single bubble from bubble bar by dragging the expanded view to "
                + "dismiss target")
        BUBBLE_BAR_BUBBLE_DISMISSED_DRAG_EXP_VIEW(1932),

        @UiEvent(doc = "dismiss bubble from app handle menu")
        BUBBLE_BAR_BUBBLE_DISMISSED_APP_MENU(1933),

        @UiEvent(doc = "bubble is dismissed due to app finishing the bubble activity")
        BUBBLE_BAR_BUBBLE_ACTIVITY_FINISH(1934),

        @UiEvent(doc = "dismissed the bubble bar by dragging it to dismiss target")
        BUBBLE_BAR_DISMISSED_DRAG_BAR(1935),

        @UiEvent(doc = "bubble bar moved to the left edge of the screen by dragging from the "
                + "expanded view")
        BUBBLE_BAR_MOVED_LEFT_DRAG_EXP_VIEW(1936),

        @UiEvent(doc = "bubble bar moved to the left edge of the screen by dragging from a single"
                + " bubble")
        BUBBLE_BAR_MOVED_LEFT_DRAG_BUBBLE(1937),

        @UiEvent(doc = "bubble bar moved to the left edge of the screen by dragging the bubble bar")
        BUBBLE_BAR_MOVED_LEFT_DRAG_BAR(1938),

        @UiEvent(doc = "bubble bar moved to the right edge of the screen by dragging from the "
                + "expanded view")
        BUBBLE_BAR_MOVED_RIGHT_DRAG_EXP_VIEW(1939),

        @UiEvent(doc = "bubble bar moved to the right edge of the screen by dragging from a "
                + "single bubble")
        BUBBLE_BAR_MOVED_RIGHT_DRAG_BUBBLE(1940),

        @UiEvent(doc = "bubble bar moved to the right edge of the screen by dragging the bubble "
                + "bar")
        BUBBLE_BAR_MOVED_RIGHT_DRAG_BAR(1941),

        @UiEvent(doc = "stop bubbling conversation from app handle menu")
        BUBBLE_BAR_APP_MENU_OPT_OUT(1942),

        @UiEvent(doc = "open app settings from app handle menu")
        BUBBLE_BAR_APP_MENU_GO_TO_SETTINGS(1943),

        @UiEvent(doc = "flyout shown for a bubble")
        BUBBLE_BAR_FLYOUT(1944),

        @UiEvent(doc = "notification for the bubble was canceled")
        BUBBLE_BAR_BUBBLE_REMOVED_CANCELED(1945),

        @UiEvent(doc = "user turned off bubbles from settings")
        BUBBLE_BAR_BUBBLE_REMOVED_BLOCKED(1946),

        @UiEvent(doc = "bubble bar overflow opened")
        BUBBLE_BAR_OVERFLOW_SELECTED(1947),

        @UiEvent(doc = "max number of bubbles was reached in bubble bar, move bubble to overflow")
        BUBBLE_BAR_OVERFLOW_ADD_AGED(1948),

        @UiEvent(doc = "bubble promoted from overflow back to bubble bar")
        BUBBLE_BAR_OVERFLOW_REMOVE_BACK_TO_BAR(1949),

        @UiEvent(doc = "application icon is dropped in the BubbleBar left drop zone")
        BUBBLE_BAR_MOVED_LEFT_APP_ICON_DROP(2082),

        @UiEvent(doc = "application icon is dropped in the BubbleBar right drop zone")
        BUBBLE_BAR_MOVED_RIGHT_APP_ICON_DROP(2083),

        @UiEvent(doc = "while bubble bar is expanded, switch to another/existing bubble")
        BUBBLE_BAR_BUBBLE_SWITCHED(1977),

        @UiEvent(doc = "bubble bar moved to the left edge of the screen by dragging a task")
        BUBBLE_BAR_MOVED_LEFT_DRAG_TASK(2146),

        @UiEvent(doc = "bubble bar moved to the right edge of the screen by dragging a task")
        BUBBLE_BAR_MOVED_RIGHT_DRAG_TASK(2147),

        // endregion
        ;

        private final int mId;

        Event(int id) {
            mId = id;
        }

        @Override
        public int getId() {
            return mId;
        }
    }

    @Inject
    public BubbleLogger(UiEventLogger uiEventLogger) {
        mUiEventLogger = uiEventLogger;
    }

    /**
     * Log an UIEvent
     */
    public void log(UiEventLogger.UiEventEnum e) {
        mUiEventLogger.log(e);
    }

    /**
     * Log an UIEvent with the given bubble info
     */
    public void log(Bubble b, UiEventLogger.UiEventEnum e) {
        mUiEventLogger.logWithInstanceId(e, b.getAppUid(), b.getPackageName(), b.getInstanceId());
    }

    /**
     * Log when a bubble is removed from overflow in stack view
     *
     * @param b Bubble removed from overflow
     * @param r Reason that bubble was removed
     */
    public void logStackOverflowRemove(Bubble b, @Bubbles.DismissReason int r) {
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
    public void logOverflowAdd(Bubble b, boolean bubbleBar, @Bubbles.DismissReason int r) {
        if (bubbleBar) {
            if (r == Bubbles.DISMISS_AGED) {
                log(b, Event.BUBBLE_BAR_OVERFLOW_ADD_AGED);
            }
        } else {
            if (r == Bubbles.DISMISS_AGED) {
                log(b, Event.BUBBLE_OVERFLOW_ADD_AGED);
            } else if (r == Bubbles.DISMISS_USER_GESTURE) {
                log(b, Event.BUBBLE_OVERFLOW_ADD_USER_GESTURE);
            } else if (r == Bubbles.DISMISS_RELOAD_FROM_DISK) {
                log(b, Event.BUBBLE_OVERFLOW_RECOVER);
            }
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