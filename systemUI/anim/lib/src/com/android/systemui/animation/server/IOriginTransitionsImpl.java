/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.animation.server;

import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_PREPARE_BACK_NAVIGATION;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.view.WindowManager.TRANSIT_TO_FRONT;

import android.Manifest;
import android.annotation.Nullable;
import android.app.TaskInfo;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.Log;
import android.view.SurfaceControl;
import android.window.IRemoteTransition;
import android.window.IRemoteTransitionFinishedCallback;
import android.window.RemoteTransition;
import android.window.TransitionFilter;
import android.window.TransitionInfo;
import android.window.TransitionInfo.Change;
import android.window.WindowAnimationState;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.IndentingPrintWriter;
import com.android.systemui.animation.shared.IOriginTransitions;
import com.android.wm.shell.shared.ShellTransitions;
import com.android.wm.shell.shared.TransitionUtil;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

/** An implementation of the {@link IOriginTransitions}. */
public class IOriginTransitionsImpl extends IOriginTransitions.Stub {
    private static final String TAG = "OriginTransitions";
    private static final boolean DEBUG = Build.IS_USERDEBUG || Log.isLoggable(TAG, Log.DEBUG);

    private final Object mLock = new Object();
    private final ShellTransitions mShellTransitions;
    private final Context mContext;

    @GuardedBy("mLock")
    private final Map<IBinder, OriginTransitionRecord> mRecords = new ArrayMap<>();

    public IOriginTransitionsImpl(Context context, ShellTransitions shellTransitions) {
        mShellTransitions = shellTransitions;
        mContext = context;
    }

    @Override
    public RemoteTransition makeOriginTransition(
            RemoteTransition launchTransition, RemoteTransition returnTransition)
            throws RemoteException {
        if (DEBUG) {
            Log.d(
                    TAG,
                    "makeOriginTransition: (" + launchTransition + ", " + returnTransition + ")");
        }
        enforceRemoteTransitionPermission();
        synchronized (mLock) {
            OriginTransitionRecord record =
                    new OriginTransitionRecord(launchTransition, returnTransition);
            mRecords.put(record.getToken(), record);
            return record.asLaunchableTransition();
        }
    }

    @Override
    public void cancelOriginTransition(RemoteTransition originTransition) {
        if (DEBUG) {
            Log.d(TAG, "cancelOriginTransition: " + originTransition);
        }
        enforceRemoteTransitionPermission();
        synchronized (mLock) {
            if (!mRecords.containsKey(originTransition.asBinder())) {
                return;
            }
            mRecords.get(originTransition.asBinder()).destroy();
        }
    }

    private void enforceRemoteTransitionPermission() {
        mContext.enforceCallingPermission(
                Manifest.permission.CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS,
                "Missing permission "
                        + Manifest.permission.CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS);
    }

    public void dump(IndentingPrintWriter ipw) {
        ipw.println("IOriginTransitionsImpl");
        ipw.println("Active records:");
        ipw.increaseIndent();
        synchronized (mLock) {
            if (mRecords.isEmpty()) {
                ipw.println("none");
            } else {
                for (OriginTransitionRecord record : mRecords.values()) {
                    record.dump(ipw);
                }
            }
        }
        ipw.decreaseIndent();
    }

    /**
     * An {@link IRemoteTransition} that delegates animation to another {@link IRemoteTransition}
     * and notify callbacks when the transition starts.
     */
    private static class RemoteTransitionDelegate extends IRemoteTransition.Stub {
        private final IRemoteTransition mTransition;
        private final Predicate<TransitionInfo> mOnStarting;
        private final Executor mExecutor;

        RemoteTransitionDelegate(
                Executor executor,
                IRemoteTransition transition,
                Predicate<TransitionInfo> onStarting) {
            mExecutor = executor;
            mTransition = transition;
            mOnStarting = onStarting;
        }

        @Override
        public void startAnimation(
                IBinder token,
                TransitionInfo info,
                SurfaceControl.Transaction t,
                IRemoteTransitionFinishedCallback finishCallback)
                throws RemoteException {
            if (DEBUG) {
                Log.d(TAG, "startAnimation: " + info);
            }
            if (maybeInterceptTransition(info, t, finishCallback)) {
                return;
            }
            mTransition.startAnimation(token, info, t, finishCallback);
        }

        @Override
        public void mergeAnimation(
                IBinder transition,
                TransitionInfo info,
                SurfaceControl.Transaction t,
                IBinder mergeTarget,
                IRemoteTransitionFinishedCallback finishCallback)
                throws RemoteException {
            if (DEBUG) {
                Log.d(TAG, "mergeAnimation: " + info);
            }
            mTransition.mergeAnimation(transition, info, t, mergeTarget, finishCallback);
        }

