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
package com.android.launcher3.touch;

import static com.android.launcher3.LauncherConstants.ActivityCodes.REQUEST_BIND_PENDING_APPWIDGET;
import static com.android.launcher3.LauncherConstants.ActivityCodes.REQUEST_RECONFIGURE_APPWIDGET;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_FOLDER_OPEN;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_PRIVATE_SPACE_INSTALL_APP_BUTTON_TAP;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_DISABLED_BY_PUBLISHER;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_DISABLED_LOCKED_USER;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_DISABLED_QUIET_USER;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_DISABLED_SAFEMODE;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_DISABLED_SUSPENDED;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.PackageInstaller.SessionInfo;
import android.os.Process;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.BuildConfig;
import com.android.launcher3.Flags;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.apppairs.AppPairIcon;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.logging.InstanceIdSequence;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.AppPairInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pm.InstallSessionHelper;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.util.ApiWrapper;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.views.FloatingIconView;
import com.android.launcher3.views.Snackbar;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.PendingAddShortcutInfo;
import com.android.launcher3.widget.PendingAddWidgetInfo;
import com.android.launcher3.widget.PendingAppWidgetHostView;
import com.android.launcher3.widget.WidgetAddFlowHandler;
import com.android.launcher3.widget.WidgetManagerHelper;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Class for handling clicks on workspace and all-apps items
 */
public class ItemClickHandler {

    private static final String TAG = "ItemClickHandler";
    private static final boolean DEBUG = true;

    /**
     * Instance used for click handling on items
     */
    public static final OnClickListener INSTANCE = ItemClickHandler::onClick;

    private static void onClick(View v) {
        // Make sure that rogue clicks don't get through while allapps is launching, or after the
        // view has detached (it's possible for this to happen if the view is removed mid touch).
        if (v.getWindowToken() == null) return;

        Launcher launcher = Launcher.getLauncher(v.getContext());
        if (!launcher.getWorkspace().isFinishedSwitchingState()) return;

        Object tag = v.getTag();
        if (tag instanceof WorkspaceItemInfo) {
            onClickAppShortcut(v, (WorkspaceItemInfo) tag, launcher);
        } else if (tag instanceof FolderInfo) {
            onClickFolderIcon(v);
        } else if (tag instanceof AppPairInfo) {
            onClickAppPairIcon(v);
        } else if (tag instanceof AppInfo) {
            startAppShortcutOrInfoActivity(v, (AppInfo) tag, launcher);
        } else if (tag instanceof LauncherAppWidgetInfo) {
            if (v instanceof PendingAppWidgetHostView) {
                if (DEBUG) {
                    String targetPackage = ((LauncherAppWidgetInfo) tag).getTargetPackage();
                    Log.d(TAG, "onClick: PendingAppWidgetHostView clicked for"
                            + " package=" + targetPackage);
                }
                onClickPendingWidget((PendingAppWidgetHostView) v, launcher);
            } else {
                if (DEBUG) {
                    String targetPackage = ((LauncherAppWidgetInfo) tag).getTargetPackage();
                    Log.d(TAG, "onClick: LauncherAppWidgetInfo clicked,"
                            + " but not instance of PendingAppWidgetHostView. Returning."
                            + " package=" + targetPackage);
                }
            }
        } else if (tag instanceof ItemClickProxy) {
            ((ItemClickProxy) tag).onItemClicked(v);
        } else if (tag instanceof PendingAddShortcutInfo) {
            CharSequence msg = Utilities.wrapForTts(
                    launcher.getText(R.string.long_press_shortcut_to_add),
                    launcher.getString(R.string.long_accessible_way_to_add_shortcut));
            Snackbar.show(launcher, msg, null);
        } else if (tag instanceof PendingAddWidgetInfo) {
            if (DEBUG) {
                String targetPackage = ((PendingAddWidgetInfo) tag).getTargetPackage();
                Log.d(TAG, "onClick: PendingAddWidgetInfo clicked for package=" + targetPackage);
            }
            CharSequence msg = Utilities.wrapForTts(
                    launcher.getText(R.string.long_press_widget_to_add),
                    launcher.getString(R.string.long_accessible_way_to_add));
            Snackbar.show(launcher, msg, null);
        }
    }

    /**
     * Event handler for a folder icon click.
     *
     * @param v The view that was clicked. Must be an instance of {@link FolderIcon}.
     */
    private static void onClickFolderIcon(View v) {
        Folder folder = ((FolderIcon) v).getFolder();
        if (!folder.isOpen() && !folder.isDestroyed()) {
            // Open the requested folder
            folder.animateOpen();
            StatsLogManager.newInstance(v.getContext()).logger().withItemInfo(folder.mInfo)
                    .log(LAUNCHER_FOLDER_OPEN);
        }
    }

