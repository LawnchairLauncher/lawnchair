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

import static com.android.launcher3.logging.LoggerUtils.newAction;
import static com.android.launcher3.logging.LoggerUtils.newCommandAction;
import static com.android.launcher3.logging.LoggerUtils.newContainerTarget;
import static com.android.launcher3.logging.LoggerUtils.newControlTarget;
import static com.android.launcher3.logging.LoggerUtils.newDropTarget;
import static com.android.launcher3.logging.LoggerUtils.newItemTarget;
import static com.android.launcher3.logging.LoggerUtils.newLauncherEvent;
import static com.android.launcher3.logging.LoggerUtils.newTarget;
import static com.android.launcher3.logging.LoggerUtils.newTouchAction;
import static com.android.launcher3.userevent.nano.LauncherLogProto.ControlType;
import static com.android.launcher3.userevent.nano.LauncherLogProto.ItemType;
import static com.android.launcher3.userevent.nano.LauncherLogProto.TipType;

import static java.util.Optional.ofNullable;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.DropTarget;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.logging.StatsLogUtils.LogContainerProvider;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.userevent.LauncherLogProto;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action;
import com.android.launcher3.userevent.nano.LauncherLogProto.LauncherEvent;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.InstantAppResolver;
import com.android.launcher3.util.LogConfig;
import com.android.launcher3.util.ResourceBasedOverride;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.nano.InvalidProtocolBufferNanoException;
import com.google.protobuf.nano.MessageNano;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Manages the creation of {@link LauncherEvent}.
 * To debug this class, execute following command before side loading a new apk.
 * <p>
 * $ adb shell setprop log.tag.UserEvent VERBOSE
 */
public class UserEventDispatcher implements ResourceBasedOverride {

    private static final String TAG = "UserEvent";
    private static final boolean IS_VERBOSE = Utilities.isPropertyEnabled(LogConfig.USEREVENT);
    private static final String UUID_STORAGE = "uuid";

    /**
     * A factory method for UserEventDispatcher
     */
    public static UserEventDispatcher newInstance(Context context) {
        SharedPreferences sharedPrefs = Utilities.getDevicePrefs(context);
        String uuidStr = sharedPrefs.getString(UUID_STORAGE, null);
        if (uuidStr == null) {
            uuidStr = UUID.randomUUID().toString();
            sharedPrefs.edit().putString(UUID_STORAGE, uuidStr).apply();
        }
        UserEventDispatcher ued = Overrides.getObject(UserEventDispatcher.class,
                context.getApplicationContext(), R.string.user_event_dispatcher_class);
        ued.mUuidStr = uuidStr;
        ued.mInstantAppResolver = InstantAppResolver.newInstance(context);
        return ued;
    }


    /**
     * Fills in the container data on the given event if the given view is not null.
     *
     * @return whether container data was added.
     */
    public boolean fillLogContainer(@Nullable View v, Target child,
            @Nullable ArrayList<Target> targets) {
        LogContainerProvider firstParent = StatsLogUtils.getLaunchProviderRecursive(v);
        if (v == null || !(v.getTag() instanceof ItemInfo) || firstParent == null) {
            return false;
        }
        final ItemInfo itemInfo = (ItemInfo) v.getTag();
        firstParent.fillInLogContainerData(itemInfo, child, targets);
        return true;
    }

    protected void onFillInLogContainerData(@NonNull ItemInfo itemInfo, @NonNull Target target,
            @NonNull ArrayList<Target> targets) {
    }

    private boolean mSessionStarted;
    private long mElapsedContainerMillis;
    private long mElapsedSessionMillis;
    private long mActionDurationMillis;
    private String mUuidStr;
    protected InstantAppResolver mInstantAppResolver;
    private boolean mAppOrTaskLaunch;
    private boolean mPreviousHomeGesture;

    //                      APP_ICON    SHORTCUT    WIDGET
    // --------------------------------------------------------------
    // packageNameHash      required    optional    required
    // componentNameHash    required                required
    // intentHash                       required
    // --------------------------------------------------------------

    @Deprecated
    public void logAppLaunch(View v, Intent intent, @Nullable UserHandle userHandle) {
        Target itemTarget = newItemTarget(v, mInstantAppResolver);
        Action action = newTouchAction(Action.Touch.TAP);
        ArrayList<Target> targets = makeTargetsList(itemTarget);
        if (fillLogContainer(v, itemTarget, targets)) {
            onFillInLogContainerData((ItemInfo) v.getTag(), itemTarget, targets);
            fillIntentInfo(itemTarget, intent, userHandle);
        }
        LauncherEvent event = newLauncherEvent(action,  targets);
        dispatchUserEvent(event, intent);
        mAppOrTaskLaunch = true;
    }

