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
import android.view.View;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.logging.StatsLogUtils;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;
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

    public StatsLogCompatManager(Context context) { }

    @Override
    public void logAppLaunch(View v, Intent intent) {
        LauncherExtension ext = new LauncherExtension();
        ext.srcTarget = new LauncherTarget[SUPPORTED_TARGET_DEPTH];
        int srcState = mStateProvider.getCurrentState();
        fillInLauncherExtension(v, ext);
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
        StatsLogUtils.LogContainerProvider provider = StatsLogUtils.getLaunchProviderRecursive(v);
        if (v == null || !(v.getTag() instanceof ItemInfo) || provider == null) {
            return false;
        }
        ItemInfo itemInfo = (ItemInfo) v.getTag();
        Target child = new Target();
        Target parent = new Target();
        provider.fillInLogContainerData(v, itemInfo, child, parent);
        copy(child, extension.srcTarget[0]);
        copy(parent, extension.srcTarget[1]);
        return true;
    }

    public static boolean fillInLauncherExtensionWithPageId(LauncherExtension ext, int pageId) {
        Target target = new Target();
        target.pageIndex = pageId;
        copy(target, ext.srcTarget[0]);
        return true;
    }

    private static void copy(Target src, LauncherTarget dst) {
        // fill in
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
