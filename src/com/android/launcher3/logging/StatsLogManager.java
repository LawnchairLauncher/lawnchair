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
package com.android.launcher3.logging;

import android.content.Context;

import androidx.annotation.Nullable;

import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.logger.LauncherAtom;
import com.android.launcher3.logging.StatsLogUtils.LogStateProvider;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.ResourceBasedOverride;

/**
 * Handles the user event logging in R+.
 * All of the event ids are defined here.
 * Most of the methods are dummy methods for Launcher3
 * Actual call happens only for Launcher variant that implements QuickStep.
 */
public class StatsLogManager implements ResourceBasedOverride {

    public interface EventEnum {
        int getId();
    }

    public enum LauncherEvent implements EventEnum {
        /* Used to prevent double logging. */
        IGNORE(-1),

        @UiEvent(doc = "App launched from workspace, hotseat or folder in launcher")
        LAUNCHER_APP_LAUNCH_TAP(338),

        @UiEvent(doc = "Task launched from overview using TAP")
        LAUNCHER_TASK_LAUNCH_TAP(339),

        @UiEvent(doc = "User tapped on notification inside popup context menu.")
        LAUNCHER_NOTIFICATION_LAUNCH_TAP(516),

        @UiEvent(doc = "Task launched from overview using SWIPE DOWN")
        LAUNCHER_TASK_LAUNCH_SWIPE_DOWN(340),

        @UiEvent(doc = "TASK dismissed from overview using SWIPE UP")
        LAUNCHER_TASK_DISMISS_SWIPE_UP(341),

        @UiEvent(doc = "User dragged a launcher item")
        LAUNCHER_ITEM_DRAG_STARTED(383),

        @UiEvent(doc = "A dragged launcher item is successfully dropped")
        LAUNCHER_ITEM_DROP_COMPLETED(385),

        @UiEvent(doc = "A dragged launcher item is successfully dropped on another item "
                + "resulting in a new folder creation")
        LAUNCHER_ITEM_DROP_FOLDER_CREATED(386),

        @UiEvent(doc = "User action resulted in or manually updated the folder label to "
                + "new/same value.")
        LAUNCHER_FOLDER_LABEL_UPDATED(460),

        @UiEvent(doc = "User long pressed on the workspace empty space.")
        LAUNCHER_WORKSPACE_LONGPRESS(461),

        @UiEvent(doc = "User tapped or long pressed on a wallpaper icon inside launcher settings.")
        LAUNCHER_WALLPAPER_BUTTON_TAP_OR_LONGPRESS(462),

        @UiEvent(doc = "User tapped or long pressed on settings icon inside launcher settings.")
        LAUNCHER_SETTINGS_BUTTON_TAP_OR_LONGPRESS(463),

        @UiEvent(doc = "User tapped or long pressed on widget tray icon inside launcher settings.")
        LAUNCHER_WIDGETSTRAY_BUTTON_TAP_OR_LONGPRESS(464),

        @UiEvent(doc = "A dragged item is dropped on 'Remove' button in the target bar")
        LAUNCHER_ITEM_DROPPED_ON_REMOVE(465),

        @UiEvent(doc = "A dragged item is dropped on 'Cancel' button in the target bar")
        LAUNCHER_ITEM_DROPPED_ON_CANCEL(466),

        @UiEvent(doc = "A predicted item is dragged and dropped on 'Don't suggest app'"
                + " button in the target bar")
        LAUNCHER_ITEM_DROPPED_ON_DONT_SUGGEST(467),

        @UiEvent(doc = "A dragged item is dropped on 'Uninstall' button in target bar")
        LAUNCHER_ITEM_DROPPED_ON_UNINSTALL(468),

        @UiEvent(doc = "User completed uninstalling the package after dropping on "
                + "the icon onto 'Uninstall' button in the target bar")
        LAUNCHER_ITEM_UNINSTALL_COMPLETED(469),

        @UiEvent(doc = "User cancelled uninstalling the package after dropping on "
                + "the icon onto 'Uninstall' button in the target bar")
        LAUNCHER_ITEM_UNINSTALL_CANCELLED(470),

        @UiEvent(doc = "User tapped or long pressed on the task icon(aka package icon) "
                + "from overview to open task menu.")
        LAUNCHER_TASK_ICON_TAP_OR_LONGPRESS(517),

        @UiEvent(doc = "User opened package specific widgets list by tapping on widgets system "
                + "shortcut inside popup context menu.")
        LAUNCHER_SYSTEM_SHORTCUT_WIDGETS_TAP(514),

        @UiEvent(doc = "User tapped on app info system shortcut.")
        LAUNCHER_SYSTEM_SHORTCUT_APP_INFO_TAP(515),

        @UiEvent(doc = "User tapped on split screen icon on a task menu.")
        LAUNCHER_SYSTEM_SHORTCUT_SPLIT_SCREEN_TAP(518),

