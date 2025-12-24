/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3.popup

import android.content.Intent
import android.os.Process
import android.util.Log
import android.view.View
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.AbstractFloatingViewHelper
import com.android.launcher3.DropTargetHandler
import com.android.launcher3.Flags
import com.android.launcher3.LauncherConstants
import com.android.launcher3.R
import com.android.launcher3.SecondaryDropTarget
import com.android.launcher3.Utilities
import com.android.launcher3.accessibility.LauncherAccessibilityDelegate
import com.android.launcher3.allapps.PrivateProfileManager
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.logging.StatsLogManager.LauncherEvent
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.popup.SystemShortcut.BubbleActivityStarter
import com.android.launcher3.popup.SystemShortcut.TaskbarBubbleActivityStarter
import com.android.launcher3.util.ActivityOptionsWrapper
import com.android.launcher3.util.ApiWrapper
import com.android.launcher3.util.PackageManagerHelper
import com.android.launcher3.util.PendingRequestArgs
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.Snackbar
import com.android.launcher3.widget.LauncherAppWidgetHostView
import com.android.launcher3.widget.WidgetsBottomSheet
import com.android.wm.shell.shared.bubbles.logging.EntryPoint
import javax.inject.Inject

@LauncherAppSingleton
class PopupDataSource @Inject constructor() {
    // Handles action from tapping remove shortcut.
    private val handleRemove = { activityContext: ActivityContext, itemInfo: ItemInfo, view: View ->
        AbstractFloatingView.closeAllOpenViews(activityContext)
        val dropTargetHandler: DropTargetHandler = activityContext.dropTargetHandler
        dropTargetHandler.prepareToUndoDelete()
        dropTargetHandler.onDeleteComplete(itemInfo, view)
    }

    // Popup data for remove shortcut.
    val removePopupData =
        PopupData(
            iconResId = R.drawable.ic_remove_no_shadow,
            labelResId = R.string.remove_drop_target_label,
            popupAction = handleRemove,
            category = PopupCategory.SYSTEM_SHORTCUT_FIXED,
        )

    private val handleAddToHomeScreenFromAllApps =
        { activityContext: ActivityContext, itemInfo: ItemInfo, view: View ->
            AbstractFloatingView.closeAllOpenViews(activityContext)
            val launcherAccessibilityDelegate =
                activityContext.accessibilityDelegate as LauncherAccessibilityDelegate
            launcherAccessibilityDelegate.addToWorkspace(itemInfo, /* accessibility= */ false)
            /*finishCallback=*/ {
                activityContext.statsLogManager
                    .logger()
                    .withItemInfo(itemInfo)
                    .log(LauncherEvent.LAUNCHER_TAP_TO_ADD_TO_HOME_SCREEN_FROM_ALL_APPS)
            }
            Unit
        }

    // Popup data for add to home screen from all apps shortcut.
    val addToHomeScreenFromAllAppsPopupData =
        PopupData(
            iconResId = R.drawable.ic_plus,
            labelResId = R.string.action_add_to_workspace,
            popupAction = handleAddToHomeScreenFromAllApps,
            category = PopupCategory.SYSTEM_SHORTCUT_FIXED,
        )

    // Handles action from tapping widget settings.
    private val handleWidgetSettings =
        { activityContext: ActivityContext, itemInfo: ItemInfo, view: View ->
            if (view is LauncherAppWidgetHostView) {
                activityContext.setWaitingForResult(
                    PendingRequestArgs.forWidgetInfo(
                        view.appWidgetId,
                        // Widget add handler is null since we're reconfiguring an existing widget.
                        /* widgetHandler= */ null,
                        itemInfo,
                    )
                )

                activityContext.appWidgetHolder?.also {
                    it.startConfigActivity(
                        ActivityContext.lookupContext(view.context),
                        view.appWidgetId,
                        LauncherConstants.ActivityCodes.REQUEST_RECONFIGURE_APPWIDGET,
                    )
                } ?: Log.e(TAG, "appWidgetHolder is null, cannot start config activity.")
            }
        }

    // Popup data for widget settings shortcut.
    val widgetSettingsPopupData =
        PopupData(
            iconResId = R.drawable.ic_setting,
            labelResId = R.string.widget_settings,
            popupAction = handleWidgetSettings,
            category = PopupCategory.SYSTEM_SHORTCUT_FIXED,
        )

