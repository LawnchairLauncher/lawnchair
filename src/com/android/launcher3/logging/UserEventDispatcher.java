/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewParent;

import com.android.launcher3.DropTarget;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.ProviderConfig;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.launcher3.userevent.nano.LauncherLogProto.LauncherEvent;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.LogConfig;

import java.util.List;
import java.util.Locale;

import static com.android.launcher3.logging.LoggerUtils.newCommandAction;
import static com.android.launcher3.logging.LoggerUtils.newContainerTarget;
import static com.android.launcher3.logging.LoggerUtils.newDropTarget;
import static com.android.launcher3.logging.LoggerUtils.newItemTarget;
import static com.android.launcher3.logging.LoggerUtils.newLauncherEvent;
import static com.android.launcher3.logging.LoggerUtils.newTarget;
import static com.android.launcher3.logging.LoggerUtils.newTouchAction;

/**
 * Manages the creation of {@link LauncherEvent}.
 * To debug this class, execute following command before side loading a new apk.
 *
 * $ adb shell setprop log.tag.UserEvent VERBOSE
 */
public class UserEventDispatcher {

    private final static int MAXIMUM_VIEW_HIERARCHY_LEVEL = 5;

    private static final String TAG = "UserEvent";
    private static final boolean IS_VERBOSE =
            ProviderConfig.IS_DOGFOOD_BUILD && Utilities.isPropertyEnabled(LogConfig.USEREVENT);

    public static UserEventDispatcher newInstance(Context context, boolean isInLandscapeMode,
            boolean isInMultiWindowMode) {
        UserEventDispatcher ued = Utilities.getOverrideObject(UserEventDispatcher.class,
                context.getApplicationContext(), R.string.user_event_dispatcher_class);
        ued.mIsInLandscapeMode = isInLandscapeMode;
        ued.mIsInMultiWindowMode = isInMultiWindowMode;
        return ued;
    }

    /**
     * Implemented by containers to provide a container source for a given child.
     */
    public interface LogContainerProvider {

        /**
         * Copies data from the source to the destination proto.
         *
         * @param v            source of the data
         * @param info         source of the data
         * @param target       dest of the data
         * @param targetParent dest of the data
         */
        void fillInLogContainerData(View v, ItemInfo info, Target target, Target targetParent);
    }

    /**
     * Recursively finds the parent of the given child which implements IconLogInfoProvider
     */
    public static LogContainerProvider getLaunchProviderRecursive(View v) {
        ViewParent parent;
        if (v != null) {
            parent = v.getParent();
        } else {
            return null;
        }

        // Optimization to only check up to 5 parents.
        int count = MAXIMUM_VIEW_HIERARCHY_LEVEL;
        while (parent != null && count-- > 0) {
            if (parent instanceof LogContainerProvider) {
                return (LogContainerProvider) parent;
            } else {
                parent = parent.getParent();
            }
        }
        return null;
    }

    private long mElapsedContainerMillis;
    private long mElapsedSessionMillis;
    private long mActionDurationMillis;
    private boolean mIsInMultiWindowMode;
    private boolean mIsInLandscapeMode;

    // Used for filling in predictedRank on {@link Target}s.
    private List<ComponentKey> mPredictedApps;

    //                      APP_ICON    SHORTCUT    WIDGET
    // --------------------------------------------------------------
    // packageNameHash      required    optional    required
    // componentNameHash    required                required
    // intentHash                       required
    // --------------------------------------------------------------

    protected LauncherEvent createLauncherEvent(View v, int intentHashCode, ComponentName cn) {
        LauncherEvent event = newLauncherEvent(newTouchAction(Action.Touch.TAP),
                newItemTarget(v), newTarget(Target.Type.CONTAINER));

        // TODO: make idx percolate up the view hierarchy if needed.
        int idx = 0;
        if (fillInLogContainerData(event, v)) {
            ItemInfo itemInfo = (ItemInfo) v.getTag();
            event.srcTarget[idx].intentHash = intentHashCode;
            if (cn != null) {
                event.srcTarget[idx].packageNameHash = cn.getPackageName().hashCode();
                event.srcTarget[idx].componentHash = cn.hashCode();
                if (mPredictedApps != null) {
                    event.srcTarget[idx].predictedRank = mPredictedApps.indexOf(
                            new ComponentKey(cn, itemInfo.user));
                }
            }
        }
        return event;
    }

