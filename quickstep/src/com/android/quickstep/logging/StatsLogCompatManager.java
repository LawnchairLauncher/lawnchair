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

package com.android.quickstep.logging;

import static android.stats.launcher.nano.Launcher.ALLAPPS;
import static android.stats.launcher.nano.Launcher.HOME;
import static android.stats.launcher.nano.Launcher.LAUNCH_APP;
import static android.stats.launcher.nano.Launcher.LAUNCH_TASK;
import static android.stats.launcher.nano.Launcher.DISMISS_TASK;
import static android.stats.launcher.nano.Launcher.BACKGROUND;
import static android.stats.launcher.nano.Launcher.OVERVIEW;

import android.content.Context;
import android.content.Intent;
import android.stats.launcher.nano.Launcher;
import android.stats.launcher.nano.LauncherExtension;
import android.stats.launcher.nano.LauncherTarget;
import android.util.Log;
import android.view.View;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.logging.StatsLogUtils;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;
import com.android.launcher3.userevent.nano.LauncherLogProto.ItemType;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.launcher3.userevent.nano.LauncherLogProto.ControlType;
import com.android.launcher3.util.ComponentKey;
import com.android.systemui.shared.system.StatsLogCompat;
import com.google.protobuf.nano.MessageNano;

/**
 * This method calls the StatsLog hidden method until they are made available public.
 *
 * To see if the logs are properly sent to statsd, execute following command.
 * $ adb root && adb shell statsd
 * $ adb shell cmd stats print-logs
 * $ adb logcat | grep statsd  OR $ adb logcat -b stats
 */
public class StatsLogCompatManager extends StatsLogManager {

    private static final int SUPPORTED_TARGET_DEPTH = 2;
    private static final String TAG = "StatsLogCompatManager";
    private static final boolean DEBUG = false;

    public StatsLogCompatManager(Context context) { }

    @Override
    public void logAppLaunch(View v, Intent intent) {
        LauncherExtension ext = new LauncherExtension();
        ext.srcTarget = new LauncherTarget[SUPPORTED_TARGET_DEPTH];
        int srcState = mStateProvider.getCurrentState();
        fillInLauncherExtension(v, ext);
        if (ext.srcTarget[0] != null) {
            ext.srcTarget[0].item = LauncherTarget.APP_ICON;
        }
        StatsLogCompat.write(LAUNCH_APP, srcState, BACKGROUND /* dstState */,
                MessageNano.toByteArray(ext), true);
    }

    @Override
    public void logTaskLaunch(View v, ComponentKey componentKey) {
        LauncherExtension ext = new LauncherExtension();
        ext.srcTarget = new LauncherTarget[SUPPORTED_TARGET_DEPTH];
        int srcState = OVERVIEW;
        fillInLauncherExtension(v, ext);
        StatsLogCompat.write(LAUNCH_TASK, srcState, BACKGROUND /* dstState */,
                MessageNano.toByteArray(ext), true);
    }

    @Override
    public void logTaskDismiss(View v, ComponentKey componentKey) {
        LauncherExtension ext = new LauncherExtension();
        ext.srcTarget = new LauncherTarget[SUPPORTED_TARGET_DEPTH];
        int srcState = OVERVIEW;
        fillInLauncherExtension(v, ext);
        StatsLogCompat.write(DISMISS_TASK, srcState, BACKGROUND /* dstState */,
                MessageNano.toByteArray(ext), true);
    }

    @Override
    public void logSwipeOnContainer(boolean isSwipingToLeft, int pageId) {
        LauncherExtension ext = new LauncherExtension();
        ext.srcTarget = new LauncherTarget[1];
        int srcState = mStateProvider.getCurrentState();
        fillInLauncherExtensionWithPageId(ext, pageId);
        int launcherAction = isSwipingToLeft ? Launcher.SWIPE_LEFT : Launcher.SWIPE_RIGHT;
        StatsLogCompat.write(launcherAction, srcState, srcState,
                MessageNano.toByteArray(ext), true);
    }

