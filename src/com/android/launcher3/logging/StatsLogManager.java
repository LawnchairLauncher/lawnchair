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

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.IGNORE;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_CLOSE_DOWN;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_OPEN_UP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_HOME_GESTURE;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_OVERVIEW_GESTURE;

import android.content.Context;

import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.launcher3.logger.LauncherAtom.ContainerInfo;
import com.android.launcher3.logger.LauncherAtom.FromState;
import com.android.launcher3.logger.LauncherAtom.ToState;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.userevent.LauncherLogProto;
import com.android.launcher3.util.ResourceBasedOverride;

import java.util.List;

/**
 * Handles the user event logging in R+.
 *
 * <pre>
 * All of the event ids are defined here.
 * Most of the methods are dummy methods for Launcher3
 * Actual call happens only for Launcher variant that implements QuickStep.
 * </pre>
 */
public class StatsLogManager implements ResourceBasedOverride {

    public static final int LAUNCHER_STATE_UNSPECIFIED = 0;
    public static final int LAUNCHER_STATE_BACKGROUND = 1;
    public static final int LAUNCHER_STATE_HOME = 2;
    public static final int LAUNCHER_STATE_OVERVIEW = 3;
    public static final int LAUNCHER_STATE_ALLAPPS = 4;
    public static final int LAUNCHER_STATE_UNCHANGED = 5;

    /**
     * Returns proper launcher state enum for {@link StatsLogManager}(to be removed during
     * UserEventDispatcher cleanup)
     */
    public static int containerTypeToAtomState(int containerType) {
        switch (containerType) {
            case LauncherLogProto.ContainerType.ALLAPPS_VALUE:
                return LAUNCHER_STATE_ALLAPPS;
            case LauncherLogProto.ContainerType.OVERVIEW_VALUE:
                return LAUNCHER_STATE_OVERVIEW;
            case LauncherLogProto.ContainerType.WORKSPACE_VALUE:
                return LAUNCHER_STATE_HOME;
            case LauncherLogProto.ContainerType.APP_VALUE:
                return LAUNCHER_STATE_BACKGROUND;
        }
        return LAUNCHER_STATE_UNSPECIFIED;
    }

    /**
     * Returns event enum based on the two {@link ContainerType} transition information when swipe
     * gesture happens(to be removed during UserEventDispatcher cleanup).
     */
    public static EventEnum getLauncherAtomEvent(int startContainerType,
            int targetContainerType, EventEnum fallbackEvent) {
        if (startContainerType == LauncherLogProto.ContainerType.WORKSPACE.getNumber()
                && targetContainerType == LauncherLogProto.ContainerType.WORKSPACE.getNumber()) {
            return LAUNCHER_HOME_GESTURE;
        } else if (startContainerType != LauncherLogProto.ContainerType.TASKSWITCHER.getNumber()
                && targetContainerType == LauncherLogProto.ContainerType.TASKSWITCHER.getNumber()) {
            return LAUNCHER_OVERVIEW_GESTURE;
        } else if (startContainerType != LauncherLogProto.ContainerType.ALLAPPS.getNumber()
                && targetContainerType == LauncherLogProto.ContainerType.ALLAPPS.getNumber()) {
            return LAUNCHER_ALLAPPS_OPEN_UP;
        } else if (startContainerType == LauncherLogProto.ContainerType.ALLAPPS.getNumber()
                && targetContainerType != LauncherLogProto.ContainerType.ALLAPPS.getNumber()) {
            return LAUNCHER_ALLAPPS_CLOSE_DOWN;
        }
        return fallbackEvent; // TODO fix
    }

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

        @UiEvent(doc = "Folder's label is automatically assigned.")
        LAUNCHER_FOLDER_AUTO_LABELED(591),

        @UiEvent(doc = "Could not auto-label a folder because primary suggestion is null or empty.")
        LAUNCHER_FOLDER_AUTO_LABELING_SKIPPED_EMPTY_PRIMARY(592),

