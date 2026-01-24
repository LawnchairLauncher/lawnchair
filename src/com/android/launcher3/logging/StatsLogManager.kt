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
package com.android.launcher3.logging

import android.content.Context
import android.view.View
import androidx.slice.SliceItem
import com.android.launcher3.R
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.logger.LauncherAtom
import com.android.launcher3.logger.LauncherAtom.ContainerInfo
import com.android.launcher3.logger.LauncherAtom.FromState
import com.android.launcher3.logger.LauncherAtom.Slice
import com.android.launcher3.logger.LauncherAtom.ToState
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_CLOSE_DOWN
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_OPEN_UP
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_HOME_GESTURE
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_OVERVIEW_GESTURE
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.views.ActivityContext
import javax.inject.Inject

/**
 * Handles the user event logging in R+.
 * <pre>
 * All of the event ids are defined here.
 * Most of the methods are placeholder methods for Launcher3
 * Actual call happens only for Launcher variant that implements QuickStep.
 * </pre> *
 */
open class StatsLogManager protected constructor(@JvmField protected val mContext: Context) {
    @JvmField
    protected val mActivityContext: ActivityContext? =
        ActivityContext.lookupContextNoThrow(mContext)

    private var mKeyboardStateManager: KeyboardStateManager? = null
    private var mInstanceId: InstanceId? = null

    /**
     * This class is purely used to support dagger bindings to be overridden in launcher variants.
     * Very similar to [dagger.assisted.AssistedFactory]. But [dagger.assisted.AssistedFactory]
     * cannot be overridden and this makes dagger binding difficult.
     */
    open class StatsLogManagerFactory @Inject constructor() {

        open fun create(context: Context): StatsLogManager {
            return StatsLogManager(context)
        }
    }

    interface EventEnum {

        val id: Int
    }

