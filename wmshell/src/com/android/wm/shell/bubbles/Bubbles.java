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

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.LOCAL_VARIABLE;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.app.NotificationChannel;
import android.content.Intent;
import android.content.pm.UserInfo;
import android.graphics.drawable.Icon;
import android.hardware.HardwareBuffer;
import android.os.UserHandle;
import android.service.notification.NotificationListenerService;
import android.service.notification.NotificationListenerService.RankingMap;
import android.util.Pair;
import android.util.SparseArray;
import android.window.ScreenCapture.ScreenshotHardwareBuffer;
import android.window.ScreenCapture.SynchronousScreenCaptureListener;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import com.android.wm.shell.common.bubbles.BubbleBarLocation;
import com.android.wm.shell.common.bubbles.BubbleBarUpdate;
import com.android.wm.shell.shared.annotations.ExternalThread;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Interface to engage bubbles feature.
 */
@ExternalThread
public interface Bubbles {

    @Retention(SOURCE)
    @IntDef({DISMISS_USER_GESTURE, DISMISS_AGED, DISMISS_TASK_FINISHED, DISMISS_BLOCKED,
            DISMISS_NOTIF_CANCEL, DISMISS_ACCESSIBILITY_ACTION, DISMISS_NO_LONGER_BUBBLE,
            DISMISS_USER_CHANGED, DISMISS_GROUP_CANCELLED, DISMISS_INVALID_INTENT,
            DISMISS_OVERFLOW_MAX_REACHED, DISMISS_SHORTCUT_REMOVED, DISMISS_PACKAGE_REMOVED,
            DISMISS_NO_BUBBLE_UP, DISMISS_RELOAD_FROM_DISK, DISMISS_USER_ACCOUNT_REMOVED,
            DISMISS_SWITCH_TO_STACK})
    @Target({FIELD, LOCAL_VARIABLE, PARAMETER})
    @interface DismissReason {
    }

    int DISMISS_USER_GESTURE = 1;
    int DISMISS_AGED = 2;
    int DISMISS_TASK_FINISHED = 3;
    int DISMISS_BLOCKED = 4;
    int DISMISS_NOTIF_CANCEL = 5;
    int DISMISS_ACCESSIBILITY_ACTION = 6;
    int DISMISS_NO_LONGER_BUBBLE = 7;
    int DISMISS_USER_CHANGED = 8;
    int DISMISS_GROUP_CANCELLED = 9;
    int DISMISS_INVALID_INTENT = 10;
    int DISMISS_OVERFLOW_MAX_REACHED = 11;
    int DISMISS_SHORTCUT_REMOVED = 12;
    int DISMISS_PACKAGE_REMOVED = 13;
    int DISMISS_NO_BUBBLE_UP = 14;
    int DISMISS_RELOAD_FROM_DISK = 15;
    int DISMISS_USER_ACCOUNT_REMOVED = 16;
    int DISMISS_SWITCH_TO_STACK = 17;

    /** Returns a binder that can be passed to an external process to manipulate Bubbles. */
    default IBubbles createExternalInterface() {
        return null;
    }

    /**
     * @return {@code true} if there is a bubble associated with the provided key and if its
     * notification is hidden from the shade or there is a group summary associated with the
     * provided key that is hidden from the shade because it has been dismissed but still has child
     * bubbles active.
     */
    boolean isBubbleNotificationSuppressedFromShade(String key, String groupKey);

    /**
     * @return {@code true} if the current notification entry same as selected bubble
     * notification entry and the stack is currently expanded.
     */
    boolean isBubbleExpanded(String key);

    /** Tell the stack of bubbles to collapse. */
    void collapseStack();

    /**
     * Request the stack expand if needed, then select the specified Bubble as current.
     * If no bubble exists for this entry, one is created.
     *
     * @param entry the notification for the bubble to be selected
     */
    void expandStackAndSelectBubble(BubbleEntry entry);

    /**
     * Request the stack expand if needed, then select the specified Bubble as current.
     *
     * @param bubble the bubble to be selected
     */
    void expandStackAndSelectBubble(Bubble bubble);