        @UiEvent(doc = "Could not auto-label a folder because no suggestions exist.")
        LAUNCHER_FOLDER_AUTO_LABELING_SKIPPED_EMPTY_SUGGESTIONS(593),

        @UiEvent(doc = "User manually updated the folder label.")
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

        /**
         * @deprecated LauncherUiChanged.rank field is repurposed to store all apps rank, so no
         * separate event is required.
         */
        @Deprecated
        @UiEvent(doc = "App launch ranking logged for all apps predictions")
        LAUNCHER_ALL_APPS_RANKED(552),

        @UiEvent(doc = "App launch ranking logged for hotseat predictions)")
        LAUNCHER_HOTSEAT_RANKED(553),
        @UiEvent(doc = "Launcher is now in background. e.g., Screen off event")
        LAUNCHER_ONSTOP(562),

        @UiEvent(doc = "Launcher is now in foreground. e.g., Screen on event, back button")
        LAUNCHER_ONRESUME(563),

        @UiEvent(doc = "User swipes or fling in LEFT direction on workspace.")
        LAUNCHER_SWIPELEFT(564),

        @UiEvent(doc = "User swipes or fling in RIGHT direction on workspace.")
        LAUNCHER_SWIPERIGHT(565),

        @UiEvent(doc = "User swipes or fling in UP direction in unknown way.")
        LAUNCHER_UNKNOWN_SWIPEUP(566),

        @UiEvent(doc = "User swipes or fling in DOWN direction in unknown way.")
        LAUNCHER_UNKNOWN_SWIPEDOWN(567),

        @UiEvent(doc = "User swipes or fling in UP direction to open apps drawer.")
        LAUNCHER_ALLAPPS_OPEN_UP(568),

        @UiEvent(doc = "User swipes or fling in DOWN direction to close apps drawer.")
        LAUNCHER_ALLAPPS_CLOSE_DOWN(569),

        @UiEvent(doc = "User swipes or fling in UP direction and hold from the bottom bazel area")
        LAUNCHER_OVERVIEW_GESTURE(570),

        @UiEvent(doc = "User swipes or fling in LEFT direction on the bottom bazel area.")
        LAUNCHER_QUICKSWITCH_LEFT(571),

        @UiEvent(doc = "User swipes or fling in RIGHT direction on the bottom bazel area.")
        LAUNCHER_QUICKSWITCH_RIGHT(572),

        @UiEvent(doc = "User swipes or fling in DOWN direction on the bottom bazel area.")
        LAUNCHER_SWIPEDOWN_NAVBAR(573),

        @UiEvent(doc = "User swipes or fling in UP direction from bottom bazel area.")
        LAUNCHER_HOME_GESTURE(574),

        @UiEvent(doc = "User's workspace layout information is snapshot in the background.")
        LAUNCHER_WORKSPACE_SNAPSHOT(579),

        @UiEvent(doc = "User tapped on the screenshot button on overview)")
        LAUNCHER_OVERVIEW_ACTIONS_SCREENSHOT(580),

        @UiEvent(doc = "User tapped on the select button on overview)")
        LAUNCHER_OVERVIEW_ACTIONS_SELECT(581),

        @UiEvent(doc = "User tapped on the share button on overview")
        LAUNCHER_OVERVIEW_ACTIONS_SHARE(582),

        @UiEvent(doc = "User tapped on the close button in select mode")
        LAUNCHER_SELECT_MODE_CLOSE(583),

        @UiEvent(doc = "User tapped on the highlight items in select mode")
        LAUNCHER_SELECT_MODE_ITEM(584),

        @UiEvent(doc = "Notification dot on app icon enabled.")
        LAUNCHER_NOTIFICATION_DOT_ENABLED(611),

        @UiEvent(doc = "Notification dot on app icon disabled.")
        LAUNCHER_NOTIFICATION_DOT_DISABLED(612),

        @UiEvent(doc = "For new apps, add app icons to home screen enabled.")
        LAUNCHER_ADD_NEW_APPS_TO_HOME_SCREEN_ENABLED(613),