    public boolean fillInLogContainerData(LauncherEvent event, View v) {
        // Fill in grid(x,y), pageIndex of the child and container type of the parent
        LogContainerProvider provider = getLaunchProviderRecursive(v);
        if (v == null || !(v.getTag() instanceof ItemInfo) || provider == null) {
            return false;
        }
        ItemInfo itemInfo = (ItemInfo) v.getTag();
        provider.fillInLogContainerData(v, itemInfo, event.srcTarget[0], event.srcTarget[1]);
        return true;
    }

    public void logAppLaunch(View v, Intent intent) {
        LauncherEvent ev = createLauncherEvent(v, intent.hashCode(), intent.getComponent());
        if (ev == null) {
            return;
        }
        dispatchUserEvent(ev, intent);
    }

    public void logNotificationLaunch(View v, PendingIntent intent) {
        ComponentName dummyComponent = new ComponentName(intent.getCreatorPackage(), "--dummy--");
        LauncherEvent ev = createLauncherEvent(v, intent.hashCode(), dummyComponent);
        if (ev == null) {
            return;
        }
        dispatchUserEvent(ev, null);
    }

    public void logActionCommand(int command, int containerType) {
        logActionCommand(command, containerType, 0);
    }

    public void logActionCommand(int command, int containerType, int pageIndex) {
        LauncherEvent event = newLauncherEvent(
                newCommandAction(command), newContainerTarget(containerType));
        event.srcTarget[0].pageIndex = pageIndex;
        dispatchUserEvent(event, null);
    }

    /**
     * TODO: Make this function work when a container view is passed as the 2nd param.
     */
    public void logActionCommand(int command, View itemView, int containerType) {
        LauncherEvent event = newLauncherEvent(newCommandAction(command),
                newItemTarget(itemView), newTarget(Target.Type.CONTAINER));

        if (fillInLogContainerData(event, itemView)) {
            // TODO: Remove the following two lines once fillInLogContainerData can take in a
            // container view.
            event.srcTarget[0].type = Target.Type.CONTAINER;
            event.srcTarget[0].containerType = containerType;
        }
        dispatchUserEvent(event, null);
    }

    public void logActionOnControl(int action, int controlType) {
        LauncherEvent event = newLauncherEvent(
                newTouchAction(action), newTarget(Target.Type.CONTROL));
        event.srcTarget[0].controlType = controlType;
        dispatchUserEvent(event, null);
    }

    public void logActionTapOutside(Target target) {
        LauncherEvent event = newLauncherEvent(newTouchAction(Action.Type.TOUCH),
                target);
        event.action.isOutside = true;
        dispatchUserEvent(event, null);
    }

    public void logActionOnContainer(int action, int dir, int containerType) {
        logActionOnContainer(action, dir, containerType, 0);
    }

    public void logActionOnContainer(int action, int dir, int containerType, int pageIndex) {
        LauncherEvent event = newLauncherEvent(newTouchAction(action),
                newContainerTarget(containerType));
        event.action.dir = dir;
        event.srcTarget[0].pageIndex = pageIndex;
        dispatchUserEvent(event, null);
    }

    public void logActionOnItem(int action, int dir, int itemType) {
        Target itemTarget = newTarget(Target.Type.ITEM);
        itemTarget.itemType = itemType;
        LauncherEvent event = newLauncherEvent(newTouchAction(action), itemTarget);
        event.action.dir = dir;
        dispatchUserEvent(event, null);
    }