    enum class LauncherEvent(override val id: Int) : EventEnum {
        /* Used to prevent double logging. */
        IGNORE(-1),
        @UiEvent(doc = "App launched from workspace, hotseat or folder in launcher")
        LAUNCHER_APP_LAUNCH_TAP(338),
        @UiEvent(doc = "Task launched from overview using TAP") LAUNCHER_TASK_LAUNCH_TAP(339),
        @UiEvent(doc = "User tapped on notification inside popup context menu.")
        LAUNCHER_NOTIFICATION_LAUNCH_TAP(516),
        @UiEvent(doc = "Task launched from overview using SWIPE DOWN")
        LAUNCHER_TASK_LAUNCH_SWIPE_DOWN(340),
        @UiEvent(doc = "App launched by dragging and dropping, probably from taskbar")
        LAUNCHER_APP_LAUNCH_DRAGDROP(1552),
        @UiEvent(doc = "TASK dismissed from overview using SWIPE UP")
        LAUNCHER_TASK_DISMISS_SWIPE_UP(341),
        @UiEvent(doc = "User dragged a launcher item") LAUNCHER_ITEM_DRAG_STARTED(383),
        @UiEvent(
            doc =
                ("A dragged launcher item is successfully dropped onto workspace, hotseat " +
                    "open folder etc")
        )
        LAUNCHER_ITEM_DROP_COMPLETED(385),
        @UiEvent(doc = "A dragged launcher item is successfully dropped onto a folder icon.")
        LAUNCHER_ITEM_DROP_COMPLETED_ON_FOLDER_ICON(697),
        @UiEvent(
            doc =
                ("A dragged launcher item is successfully dropped on another item " +
                    "resulting in a new folder creation")
        )
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
        @UiEvent(doc = "User tapped or long pressed on apps icon inside launcher settings.")
        LAUNCHER_ALL_APPS_TAP_OR_LONGPRESS(2204),
        @UiEvent(doc = "User tapped or long pressed on widget tray icon inside launcher settings.")
        LAUNCHER_WIDGETSTRAY_BUTTON_TAP_OR_LONGPRESS(464),
        @UiEvent(doc = "User expanded the list of widgets for a single app in the widget picker.")
        LAUNCHER_WIDGETSTRAY_APP_EXPANDED(818),
        @UiEvent(doc = "User searched for a widget in the widget picker.")
        LAUNCHER_WIDGETSTRAY_SEARCHED(819),
        @UiEvent(
            doc =
                ("User clicked on view all button to expand the displayed list in the " +
                    "widget picker.")
        )
        LAUNCHER_WIDGETSTRAY_EXPAND_PRESS(1978),
        @UiEvent(doc = "A dragged item is dropped on 'Remove' button in the target bar")
        LAUNCHER_ITEM_DROPPED_ON_REMOVE(465),
        @UiEvent(doc = "A dragged item is dropped on 'Cancel' button in the target bar")
        LAUNCHER_ITEM_DROPPED_ON_CANCEL(466),
        @UiEvent(
            doc =
                ("A predicted item is dragged and dropped on 'Don't suggest app'" +
                    " button in the target bar")
        )
        LAUNCHER_ITEM_DROPPED_ON_DONT_SUGGEST(467),
        @UiEvent(doc = "A dragged item is dropped on 'Uninstall' button in target bar")
        LAUNCHER_ITEM_DROPPED_ON_UNINSTALL(468),
        @UiEvent(
            doc =
                ("User completed uninstalling the package after dropping on " +
                    "the icon onto 'Uninstall' button in the target bar")
        )
        LAUNCHER_ITEM_UNINSTALL_COMPLETED(469),
        @UiEvent(
            doc =
                ("User cancelled uninstalling the package after dropping on " +
                    "the icon onto 'Uninstall' button in the target bar")
        )
        LAUNCHER_ITEM_UNINSTALL_CANCELLED(470),
        @UiEvent(
            doc =
                ("User tapped or long pressed on the task icon(aka package icon) " +
                    "from overview to open task menu.")
        )
        LAUNCHER_TASK_ICON_TAP_OR_LONGPRESS(517),
        @UiEvent(
            doc =
                ("User opened package specific widgets list by tapping on widgets system " +
                    "shortcut inside popup context menu.")
        )
        LAUNCHER_SYSTEM_SHORTCUT_WIDGETS_TAP(514),
        @UiEvent(doc = "User tapped on app info system shortcut.")
        LAUNCHER_SYSTEM_SHORTCUT_APP_INFO_TAP(515),
        @UiEvent(doc = "User tapped on split screen icon on a task menu.")
        @Deprecated(
            """Use {@link #LAUNCHER_APP_ICON_MENU_SPLIT_LEFT_TOP} or
                      {@link #LAUNCHER_APP_ICON_MENU_SPLIT_RIGHT_BOTTOM}"""
        )
        LAUNCHER_SYSTEM_SHORTCUT_SPLIT_SCREEN_TAP(518),
        @UiEvent(doc = "User tapped on free form icon on a task menu.")
        LAUNCHER_SYSTEM_SHORTCUT_FREE_FORM_TAP(519),
        @UiEvent(doc = "User tapped on desktop icon on a task menu.")
        LAUNCHER_SYSTEM_SHORTCUT_DESKTOP_TAP(1706),
        @UiEvent(doc = "User tapped on external display icon on a task menu,")
        LAUNCHER_SYSTEM_SHORTCUT_EXTERNAL_DISPLAY_TAP(1957),
        @UiEvent(doc = "User tapped on close app on a task menu,")
        LAUNCHER_SYSTEM_SHORTCUT_CLOSE_APP_TAP(2081),
        @UiEvent(doc = "User tapped on pause app system shortcut.")
        LAUNCHER_SYSTEM_SHORTCUT_PAUSE_TAP(521),
        @UiEvent(doc = "User tapped on pin system shortcut.") LAUNCHER_SYSTEM_SHORTCUT_PIN_TAP(522),
        @UiEvent(doc = "User tapped on don't suggest app system shortcut.")
        LAUNCHER_SYSTEM_SHORTCUT_DONT_SUGGEST_APP_TAP(1603),
        @UiEvent(doc = "User is shown All Apps education view.") LAUNCHER_ALL_APPS_EDU_SHOWN(523),
        @UiEvent(doc = "User opened a folder.") LAUNCHER_FOLDER_OPEN(551),
        @UiEvent(doc = "Hotseat education half sheet seen") LAUNCHER_HOTSEAT_EDU_SEEN(479),
        @UiEvent(doc = "Hotseat migration accepted") LAUNCHER_HOTSEAT_EDU_ACCEPT(480),
        @UiEvent(doc = "Hotseat migration denied") LAUNCHER_HOTSEAT_EDU_DENY(481),
        @UiEvent(doc = "Hotseat education tip shown") LAUNCHER_HOTSEAT_EDU_ONLY_TIP(482),
        @UiEvent(doc = "App launch ranking logged for all apps predictions")
        @Deprecated(
            """LauncherUiChanged.rank field is repurposed to store all apps rank, so no
          separate event is required."""
        )
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
        @UiEvent(doc = "User tap outside apps drawer sheet to close apps drawer.")
        LAUNCHER_ALLAPPS_CLOSE_TAP_OUTSIDE(941),
        @UiEvent(doc = "User swipes or fling in UP direction and hold from the bottom bazel area")
        LAUNCHER_OVERVIEW_GESTURE(570),
        @UiEvent(doc = "User swipes or fling in LEFT direction on the bottom bazel area.")
        LAUNCHER_QUICKSWITCH_LEFT(571),
        @UiEvent(doc = "User swipes or fling in RIGHT direction on the bottom bazel area.")
        LAUNCHER_QUICKSWITCH_RIGHT(572),
        @UiEvent(doc = "User swipes or fling on the bottom bazel area to enter Desktop mode.")
        LAUNCHER_QUICKSWITCH_ENTER_DESKTOP_MODE(2025),
        @UiEvent(doc = "User swipes or fling on the bottom bazel area to exit Desktop mode.")
        LAUNCHER_QUICKSWITCH_EXIT_DESKTOP_MODE(2026),
        @UiEvent(doc = "User swipes or fling in DOWN direction on the bottom bazel area.")
        LAUNCHER_SWIPEDOWN_NAVBAR(573),
        @UiEvent(doc = "User deep presses on the bottom bezel area.")
        LAUNCHER_DEEP_PRESS_NAVBAR(1543),
        @UiEvent(doc = "User long presses on the bottom bezel area.")
        LAUNCHER_LONG_PRESS_NAVBAR(1544),
        @UiEvent(doc = "User deep presses on the stashed taskbar")
        LAUNCHER_DEEP_PRESS_STASHED_TASKBAR(1602),
        @UiEvent(doc = "User long presses on the stashed taskbar")
        LAUNCHER_LONG_PRESS_STASHED_TASKBAR(1592),
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
        @UiEvent(doc = "User tapped on the split screen button on overview")
        LAUNCHER_OVERVIEW_ACTIONS_SPLIT(895),
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
        LAUNCHER_SELECT_MODE_IMAGE(627),
        @UiEvent(doc = "User tapped on barcode content in Overview Select mode.")
        LAUNCHER_SELECT_MODE_BARCODE(1531),
        @UiEvent(doc = "Highlight gleams for barcode content in Overview Select mode.")
        LAUNCHER_SELECT_MODE_SHOW_BARCODE_REGIONS(1532),
        @UiEvent(doc = "Activity to add external item was started")
        LAUNCHER_ADD_EXTERNAL_ITEM_START(641),
        @UiEvent(doc = "Activity to add external item was cancelled")
        LAUNCHER_ADD_EXTERNAL_ITEM_CANCELLED(642),
        @UiEvent(doc = "Activity to add external item was backed out")
        LAUNCHER_ADD_EXTERNAL_ITEM_BACK(643),
        @UiEvent(doc = "Item was placed automatically in external item addition flow")
        LAUNCHER_ADD_EXTERNAL_ITEM_PLACED_AUTOMATICALLY(644),
        @UiEvent(doc = "Item was dragged in external item addition flow")
        LAUNCHER_ADD_EXTERNAL_ITEM_DRAGGED(645),
        @UiEvent(doc = "A folder was replaced by a single item")
        LAUNCHER_FOLDER_CONVERTED_TO_ICON(646),
        @UiEvent(doc = "A hotseat prediction item was pinned")
        LAUNCHER_HOTSEAT_PREDICTION_PINNED(647),
        @UiEvent(doc = "Undo event was tapped.") LAUNCHER_UNDO(648),
        @UiEvent(doc = "Task switcher clear all target was tapped.") LAUNCHER_TASK_CLEAR_ALL(649),
        @UiEvent(doc = "Task preview was long pressed.") LAUNCHER_TASK_PREVIEW_LONGPRESS(650),
        @UiEvent(doc = "User swiped down on workspace (triggering noti shade to open).")
        LAUNCHER_SWIPE_DOWN_WORKSPACE_NOTISHADE_OPEN(651),
        @UiEvent(doc = "Notification dismissed by swiping right.")
        LAUNCHER_NOTIFICATION_DISMISSED(652),
        @UiEvent(doc = "Current grid size is changed to 2x2") LAUNCHER_GRID_SIZE_2_BY_2(2181),
        @UiEvent(doc = "Current grid size is changed to 3x3") LAUNCHER_GRID_SIZE_3_BY_3(2182),
        @UiEvent(doc = "Current grid size is changed to 4x4") LAUNCHER_GRID_SIZE_4_BY_4(2183),
        @UiEvent(doc = "Current grid size is changed to 4x5") LAUNCHER_GRID_SIZE_4_BY_5(2184),
        @UiEvent(doc = "Current grid size is changed to 4x6") LAUNCHER_GRID_SIZE_4_BY_6(2185),
        @UiEvent(doc = "Current grid size is changed to 4x7")
        LAUNCHER_GRID_SIZE_4_BY_7(2189),

