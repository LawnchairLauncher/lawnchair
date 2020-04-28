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

import static java.util.Optional.ofNullable;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.DropTarget;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.logging.StatsLogUtils.LogContainerProvider;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action;
import com.android.launcher3.userevent.nano.LauncherLogProto.LauncherEvent;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.InstantAppResolver;
import com.android.launcher3.util.LogConfig;
import com.android.launcher3.util.ResourceBasedOverride;

import java.util.Locale;
import java.util.UUID;

/**
 * Manages the creation of {@link LauncherEvent}.
 * To debug this class, execute following command before side loading a new apk.
 * <p>
 * $ adb shell setprop log.tag.UserEvent VERBOSE
 */
public class UserEventDispatcher implements ResourceBasedOverride {

    private static final String TAG = "UserEvent";
    private static final boolean IS_VERBOSE =
            FeatureFlags.IS_DOGFOOD_BUILD && Utilities.isPropertyEnabled(LogConfig.USEREVENT);
    private static final String UUID_STORAGE = "uuid";

    public static UserEventDispatcher newInstance(Context context,
            UserEventDelegate delegate) {
        SharedPreferences sharedPrefs = Utilities.getDevicePrefs(context);
        String uuidStr = sharedPrefs.getString(UUID_STORAGE, null);
        if (uuidStr == null) {
            uuidStr = UUID.randomUUID().toString();
            sharedPrefs.edit().putString(UUID_STORAGE, uuidStr).apply();
        }
        UserEventDispatcher ued = Overrides.getObject(UserEventDispatcher.class,
                context.getApplicationContext(), R.string.user_event_dispatcher_class);
        ued.mDelegate = delegate;
        ued.mUuidStr = uuidStr;
        ued.mInstantAppResolver = InstantAppResolver.newInstance(context);
        return ued;
    }

    public static UserEventDispatcher newInstance(Context context) {
        return newInstance(context, null);
    }

    public interface UserEventDelegate {
        void modifyUserEvent(LauncherEvent event);
    }

    /**
     * Fills in the container data on the given event if the given view is not null.
     *
     * @return whether container data was added.
     */
    public boolean fillInLogContainerData(LauncherLogProto.LauncherEvent event, @Nullable View v) {
        // Fill in grid(x,y), pageIndex of the child and container type of the parent
        LogContainerProvider provider = StatsLogUtils.getLaunchProviderRecursive(v);
        if (v == null || !(v.getTag() instanceof ItemInfo) || provider == null) {
            return false;
        }
        final ItemInfo itemInfo = (ItemInfo) v.getTag();
        final Target target = event.srcTarget[0];
        final Target targetParent = event.srcTarget[1];
        onFillInLogContainerData(itemInfo, target, targetParent);
        provider.fillInLogContainerData(v, itemInfo, target, targetParent);
        return true;
    }

    protected void onFillInLogContainerData(
            @NonNull ItemInfo itemInfo, @NonNull Target target, @NonNull Target targetParent) { }

    private boolean mSessionStarted;
    private long mElapsedContainerMillis;
    private long mElapsedSessionMillis;
    private long mActionDurationMillis;
    private String mUuidStr;
    protected InstantAppResolver mInstantAppResolver;
    private boolean mAppOrTaskLaunch;
    private UserEventDelegate mDelegate;
    private boolean mPreviousHomeGesture;

    //                      APP_ICON    SHORTCUT    WIDGET
    // --------------------------------------------------------------
    // packageNameHash      required    optional    required
    // componentNameHash    required                required
    // intentHash                       required
    // --------------------------------------------------------------

    @Deprecated
    public void logAppLaunch(View v, Intent intent) {
        LauncherEvent event = newLauncherEvent(newTouchAction(Action.Touch.TAP),
                newItemTarget(v, mInstantAppResolver), newTarget(Target.Type.CONTAINER));

        if (fillInLogContainerData(event, v)) {
            if (mDelegate != null) {
                mDelegate.modifyUserEvent(event);
            }
            fillIntentInfo(event.srcTarget[0], intent);
        }
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
        event.srcTarget[0].itemType = LauncherLogProto.ItemType.TASK;
        event.srcTarget[0].pageIndex = taskIndex;
        fillComponentInfo(event.srcTarget[0], componentKey.componentName);
        dispatchUserEvent(event, null);
        mAppOrTaskLaunch = true;
    }