        @UiEvent(doc = "User tapped on free form icon on a task menu.")
        LAUNCHER_SYSTEM_SHORTCUT_FREE_FORM_TAP(519),

        @UiEvent(doc = "User tapped on pause app system shortcut.")
        LAUNCHER_SYSTEM_SHORTCUT_PAUSE_TAP(521),

        @UiEvent(doc = "User tapped on pin system shortcut.")
        LAUNCHER_SYSTEM_SHORTCUT_PIN_TAP(522),

        @UiEvent(doc = "User is shown All Apps education view.")
        LAUNCHER_ALL_APPS_EDU_SHOWN(523),

        @UiEvent(doc = "User opened a folder.")
        LAUNCHER_FOLDER_OPEN(551),

        @UiEvent(doc = "Hotseat education half sheet seen")
        LAUNCHER_HOTSEAT_EDU_SEEN(479),

        @UiEvent(doc = "Hotseat migration accepted")
        LAUNCHER_HOTSEAT_EDU_ACCEPT(480),

        @UiEvent(doc = "Hotseat migration denied")
        LAUNCHER_HOTSEAT_EDU_DENY(481),

        @UiEvent(doc = "Hotseat education tip shown")
        LAUNCHER_HOTSEAT_EDU_ONLY_TIP(482),

        @UiEvent(doc = "App launch ranking logged for all apps predictions")
        LAUNCHER_ALL_APPS_RANKED(552),

        @UiEvent(doc = "App launch ranking logged for hotseat predictions)")
        LAUNCHER_HOTSEAT_RANKED(553);
        // ADD MORE
        private final int mId;

        LauncherEvent(int id) {
            mId = id;
        }

        public int getId() {
            return mId;
        }
    }

    /**
     * Launcher specific ranking related events.
     */
    public enum LauncherRankingEvent implements EventEnum {

        UNKNOWN(0);
        // ADD MORE

        private final int mId;

        LauncherRankingEvent(int id) {
            mId = id;
        }

        public int getId() {
            return mId;
        }
    }

    protected LogStateProvider mStateProvider;

    /**
     * Creates a new instance of {@link StatsLogManager} based on provided context.
     */
    public static StatsLogManager newInstance(Context context) {
        return newInstance(context, null);
    }

    public static StatsLogManager newInstance(Context context, LogStateProvider stateProvider) {
        StatsLogManager mgr = Overrides.getObject(StatsLogManager.class,
                context.getApplicationContext(), R.string.stats_log_manager_class);
        mgr.mStateProvider = stateProvider;
        return mgr;
    }

    /**
     * Logs an event.
     *
     * @param event an enum implementing EventEnum interface.
     */
    public void log(EventEnum event) {
    }

    /**
     * Logs an event.
     *
     * @param event an enum implementing EventEnum interface.
     * @param instanceId an identifier obtained from an InstanceIdSequence.
     */
    public void log(EventEnum event, InstanceId instanceId) {
    }

    /**
     * Logs an event.
     *
     * @param event an enum implementing EventEnum interface.
     * @param itemInfo item typically containing app or task launch related information.
     */
    public void log(EventEnum event, @Nullable ItemInfo itemInfo) {
    }

    /**
     * Logs an event.
     *
     * @param event an enum implementing EventEnum interface.
     * @param atomInfo item typically containing app or task launch related information.
     */
    public void log(EventEnum event, InstanceId instanceId, LauncherAtom.ItemInfo atomInfo) {
    }

    /**
     * Logs an event and accompanying {@link LauncherState}s.
     *
     * @param event an enum implementing EventEnum interface.
     * @param launcherAtomItemInfo item typically containing app or task launch related information.
     */
    public void log(EventEnum event, @Nullable LauncherAtom.ItemInfo launcherAtomItemInfo,
            int srcState, int dstState) {
    }

    /**
     * Logs an event.
     *
     * @param event an enum implementing EventEnum interface.
     * @param instanceId an identifier obtained from an InstanceIdSequence.
     * @param itemInfo item typically containing app or task launch related information.
     */
    public void log(EventEnum event, InstanceId instanceId, @Nullable ItemInfo itemInfo) {
    }

    /**
     * Log an event with ranked-choice information along with package. Does nothing if event.getId()
     * <= 0.
     *
     * @param rankingEvent an enum implementing EventEnum interface.
     * @param instanceId An identifier obtained from an InstanceIdSequence.
     * @param packageName the package name of the relevant app, if known (null otherwise).
     * @param position the position picked.
     */
    public void log(EventEnum rankingEvent, InstanceId instanceId, @Nullable String packageName,
            int position) {
    }

    /**
     * Logs an event and accompanying {@link LauncherState}s. If either of the state refers
     * to workspace state, then use pageIndex to pass in index of workspace.
     */
    public void log(EventEnum event, int srcState, int dstState, int pageIndex) {
    }

    /**
     * Logs snapshot, or impression of the current workspace.
     */
    public void logSnapshot() {
    }
}