    public void logDeepShortcutsOpen(View icon) {
        LogContainerProvider provider = getLaunchProviderRecursive(icon);
        if (icon == null || !(icon.getTag() instanceof ItemInfo)) {
            return;
        }
        ItemInfo info = (ItemInfo) icon.getTag();
        LauncherEvent event = newLauncherEvent(newTouchAction(Action.Touch.LONGPRESS),
                newItemTarget(info), newTarget(Target.Type.CONTAINER));
        provider.fillInLogContainerData(icon, info, event.srcTarget[0], event.srcTarget[1]);
        dispatchUserEvent(event, null);

        resetElapsedContainerMillis();
    }

    public void setPredictedApps(List<ComponentKey> predictedApps) {
        mPredictedApps = predictedApps;
    }

    /* Currently we are only interested in whether this event happens or not and don't
    * care about which screen moves to where. */
    public void logOverviewReorder() {
        LauncherEvent event = newLauncherEvent(newTouchAction(Action.Touch.DRAGDROP),
                newContainerTarget(ContainerType.WORKSPACE),
                newContainerTarget(ContainerType.OVERVIEW));
        dispatchUserEvent(event, null);
    }

    public void logDragNDrop(DropTarget.DragObject dragObj, View dropTargetAsView) {
        LauncherEvent event = newLauncherEvent(newTouchAction(Action.Touch.DRAGDROP),
                newItemTarget(dragObj.originalDragInfo), newTarget(Target.Type.CONTAINER));
        event.destTarget = new Target[] {
                newItemTarget(dragObj.originalDragInfo), newDropTarget(dropTargetAsView)
        };

        dragObj.dragSource.fillInLogContainerData(null, dragObj.originalDragInfo,
                event.srcTarget[0], event.srcTarget[1]);

        if (dropTargetAsView instanceof LogContainerProvider) {
            ((LogContainerProvider) dropTargetAsView).fillInLogContainerData(null,
                    dragObj.dragInfo, event.destTarget[0], event.destTarget[1]);

        }
        event.actionDurationMillis = SystemClock.uptimeMillis() - mActionDurationMillis;
        dispatchUserEvent(event, null);
    }

    /**
     * Currently logs following containers: workspace, allapps, widget tray.
     */
    public final void resetElapsedContainerMillis() {
        mElapsedContainerMillis = SystemClock.uptimeMillis();
    }

    public final void resetElapsedSessionMillis() {
        mElapsedSessionMillis = SystemClock.uptimeMillis();
        mElapsedContainerMillis = SystemClock.uptimeMillis();
    }

    public final void resetActionDurationMillis() {
        mActionDurationMillis = SystemClock.uptimeMillis();
    }

    public void dispatchUserEvent(LauncherEvent ev, Intent intent) {
        ev.isInLandscapeMode = mIsInLandscapeMode;
        ev.isInMultiWindowMode = mIsInMultiWindowMode;
        ev.elapsedContainerMillis = SystemClock.uptimeMillis() - mElapsedContainerMillis;
        ev.elapsedSessionMillis = SystemClock.uptimeMillis() - mElapsedSessionMillis;

        if (!IS_VERBOSE) {
            return;
        }
        String log = "action:" + LoggerUtils.getActionStr(ev.action);
        if (ev.srcTarget != null && ev.srcTarget.length > 0) {
            log += "\n Source " + getTargetsStr(ev.srcTarget);
        }
        if (ev.destTarget != null && ev.destTarget.length > 0) {
            log += "\n Destination " + getTargetsStr(ev.destTarget);
        }
        log += String.format(Locale.US,
                "\n Elapsed container %d ms session %d ms action %d ms",
                ev.elapsedContainerMillis,
                ev.elapsedSessionMillis,
                ev.actionDurationMillis);
        log += "\n isInLandscapeMode " + ev.isInLandscapeMode;
        log += "\n isInMultiWindowMode " + ev.isInMultiWindowMode;
        Log.d(TAG, log);
    }

    private static String getTargetsStr(Target[] targets) {
        return "child:" + LoggerUtils.getTargetStr(targets[0]) +
                (targets.length > 1 ? "\tparent:" + LoggerUtils.getTargetStr(targets[1]) : "");
    }
}