        @Override
        public void takeOverAnimation(
                IBinder transition,
                TransitionInfo info,
                SurfaceControl.Transaction t,
                IRemoteTransitionFinishedCallback finishCallback,
                WindowAnimationState[] states)
                throws RemoteException {
            if (DEBUG) {
                Log.d(TAG, "takeOverAnimation: " + info);
            }
            if (maybeInterceptTransition(info, t, finishCallback)) {
                return;
            }
            mTransition.takeOverAnimation(transition, info, t, finishCallback, states);
        }

        @Override
        public void onTransitionConsumed(IBinder transition, boolean aborted)
                throws RemoteException {
            if (DEBUG) {
                Log.d(TAG, "onTransitionConsumed: aborted=" + aborted);
            }
            mTransition.onTransitionConsumed(transition, aborted);
        }

        @Override
        public String toString() {
            return "RemoteTransitionDelegate{transition=" + mTransition + "}";
        }

        private boolean maybeInterceptTransition(
                TransitionInfo info,
                SurfaceControl.Transaction t,
                IRemoteTransitionFinishedCallback finishCallback) {
            if (!mOnStarting.test(info)) {
                Log.w(TAG, "Intercepting cancelled transition " + mTransition);
                t.addTransactionCommittedListener(
                                mExecutor,
                                () -> {
                                    try {
                                        finishCallback.onTransitionFinished(null, null);
                                    } catch (RemoteException e) {
                                        Log.e(TAG, "Unable to report finish.", e);
                                    }
                                })
                        .apply();
                return true;
            }
            return false;
        }
    }

    /** A data record containing the origin transition pieces. */
    private class OriginTransitionRecord implements IBinder.DeathRecipient {
        private final RemoteTransition mWrappedLaunchTransition;
        private final RemoteTransition mWrappedReturnTransition;

        @GuardedBy("mLock")
        private boolean mDestroyed;

        OriginTransitionRecord(RemoteTransition launchTransition, RemoteTransition returnTransition)
                throws RemoteException {
            mWrappedLaunchTransition = wrap(launchTransition, this::onLaunchTransitionStarting);
            mWrappedReturnTransition = wrap(returnTransition, this::onReturnTransitionStarting);
            linkToDeath();
        }

        private boolean onLaunchTransitionStarting(TransitionInfo info) {
            synchronized (mLock) {
                if (mDestroyed) {
                    return false;
                }
                TransitionFilter filter =
                        createFilterForReverseTransition(
                                info, /* forPredictiveBackTakeover= */ false);
                if (filter != null) {
                    if (DEBUG) {
                        Log.d(TAG, "Registering filter " + filter);
                    }
                    mShellTransitions.registerRemote(filter, mWrappedReturnTransition);
                }
                TransitionFilter takeoverFilter =
                        createFilterForReverseTransition(
                                info, /* forPredictiveBackTakeover= */ true);
                if (takeoverFilter != null) {
                    if (DEBUG) {
                        Log.d(TAG, "Registering filter for takeover " + takeoverFilter);
                    }
                    mShellTransitions.registerRemoteForTakeover(
                            takeoverFilter, mWrappedReturnTransition);
                }
                return true;
            }
        }

        private boolean onReturnTransitionStarting(TransitionInfo info) {
            synchronized (mLock) {
                if (mDestroyed) {
                    return false;
                }
                // Clean up stuff.
                destroy();
                return true;
            }
        }

        public void destroy() {
            synchronized (mLock) {
                if (mDestroyed) {
                    // Already destroyed.
                    return;
                }
                if (DEBUG) {
                    Log.d(TAG, "Destroying origin transition record " + this);
                }
                mDestroyed = true;
                unlinkToDeath();
                mShellTransitions.unregisterRemote(mWrappedReturnTransition);
                mRecords.remove(getToken());
            }
        }

        private void linkToDeath() throws RemoteException {
            asDelegate(mWrappedLaunchTransition).mTransition.asBinder().linkToDeath(this, 0);
            asDelegate(mWrappedReturnTransition).mTransition.asBinder().linkToDeath(this, 0);
        }

        private void unlinkToDeath() {
            asDelegate(mWrappedLaunchTransition).mTransition.asBinder().unlinkToDeath(this, 0);
            asDelegate(mWrappedReturnTransition).mTransition.asBinder().unlinkToDeath(this, 0);
        }

        public IBinder getToken() {
            return asLaunchableTransition().asBinder();
        }

        public RemoteTransition asLaunchableTransition() {
            return mWrappedLaunchTransition;
        }

        @Override
        public void binderDied() {
            destroy();
        }

        @Override
        public String toString() {
            return "OriginTransitionRecord{launch="
                    + mWrappedReturnTransition
                    + ", return="
                    + mWrappedReturnTransition
                    + "}";
        }

        public void dump(IndentingPrintWriter ipw) {
            synchronized (mLock) {
                ipw.println("OriginTransitionRecord");
                ipw.increaseIndent();
                ipw.println("mDestroyed: " + mDestroyed);
                ipw.println("Launch transition:");
                ipw.increaseIndent();
                ipw.println(mWrappedLaunchTransition);
                ipw.decreaseIndent();
                ipw.println("Return transition:");
                ipw.increaseIndent();
                ipw.println(mWrappedReturnTransition);
                ipw.decreaseIndent();
                ipw.decreaseIndent();
            }
        }