    public static boolean fillInLauncherExtension(View v, LauncherExtension extension) {
        if (DEBUG) {
            Log.d(TAG, "fillInLauncherExtension");
        }

        StatsLogUtils.LogContainerProvider provider = StatsLogUtils.getLaunchProviderRecursive(v);
        if (v == null || !(v.getTag() instanceof ItemInfo) || provider == null) {
            if (DEBUG) {
                Log.d(TAG, "View or provider is null, or view doesn't have an ItemInfo tag.");
            }

            return false;
        }
        ItemInfo itemInfo = (ItemInfo) v.getTag();
        Target child = new Target();
        Target parent = new Target();
        provider.fillInLogContainerData(v, itemInfo, child, parent);
        extension.srcTarget[0] = new LauncherTarget();
        extension.srcTarget[1] = new LauncherTarget();
        copy(child, extension.srcTarget[0]);
        copy(parent, extension.srcTarget[1]);
        return true;
    }

    public static boolean fillInLauncherExtensionWithPageId(LauncherExtension ext, int pageId) {
        if (DEBUG) {
            Log.d(TAG, "fillInLauncherExtensionWithPageId, pageId = " + pageId);
        }

        Target target = new Target();
        target.pageIndex = pageId;
        ext.srcTarget[0] = new LauncherTarget();
        copy(target, ext.srcTarget[0]);
        return true;
    }

    private static void copy(Target src, LauncherTarget dst) {
        if (DEBUG) {
            Log.d(TAG, "copy target information from clearcut Target to LauncherTarget.");
        }

        // Fill in type
        switch (src.type) {
            case Target.Type.ITEM:
                dst.type = LauncherTarget.ITEM_TYPE;
                break;
            case Target.Type.CONTROL:
                dst.type = LauncherTarget.CONTROL_TYPE;
                break;
            case Target.Type.CONTAINER:
                dst.type = LauncherTarget.CONTAINER_TYPE;
                break;
            default:
                dst.type = LauncherTarget.NONE;
                break;
        }

        // Fill in item
        switch (src.itemType) {
            case ItemType.APP_ICON:
                dst.item = LauncherTarget.APP_ICON;
                break;
            case ItemType.SHORTCUT:
                dst.item = LauncherTarget.SHORTCUT;
                break;
            case ItemType.WIDGET:
                dst.item = LauncherTarget.WIDGET;
                break;
            case ItemType.FOLDER_ICON:
                dst.item = LauncherTarget.FOLDER_ICON;
                break;
            case ItemType.DEEPSHORTCUT:
                dst.item = LauncherTarget.DEEPSHORTCUT;
                break;
            case ItemType.SEARCHBOX:
                dst.item = LauncherTarget.SEARCHBOX;
                break;
            case ItemType.EDITTEXT:
                dst.item = LauncherTarget.EDITTEXT;
                break;
            case ItemType.NOTIFICATION:
                dst.item = LauncherTarget.NOTIFICATION;
                break;
            case ItemType.TASK:
                dst.item = LauncherTarget.TASK;
                break;
            default:
                dst.item = LauncherTarget.DEFAULT_ITEM;
                break;
        }

        // Fill in container
        switch (src.containerType) {
            case ContainerType.HOTSEAT:
                dst.container = LauncherTarget.HOTSEAT;
                break;
            case ContainerType.FOLDER:
                dst.container = LauncherTarget.FOLDER;
                break;
            case ContainerType.PREDICTION:
                dst.container = LauncherTarget.PREDICTION;
                break;
            case ContainerType.SEARCHRESULT:
                dst.container = LauncherTarget.SEARCHRESULT;
                break;
            default:
                dst.container = LauncherTarget.DEFAULT_CONTAINER;
                break;
        }

        // Fill in control
        switch (src.controlType) {
            case ControlType.UNINSTALL_TARGET:
                dst.control = LauncherTarget.UNINSTALL;
                break;
            case ControlType.REMOVE_TARGET:
                dst.control = LauncherTarget.REMOVE;
                break;
            default:
                dst.control = LauncherTarget.DEFAULT_CONTROL;
                break;
        }

        // Fill in other fields
        dst.pageId = src.pageIndex;
        dst.gridX = src.gridX;
        dst.gridY = src.gridY;
    }

    @Override
    public void verify() {
        if(!(StatsLogUtils.LAUNCHER_STATE_ALLAPPS == ALLAPPS &&
                StatsLogUtils.LAUNCHER_STATE_BACKGROUND == BACKGROUND &&
                StatsLogUtils.LAUNCHER_STATE_OVERVIEW == OVERVIEW &&
                StatsLogUtils.LAUNCHER_STATE_HOME == HOME)) {
            throw new IllegalStateException(
                    "StatsLogUtil constants doesn't match enums in launcher.proto");
        }
    }
}