    // Handles action from tapping widgets shortcut.
    private val handleWidgets =
        { activityContext: ActivityContext, itemInfo: ItemInfo, view: View ->
            AbstractFloatingView.closeAllOpenViews(activityContext)
            val widgetsBottomSheet =
                activityContext
                    .getLayoutInflater()
                    .inflate(R.layout.widgets_bottom_sheet, activityContext.getDragLayer(), false)
                    as WidgetsBottomSheet
            widgetsBottomSheet.populateAndShow(itemInfo)
            activityContext.statsLogManager
                .logger()
                .withItemInfo(itemInfo)
                .log(LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_WIDGETS_TAP)
        }

    // Popup data for widgets shortcut.
    val widgetsPopupData =
        PopupData(
            iconResId =
                if (Flags.enableLauncherVisualRefresh()) R.drawable.widgets_24px
                else R.drawable.ic_widget,
            labelResId = R.string.widget_button_text,
            popupAction = handleWidgets,
            category = PopupCategory.SYSTEM_SHORTCUT_FIXED,
        )

    // Handle action from tapping app info shortcut.
    private val handleAppInfo =
        { activityContext: ActivityContext, itemInfo: ItemInfo, view: View ->
            val sourceBounds = Utilities.getViewBounds(view)
            val options: ActivityOptionsWrapper =
                activityContext.getActivityLaunchOptions(view, itemInfo)

            // Dismiss the taskMenu when the app launch animation is complete
            options.onEndCallback.add { dismissTaskMenuView(activityContext) }
            PackageManagerHelper.startDetailsActivityForInfo(
                view.context,
                itemInfo,
                sourceBounds,
                options.toBundle(),
            )
            activityContext.statsLogManager
                .logger()
                .withItemInfo(itemInfo)
                .log(LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_APP_INFO_TAP)
        }

    // Popup data for app info shortcut.
    val appInfoPopupData =
        PopupData(
            iconResId =
                if (Flags.enableLauncherVisualRefresh()) R.drawable.info_24px
                else R.drawable.ic_info_no_shadow,
            labelResId = R.string.app_info_drop_target_label,
            popupAction = handleAppInfo,
            category = PopupCategory.SYSTEM_SHORTCUT,
        )

    // Handle action from tapping private profile install shortcut.
    private val handlePrivateProfileInstall =
        { activityContext: ActivityContext, itemInfo: ItemInfo, view: View ->
            val privateProfileManager: PrivateProfileManager =
                activityContext.appsView.privateProfileManager
            val intent =
                ApiWrapper.INSTANCE[view.context].getAppMarketActivityIntent(
                    itemInfo.targetComponent?.packageName,
                    privateProfileManager.profileUser,
                )
            activityContext.startActivitySafely(view, intent, itemInfo)
            AbstractFloatingView.closeAllOpenViews(activityContext)
            activityContext.statsLogManager
                .logger()
                .withItemInfo(itemInfo)
                .log(LauncherEvent.LAUNCHER_PRIVATE_SPACE_INSTALL_SYSTEM_SHORTCUT_TAP)
        }

    // Popup data for private profile install shortcut.
    val privateProfileInstallPopupData =
        PopupData(
            iconResId = R.drawable.ic_remove_no_shadow,
            labelResId = R.string.remove_drop_target_label,
            popupAction = handlePrivateProfileInstall,
            category = PopupCategory.SYSTEM_SHORTCUT,
        )

    // Handles action from tapping install shortcut.
    private val handleInstall =
        { activityContext: ActivityContext, itemInfo: ItemInfo, view: View ->
            val intent =
                ApiWrapper.INSTANCE[view.context].getAppMarketActivityIntent(
                    itemInfo.targetComponent?.packageName,
                    Process.myUserHandle(),
                )
            activityContext.startActivitySafely(view, intent, itemInfo)
            AbstractFloatingView.closeAllOpenViews(activityContext)
        }

    // Popup data for install shortcut.
    val installPopupData =
        PopupData(
            iconResId = R.drawable.ic_install_no_shadow,
            labelResId = R.string.install_drop_target_label,
            popupAction = handleInstall,
            category = PopupCategory.SYSTEM_SHORTCUT,
        )