        private static RemoteTransitionDelegate asDelegate(RemoteTransition transition) {
            return (RemoteTransitionDelegate) transition.getRemoteTransition();
        }

        private RemoteTransition wrap(
                RemoteTransition transition, Predicate<TransitionInfo> onStarting) {
            return new RemoteTransition(
                    new RemoteTransitionDelegate(
                            mContext.getMainExecutor(),
                            transition.getRemoteTransition(),
                            onStarting),
                    transition.getDebugName());
        }

        @Nullable
        private static TransitionFilter createFilterForReverseTransition(
                TransitionInfo info, boolean forPredictiveBackTakeover) {
            TaskInfo launchingTaskInfo = null;
            TaskInfo launchedTaskInfo = null;
            ComponentName launchingActivity = null;
            ComponentName launchedActivity = null;
            for (Change change : info.getChanges()) {
                int mode = change.getMode();
                TaskInfo taskInfo = change.getTaskInfo();
                ComponentName activity = change.getActivityComponent();
                if (TransitionUtil.isClosingMode(mode)
                        && launchingTaskInfo == null
                        && taskInfo != null) {
                    // Found the launching task!
                    launchingTaskInfo = taskInfo;
                } else if (TransitionUtil.isOpeningMode(mode)
                        && launchedTaskInfo == null
                        && taskInfo != null) {
                    // Found the launched task!
                    launchedTaskInfo = taskInfo;
                } else if (TransitionUtil.isClosingMode(mode)
                        && launchingActivity == null
                        && activity != null) {
                    // Found the launching activity
                    launchingActivity = activity;
                } else if (TransitionUtil.isOpeningMode(mode)
                        && launchedActivity == null
                        && activity != null) {
                    // Found the launched activity!
                    launchedActivity = activity;
                }
            }
            if (DEBUG) {
                Log.d(
                        TAG,
                        "createFilterForReverseTransition: forPredictiveBackTakeover="
                                + forPredictiveBackTakeover
                                + ", launchingTaskInfo="
                                + launchingTaskInfo
                                + ", launchedTaskInfo="
                                + launchedTaskInfo
                                + ", launchingActivity="
                                + launchedActivity
                                + ", launchedActivity="
                                + launchedActivity);
            }
            if (launchingTaskInfo == null && launchingActivity == null) {
                Log.w(
                        TAG,
                        "createFilterForReverseTransition: unable to find launching task or"
                                + " launching activity!");
                return null;
            }
            if (launchedTaskInfo == null && launchedActivity == null) {
                Log.w(
                        TAG,
                        "createFilterForReverseTransition: unable to find launched task or launched"
                                + " activity!");
                return null;
            }
            if (launchedTaskInfo != null && launchedTaskInfo.launchCookies.isEmpty()) {
                Log.w(
                        TAG,
                        "createFilterForReverseTransition: skipped - launched task has no launch"
                                + " cookie!");
                return null;
            }
            if (forPredictiveBackTakeover && launchedTaskInfo == null) {
                // Predictive back take over currently only support cross-task transition.
                Log.d(
                        TAG,
                        "createFilterForReverseTransition: skipped - unable to find launched task"
                                + " for predictive back takeover");
                return null;
            }
            TransitionFilter filter = new TransitionFilter();
            if (forPredictiveBackTakeover) {
                filter.mTypeSet = new int[] {TRANSIT_PREPARE_BACK_NAVIGATION};
            } else {
                filter.mTypeSet =
                        new int[] {TRANSIT_CLOSE, TRANSIT_TO_BACK, TRANSIT_OPEN, TRANSIT_TO_FRONT};
            }

            // The opening activity of the return transition must match the activity we just closed.
            TransitionFilter.Requirement req1 = new TransitionFilter.Requirement();
            req1.mModes = new int[] {TRANSIT_OPEN, TRANSIT_TO_FRONT};
            req1.mTopActivity =
                    launchingActivity == null ? launchingTaskInfo.topActivity : launchingActivity;

            TransitionFilter.Requirement req2 = new TransitionFilter.Requirement();
            if (forPredictiveBackTakeover) {
                req2.mModes = new int[] {TRANSIT_CHANGE};
            } else {
                req2.mModes = new int[] {TRANSIT_CLOSE, TRANSIT_TO_BACK};
            }
            if (launchedTaskInfo != null) {
                // For task transitions, the closing task's cookie must match the task we just
                // launched.
                req2.mLaunchCookie = launchedTaskInfo.launchCookies.get(0);
            } else {
                // For activity transitions, the closing activity of the return transition must
                // match the activity we just launched.
                req2.mTopActivity = launchedActivity;
            }

            filter.mRequirements = new TransitionFilter.Requirement[] {req1, req2};
            return filter;
        }
    }
}