    /**
     * Event handler for an app pair icon click.
     *
     * @param v The view that was clicked. Must be an instance of {@link AppPairIcon}.
     */
    private static void onClickAppPairIcon(View v) {
        Launcher launcher = Launcher.getLauncher(v.getContext());
        AppPairIcon icon = (AppPairIcon) v;
        AppPairInfo info = icon.getInfo();
        boolean isApp1Launchable = info.isLaunchable(launcher).getFirst(),
                isApp2Launchable = info.isLaunchable(launcher).getSecond();
        if (!isApp1Launchable || !isApp2Launchable) {
            // App pair is unlaunchable due to screen size.
            boolean isFoldable = InvariantDeviceProfile.INSTANCE.get(launcher)
                    .supportedProfiles.stream().anyMatch(dp -> dp.isTwoPanels);
            Toast.makeText(launcher, isFoldable
                            ? R.string.app_pair_needs_unfold
                            : R.string.app_pair_unlaunchable_at_screen_size,
                    Toast.LENGTH_SHORT).show();
            return;
        } else if (info.isDisabled()) {
            // App pair is disabled for another reason.
            WorkspaceItemInfo app1 = info.getFirstApp();
            WorkspaceItemInfo app2 = info.getSecondApp();
            // Show the user why the app pair is disabled.
            if (app1.isDisabled() && app2.isDisabled()) {
                // Both apps are disabled, show generic "app pair is not available" toast.
                Toast.makeText(launcher, R.string.app_pair_not_available, Toast.LENGTH_SHORT)
                        .show();
                return;
            } else if ((app1.isDisabled() && handleDisabledItemClicked(app1, launcher))
                    || (app2.isDisabled() && handleDisabledItemClicked(app2, launcher))) {
                // Only one is disabled, and handleDisabledItemClicked() showed a specific toast
                // explaining why, so we are done.
                return;
            }
        }

        // Either the app pair is not disabled, or it is a disabled state that can be handled by
        // framework directly (e.g. one app is paused), so go ahead and launch.
        launcher.launchAppPair(icon);
    }

