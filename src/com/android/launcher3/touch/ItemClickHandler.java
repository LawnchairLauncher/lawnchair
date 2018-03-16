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

import static com.android.launcher3.ItemInfoWithIcon.FLAG_DISABLED_BY_PUBLISHER;
import static com.android.launcher3.ItemInfoWithIcon.FLAG_DISABLED_LOCKED_USER;
import static com.android.launcher3.ItemInfoWithIcon.FLAG_DISABLED_QUIET_USER;
import static com.android.launcher3.ItemInfoWithIcon.FLAG_DISABLED_SAFEMODE;
import static com.android.launcher3.ItemInfoWithIcon.FLAG_DISABLED_SUSPENDED;
import static com.android.launcher3.Launcher.REQUEST_BIND_PENDING_APPWIDGET;
import static com.android.launcher3.Launcher.REQUEST_RECONFIGURE_APPWIDGET;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Process;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;

import com.android.launcher3.AppInfo;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.FolderInfo;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppWidgetInfo;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.PromiseAppInfo;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.compat.AppWidgetManagerCompat;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.widget.PendingAppWidgetHostView;
import com.android.launcher3.widget.WidgetAddFlowHandler;

/**
 * Class for handling clicks on workspace and all-apps items
 */
public class ItemClickHandler {

    /**
     * Instance used for click handling on items
     */
    public static final OnClickListener INSTANCE = ItemClickHandler::onClick;

    private static void onClick(View v) {
        // Make sure that rogue clicks don't get through while allapps is launching, or after the
        // view has detached (it's possible for this to happen if the view is removed mid touch).
        if (v.getWindowToken() == null) {
            return;
        }

        Launcher launcher = Launcher.getLauncher(v.getContext());
        if (!launcher.getWorkspace().isFinishedSwitchingState()) {
            return;
        }

        Object tag = v.getTag();
        if (tag instanceof ShortcutInfo) {
            onClickAppShortcut(v, (ShortcutInfo) tag, launcher);
        } else if (tag instanceof FolderInfo) {
            if (v instanceof FolderIcon) {
                onClickFolderIcon(v);
            }
        } else if (tag instanceof AppInfo) {
            startAppShortcutOrInfoActivity(v, (AppInfo) tag, launcher);
        } else if (tag instanceof LauncherAppWidgetInfo) {
            if (v instanceof PendingAppWidgetHostView) {
                onClickPendingWidget((PendingAppWidgetHostView) v, launcher);
            }
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
        }
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
            LauncherAppWidgetProviderInfo appWidgetInfo = AppWidgetManagerCompat
                    .getInstance(launcher).findProvider(info.providerName, info.user);
            if (appWidgetInfo == null) {
                return;
            }
            WidgetAddFlowHandler addFlowHandler = new WidgetAddFlowHandler(appWidgetInfo);

            if (info.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_ID_NOT_VALID)) {
                if (!info.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_ID_ALLOCATED)) {
                    // This should not happen, as we make sure that an Id is allocated during bind.
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
        if (downloadStarted) {
            // If the download has started, simply direct to the market app.
            startMarketIntentForPackage(v, launcher, packageName);
            return;
        }
        new AlertDialog.Builder(launcher)
                .setTitle(R.string.abandoned_promises_title)
                .setMessage(R.string.abandoned_promise_explanation)
                .setPositiveButton(R.string.abandoned_search,
                        (d, i) -> startMarketIntentForPackage(v, launcher, packageName))
                .setNeutralButton(R.string.abandoned_clean_this,
                        (d, i) -> launcher.getWorkspace()
                                .removeAbandonedPromise(packageName, Process.myUserHandle()))
                .create().show();
    }

    private static void startMarketIntentForPackage(View v, Launcher launcher, String packageName) {
        ItemInfo item = (ItemInfo) v.getTag();
        Intent intent = new PackageManagerHelper(launcher).getMarketIntent(packageName);
        launcher.startActivitySafely(v, intent, item);
    }

    /**
     * Event handler for an app shortcut click.
     *
     * @param v The view that was clicked. Must be a tagged with a {@link ShortcutInfo}.
     */
    private static void onClickAppShortcut(View v, ShortcutInfo shortcut, Launcher launcher) {
        if (shortcut.isDisabled()) {
            final int disabledFlags = shortcut.runtimeStatusFlags & ShortcutInfo.FLAG_DISABLED_MASK;
            if ((disabledFlags &
                    ~FLAG_DISABLED_SUSPENDED &
                    ~FLAG_DISABLED_QUIET_USER) == 0) {
                // If the app is only disabled because of the above flags, launch activity anyway.
                // Framework will tell the user why the app is suspended.
            } else {
                if (!TextUtils.isEmpty(shortcut.disabledMessage)) {
                    // Use a message specific to this shortcut, if it has one.
                    Toast.makeText(launcher, shortcut.disabledMessage, Toast.LENGTH_SHORT).show();
                    return;
                }
                // Otherwise just use a generic error message.
                int error = R.string.activity_not_available;
                if ((shortcut.runtimeStatusFlags & FLAG_DISABLED_SAFEMODE) != 0) {
                    error = R.string.safemode_shortcut_error;
                } else if ((shortcut.runtimeStatusFlags & FLAG_DISABLED_BY_PUBLISHER) != 0 ||
                        (shortcut.runtimeStatusFlags & FLAG_DISABLED_LOCKED_USER) != 0) {
                    error = R.string.shortcut_not_available;
                }
                Toast.makeText(launcher, error, Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // Check for abandoned promise
        if ((v instanceof BubbleTextView) && shortcut.hasPromiseIconUi()) {
            String packageName = shortcut.intent.getComponent() != null ?
                    shortcut.intent.getComponent().getPackageName() : shortcut.intent.getPackage();
            if (!TextUtils.isEmpty(packageName)) {
                onClickPendingAppItem(v, launcher, packageName,
                        shortcut.hasStatusFlag(ShortcutInfo.FLAG_INSTALL_SESSION_ACTIVE));
                return;
            }
        }

        // Start activities
        startAppShortcutOrInfoActivity(v, shortcut, launcher);
    }

    private static void startAppShortcutOrInfoActivity(View v, ItemInfo item, Launcher launcher) {
        Intent intent;
        if (item instanceof PromiseAppInfo) {
            PromiseAppInfo promiseAppInfo = (PromiseAppInfo) item;
            intent = promiseAppInfo.getMarketIntent(launcher);
        } else {
            intent = item.getIntent();
        }
        if (intent == null) {
            throw new IllegalArgumentException("Input must have a valid intent");
        }
        if (item instanceof ShortcutInfo) {
            ShortcutInfo si = (ShortcutInfo) item;
            if (si.hasStatusFlag(ShortcutInfo.FLAG_SUPPORTS_WEB_UI)
                    && intent.getAction() == Intent.ACTION_VIEW) {
                // make a copy of the intent that has the package set to null
                // we do this because the platform sometimes disables instant
                // apps temporarily (triggered by the user) and fallbacks to the
                // web ui. This only works though if the package isn't set
                intent = new Intent(intent);
                intent.setPackage(null);
            }
        }
        launcher.startActivitySafely(v, intent, item);
    }
}