        @UiEvent(doc = "Current grid size is changed to 5x5") LAUNCHER_GRID_SIZE_5_BY_5(2186),
        @UiEvent(doc = "Current grid size is changed to 5x6") LAUNCHER_GRID_SIZE_5_BY_6(2187),
        @UiEvent(doc = "Current grid size is changed to 6x5") LAUNCHER_GRID_SIZE_6_BY_5(2188),
        @UiEvent(doc = "App icon shape changed to Circle") LAUNCHER_ICON_SHAPE_CIRCLE(2229),
        @UiEvent(doc = "App icon shape changed to Square") LAUNCHER_ICON_SHAPE_SQUARE(2230),
        @UiEvent(doc = "App icon shape changed to 4-sided Cookie")
        LAUNCHER_ICON_SHAPE_FOUR_SIDED_COOKIE(2231),
        @UiEvent(doc = "App icon shape changed to 7-sided Cookie")
        LAUNCHER_ICON_SHAPE_SEVEN_SIDED_COOKIE(2232),
        @UiEvent(doc = "App icon shape changed to Arch") LAUNCHER_ICON_SHAPE_ARCH(2233),
        @UiEvent(doc = "Launcher entered into AllApps state.") LAUNCHER_ALLAPPS_ENTRY(692),
        @UiEvent(doc = "Launcher exited from AllApps state.") LAUNCHER_ALLAPPS_EXIT(693),
        @UiEvent(doc = "User closed the AllApps keyboard.") LAUNCHER_ALLAPPS_KEYBOARD_CLOSED(694),
        @UiEvent(doc = "User switched to AllApps Main/Personal tab by swiping left.")
        LAUNCHER_ALLAPPS_SWIPE_TO_PERSONAL_TAB(695),
        @UiEvent(doc = "User switched to AllApps Work tab by swiping right.")
        LAUNCHER_ALLAPPS_SWIPE_TO_WORK_TAB(696),
        @UiEvent(
            doc =
                ("Default event when dedicated UI event is not available for the user action" +
                    " on slice .")
        )
        LAUNCHER_SLICE_DEFAULT_ACTION(700),
        @UiEvent(doc = "User toggled-on a Slice item.") LAUNCHER_SLICE_TOGGLE_ON(701),
        @UiEvent(doc = "User toggled-off a Slice item.") LAUNCHER_SLICE_TOGGLE_OFF(702),
        @UiEvent(doc = "User acted on a Slice item with a button.")
        LAUNCHER_SLICE_BUTTON_ACTION(703),
        @UiEvent(doc = "User acted on a Slice item with a slider.")
        LAUNCHER_SLICE_SLIDER_ACTION(704),
        @UiEvent(doc = "User tapped on the entire row of a Slice.")
        LAUNCHER_SLICE_CONTENT_ACTION(705),
        @UiEvent(doc = "User tapped on the see more button of a Slice.")
        LAUNCHER_SLICE_SEE_MORE_ACTION(706),
        @UiEvent(doc = "User selected from a selection row of Slice.")
        LAUNCHER_SLICE_SELECTION_ACTION(707),
        @UiEvent(doc = "IME is used for selecting the focused item on the AllApps screen.")
        LAUNCHER_ALLAPPS_FOCUSED_ITEM_SELECTED_WITH_IME(718),
        @UiEvent(doc = "User long-pressed on an AllApps item.")
        LAUNCHER_ALLAPPS_ITEM_LONG_PRESSED(719),
        @UiEvent(doc = "Launcher entered into AllApps state with device search enabled.")
        LAUNCHER_ALLAPPS_ENTRY_WITH_DEVICE_SEARCH(720),
        @UiEvent(doc = "User switched to AllApps Main/Personal tab by tapping on it.")
        LAUNCHER_ALLAPPS_TAP_ON_PERSONAL_TAB(721),
        @UiEvent(doc = "User switched to AllApps Work tab by tapping on it.")
        LAUNCHER_ALLAPPS_TAP_ON_WORK_TAB(722),
        @UiEvent(doc = "All apps vertical fling started.")
        LAUNCHER_ALLAPPS_VERTICAL_SWIPE_BEGIN(724),
        @UiEvent(doc = "All apps vertical fling ended.") LAUNCHER_ALLAPPS_VERTICAL_SWIPE_END(725),
        @UiEvent(doc = "Show URL indicator for Overview Sharing")
        LAUNCHER_OVERVIEW_SHARING_SHOW_URL_INDICATOR(764),
        @UiEvent(doc = "Show image indicator for Overview Sharing")
        LAUNCHER_OVERVIEW_SHARING_SHOW_IMAGE_INDICATOR(765),
        @UiEvent(doc = "User taps URL indicator in Overview")
        LAUNCHER_OVERVIEW_SHARING_URL_INDICATOR_TAP(766),
        @UiEvent(doc = "User taps image indicator in Overview")
        LAUNCHER_OVERVIEW_SHARING_IMAGE_INDICATOR_TAP(767),
        @UiEvent(doc = "User long presses an image in Overview")
        LAUNCHER_OVERVIEW_SHARING_IMAGE_LONG_PRESS(768),
        @UiEvent(doc = "User drags a URL in Overview") LAUNCHER_OVERVIEW_SHARING_URL_DRAG(769),
        @UiEvent(doc = "User drags an image in Overview") LAUNCHER_OVERVIEW_SHARING_IMAGE_DRAG(770),
        @UiEvent(doc = "User drops URL to a direct share target")
        LAUNCHER_OVERVIEW_SHARING_DROP_URL_TO_TARGET(771),
        @UiEvent(doc = "User drops an image to a direct share target")
        LAUNCHER_OVERVIEW_SHARING_DROP_IMAGE_TO_TARGET(772),
        @UiEvent(doc = "User drops URL to the More button")
        LAUNCHER_OVERVIEW_SHARING_DROP_URL_TO_MORE(773),
        @UiEvent(doc = "User drops an image to the More button")
        LAUNCHER_OVERVIEW_SHARING_DROP_IMAGE_TO_MORE(774),
        @UiEvent(doc = "User taps a share target to share URL")
        LAUNCHER_OVERVIEW_SHARING_TAP_TARGET_TO_SHARE_URL(775),
        @UiEvent(doc = "User taps a share target to share an image")
        LAUNCHER_OVERVIEW_SHARING_TAP_TARGET_TO_SHARE_IMAGE(776),
        @UiEvent(doc = "User taps the More button to share URL")
        LAUNCHER_OVERVIEW_SHARING_TAP_MORE_TO_SHARE_URL(777),
        @UiEvent(doc = "User taps the More button to share an image")
        LAUNCHER_OVERVIEW_SHARING_TAP_MORE_TO_SHARE_IMAGE(778),
        @UiEvent(doc = "Show Barode indicator for overview sharing")
        LAUNCHER_OVERVIEW_SHARING_SHOW_BARCODE_INDICATOR(1533),
        @UiEvent(doc = "User taps barcode indicator in overview")
        LAUNCHER_OVERVIEW_SHARING_BARCODE_INDICATOR_TAP(1534),
        @UiEvent(doc = "Configure barcode region for long_press action for overview sharing")
        LAUNCHER_OVERVIEW_SHARING_CONFIGURE_BARCODE_REGION_LONG_PRESS(1535),
        @UiEvent(doc = "User long presses a barcode region in overview")
        LAUNCHER_OVERVIEW_SHARING_BARCODE_REGION_LONG_PRESS(1536),
        @UiEvent(doc = "User drags a barcode region in overview")
        LAUNCHER_OVERVIEW_SHARING_BARCODE_REGION_DRAG(1537),
        @UiEvent(doc = "User started resizing a widget on their home screen.")
        LAUNCHER_WIDGET_RESIZE_STARTED(820),
        @UiEvent(doc = "User finished resizing a widget on their home screen.")
        LAUNCHER_WIDGET_RESIZE_COMPLETED(824),
        @UiEvent(doc = "User reconfigured a widget on their home screen.")
        LAUNCHER_WIDGET_RECONFIGURED(821),
        @UiEvent(doc = "User enabled themed icons option in wallpaper & style settings.")
        LAUNCHER_THEMED_ICON_ENABLED(836),
        @UiEvent(doc = "User disabled themed icons option in wallpaper & style settings.")
        LAUNCHER_THEMED_ICON_DISABLED(837),
        @UiEvent(doc = "User tapped on 'Turn on work apps' button in all apps window.")
        LAUNCHER_TURN_ON_WORK_APPS_TAP(838),
        @UiEvent(doc = "User tapped on 'Turn off work apps' button in all apps window.")
        LAUNCHER_TURN_OFF_WORK_APPS_TAP(839),
        @UiEvent(doc = "Launcher item drop failed since there was not enough room on the screen.")
        LAUNCHER_ITEM_DROP_FAILED_INSUFFICIENT_SPACE(872),
        @UiEvent(doc = "User clicks on the search icon on header to launch search in app.")
        LAUNCHER_ALLAPPS_SEARCHINAPP_LAUNCH(913),
        @UiEvent(doc = "User is shown the back gesture navigation tutorial step.")
        LAUNCHER_GESTURE_TUTORIAL_BACK_STEP_SHOWN(959),
        @UiEvent(doc = "User is shown the home gesture navigation tutorial step.")
        LAUNCHER_GESTURE_TUTORIAL_HOME_STEP_SHOWN(960),
        @UiEvent(doc = "User is shown the overview gesture navigation tutorial step.")
        LAUNCHER_GESTURE_TUTORIAL_OVERVIEW_STEP_SHOWN(961),
        @UiEvent(doc = "User completed the back gesture navigation tutorial step.")
        LAUNCHER_GESTURE_TUTORIAL_BACK_STEP_COMPLETED(962),
        @UiEvent(doc = "User completed the home gesture navigation tutorial step.")
        LAUNCHER_GESTURE_TUTORIAL_HOME_STEP_COMPLETED(963),
        @UiEvent(doc = "User completed the overview gesture navigation tutorial step.")
        LAUNCHER_GESTURE_TUTORIAL_OVERVIEW_STEP_COMPLETED(964),
        @UiEvent(doc = "User skips the gesture navigation tutorial.")
        LAUNCHER_GESTURE_TUTORIAL_SKIPPED(965),
        @UiEvent(
            doc =
                ("User scrolled on one of the all apps surfaces such as A-Z list, search " +
                    "result page etc.")
        )
        LAUNCHER_ALLAPPS_SCROLLED(985),
        @UiEvent(doc = "User scrolled up on the all apps personal A-Z list.")
        LAUNCHER_ALLAPPS_PERSONAL_SCROLLED_UP(1287),
        @UiEvent(doc = "User scrolled down on the all apps personal A-Z list.")
        LAUNCHER_ALLAPPS_PERSONAL_SCROLLED_DOWN(1288),
        @UiEvent(
            doc =
                ("User scrolled on one of the all apps surfaces such as A-Z list, search " +
                    "result page etc and we don't know the direction since user came back to " +
                    "original position from which they scrolled.")
        )
        LAUNCHER_ALLAPPS_SCROLLED_UNKNOWN_DIRECTION(1231),
        @UiEvent(doc = "User tapped taskbar home button") LAUNCHER_TASKBAR_HOME_BUTTON_TAP(1003),
        @UiEvent(doc = "User tapped taskbar back button") LAUNCHER_TASKBAR_BACK_BUTTON_TAP(1004),
        @UiEvent(doc = "User tapped taskbar overview/recents button")
        LAUNCHER_TASKBAR_OVERVIEW_BUTTON_TAP(1005),
        @UiEvent(doc = "User tapped taskbar IME switcher button")
        LAUNCHER_TASKBAR_IME_SWITCHER_BUTTON_TAP(1006),
        @UiEvent(doc = "User tapped taskbar a11y button") LAUNCHER_TASKBAR_A11Y_BUTTON_TAP(1007),
        @UiEvent(doc = "User tapped taskbar home button")
        LAUNCHER_TASKBAR_HOME_BUTTON_LONGPRESS(1008),
        @UiEvent(doc = "User tapped taskbar back button")
        LAUNCHER_TASKBAR_BACK_BUTTON_LONGPRESS(1009),
        @UiEvent(doc = "User tapped taskbar overview/recents button")
        LAUNCHER_TASKBAR_OVERVIEW_BUTTON_LONGPRESS(1010),
        @UiEvent(doc = "User tapped taskbar a11y button")
        LAUNCHER_TASKBAR_A11Y_BUTTON_LONGPRESS(1011),
        @UiEvent(doc = "Show an 'Undo' snackbar when users dismiss a predicted hotseat item")
        LAUNCHER_DISMISS_PREDICTION_UNDO(1035),
        @UiEvent(doc = "User clicked on IME quicksearch button.")
        LAUNCHER_ALLAPPS_QUICK_SEARCH_WITH_IME(1047),
        @UiEvent(doc = "User tapped taskbar All Apps button.")
        LAUNCHER_TASKBAR_ALLAPPS_BUTTON_TAP(1057),
        @UiEvent(doc = "User long pressed taskbar All Apps button.")
        LAUNCHER_TASKBAR_ALLAPPS_BUTTON_LONG_PRESS(1607),
        @UiEvent(doc = "User tapped on Share app system shortcut.")
        LAUNCHER_SYSTEM_SHORTCUT_APP_SHARE_TAP(1075),
        @UiEvent(doc = "User has invoked split to right half from an app icon menu")
        LAUNCHER_APP_ICON_MENU_SPLIT_RIGHT_BOTTOM(1199),
        @UiEvent(doc = "User has invoked split to left half from an app icon menu")
        LAUNCHER_APP_ICON_MENU_SPLIT_LEFT_TOP(1200),
        @UiEvent(doc = "Number of apps in A-Z list (personal and work profile)")
        LAUNCHER_ALLAPPS_COUNT(1225),
        @UiEvent(doc = "User has invoked split to right half with a keyboard shortcut.")
        LAUNCHER_KEYBOARD_SHORTCUT_SPLIT_RIGHT_BOTTOM(1232),
        @UiEvent(doc = "User has invoked split to left half with a keyboard shortcut.")
        LAUNCHER_KEYBOARD_SHORTCUT_SPLIT_LEFT_TOP(1233),
        @UiEvent(doc = "User has invoked split to right half from desktop mode.")
        LAUNCHER_DESKTOP_MODE_SPLIT_RIGHT_BOTTOM(1412),
        @UiEvent(doc = "User has invoked split to left half from desktop mode.")
        LAUNCHER_DESKTOP_MODE_SPLIT_LEFT_TOP(1464),
        @UiEvent(
            doc =
                ("User has collapsed the work FAB button by scrolling down in the all apps" +
                    " work A-Z list.")
        )
        LAUNCHER_WORK_FAB_BUTTON_COLLAPSE(1276),
        @UiEvent(
            doc =
                ("User has collapsed the work FAB button by scrolling up in the all apps" +
                    " work A-Z list.")
        )
        LAUNCHER_WORK_FAB_BUTTON_EXTEND(1277),
        @UiEvent(doc = "User scrolled down on the search result page.")
        LAUNCHER_ALLAPPS_SEARCH_SCROLLED_DOWN(1285),
        @UiEvent(doc = "User scrolled up on the search result page.")
        LAUNCHER_ALLAPPS_SEARCH_SCROLLED_UP(1286),
        @UiEvent(doc = "User or automatic timeout has hidden transient taskbar.")
        LAUNCHER_TRANSIENT_TASKBAR_HIDE(1330),
        @UiEvent(doc = "User has swiped upwards from the gesture handle to show transient taskbar.")
        LAUNCHER_TRANSIENT_TASKBAR_SHOW(1331),
        @UiEvent(doc = "User has clicked an app pair and launched directly into split screen.")
        LAUNCHER_APP_PAIR_LAUNCH(1374),
        @UiEvent(doc = "User saved an app pair.") LAUNCHER_APP_PAIR_SAVE(1456),
        @UiEvent(doc = "App launched through pending intent")
        LAUNCHER_APP_LAUNCH_PENDING_INTENT(1394),
        @UiEvent(doc = "User long pressed on taskbar divider icon to open popup menu")
        LAUNCHER_TASKBAR_DIVIDER_MENU_OPEN(1488),
        @UiEvent(doc = "User long pressed on taskbar divider icon to close popup menu")
        LAUNCHER_TASKBAR_DIVIDER_MENU_CLOSE(1489),
        @UiEvent(doc = "User has pinned taskbar using taskbar divider menu")
        LAUNCHER_TASKBAR_PINNED(1490),
        @UiEvent(doc = "User has unpinned taskbar using taskbar divider menu")
        LAUNCHER_TASKBAR_UNPINNED(1491),
        @UiEvent(doc = "User tapped private space lock button")
        LAUNCHER_PRIVATE_SPACE_LOCK_TAP(1548),
        @UiEvent(doc = "User tapped private space unlock button")
        LAUNCHER_PRIVATE_SPACE_UNLOCK_TAP(1549),
        @UiEvent(doc = "User tapped private space settings button")
        LAUNCHER_PRIVATE_SPACE_SETTINGS_TAP(1550),
        @UiEvent(doc = "User tapped on install to private space system shortcut.")
        LAUNCHER_PRIVATE_SPACE_INSTALL_SYSTEM_SHORTCUT_TAP(1565),
        @UiEvent(doc = "User tapped private space install app button.")
        LAUNCHER_PRIVATE_SPACE_INSTALL_APP_BUTTON_TAP(1605),
        @UiEvent(doc = "User attempted to create split screen with a widget")
        LAUNCHER_SPLIT_WIDGET_ATTEMPT(1604),
        @UiEvent(doc = "User tapped on private space uninstall system shortcut.")
        LAUNCHER_PRIVATE_SPACE_UNINSTALL_SYSTEM_SHORTCUT_TAP(1608),
        @UiEvent(doc = "User initiated split selection") LAUNCHER_SPLIT_SELECTION_INITIATED(1618),
        @UiEvent(doc = "User finished a split selection session")
        LAUNCHER_SPLIT_SELECTION_COMPLETE(1619),
        @UiEvent(doc = "User selected both apps for split screen")
        LAUNCHER_SPLIT_SELECTED_SECOND_APP(1609),
        @UiEvent(
            doc =
                ("User exited split selection by going home via swipe, button, or state " +
                    "transition")
        )
        LAUNCHER_SPLIT_SELECTION_EXIT_HOME(1610),
        @UiEvent(doc = "User exited split selection by tapping cancel in split instructions view")
        LAUNCHER_SPLIT_SELECTION_EXIT_CANCEL_BUTTON(1611),
        @UiEvent(
            doc =
                ("User exited split selection when another activity/app came to foreground" +
                    " after first app had been selected OR if user long-pressed on home. Default exit" +
                    " metric.")
        )
        LAUNCHER_SPLIT_SELECTION_EXIT_INTERRUPTED(1612),
        @UiEvent(doc = "User tapped add widget button in widget sheet.")
        LAUNCHER_WIDGET_ADD_BUTTON_TAP(1622),
        @UiEvent(doc = "Number of user installed Private profile apps, shown above separator line")
        LAUNCHER_PRIVATE_SPACE_USER_INSTALLED_APPS_COUNT(1672),
        @UiEvent(doc = "Number of preinstalled Private profile apps, shown under separator line")
        LAUNCHER_PRIVATE_SPACE_PREINSTALLED_APPS_COUNT(1673),
        @UiEvent(doc = "Private space lock animation started")
        LAUNCHER_PRIVATE_SPACE_LOCK_ANIMATION_BEGIN(1725),
        @UiEvent(doc = "Private space lock animation finished")
        LAUNCHER_PRIVATE_SPACE_LOCK_ANIMATION_END(1726),
        @UiEvent(doc = "Private space unlock animation started")
        LAUNCHER_PRIVATE_SPACE_UNLOCK_ANIMATION_BEGIN(1727),
        @UiEvent(doc = "Private space unlock animation finished")
        LAUNCHER_PRIVATE_SPACE_UNLOCK_ANIMATION_END(1728),
        @UiEvent(doc = "User rotates whilst in Overview / RecentsView")
        LAUNCHER_OVERVIEW_ORIENTATION_CHANGED(1762),
        @UiEvent(doc = "User launches Overview from 3 button navigation")
        LAUNCHER_OVERVIEW_SHOW_OVERVIEW_FROM_3_BUTTON(1763),
        @UiEvent(doc = "User launches Overview from alt+tab keyboard quick switch")
        LAUNCHER_OVERVIEW_SHOW_OVERVIEW_FROM_KEYBOARD_QUICK_SWITCH(1764),
        @UiEvent(doc = "User launches Overview from meta+tab keyboard shortcut")
        LAUNCHER_OVERVIEW_SHOW_OVERVIEW_FROM_KEYBOARD_SHORTCUT(1765),
        @UiEvent(doc = "User long pressed on the taskbar IME switcher button")
        LAUNCHER_TASKBAR_IME_SWITCHER_BUTTON_LONGPRESS(1798),
        @UiEvent(doc = "Failed to launch assistant due to Google assistant not available")
        LAUNCHER_LAUNCH_ASSISTANT_FAILED_NOT_AVAILABLE(1465),
        @UiEvent(doc = "Failed to launch assistant due to service error")
        LAUNCHER_LAUNCH_ASSISTANT_FAILED_SERVICE_ERROR(1466),
        @UiEvent(doc = "User launched assistant by long-pressing nav handle")
        LAUNCHER_LAUNCH_ASSISTANT_SUCCESSFUL_NAV_HANDLE(1467),
        @UiEvent(doc = "Failed to launch due to Contextual Search not available")
        LAUNCHER_LAUNCH_OMNI_FAILED_NOT_AVAILABLE(1471),
        @UiEvent(doc = "Failed to launch due to Contextual Search setting disabled")
        LAUNCHER_LAUNCH_OMNI_FAILED_SETTING_DISABLED(1632),
        @UiEvent(doc = "User launched Contextual Search by long-pressing home in 3-button mode")
        LAUNCHER_LAUNCH_OMNI_SUCCESSFUL_HOME(1481),
        @UiEvent(doc = "User launched Contextual Search by using accessibility System Action")
        LAUNCHER_LAUNCH_OMNI_SUCCESSFUL_SYSTEM_ACTION(1492),
        @UiEvent(doc = "User launched Contextual Search by long pressing the meta key")
        LAUNCHER_LAUNCH_OMNI_SUCCESSFUL_META(1606),
        @UiEvent(doc = "Contextual Search invocation was attempted over the notification shade")
        LAUNCHER_LAUNCH_OMNI_ATTEMPTED_OVER_NOTIFICATION_SHADE(1485),
        @UiEvent(doc = "The Contextual Search all entrypoints toggle value in Settings")
        LAUNCHER_SETTINGS_OMNI_ALL_ENTRYPOINTS_TOGGLE_VALUE(1633),
        @UiEvent(doc = "Contextual Search invocation was attempted over the keyguard")
        LAUNCHER_LAUNCH_OMNI_ATTEMPTED_OVER_KEYGUARD(1501),
        @UiEvent(doc = "Contextual Search invocation was attempted while splitscreen is active")
        LAUNCHER_LAUNCH_OMNI_ATTEMPTED_SPLITSCREEN(1505),
        @UiEvent(doc = "User long press nav handle and a long press runnable was created.")
        LAUNCHER_OMNI_GET_LONG_PRESS_RUNNABLE(1545),
        @UiEvent(doc = "User tapped on \"change aspect ratio\" system shortcut.")
        LAUNCHER_ASPECT_RATIO_SETTINGS_SYSTEM_SHORTCUT_TAP(2219),