    /**
     * Event handler for the app widget view which has not fully restored.
     */
    private static void onClickPendingWidget(PendingAppWidgetHostView v, Launcher launcher) {
        if (launcher.getPackageManager().isSafeMode()) {
            Toast.makeText(launcher, R.string.safemode_widget_error, Toast.LENGTH_SHORT).show();
            return;
        }

        final LauncherAppWidgetInfo info = (LauncherAppWidgetInfo) v.getTag();
        if (v.isReadyForClickSetup()) {
            LauncherAppWidgetProviderInfo appWidgetInfo = new WidgetManagerHelper(launcher)
                    .findProvider(info.providerName, info.user);
            if (appWidgetInfo == null) {
                Log.e(TAG, "onClickPendingWidget: Pending widget ready for click setup,"
                        + " but LauncherAppWidgetProviderInfo was null. Returning."
                        + " component=" + info.getTargetComponent());
                return;
            }
            WidgetAddFlowHandler addFlowHandler = new WidgetAddFlowHandler(appWidgetInfo);

            if (info.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_ID_NOT_VALID)) {
                if (!info.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_ID_ALLOCATED)) {
                    // This should not happen, as we make sure that an Id is allocated during bind.
                    Log.e(TAG, "onClickPendingWidget: Pending widget ready for click setup,"
                            + " and LauncherAppWidgetProviderInfo was found. However,"
                            + " no appWidgetId was allocated. Returning."
                            + " component=" + info.getTargetComponent());
                    return;
                }
                addFlowHandler.startBindFlow(launcher, info.appWidgetId, info,
                        REQUEST_BIND_PENDING_APPWIDGET);
            } else {
                addFlowHandler.startConfigActivity(launcher, info, REQUEST_RECONFIGURE_APPWIDGET);
            }
        } else {
            final String packageName = info.providerName.getPackageName();
            onClickPendingAppItem(v, launcher, packageName, info.installProgress >= 0);
        }
    }

    private static void onClickPendingAppItem(View v, Launcher launcher, String packageName,
            boolean downloadStarted) {
        ItemInfo item = (ItemInfo) v.getTag();
        CompletableFuture<SessionInfo> siFuture;
        siFuture = CompletableFuture.supplyAsync(() ->
                        InstallSessionHelper.INSTANCE.get(launcher)
                                .getActiveSessionInfo(item.user, packageName),
                UI_HELPER_EXECUTOR);
        Consumer<SessionInfo> marketLaunchAction = sessionInfo -> {
            if (sessionInfo != null) {
                LauncherApps launcherApps = launcher.getSystemService(LauncherApps.class);
                try {
                    launcherApps.startPackageInstallerSessionDetailsActivity(sessionInfo, null,
                            launcher.getActivityLaunchOptions(v, item).toBundle());
                    return;
                } catch (Exception e) {
                    Log.e(TAG, "Unable to launch market intent for package=" + packageName, e);
                }
            }
            // Fallback to using custom market intent.
            Intent intent = ApiWrapper.INSTANCE.get(launcher).getAppMarketActivityIntent(
                    packageName, Process.myUserHandle());
            launcher.startActivitySafely(v, intent, item);
        };

        if (downloadStarted) {
            // If the download has started, simply direct to the market app.
            siFuture.thenAcceptAsync(marketLaunchAction, MAIN_EXECUTOR);
            return;
        }
        new AlertDialog.Builder(launcher)
                .setTitle(R.string.abandoned_promises_title)
                .setMessage(R.string.abandoned_promise_explanation)
                .setPositiveButton(R.string.abandoned_search,
                        (d, i) -> siFuture.thenAcceptAsync(marketLaunchAction, MAIN_EXECUTOR))
                .setNeutralButton(R.string.abandoned_clean_this,
                        (d, i) -> launcher.getWorkspace()
                                .persistRemoveItemsByMatcher(ItemInfoMatcher.ofPackages(
                                        Collections.singleton(packageName), item.user),
                                        "user explicitly removes the promise app icon"))
                .create().show();
    }

    /**
     * Handles clicking on a disabled shortcut
     *
     * @return true iff the disabled item click has been handled.
     */
    public static boolean handleDisabledItemClicked(WorkspaceItemInfo shortcut, Context context) {
        final int disabledFlags = shortcut.runtimeStatusFlags
                & WorkspaceItemInfo.FLAG_DISABLED_MASK;
        // Handle the case where the disabled reason is DISABLED_REASON_VERSION_LOWER.
        // Show an AlertDialog for the user to choose either updating the app or cancel the launch.
        if (maybeCreateAlertDialogForShortcut(shortcut, context)) {
            return true;
        }

        if ((disabledFlags
                & ~FLAG_DISABLED_SUSPENDED
                & ~FLAG_DISABLED_QUIET_USER) == 0) {
            // If the app is only disabled because of the above flags, launch activity anyway.
            // Framework will tell the user why the app is suspended.
            return false;
        } else {
            if (!TextUtils.isEmpty(shortcut.disabledMessage)) {
                // Use a message specific to this shortcut, if it has one.
                Toast.makeText(context, shortcut.disabledMessage, Toast.LENGTH_SHORT).show();
                return true;
            }
            // Otherwise just use a generic error message.
            int error = R.string.activity_not_available;
            if ((shortcut.runtimeStatusFlags & FLAG_DISABLED_SAFEMODE) != 0) {
                error = R.string.safemode_shortcut_error;
            } else if ((shortcut.runtimeStatusFlags & FLAG_DISABLED_BY_PUBLISHER) != 0
                    || (shortcut.runtimeStatusFlags & FLAG_DISABLED_LOCKED_USER) != 0) {
                error = R.string.shortcut_not_available;
            }
            Toast.makeText(context, error, Toast.LENGTH_SHORT).show();
            return true;
        }
    }

    private static boolean maybeCreateAlertDialogForShortcut(final WorkspaceItemInfo shortcut,
            Context context) {
        try {
            final Launcher launcher = Launcher.getLauncher(context);
            if (shortcut.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
                    && shortcut.isDisabledVersionLower()) {
                final Intent marketIntent = shortcut.getMarketIntent(context);
                // No market intent means no target package for the shortcut, which should be an
                // issue. Falling back to showing toast messages.
                if (marketIntent == null) {
                    return false;
                }

                new AlertDialog.Builder(context)
                        .setTitle(R.string.dialog_update_title)
                        .setMessage(R.string.dialog_update_message)
                        .setPositiveButton(R.string.dialog_update, (d, i) -> {
                            // Direct the user to the play store to update the app
                            context.startActivity(marketIntent);
                        })
                        .setNeutralButton(R.string.dialog_remove, (d, i) -> {
                            // Remove the icon if launcher is successfully initialized
                            launcher.getWorkspace().persistRemoveItemsByMatcher(ItemInfoMatcher
                                    .ofShortcutKeys(Collections.singleton(ShortcutKey
                                            .fromItemInfo(shortcut))),
                                    "user explicitly removes disabled shortcut");
                        })
                        .create()
                        .show();
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error creating alert dialog", e);
        }

        return false;
    }

    /**
     * Event handler for an app shortcut click.
     *
     * @param v The view that was clicked. Must be a tagged with a {@link WorkspaceItemInfo}.
     */
    public static void onClickAppShortcut(View v, WorkspaceItemInfo shortcut, Launcher launcher) {
        if (shortcut.isDisabled() && handleDisabledItemClicked(shortcut, launcher)) {
            return;
        }

        // Check for abandoned promise
        if ((v instanceof BubbleTextView) && shortcut.hasPromiseIconUi()
                && (!Flags.enableSupportForArchiving() || !shortcut.isArchived())) {
            String packageName = shortcut.getIntent().getComponent() != null
                    ? shortcut.getIntent().getComponent().getPackageName()
                    : shortcut.getIntent().getPackage();
            if (!TextUtils.isEmpty(packageName)) {
                onClickPendingAppItem(
                        v,
                        launcher,
                        packageName,
                        (shortcut.runtimeStatusFlags
                                & ItemInfoWithIcon.FLAG_INSTALL_SESSION_ACTIVE) != 0);
                return;
            }
        }

        // Start activities
        startAppShortcutOrInfoActivity(v, shortcut, launcher);
    }

    private static void startAppShortcutOrInfoActivity(View v, ItemInfo item, Launcher launcher) {
        TestLogging.recordEvent(
                TestProtocol.SEQUENCE_MAIN, "start: startAppShortcutOrInfoActivity");
        Intent intent = item.getIntent();
        if (item instanceof ItemInfoWithIcon itemInfoWithIcon) {
            if ((itemInfoWithIcon.runtimeStatusFlags
                    & ItemInfoWithIcon.FLAG_INSTALL_SESSION_ACTIVE) != 0) {
                intent = ApiWrapper.INSTANCE.get(launcher).getAppMarketActivityIntent(
                        itemInfoWithIcon.getTargetComponent().getPackageName(),
                        Process.myUserHandle());
            } else if (itemInfoWithIcon.itemType
                    == LauncherSettings.Favorites.ITEM_TYPE_PRIVATE_SPACE_INSTALL_APP_BUTTON) {
                intent = ApiWrapper.INSTANCE.get(launcher).getAppMarketActivityIntent(
                        BuildConfig.APPLICATION_ID,
                        launcher.getAppsView().getPrivateProfileManager().getProfileUser());
                launcher.getStatsLogManager().logger().log(
                        LAUNCHER_PRIVATE_SPACE_INSTALL_APP_BUTTON_TAP);
            }
        }
        if (intent == null) {
            throw new IllegalArgumentException("Input must have a valid intent");
        }
        if (item instanceof WorkspaceItemInfo) {
            WorkspaceItemInfo si = (WorkspaceItemInfo) item;
            if (si.hasStatusFlag(WorkspaceItemInfo.FLAG_SUPPORTS_WEB_UI)
                    && Intent.ACTION_VIEW.equals(intent.getAction())) {
                // make a copy of the intent that has the package set to null
                // we do this because the platform sometimes disables instant
                // apps temporarily (triggered by the user) and fallbacks to the
                // web ui. This only works though if the package isn't set
                intent = new Intent(intent);
                intent.setPackage(null);
            }
            if ((si.options & WorkspaceItemInfo.FLAG_START_FOR_RESULT) != 0) {
                launcher.startActivityForResult(item.getIntent(), 0);
                InstanceId instanceId = new InstanceIdSequence().newInstanceId();
                launcher.logAppLaunch(launcher.getStatsLogManager(), item, instanceId);
                return;
            }
        }
        if (v != null && launcher.supportsAdaptiveIconAnimation(v)
                && !item.shouldUseBackgroundAnimation()) {
            // Preload the icon to reduce latency b/w swapping the floating view with the original.
            FloatingIconView.fetchIcon(launcher, v, item, true /* isOpening */);
        }
        launcher.startActivitySafely(v, intent, item);
    }

    /**
     * Interface to indicate that an item will handle the click itself.
     */
    public interface ItemClickProxy {

        /**
         * Called when the item is clicked
         */
        void onItemClicked(View view);
    }
}