    protected void fillIntentInfo(Target target, Intent intent) {
        target.intentHash = intent.hashCode();
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
        if (fillInLogContainerData(event, v)) {
            event.srcTarget[0].packageNameHash = (mUuidStr + intent.getCreatorPackage()).hashCode();
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

        if (fillInLogContainerData(event, itemView)) {
            // TODO: Remove the following two lines once fillInLogContainerData can take in a
            // container view.
            event.srcTarget[0].type = Target.Type.CONTAINER;
            event.srcTarget[0].containerType = srcContainerType;
        }
        dispatchUserEvent(event, null);
    }

    public void logActionOnControl(int action, int controlType) {
        logActionOnControl(action, controlType, null, -1);
    }

    public void logActionOnControl(int action, int controlType, int parentContainerType) {
        logActionOnControl(action, controlType, null, parentContainerType);
    }

    public void logActionOnControl(int action, int controlType, @Nullable View controlInContainer) {
        logActionOnControl(action, controlType, controlInContainer, -1);
    }

    public void logActionOnControl(int action, int controlType, int parentContainer,
            int grandParentContainer) {
        LauncherEvent event = newLauncherEvent(newTouchAction(action),
                newControlTarget(controlType),
                newContainerTarget(parentContainer),
                newContainerTarget(grandParentContainer));
        dispatchUserEvent(event, null);
    }

    public void logActionOnControl(int action, int controlType, @Nullable View controlInContainer,
            int parentContainerType) {
        final LauncherEvent event = (controlInContainer == null && parentContainerType < 0)
                ? newLauncherEvent(newTouchAction(action), newTarget(Target.Type.CONTROL))
                : newLauncherEvent(newTouchAction(action), newTarget(Target.Type.CONTROL),
                newTarget(Target.Type.CONTAINER));
        event.srcTarget[0].controlType = controlType;
        if (controlInContainer != null) {
            fillInLogContainerData(event, controlInContainer);
        }
        if (parentContainerType >= 0) {
            event.srcTarget[1].containerType = parentContainerType;
        }
        if (action == Action.Touch.DRAGDROP) {
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
        event.srcTarget[0].tipType = LauncherLogProto.TipType.BOUNCE;
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
        if (srcChildTargetType == LauncherLogProto.ItemType.TASK) {
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

    public void logDeepShortcutsOpen(View icon) {
        LogContainerProvider provider = StatsLogUtils.getLaunchProviderRecursive(icon);
        if (icon == null || !(icon.getTag() instanceof ItemInfo || provider == null)) {
            return;
        }
        ItemInfo info = (ItemInfo) icon.getTag();
        LauncherEvent event = newLauncherEvent(newTouchAction(Action.Touch.LONGPRESS),
                newItemTarget(info, mInstantAppResolver), newTarget(Target.Type.CONTAINER));
        provider.fillInLogContainerData(icon, info, event.srcTarget[0], event.srcTarget[1]);
        dispatchUserEvent(event, null);

        resetElapsedContainerMillis("deep shortcut open");
    }

    public void logDragNDrop(DropTarget.DragObject dragObj, View dropTargetAsView) {
        LauncherEvent event = newLauncherEvent(newTouchAction(Action.Touch.DRAGDROP),
                newItemTarget(dragObj.originalDragInfo, mInstantAppResolver),
                newTarget(Target.Type.CONTAINER));
        event.destTarget = new Target[]{
                newItemTarget(dragObj.originalDragInfo, mInstantAppResolver),
                newDropTarget(dropTargetAsView)
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

    public void logActionBack(boolean completed, int downX, int downY, boolean isButton,
            boolean gestureSwipeLeft, int containerType) {
        int actionTouch = isButton ? Action.Touch.TAP : Action.Touch.SWIPE;
        Action action = newCommandAction(actionTouch);
        action.command = Action.Command.BACK;
        action.dir = isButton ? Action.Direction.NONE :
                gestureSwipeLeft ? Action.Direction.LEFT : Action.Direction.RIGHT;
        Target target = newControlTarget(isButton ? LauncherLogProto.ControlType.BACK_BUTTON :
                LauncherLogProto.ControlType.BACK_GESTURE);
        target.spanX = downX;
        target.spanY = downY;
        target.cardinality = completed ? 1 : 0;
        LauncherEvent event = newLauncherEvent(action, target, newContainerTarget(containerType));

        dispatchUserEvent(event, null);
    }

    /**
     * Currently logs following containers: workspace, allapps, widget tray.
     *
     * @param reason
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
        Log.d(TAG, generateLog(ev));
    }

    /**
     * Returns a human-readable log for given user event.
     */
    public static String generateLog(LauncherEvent ev) {
        String log = "\n-----------------------------------------------------"
                + "\naction:" + LoggerUtils.getActionStr(ev.action);
        if (ev.srcTarget != null && ev.srcTarget.length > 0) {
            log += "\n Source " + getTargetsStr(ev.srcTarget);
        }
        if (ev.destTarget != null && ev.destTarget.length > 0) {
            log += "\n Destination " + getTargetsStr(ev.destTarget);
        }
        log += String.format(Locale.US,
                "\n Elapsed container %d ms, session %d ms, action %d ms",
                ev.elapsedContainerMillis,
                ev.elapsedSessionMillis,
                ev.actionDurationMillis);
        log += "\n\n";
        return log;
    }

    private static String getTargetsStr(Target[] targets) {
        String result = "child:" + LoggerUtils.getTargetStr(targets[0]);
        for (int i = 1; i < targets.length; i++) {
            result += "\tparent:" + LoggerUtils.getTargetStr(targets[i]);
        }
        return result;
    }
}