        // One Grid Flags
        @UiEvent(doc = "User sets the device in Fixed Landscape")
        FIXED_LANDSCAPE_TOGGLE_ENABLE(2014),
        @UiEvent(doc = "User sets the device in Fixed Landscape")
        FIXED_LANDSCAPE_TOGGLE_DISABLED(2020),
        @UiEvent(doc = "Work utility view expand animation started")
        LAUNCHER_WORK_UTILITY_VIEW_EXPAND_ANIMATION_BEGIN(2075),
        @UiEvent(doc = "Work utility view expand animation ended")
        LAUNCHER_WORK_UTILITY_VIEW_EXPAND_ANIMATION_END(2076),
        @UiEvent(doc = "Work utility view shrink animation started")
        LAUNCHER_WORK_UTILITY_VIEW_SHRINK_ANIMATION_BEGIN(2077),
        @UiEvent(doc = "Work utility view shrink animation ended")
        LAUNCHER_WORK_UTILITY_VIEW_SHRINK_ANIMATION_END(2078),
        @UiEvent(doc = "Standard grid migration occurred") LAUNCHER_STANDARD_GRID_MIGRATION(2200),
        @UiEvent(doc = "Row shift grid migration occurred") LAUNCHER_ROW_SHIFT_GRID_MIGRATION(2201),
        @UiEvent(doc = "Do standard migration when upgrading to one grid")
        LAUNCHER_STANDARD_ONE_GRID_MIGRATION(2205),
        @UiEvent(doc = "Do row shift migration when upgrading to one grid")
        LAUNCHER_ROW_SHIFT_ONE_GRID_MIGRATION(2206),
        @UiEvent(doc = "User has pinned taskbar in desktop mode using taskbar divider menu")
        LAUNCHER_DESKTOP_MODE_TASKBAR_PINNED(2241),
        @UiEvent(doc = "User has unpinned taskbar in desktop mode using taskbar divider menu")
        LAUNCHER_DESKTOP_MODE_TASKBAR_UNPINNED(2242),
        @UiEvent(doc = "Launcher window insets controller reportedly null")
        LAUNCHER_WINDOW_INSETS_CONTROLLER_NULL(2400),
        // ADD MORE
    }