    /**
     * This method has different behavior depending on:
     * - if an app bubble exists
     * - if an app bubble is expanded
     *
     * If no app bubble exists, this will add and expand a bubble with the provided intent. The
     * intent must be explicit (i.e. include a package name or fully qualified component class name)
     * and the activity for it should be resizable.
     *
     * If an app bubble exists, this will toggle the visibility of it, i.e. if the app bubble is
     * expanded, calling this method will collapse it. If the app bubble is not expanded, calling
     * this method will expand it.
     *
     * These bubbles are <b>not</b> backed by a notification and remain until the user dismisses
     * the bubble or bubble stack.
     *
     * Some notes:
     * - Only one app bubble is supported at a time, regardless of users. Multi-users support is
     * tracked in b/273533235.
     * - Calling this method with a different intent than the existing app bubble will do nothing
     *
     * @param intent the intent to display in the bubble expanded view.
     * @param user   the {@link UserHandle} of the user to start this activity for.
     * @param icon   the {@link Icon} to use for the bubble view.
     */
    void showOrHideAppBubble(Intent intent, UserHandle user, @Nullable Icon icon);

    /** @return true if the specified {@code taskId} corresponds to app bubble's taskId. */
    boolean isAppBubbleTaskId(int taskId);

    /**
`    * @return a {@link SynchronousScreenCaptureListener} after performing a screenshot that may
     * exclude the bubble layer, if one is present. The underlying
     * {@link ScreenshotHardwareBuffer} can be accessed via
     * {@link SynchronousScreenCaptureListener#getBuffer()} asynchronously and care should be taken
     * to {@link HardwareBuffer#close()} the associated
     * {@link ScreenshotHardwareBuffer#getHardwareBuffer()} when no longer required.`
     */
    SynchronousScreenCaptureListener getScreenshotExcludingBubble(int displayId);

    /**
     * @return a bubble that matches the provided shortcutId, if one exists.
     */
    @Nullable
    Bubble getBubbleWithShortcutId(String shortcutId);

    /**
     * We intercept notification entries (including group summaries) dismissed by the user when
     * there is an active bubble associated with it. We do this so that developers can still
     * cancel it (and hence the bubbles associated with it). However, these intercepted
     * notifications should then be hidden from the shade since the user has cancelled them, so we
     * {@link Bubble#setSuppressNotification}.  For the case of suppressed summaries, we also add
     * {@link BubbleData#addSummaryToSuppress}.
     *
     * @param entry          the notification of the BubbleEntry should be removed.
     * @param children       the list of child notification of the BubbleEntry from 1st param entry,
     *                       this will be null if entry does have no children.
     * @param removeCallback the remove callback for SystemUI side to remove notification, the int
     *                       number should be list position of children list and use -1 for
     *                       removing the parent notification.
     * @return true if we want to intercept the dismissal of the entry, else false.
     */
    boolean handleDismissalInterception(BubbleEntry entry, @Nullable List<BubbleEntry> children,
            IntConsumer removeCallback, Executor callbackExecutor);

    /** Set the proxy to commnuicate with SysUi side components. */
    void setSysuiProxy(SysuiProxy proxy);

    /** Set a listener to be notified of bubble expand events. */
    void setExpandListener(BubbleExpandListener listener);

    /**
     * Called when new notification entry added.
     *
     * @param entry the {@link BubbleEntry} by the notification.
     */
    void onEntryAdded(BubbleEntry entry);

    /**
     * Called when new notification entry updated.
     *
     * @param entry          the {@link BubbleEntry} by the notification.
     * @param shouldBubbleUp {@code true} if this notification should bubble up.
     * @param fromSystem     {@code true} if this update is from NotificationManagerService.
     */
    void onEntryUpdated(BubbleEntry entry, boolean shouldBubbleUp, boolean fromSystem);

    /**
     * Called when new notification entry removed.
     *
     * @param entry the {@link BubbleEntry} by the notification.
     */
    void onEntryRemoved(BubbleEntry entry);

    /**
     * Called when NotificationListener has received adjusted notification rank and reapplied
     * filtering and sorting. This is used to dismiss or create bubbles based on changes in
     * permissions on the notification channel or the global setting.
     *
     * @param rankingMap     the updated ranking map from NotificationListenerService
     * @param entryDataByKey a map of ranking key to bubble entry and whether the entry should
     *                       bubble up
     */
    void onRankingUpdated(
            RankingMap rankingMap,
            HashMap<String, Pair<BubbleEntry, Boolean>> entryDataByKey);

