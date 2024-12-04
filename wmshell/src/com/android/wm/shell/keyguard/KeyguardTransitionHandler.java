/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.keyguard;

import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_DREAM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.service.dreams.Flags.dismissDreamOnKeyguardDismiss;
import static android.view.WindowManager.KEYGUARD_VISIBILITY_TRANSIT_FLAGS;
import static android.view.WindowManager.TRANSIT_FLAG_KEYGUARD_APPEARING;
import static android.view.WindowManager.TRANSIT_FLAG_KEYGUARD_GOING_AWAY;
import static android.view.WindowManager.TRANSIT_FLAG_KEYGUARD_LOCKED;
import static android.view.WindowManager.TRANSIT_FLAG_KEYGUARD_OCCLUDING;
import static android.view.WindowManager.TRANSIT_FLAG_KEYGUARD_UNOCCLUDING;
import static android.view.WindowManager.TRANSIT_SLEEP;

import static com.android.wm.shell.shared.TransitionUtil.isOpeningType;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.Log;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.IRemoteTransition;
import android.window.IRemoteTransitionFinishedCallback;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import com.android.internal.protolog.common.ProtoLog;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.TaskStackListenerCallback;
import com.android.wm.shell.common.TaskStackListenerImpl;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.shared.annotations.ExternalThread;
import com.android.wm.shell.sysui.KeyguardChangeListener;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.Transitions;
import com.android.wm.shell.transition.Transitions.TransitionFinishCallback;

/**
 * The handler for Keyguard enter/exit and occlude/unocclude animations.
 *
 * <p>This takes the highest priority.
 */