    /** Launcher's latency events. */
    enum class LauncherLatencyEvent(override val id: Int) : EventEnum {
        // Details of below 6 events with prefix of "LAUNCHER_LATENCY_STARTUP_" are discussed in
        // go/launcher-startup-latency
        @UiEvent(doc = "The total duration of launcher startup latency.")
        LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION(1362),
        @UiEvent(doc = "The duration of launcher activity's onCreate().")
        LAUNCHER_LATENCY_STARTUP_ACTIVITY_ON_CREATE(1363),
        @UiEvent(
            doc = "The duration to inflate launcher root view in launcher activity's onCreate()."
        )
        LAUNCHER_LATENCY_STARTUP_VIEW_INFLATION(1364),
        @UiEvent(doc = "The duration of asynchronous loading workspace")
        LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_ASYNC(1367),
        @UiEvent(
            doc =
                ("Time passed between Contextual Search runnable creation and execution. This" +
                    " ensures that Recent animations have finished before Contextual Search starts.")
        )
        LAUNCHER_LATENCY_OMNI_RUNNABLE(1546),
        @UiEvent(
            doc =
                ("Time passed between nav handle touch down and cancellation without " +
                    "triggering Contextual Search")
        )
        LAUNCHER_LATENCY_CONTEXTUAL_SEARCH_LPNH_ABANDON(2171),
    }