        @UiEvent(doc = "For new apps, add app icons to home screen disabled.")
        LAUNCHER_ADD_NEW_APPS_TO_HOME_SCREEN_DISABLED(614),

        @UiEvent(doc = "Home screen rotation is enabled when phone is rotated.")
        LAUNCHER_HOME_SCREEN_ROTATION_ENABLED(615),

        @UiEvent(doc = "Home screen rotation is disabled when phone is rotated.")
        LAUNCHER_HOME_SCREEN_ROTATION_DISABLED(616),

        @UiEvent(doc = "Suggestions in all apps list enabled.")
        LAUNCHER_ALL_APPS_SUGGESTIONS_ENABLED(619),

        @UiEvent(doc = "Suggestions in all apps list disabled.")
        LAUNCHER_ALL_APPS_SUGGESTIONS_DISABLED(620),

        @UiEvent(doc = "Suggestions on home screen is enabled.")
        LAUNCHER_HOME_SCREEN_SUGGESTIONS_ENABLED(621),

        @UiEvent(doc = "Suggestions on home screen is disabled.")
        LAUNCHER_HOME_SCREEN_SUGGESTIONS_DISABLED(622),

        @UiEvent(doc = "System navigation is 3 button mode.")
        LAUNCHER_NAVIGATION_MODE_3_BUTTON(623),

        @UiEvent(doc = "System navigation mode is 2 button mode.")
        LAUNCHER_NAVIGATION_MODE_2_BUTTON(624),

        @UiEvent(doc = "System navigation mode is 0 button mode/gesture navigation mode .")
        LAUNCHER_NAVIGATION_MODE_GESTURE_BUTTON(625),

        @UiEvent(doc = "User tapped on image content in Overview Select mode.")
        LAUNCHER_SELECT_MODE_IMAGE(627);

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

    /**
     * Helps to construct and write the log message.
     */
    public interface StatsLogger {

        /**
         * Sets log fields from provided {@link ItemInfo}.
         */
        default StatsLogger withItemInfo(ItemInfo itemInfo) {
            return this;
        }


        /**
         * Sets {@link InstanceId} of log message.
         */
        default StatsLogger withInstanceId(InstanceId instanceId) {
            return this;
        }

        /**
         * Sets rank field of log message.
         */
        default StatsLogger withRank(int rank) {
            return this;
        }

        /**
         * Sets source launcher state field of log message.
         */
        default StatsLogger withSrcState(int srcState) {
            return this;
        }

        /**
         * Sets destination launcher state field of log message.
         */
        default StatsLogger withDstState(int dstState) {
            return this;
        }

        /**
         * Sets FromState field of log message.
         */
        default StatsLogger withFromState(FromState fromState) {
            return this;
        }

        /**
         * Sets ToState field of log message.
         */
        default StatsLogger withToState(ToState toState) {
            return this;
        }

        /**
         * Sets editText field of log message.
         */
        default StatsLogger withEditText(String editText) {
            return this;
        }

        /**
         * Sets the final value for container related fields of log message.
         *
         * By default container related fields are derived from {@link ItemInfo}, this method would
         * override those values.
         */
        default StatsLogger withContainerInfo(ContainerInfo containerInfo) {
            return this;
        }

        /**
         * Builds the final message and logs it as {@link EventEnum}.
         */
        default void log(EventEnum event) {
        }
    }

    /**
     * Returns new logger object.
     */
    public StatsLogger logger() {
        return new StatsLogger() {
        };
    }

    /**
     * Creates a new instance of {@link StatsLogManager} based on provided context.
     */
    public static StatsLogManager newInstance(Context context) {
        StatsLogManager mgr = Overrides.getObject(StatsLogManager.class,
                context.getApplicationContext(), R.string.stats_log_manager_class);
        return mgr;
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
     * Logs impression of the current workspace with additional launcher events.
     */
    public void logSnapshot(List<EventEnum> additionalEvents) {
    }
}