    /**
     * Dummy method.
     */
    public void logActionTip(int actionType, int viewType) {
    }

    @Deprecated
    public void logTaskLaunchOrDismiss(int action, int direction, int taskIndex,
            ComponentKey componentKey) {
        LauncherEvent event = newLauncherEvent(newTouchAction(action), // TAP or SWIPE or FLING
                newTarget(Target.Type.ITEM));
        if (action == Action.Touch.SWIPE || action == Action.Touch.FLING) {
            // Direction DOWN means the task was launched, UP means it was dismissed.
            event.action.dir = direction;
        }
        event.srcTarget[0].itemType = ItemType.TASK;
        event.srcTarget[0].pageIndex = taskIndex;
        fillComponentInfo(event.srcTarget[0], componentKey.componentName);
        dispatchUserEvent(event, null);
        mAppOrTaskLaunch = true;
    }

    protected void fillIntentInfo(Target target, Intent intent, @Nullable UserHandle userHandle) {
        target.intentHash = intent.hashCode();
        target.isWorkApp = userHandle != null && !userHandle.equals(Process.myUserHandle());
        fillComponentInfo(target, intent.getComponent());
    }

    private void fillComponentInfo(Target target, ComponentName cn) {
        if (cn != null) {
            target.packageNameHash = (mUuidStr + cn.getPackageName()).hashCode();
            target.componentHash = (mUuidStr + cn.flattenToString()).hashCode();
        }
    }

    public void logNotificationLaunch(View v, PendingIntent intent) {
        LauncherEvent event = newLauncherEvent(newTouchAction(Action.Touch.TAP),
                newItemTarget(v, mInstantAppResolver), newTarget(Target.Type.CONTAINER));
        Target itemTarget = newItemTarget(v, mInstantAppResolver);
        ArrayList<Target> targets = makeTargetsList(itemTarget);

        if (fillLogContainer(v, itemTarget, targets)) {
            itemTarget.packageNameHash = (mUuidStr + intent.getCreatorPackage()).hashCode();
        }
        dispatchUserEvent(event, null);
    }

    public void logActionCommand(int command, Target srcTarget) {
        logActionCommand(command, srcTarget, null);
    }

    public void logActionCommand(int command, int srcContainerType, int dstContainerType) {
        logActionCommand(command, newContainerTarget(srcContainerType),
                dstContainerType >= 0 ? newContainerTarget(dstContainerType) : null);
    }

    public void logActionCommand(int command, int srcContainerType, int dstContainerType,
            int pageIndex) {
        Target srcTarget = newContainerTarget(srcContainerType);
        srcTarget.pageIndex = pageIndex;
        logActionCommand(command, srcTarget,
                dstContainerType >= 0 ? newContainerTarget(dstContainerType) : null);
    }

    public void logActionCommand(int command, Target srcTarget, Target dstTarget) {
        LauncherEvent event = newLauncherEvent(newCommandAction(command), srcTarget);
        if (command == Action.Command.STOP) {
            if (mAppOrTaskLaunch || !mSessionStarted) {
                mSessionStarted = false;
                return;
            }
        }

        if (dstTarget != null) {
            event.destTarget = new Target[1];
            event.destTarget[0] = dstTarget;
            event.action.isStateChange = true;
        }
        dispatchUserEvent(event, null);
    }

    /**
     * TODO: Make this function work when a container view is passed as the 2nd param.
     */
    public void logActionCommand(int command, View itemView, int srcContainerType) {
        LauncherEvent event = newLauncherEvent(newCommandAction(command),
                newItemTarget(itemView, mInstantAppResolver), newTarget(Target.Type.CONTAINER));

        Target itemTarget = newItemTarget(itemView, mInstantAppResolver);
        ArrayList<Target> targets = makeTargetsList(itemTarget);

        if (fillLogContainer(itemView, itemTarget, targets)) {
            // TODO: Remove the following two lines once fillInLogContainerData can take in a
            // container view.
            itemTarget.type = Target.Type.CONTAINER;
            itemTarget.containerType = srcContainerType;
        }
        dispatchUserEvent(event, null);
    }

    public void logActionOnControl(int action, int controlType) {
        logActionOnControl(action, controlType, null);
    }

    public void logActionOnControl(int action, int controlType, int parentContainerType) {
        logActionOnControl(action, controlType, null, parentContainerType);
    }