    /** Launcher specific ranking related events. */
    enum class LauncherRankingEvent(override val id: Int) : EventEnum {
        UNKNOWN(0) // ADD MORE
    }

    /** Helps to construct and log launcher event. */
    interface StatsLogger {
        /** Sets log fields from provided [ItemInfo]. */
        fun withItemInfo(itemInfo: ItemInfo?) = this

        /** Sets [InstanceId] of log message. */
        fun withInstanceId(instanceId: InstanceId?) = this

        /** Sets rank field of log message. */
        fun withRank(rank: Int) = this

        /** Sets source launcher state field of log message. */
        fun withSrcState(srcState: Int) = this

        /** Sets destination launcher state field of log message. */
        fun withDstState(dstState: Int) = this

        /** Sets FromState field of log message. */
        fun withFromState(fromState: FromState?) = this

        /** Sets ToState field of log message. */
        fun withToState(toState: ToState?) = this

        /** Sets editText field of log message. */
        fun withEditText(editText: String?) = this

        /**
         * Sets the final value for container related fields of log message.
         *
         * By default container related fields are derived from [ItemInfo], this method would
         * override those values.
         */
        fun withContainerInfo(containerInfo: ContainerInfo?) = this

        /** Sets logging fields from provided [SliceItem]. */
        fun withSliceItem(sliceItem: SliceItem) = this