    /**
     * Called when a notification channel is modified, in response to
     * {@link NotificationListenerService#onNotificationChannelModified}.
     *
     * @param pkg              the package the notification channel belongs to.
     * @param user             the user the notification channel belongs to.
     * @param channel          the channel being modified.
     * @param modificationType the type of modification that occurred to the channel.
     */
    void onNotificationChannelModified(
            String pkg,
            UserHandle user,
            NotificationChannel channel,
            int modificationType);

    /**
     * Called when notification panel is expanded or collapsed
     */
    void onNotificationPanelExpandedChanged(boolean expanded);

    /**
     * Called when the status bar has become visible or invisible (either permanently or
     * temporarily).
     */
    void onStatusBarVisibilityChanged(boolean visible);

    /** Called when system zen mode state changed. */
    void onZenStateChanged();

    /**
     * Called when statusBar state changed.
     *
     * @param isShade {@code true} is state is SHADE.
     */
    void onStatusBarStateChanged(boolean isShade);

    /**
     * Called when the current user changed.
     *
     * @param newUserId the new user's id.
     */
    void onUserChanged(int newUserId);

    /**
     * Called when the current user profiles change.
     *
     * @param currentProfiles the user infos for the current profile.
     */
    void onCurrentProfilesChanged(SparseArray<UserInfo> currentProfiles);

    /**
     * Called when a user is removed.
     *
     * @param removedUserId the id of the removed user.
     */
    void onUserRemoved(int removedUserId);

    /**
     * Called when the Sensitive notification protection state has changed, such as when media
     * projection starts and stops.
     *
     * @param sensitiveNotificationProtectionActive {@code true} if notifications should be
     *     protected
     */
    void onSensitiveNotificationProtectionStateChanged(
            boolean sensitiveNotificationProtectionActive);

    /**
     * Determines whether Bubbles can show notifications.
     *
     * <p>Normally bubble notifications are shown by Bubbles, but in some cases the bubble
     * notification is suppressed and should be shown by the Notifications pipeline as regular
     * notifications.
     */
    boolean canShowBubbleNotification();

    /**
     * A listener to be notified of bubble state changes, used by launcher to render bubbles in
     * its process.
     */
    interface BubbleStateListener {
        /**
         * Called when the bubbles state changes.
         */
        void onBubbleStateChange(BubbleBarUpdate update);

        /**
         * Called when bubble bar should temporarily be animated to a new location.
         * Does not result in a state change.
         */
        void animateBubbleBarLocation(BubbleBarLocation location);
    }

    /** Listener to find out about stack expansion / collapse events. */
    interface BubbleExpandListener {
        /**
         * Called when the expansion state of the bubble stack changes.
         *
         * @param isExpanding whether it's expanding or collapsing
         * @param key         the notification key associated with bubble being expanded
         */
        void onBubbleExpandChanged(boolean isExpanding, String key);
    }

    /** Listener to be notified when the flags on BubbleMetadata have changed. */
    interface BubbleMetadataFlagListener {
        /** Called when the flags on BubbleMetadata have changed for the provided bubble. */
        void onBubbleMetadataFlagChanged(Bubble bubble);
    }

    /** Listener to be notified when a pending intent has been canceled for a bubble. */
    interface PendingIntentCanceledListener {
        /** Called when the pending intent for a bubble has been canceled. */
        void onPendingIntentCanceled(Bubble bubble);
    }

    /** Callback to tell SysUi components execute some methods. */
    interface SysuiProxy {

        /** Provider interface for {@link SysuiProxy}. */
        interface Provider {
            /** Returns {@link SysuiProxy}. */
            SysuiProxy getSysuiProxy();
        }

        void isNotificationPanelExpand(Consumer<Boolean> callback);

        void getPendingOrActiveEntry(String key, Consumer<BubbleEntry> callback);

        void getShouldRestoredEntries(Set<String> savedBubbleKeys,
                Consumer<List<BubbleEntry>> callback);

        void setNotificationInterruption(String key);

        void requestNotificationShadeTopUi(boolean requestTopUi, String componentTag);

        void notifyRemoveNotification(String key, int reason);

        void notifyInvalidateNotifications(String reason);

        void updateNotificationBubbleButton(String key);

        void onStackExpandChanged(boolean shouldExpand);

        void onManageMenuExpandChanged(boolean menuExpanded);

        void onUnbubbleConversation(String key);
    }
}