    /**
     * Logs control action with proper parent hierarchy
     */
    public void logActionOnControl(int actionType, int controlType,
            @Nullable View controlInContainer, int... parentTypes) {
        Target control = newTarget(Target.Type.CONTROL);
        control.controlType = controlType;
        Action action = newAction(actionType);

        ArrayList<Target> targets = makeTargetsList(control);
        if (controlInContainer != null) {
            fillLogContainer(controlInContainer, control, targets);
        }
        for (int parentContainerType : parentTypes) {
            if (parentContainerType < 0) continue;
            targets.add(newContainerTarget(parentContainerType));
        }
        LauncherEvent event = newLauncherEvent(action, targets);
        if (actionType == Action.Touch.DRAGDROP) {
            event.actionDurationMillis = SystemClock.uptimeMillis() - mActionDurationMillis;
        }
        dispatchUserEvent(event, null);
    }

    public void logActionTapOutside(Target target) {
        LauncherEvent event = newLauncherEvent(newTouchAction(Action.Type.TOUCH),
                target);
        event.action.isOutside = true;
        dispatchUserEvent(event, null);
    }

    public void logActionBounceTip(int containerType) {
        LauncherEvent event = newLauncherEvent(newAction(Action.Type.TIP),
                newContainerTarget(containerType));
        event.srcTarget[0].tipType = TipType.BOUNCE;
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

    /**
     * Used primarily for swipe up and down when state changes when swipe up happens from the
     * navbar bezel, the {@param srcChildContainerType} is NAVBAR and
     * {@param srcParentContainerType} is either one of the two
     * (1) WORKSPACE: if the launcher is the foreground activity
     * (2) APP: if another app was the foreground activity
     */
    public void logStateChangeAction(int action, int dir, int downX, int downY,
            int srcChildTargetType, int srcParentContainerType, int dstContainerType,
            int pageIndex) {
        LauncherEvent event;
        if (srcChildTargetType == ItemType.TASK) {
            event = newLauncherEvent(newTouchAction(action),
                    newItemTarget(srcChildTargetType),
                    newContainerTarget(srcParentContainerType));
        } else {
            event = newLauncherEvent(newTouchAction(action),
                    newContainerTarget(srcChildTargetType),
                    newContainerTarget(srcParentContainerType));
        }
        event.destTarget = new Target[1];
        event.destTarget[0] = newContainerTarget(dstContainerType);
        event.action.dir = dir;
        event.action.isStateChange = true;
        event.srcTarget[0].pageIndex = pageIndex;
        event.srcTarget[0].spanX = downX;
        event.srcTarget[0].spanY = downY;
        dispatchUserEvent(event, null);
        resetElapsedContainerMillis("state changed");
    }

    public void logActionOnItem(int action, int dir, int itemType) {
        logActionOnItem(action, dir, itemType, null, null);
    }

    /**
     * Creates new {@link LauncherEvent} of ITEM target type with input arguments and dispatches it.
     *
     * @param touchAction ENUM value of {@link LauncherLogProto.Action.Touch} Action
     * @param dir         ENUM value of {@link LauncherLogProto.Action.Direction} Action
     * @param itemType    ENUM value of {@link LauncherLogProto.ItemType}
     * @param gridX       Nullable X coordinate of item's position on the workspace grid
     * @param gridY       Nullable Y coordinate of item's position on the workspace grid
     */
    public void logActionOnItem(int touchAction, int dir, int itemType,
            @Nullable Integer gridX, @Nullable Integer gridY) {
        Target itemTarget = newTarget(Target.Type.ITEM);
        itemTarget.itemType = itemType;
        ofNullable(gridX).ifPresent(value -> itemTarget.gridX = value);
        ofNullable(gridY).ifPresent(value -> itemTarget.gridY = value);
        LauncherEvent event = newLauncherEvent(newTouchAction(touchAction), itemTarget);
        event.action.dir = dir;
        dispatchUserEvent(event, null);
    }

    /**
     * Logs proto lite version of LauncherEvent object to clearcut.
     */
    public void logLauncherEvent(
            com.android.launcher3.userevent.LauncherLogProto.LauncherEvent launcherEvent) {

        if (mPreviousHomeGesture) {
            mPreviousHomeGesture = false;
        }
        mAppOrTaskLaunch = false;
        launcherEvent.toBuilder()
                .setElapsedContainerMillis(SystemClock.uptimeMillis() - mElapsedContainerMillis)
                .setElapsedSessionMillis(
                        SystemClock.uptimeMillis() - mElapsedSessionMillis).build();
        try {
            dispatchUserEvent(LauncherEvent.parseFrom(launcherEvent.toByteArray()), null);
        } catch (InvalidProtocolBufferNanoException e) {
            throw new RuntimeException("Cannot convert LauncherEvent from Lite to Nano version.");
        }
    }

    public void logDeepShortcutsOpen(View icon) {
        ItemInfo info = (ItemInfo) icon.getTag();
        Target child = newItemTarget(info, mInstantAppResolver);
        ArrayList<Target> targets = makeTargetsList(child);
        fillLogContainer(icon, child, targets);
        dispatchUserEvent(newLauncherEvent(newTouchAction(Action.Touch.TAP), targets), null);
        resetElapsedContainerMillis("deep shortcut open");
    }

    public void logDragNDrop(DropTarget.DragObject dragObj, View dropTargetAsView) {
        Target srcChild = newItemTarget(dragObj.originalDragInfo, mInstantAppResolver);
        ArrayList<Target> srcTargets = makeTargetsList(srcChild);


        Target destChild = newItemTarget(dragObj.originalDragInfo, mInstantAppResolver);
        ArrayList<Target> destTargets = makeTargetsList(destChild);

        dragObj.dragSource.fillInLogContainerData(dragObj.originalDragInfo, srcChild, srcTargets);
        if (dropTargetAsView instanceof LogContainerProvider) {
            ((LogContainerProvider) dropTargetAsView).fillInLogContainerData(dragObj.dragInfo,
                    destChild, destTargets);
        }
        else {
            destTargets.add(newDropTarget(dropTargetAsView));
        }
        LauncherEvent event = newLauncherEvent(newTouchAction(Action.Touch.DRAGDROP), srcTargets);
        Target[] destTargetsArray = new Target[destTargets.size()];
        destTargets.toArray(destTargetsArray);
        event.destTarget = destTargetsArray;

        event.actionDurationMillis = SystemClock.uptimeMillis() - mActionDurationMillis;
        dispatchUserEvent(event, null);
    }

    public void logActionBack(boolean completed, int downX, int downY, boolean isButton,
            boolean gestureSwipeLeft, int containerType) {
        int actionTouch = isButton ? Action.Touch.TAP : Action.Touch.SWIPE;
        Action action = newCommandAction(actionTouch);
        action.command = Action.Command.BACK;
        action.dir = isButton ? Action.Direction.NONE :
                gestureSwipeLeft ? Action.Direction.LEFT : Action.Direction.RIGHT;
        Target target = newControlTarget(isButton ? ControlType.BACK_BUTTON :
                ControlType.BACK_GESTURE);
        target.spanX = downX;
        target.spanY = downY;
        target.cardinality = completed ? 1 : 0;
        LauncherEvent event = newLauncherEvent(action, target, newContainerTarget(containerType));

        dispatchUserEvent(event, null);
    }

    /**
     * Currently logs following containers: workspace, allapps, widget tray.
     */
    public final void resetElapsedContainerMillis(String reason) {
        mElapsedContainerMillis = SystemClock.uptimeMillis();
        if (!IS_VERBOSE) {
            return;
        }
        Log.d(TAG, "resetElapsedContainerMillis reason=" + reason);

    }

    public final void startSession() {
        mSessionStarted = true;
        mElapsedSessionMillis = SystemClock.uptimeMillis();
        mElapsedContainerMillis = SystemClock.uptimeMillis();
    }

    public final void setPreviousHomeGesture(boolean homeGesture) {
        mPreviousHomeGesture = homeGesture;
    }

    public final boolean isPreviousHomeGesture() {
        return mPreviousHomeGesture;
    }

    public final void resetActionDurationMillis() {
        mActionDurationMillis = SystemClock.uptimeMillis();
    }

    public void dispatchUserEvent(LauncherEvent ev, Intent intent) {
        if (mPreviousHomeGesture) {
            mPreviousHomeGesture = false;
        }
        mAppOrTaskLaunch = false;
        ev.elapsedContainerMillis = SystemClock.uptimeMillis() - mElapsedContainerMillis;
        ev.elapsedSessionMillis = SystemClock.uptimeMillis() - mElapsedSessionMillis;
        if (!IS_VERBOSE) {
            return;
        }
        LauncherLogProto.LauncherEvent liteLauncherEvent;
        try {
            liteLauncherEvent =
                    LauncherLogProto.LauncherEvent.parseFrom(MessageNano.toByteArray(ev));
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException("Cannot parse LauncherEvent from Nano to Lite version");
        }
        Log.d(TAG, liteLauncherEvent.toString());
    }

    /**
     * Constructs an ArrayList with targets
     */
    public static ArrayList<Target> makeTargetsList(Target... targets) {
        ArrayList<Target> result = new ArrayList<>();
        for (Target target : targets) {
            result.add(target);
        }
        return result;
    }
}