        /** Sets logging fields from provided [LauncherAtom.Slice]. */
        fun withSlice(slice: Slice) = this

        /** Sets cardinality of log message. */
        fun withCardinality(cardinality: Int) = this

        /** Sets the input type of the log message. */
        fun withInputType(inputType: Int) = this

        /** Set the features of the log message. */
        fun withFeatures(feature: Int) = this

        /** Set the package name of the log message. */
        fun withPackageName(packageName: String?) = this

        /** Builds the final message and logs it as [EventEnum]. */
        fun log(event: EventEnum) {}

        /**
         * Builds the final message and logs it to two different atoms, one for event tracking and
         * the other for jank tracking.
         */
        fun sendToInteractionJankMonitor(event: EventEnum?, v: View?) {}
    }

    /** Helps to construct and log latency event. */
    interface StatsLatencyLogger {
        /**
         * Should be in sync with:
         * google3/wireless/android/sysui/aster/asterstats/launcher_event_processed.proto
         */
        enum class LatencyType(val id: Int) {
            UNKNOWN(0),

            // example: launcher restart that happens via daily backup and restore
            COLD(1),
            HOT(2),
            TIMEOUT(3),
            FAIL(4),
            COLD_USERWAITING(5),
            ATOMIC(6),
            CONTROLLED(7),
            CACHED(8),