public class KeyguardTransitionHandler
        implements Transitions.TransitionHandler, KeyguardChangeListener,
        TaskStackListenerCallback {
    private static final String TAG = "KeyguardTransition";

    private final Transitions mTransitions;
    private final ShellController mShellController;
    private final Handler mMainHandler;
    private final ShellExecutor mMainExecutor;

    private final ArrayMap<IBinder, StartedTransition> mStartedTransitions = new ArrayMap<>();
    private final TaskStackListenerImpl mTaskStackListener;

    /**
     * Local IRemoteTransition implementations registered by the keyguard service.
     * @see KeyguardTransitions
     */
    private IRemoteTransition mExitTransition = null;
    private IRemoteTransition mAppearTransition = null;
    private IRemoteTransition mOccludeTransition = null;
    private IRemoteTransition mOccludeByDreamTransition = null;
    private IRemoteTransition mUnoccludeTransition = null;

    // While set true, Keyguard has created a remote animation runner to handle the open app
    // transition.
    private boolean mIsLaunchingActivityOverLockscreen;

    // Last value reported by {@link KeyguardChangeListener}.
    private boolean mKeyguardShowing = true;
    @Nullable
    private WindowContainerToken mDreamToken;

    private final class StartedTransition {
        final TransitionInfo mInfo;
        final SurfaceControl.Transaction mFinishT;
        final IRemoteTransition mPlayer;

        public StartedTransition(TransitionInfo info,
                SurfaceControl.Transaction finishT, IRemoteTransition player) {
            mInfo = info;
            mFinishT = finishT;
            mPlayer = player;
        }
    }

    public KeyguardTransitionHandler(
            @NonNull ShellInit shellInit,
            @NonNull ShellController shellController,
            @NonNull Transitions transitions,
            @NonNull TaskStackListenerImpl taskStackListener,
            @NonNull Handler mainHandler,
            @NonNull ShellExecutor mainExecutor) {
        mTransitions = transitions;
        mShellController = shellController;
        mMainHandler = mainHandler;
        mMainExecutor = mainExecutor;
        mTaskStackListener = taskStackListener;
        shellInit.addInitCallback(this::onInit, this);
    }

    private void onInit() {
        mTransitions.addHandler(this);
        mShellController.addKeyguardChangeListener(this);
        if (dismissDreamOnKeyguardDismiss()) {
            mTaskStackListener.addListener(this);
        }
    }

    /**
     * Interface for SystemUI implementations to set custom Keyguard exit/occlude handlers.
     */
    @ExternalThread
    public KeyguardTransitions asKeyguardTransitions() {
        return new KeyguardTransitionsImpl();
    }

    public static boolean handles(TransitionInfo info) {
        // There is no animation for screen-wake unless we are immediately unlocking.
        if (info.getType() == WindowManager.TRANSIT_WAKE && !info.isKeyguardGoingAway()) {
            return false;
        }
        return (info.getFlags() & KEYGUARD_VISIBILITY_TRANSIT_FLAGS) != 0;
    }

    @Override
    public void onKeyguardVisibilityChanged(
            boolean visible, boolean occluded, boolean animatingDismiss) {
        mKeyguardShowing = visible;
    }

    public boolean isKeyguardShowing() {
        return mKeyguardShowing;
    }

    @Override
    public void onTaskMovedToFront(ActivityManager.RunningTaskInfo taskInfo) {
        mDreamToken = taskInfo.getActivityType() == ACTIVITY_TYPE_DREAM ? taskInfo.token : null;
    }

    @Override
    public boolean startAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull TransitionFinishCallback finishCallback) {
        if (!handles(info) || mIsLaunchingActivityOverLockscreen) {
            return false;
        }

        // Choose a transition applicable for the changes and keyguard state.
        if ((info.getFlags() & TRANSIT_FLAG_KEYGUARD_GOING_AWAY) != 0) {
            return startAnimation(mExitTransition, "going-away",
                    transition, info, startTransaction, finishTransaction, finishCallback);
        }

        if ((info.getFlags() & TRANSIT_FLAG_KEYGUARD_APPEARING) != 0) {
            return startAnimation(mAppearTransition, "appearing",
                    transition, info, startTransaction, finishTransaction, finishCallback);
        }


        // Occlude/unocclude animations are only played if the keyguard is locked.
        if ((info.getFlags() & TRANSIT_FLAG_KEYGUARD_LOCKED) != 0) {
            if ((info.getFlags() & TRANSIT_FLAG_KEYGUARD_OCCLUDING) != 0) {
                if (hasOpeningDream(info)) {
                    return startAnimation(mOccludeByDreamTransition, "occlude-by-dream",
                            transition, info, startTransaction, finishTransaction, finishCallback);
                } else {
                    return startAnimation(mOccludeTransition, "occlude",
                            transition, info, startTransaction, finishTransaction, finishCallback);
                }
            } else if ((info.getFlags() & TRANSIT_FLAG_KEYGUARD_UNOCCLUDING) != 0) {
                return startAnimation(mUnoccludeTransition, "unocclude",
                        transition, info, startTransaction, finishTransaction, finishCallback);
            }
        }

        Log.i(TAG, "Refused to play keyguard transition: " + info);
        return false;
    }

    private boolean startAnimation(IRemoteTransition remoteHandler, String description,
            @NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull TransitionFinishCallback finishCallback) {

        if (remoteHandler == null) {
            ProtoLog.e(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                    "missing handler for keyguard %s transition", description);
            return false;
        }

        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                "start keyguard %s transition, info = %s", description, info);
        try {
            mStartedTransitions.put(transition,
                    new StartedTransition(info, finishTransaction, remoteHandler));
            remoteHandler.startAnimation(transition, info, startTransaction,
                    new IRemoteTransitionFinishedCallback.Stub() {
                        @Override
                        public void onTransitionFinished(
                                WindowContainerTransaction wct, SurfaceControl.Transaction sct) {
                            if (sct != null) {
                                finishTransaction.merge(sct);
                            }
                            final WindowContainerTransaction mergedWct =
                                    new WindowContainerTransaction();
                            if (wct != null) {
                                mergedWct.merge(wct, true);
                            }
                            maybeDismissFreeformOccludingKeyguard(mergedWct, info);
                            // Post our finish callback to let startAnimation finish first.
                            mMainExecutor.executeDelayed(() -> {
                                mStartedTransitions.remove(transition);
                                finishCallback.onTransitionFinished(mergedWct);
                            }, 0);
                        }
                    });
        } catch (RemoteException e) {
            Log.wtf(TAG, "RemoteException thrown from local IRemoteTransition", e);
            return false;
        }
        startTransaction.clear();
        return true;
    }

    @Override
    public void mergeAnimation(@NonNull IBinder nextTransition, @NonNull TransitionInfo nextInfo,
            @NonNull SurfaceControl.Transaction nextT, @NonNull IBinder currentTransition,
            @NonNull TransitionFinishCallback nextFinishCallback) {
        final StartedTransition playing = mStartedTransitions.get(currentTransition);
        if (playing == null) {
            ProtoLog.e(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                    "unknown keyguard transition %s", currentTransition);
            return;
        }
        if ((nextInfo.getFlags() & WindowManager.TRANSIT_FLAG_KEYGUARD_APPEARING) != 0
                && (playing.mInfo.getFlags() & TRANSIT_FLAG_KEYGUARD_GOING_AWAY) != 0) {
            // Keyguard unlocking has been canceled. Merge the unlock and re-lock transitions to
            // avoid a flicker where we flash one frame with the screen fully unlocked.
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                    "canceling keyguard exit transition %s", currentTransition);
            playing.mFinishT.merge(nextT);
            try {
                playing.mPlayer.mergeAnimation(nextTransition, nextInfo, nextT, currentTransition,
                        new FakeFinishCallback());
            } catch (RemoteException e) {
                // There is no good reason for this to happen because the player is a local object
                // implementing an AIDL interface.
                Log.wtf(TAG, "RemoteException thrown from KeyguardService transition", e);
            }
            nextFinishCallback.onTransitionFinished(null);
        } else {
            // In all other cases, fast-forward to let the next queued transition start playing.
            finishAnimationImmediately(currentTransition, playing);
        }
    }

    @Override
    public void onTransitionConsumed(IBinder transition, boolean aborted,
            SurfaceControl.Transaction finishTransaction) {
        final StartedTransition playing = mStartedTransitions.remove(transition);
        if (playing != null) {
            finishAnimationImmediately(transition, playing);
        }
    }

    @Nullable
    @Override
    public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
            @NonNull TransitionRequestInfo request) {
        if (dismissDreamOnKeyguardDismiss()
                && (request.getFlags() & TRANSIT_FLAG_KEYGUARD_GOING_AWAY) != 0
                && mDreamToken != null) {
            // Dismiss the dream in the same transaction, so that it isn't visible once the device
            // is unlocked.
            return new WindowContainerTransaction().removeTask(mDreamToken);
        }
        return null;
    }

    private static boolean hasOpeningDream(@NonNull TransitionInfo info) {
        for (int i = info.getChanges().size() - 1; i >= 0; i--) {
            final TransitionInfo.Change change = info.getChanges().get(i);
            if (isOpeningType(change.getMode())
                    && change.getTaskInfo() != null
                    && change.getTaskInfo().getActivityType() == ACTIVITY_TYPE_DREAM) {
                return true;
            }
        }
        return false;
    }

    private void finishAnimationImmediately(IBinder transition, StartedTransition playing) {
        final IBinder fakeTransition = new Binder();
        final TransitionInfo fakeInfo = new TransitionInfo(TRANSIT_SLEEP, 0x0);
        final SurfaceControl.Transaction fakeT = new SurfaceControl.Transaction();
        final FakeFinishCallback fakeFinishCb = new FakeFinishCallback();
        try {
            playing.mPlayer.mergeAnimation(
                    fakeTransition, fakeInfo, fakeT, transition, fakeFinishCb);
        } catch (RemoteException e) {
            // There is no good reason for this to happen because the player is a local object
            // implementing an AIDL interface.
            Log.wtf(TAG, "RemoteException thrown from KeyguardService transition", e);
        }
    }

    private void maybeDismissFreeformOccludingKeyguard(
            WindowContainerTransaction wct, TransitionInfo info) {
        if ((info.getFlags() & TRANSIT_FLAG_KEYGUARD_OCCLUDING) == 0) {
            return;
        }
        // There's a window occluding the Keyguard, find it and if it's in freeform mode, change it
        // to fullscreen.
        for (int i = 0; i < info.getChanges().size(); i++) {
            final TransitionInfo.Change change = info.getChanges().get(i);
            final ActivityManager.RunningTaskInfo taskInfo = change.getTaskInfo();
            if (taskInfo != null && taskInfo.taskId != INVALID_TASK_ID
                    && taskInfo.getWindowingMode() == WINDOWING_MODE_FREEFORM
                    && taskInfo.isFocused && change.getContainer() != null) {
                wct.setWindowingMode(change.getContainer(), WINDOWING_MODE_FULLSCREEN);
                wct.setBounds(change.getContainer(), null);
                return;
            }
        }
    }

    private static class FakeFinishCallback extends IRemoteTransitionFinishedCallback.Stub {
        @Override
        public void onTransitionFinished(
                WindowContainerTransaction wct, SurfaceControl.Transaction t) {
            return;
        }
    }

    @ExternalThread
    private final class KeyguardTransitionsImpl implements KeyguardTransitions {
        @Override
        public void register(
                IRemoteTransition exitTransition,
                IRemoteTransition appearTransition,
                IRemoteTransition occludeTransition,
                IRemoteTransition occludeByDreamTransition,
                IRemoteTransition unoccludeTransition) {
            mMainExecutor.execute(() -> {
                mExitTransition = exitTransition;
                mAppearTransition = appearTransition;
                mOccludeTransition = occludeTransition;
                mOccludeByDreamTransition = occludeByDreamTransition;
                mUnoccludeTransition = unoccludeTransition;
            });
        }

        @Override
        public void setLaunchingActivityOverLockscreen(boolean isLaunchingActivityOverLockscreen) {
            mMainExecutor.execute(() ->
                    mIsLaunchingActivityOverLockscreen = isLaunchingActivityOverLockscreen);
        }
    }
}