    // Handles action from tapping "don't suggest app" shortcut.
    private val handleDontSuggestApp =
        { activityContext: ActivityContext, itemInfo: ItemInfo, view: View ->
            dismissTaskMenuView(activityContext)
            activityContext.statsLogManager
                .logger()
                .withItemInfo(itemInfo)
                .log(LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_DONT_SUGGEST_APP_TAP)
            Snackbar.show(
                activityContext,
                view.context.getString(R.string.item_removed),
                R.string.undo,
                {},
                {
                    activityContext.statsLogManager
                        .logger()
                        .withItemInfo(itemInfo)
                        .log(LauncherEvent.LAUNCHER_DISMISS_PREDICTION_UNDO)
                },
            )
        }

    // Popup data the "don't suggest app" shortcut.
    val dontSuggestAppPopupData =
        PopupData(
            iconResId = R.drawable.ic_block_no_shadow,
            labelResId = R.string.dismiss_prediction_label,
            popupAction = handleDontSuggestApp,
            category = PopupCategory.SYSTEM_SHORTCUT,
        )

    // Handles action when tapping uninstall app shortcut.
    private val handleUninstallApp =
        { activityContext: ActivityContext, itemInfo: ItemInfo, view: View ->
            dismissTaskMenuView(activityContext)
            val componentName = SecondaryDropTarget.getUninstallTarget(view.context, itemInfo)
            SecondaryDropTarget.performUninstall(view.context, componentName, itemInfo)
            activityContext.statsLogManager
                .logger()
                .withItemInfo(itemInfo)
                .log(LauncherEvent.LAUNCHER_PRIVATE_SPACE_UNINSTALL_SYSTEM_SHORTCUT_TAP)
        }

    // Popup data for uninstall app shortcut.
    val uninstallAppPopupData =
        PopupData(
            iconResId = R.drawable.ic_uninstall_no_shadow,
            labelResId = R.string.uninstall_private_system_shortcut_label,
            popupAction = handleUninstallApp,
            category = PopupCategory.SYSTEM_SHORTCUT,
        )

    // Handles action when tapping bubble shortcut.
    private val handleBubbleShortcut =
        { activityContext: ActivityContext, itemInfo: ItemInfo, _: View ->
            val starter: BubbleActivityStarter = activityContext as BubbleActivityStarter

            dismissTaskMenuView(activityContext)
            showBubbleShortcut(starter, itemInfo)
        }

    private fun showBubbleShortcut(starter: BubbleActivityStarter, itemInfo: ItemInfo) {
        fun ItemInfo.getEntryPoint() =
            when {
                isInAllApps -> EntryPoint.ALL_APPS_ICON_MENU
                isInHotseat ->
                    if (starter is TaskbarBubbleActivityStarter) {
                        EntryPoint.TASKBAR_ICON_MENU
                    } else {
                        EntryPoint.HOTSEAT_ICON_MENU
                    }
                else -> EntryPoint.LAUNCHER_ICON_MENU
            }

        // TODO: handle GroupTask (single) items so that recent items in taskbar work
        if (itemInfo is WorkspaceItemInfo) {
            val shortcutInfo = itemInfo.deepShortcutInfo
            if (shortcutInfo != null) {
                starter.showShortcutBubble(shortcutInfo, itemInfo.getEntryPoint())
                return
            }
        }

        // If we're here check for an intent
        if (itemInfo.intent != null) {
            val intent = Intent(itemInfo.intent)
            if (intent.getPackage() == null) {
                intent.setPackage(itemInfo.getTargetPackage())
            }
            starter.showAppBubble(intent, itemInfo.user, itemInfo.getEntryPoint())
        } else {
            Log.w(TAG, "unable to bubble, no intent: $itemInfo")
        }
    }

    // Popup data for bubble shortcut
    val bubblePopupData =
        PopupData(
            iconResId = R.drawable.ic_bubble_button,
            labelResId = R.string.bubble,
            popupAction = handleBubbleShortcut,
            category = PopupCategory.SYSTEM_SHORTCUT,
        )

    private fun dismissTaskMenuView(activityContext: ActivityContext) {
        AbstractFloatingViewHelper()
            .closeOpenViews(
                activityContext,
                true,
                AbstractFloatingView.TYPE_ALL and AbstractFloatingView.TYPE_REBIND_SAFE.inv(),
            )
    }

    companion object {
        private const val TAG = "PopupDataSource"
    }
}