            // example: device is rebooting via power key or shell command `adb reboot`
            COLD_DEVICE_REBOOTING(9),

            // Tracking warm startup latency:
            // https://developer.android.com/topic/performance/vitals/launch-time#warm
            WARM(10),
        }

        /** Sets [InstanceId] of log message. */
        fun withInstanceId(instanceId: InstanceId?) = this

        /** Sets latency of the event. */
        fun withLatency(latencyInMillis: Long) = this

        /** Sets [LatencyType] of log message. */
        fun withType(type: LatencyType?) = this

        /** Sets query length of the event. */
        fun withQueryLength(queryLength: Int) = this

        /** Sets sub event type. */
        fun withSubEventType(type: Int) = this

        /** Sets cardinality of the event. */
        fun withCardinality(cardinality: Int) = this

        /** Sets packageId of log message. */
        fun withPackageId(packageId: Int) = this

        /** Builds the final message and logs it as [EventEnum]. */
        fun log(event: EventEnum) {}

        companion object {
            /** Package Id for Launcher */
            const val LAUNCHER_LATENCY_PACKAGE_ID: Int = 3
        }
    }

    /** Helps to construct and log impression event. */
    interface StatsImpressionLogger {

        enum class State(val launcherState: Int) {
            UNKNOWN(0),
            ALLAPPS(1),
            SEARCHBOX_WIDGET(2),
        }

        /** Sets [InstanceId] of log message. */
        fun withInstanceId(instanceId: InstanceId?) = this

        /** Sets [State] of impression event. */
        fun withState(state: State?) = this

        /** Sets query length of the event. */
        fun withQueryLength(queryLength: Int) = this

        /** Sets [com.android.app.search.ResultType] for the impression event. */
        fun withResultType(resultType: Int) = this

        /**
         * Sets boolean for each of [com.android.app.search.ResultType] that indicates if this
         * result is above keyboard or not for the impression event.
         */
        fun withAboveKeyboard(aboveKeyboard: Boolean) = this

        /**
         * Sets uid for each of [com.android.app.search.ResultType] that indicates package name for
         * the impression event.
         */
        fun withUid(uid: Int) = this

        /** Sets result source that indicates the origin of the result for the impression event. */
        fun withResultSource(resultSource: Int) = this

        /** Builds the final message and logs it as [EventEnum]. */
        fun log(event: EventEnum) {}
    }

    /** Returns new logger object. */
    fun logger(): StatsLogger {
        val logger = createLogger()
        if (mInstanceId != null) {
            logger.withInstanceId(mInstanceId)
        }
        return logger
    }

    /** Returns new latency logger object. */
    fun latencyLogger(): StatsLatencyLogger {
        val logger = createLatencyLogger()
        if (mInstanceId != null) {
            logger.withInstanceId(mInstanceId)
        }
        return logger
    }

    /** Returns new impression logger object. */
    fun impressionLogger(): StatsImpressionLogger {
        val logger = createImpressionLogger()
        if (mInstanceId != null) {
            logger.withInstanceId(mInstanceId)
        }
        return logger
    }

    /** Returns a singleton KeyboardStateManager. */
    fun keyboardStateManager(): KeyboardStateManager {
        if (mKeyboardStateManager == null) {
            mKeyboardStateManager =
                KeyboardStateManager(
                    mContext.resources?.getDimensionPixelSize(R.dimen.default_ime_height) ?: 0
                )
        }
        return mKeyboardStateManager!!
    }

    protected open fun createLogger(): StatsLogger {
        return object : StatsLogger {}
    }

    protected open fun createLatencyLogger(): StatsLatencyLogger {
        return object : StatsLatencyLogger {}
    }

    protected open fun createImpressionLogger(): StatsImpressionLogger {
        return object : StatsImpressionLogger {}
    }

    /** Sets InstanceId to every new [StatsLogger] object returned by [.logger] when not-null. */
    fun withDefaultInstanceId(instanceId: InstanceId?): StatsLogManager {
        this.mInstanceId = instanceId
        return this
    }

    companion object {
        const val LAUNCHER_STATE_UNSPECIFIED: Int = 0
        const val LAUNCHER_STATE_BACKGROUND: Int = 1
        const val LAUNCHER_STATE_HOME: Int = 2
        const val LAUNCHER_STATE_OVERVIEW: Int = 3
        const val LAUNCHER_STATE_ALLAPPS: Int = 4
        const val LAUNCHER_STATE_UNCHANGED: Int = 5

        /**
         * Returns event enum based on the two state transition information when swipe gesture
         * happens(to be removed during UserEventDispatcher cleanup).
         */
        @JvmStatic
        fun getLauncherAtomEvent(
            startState: Int,
            targetState: Int,
            fallbackEvent: EventEnum,
        ): EventEnum {
            return when {
                startState == LAUNCHER_STATE_HOME && targetState == LAUNCHER_STATE_HOME ->
                    LAUNCHER_HOME_GESTURE
                startState != LAUNCHER_STATE_OVERVIEW && targetState == LAUNCHER_STATE_OVERVIEW ->
                    LAUNCHER_OVERVIEW_GESTURE
                startState != LAUNCHER_STATE_ALLAPPS && targetState == LAUNCHER_STATE_ALLAPPS ->
                    LAUNCHER_ALLAPPS_OPEN_UP
                startState == LAUNCHER_STATE_ALLAPPS && targetState != LAUNCHER_STATE_ALLAPPS ->
                    LAUNCHER_ALLAPPS_CLOSE_DOWN
                else -> fallbackEvent // TODO fix
            }
        }

        /** Creates a new instance of [StatsLogManager] based on provided context. */
        @JvmStatic
        fun newInstance(context: Context): StatsLogManager {
            return context.appComponent.statsLogManagerFactory.create(context)
        }
    }
}
