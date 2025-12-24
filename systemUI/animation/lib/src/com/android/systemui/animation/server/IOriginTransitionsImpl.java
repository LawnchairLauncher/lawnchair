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
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.IndentingPrintWriter;
import com.android.systemui.animation.shared.IOriginTransitions;
import com.android.wm.shell.shared.ShellTransitions;
import com.android.wm.shell.shared.TransitionUtil;

import java.util.HashMap;
import java.util.List;
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
            // for compatibility, wrap the single return transition in a map to create the
            // corresponding record. Since no filters are provided here, default will be provided.
            final Map<RemoteTransition, TransitionFilter> returnTransitions = new HashMap<>();
            returnTransitions.put(returnTransition, null);
            OriginTransitionRecord record =
                    new OriginTransitionRecord(launchTransition, returnTransitions);
            mRecords.put(record.getToken(), record);
            return record.asLaunchableTransition();
        }
    }

    @Override
    public RemoteTransition makeOriginTransitionWithReturnFilters(
            RemoteTransition launchTransition,
            List<RemoteTransition> returnTransitions,
            List<TransitionFilter> filters)
            throws RemoteException {
        if (DEBUG) {
            Log.d(
                    TAG,
                    "makeOriginTransitionWithReturnFilters: ("
                            + launchTransition + ", "
                            + filters + ", "
                            + returnTransitions + ")");
        }
        enforceRemoteTransitionPermission();
        if (filters.size() != returnTransitions.size() || returnTransitions.isEmpty()) {
            throw new IllegalArgumentException(
                    "Lists of return transitions and filters must be the same size (non-empty).");
        }
        // Wrap the return transitions and corresponding filters in a map to create the record.
        final Map<RemoteTransition, TransitionFilter> returnTransitionMap = new HashMap<>();
        for (int i = 0; i < returnTransitions.size(); i++) {
            returnTransitionMap.put(returnTransitions.get(i), filters.get(i));
        }
        synchronized (mLock) {
            OriginTransitionRecord record =
                    new OriginTransitionRecord(launchTransition, returnTransitionMap);
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

    @Override
    public IBinder getDefaultTransactionApplyToken() {
        enforceRemoteTransitionPermission();
        return SurfaceControl.Transaction.getDefaultApplyToken();
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
        private final Map<RemoteTransition, TransitionFilter> mWrappedReturnTransitionMap;

        @GuardedBy("mLock")
        private boolean mDestroyed;

        OriginTransitionRecord(
                RemoteTransition launchTransition,
                Map<RemoteTransition, TransitionFilter> returnTransitionsMap
        ) throws RemoteException {
            mWrappedLaunchTransition =
                    wrapLaunch(launchTransition, this::onLaunchTransitionStarting);
            mWrappedReturnTransitionMap =
                    wrapReturns(returnTransitionsMap, this::onReturnTransitionStarting);
            linkToDeath();
        }

        private boolean onLaunchTransitionStarting(TransitionInfo info) {
            synchronized (mLock) {
                if (mDestroyed || mWrappedReturnTransitionMap.isEmpty()) {
                    return false;
                }
                final TransitionInfoContainer tic = TransitionInfoContainer.extractInfo(info);

                int registeredRemotes = 0;
                for (Map.Entry<RemoteTransition, TransitionFilter> entry
                        : mWrappedReturnTransitionMap.entrySet()) {
                    RemoteTransition t = entry.getKey();
                    TransitionFilter f = entry.getValue();

                    if (f == null) {
                        // a single transition with no filters represents a default case so we
                        // simply construct default filters & register
                        TransitionFilter filter =
                                createReturnTransitionFilter(/* forTakeover= */ false);
                        filter = updateTransitionFilterForInfo(filter, tic);
                        if (filter != null) {
                            if (DEBUG) {
                                Log.d(TAG, "Registering filter " + filter);
                            }
                            mShellTransitions.registerRemote(filter, t);
                            registeredRemotes++;
                        } else {
                            Log.w(TAG, "Failed to update default filter:" + filter);
                        }
                        TransitionFilter takeoverFilter =
                                createReturnTransitionFilter(/* forTakeover= */ true);
                        takeoverFilter = updateTransitionFilterForInfo(takeoverFilter, tic);
                        if (takeoverFilter != null) {
                            if (DEBUG) {
                                Log.d(TAG, "Registering filter for takeover " + takeoverFilter);
                            }
                            mShellTransitions.registerRemoteForTakeover(
                                    takeoverFilter, t);
                            registeredRemotes++;
                        } else {
                            Log.w(TAG, "Failed to update takeover filter: " + takeoverFilter);
                        }
                    } else {
                        // take the provided filters and update them with the required info
                        TransitionFilter updatedFilter = updateTransitionFilterForInfo(f, tic);
                        if (updatedFilter != null) {
                            if (DEBUG) {
                                Log.d(TAG, "Registering updated filter " + updatedFilter);
                            }
                            if (isFilterForTakeover(updatedFilter)) {
                                mShellTransitions.registerRemoteForTakeover(updatedFilter, t);
                            } else {
                                mShellTransitions.registerRemote(updatedFilter, t);
                            }
                            registeredRemotes++;
                        } else {
                            Log.w(TAG, "Failed to update provided filter: " + f);
                        }
                    }
                }
                if (registeredRemotes == 0) {
                    // clean up since we don't have anything that needs holding onto
                    destroy();
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
                // unregister potentially pending returns
                for (RemoteTransition rt : mWrappedReturnTransitionMap.keySet()) {
                    mShellTransitions.unregisterRemote(rt);
                }
                mRecords.remove(getToken());
            }
        }

        private void linkToDeath() throws RemoteException {
            asDelegate(mWrappedLaunchTransition).mTransition.asBinder().linkToDeath(this, 0);
            for (RemoteTransition rt : mWrappedReturnTransitionMap.keySet()) {
                asDelegate(rt).mTransition.asBinder().linkToDeath(this, 0);
            }
        }

        private void unlinkToDeath() {
            asDelegate(mWrappedLaunchTransition).mTransition.asBinder().unlinkToDeath(this, 0);
            for (RemoteTransition rt : mWrappedReturnTransitionMap.keySet()) {
                asDelegate(rt).mTransition.asBinder().unlinkToDeath(this, 0);
            }
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
                    + mWrappedLaunchTransition
                    + ", returns="
                    + mWrappedReturnTransitionMap.keySet()
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
                ipw.println("Return transitions:");
                ipw.increaseIndent();
                ipw.println(mWrappedReturnTransitionMap.keySet());
                ipw.decreaseIndent();
                ipw.decreaseIndent();
            }
        }

        private static RemoteTransitionDelegate asDelegate(RemoteTransition transition) {
            return (RemoteTransitionDelegate) transition.getRemoteTransition();
        }

        private RemoteTransition wrapLaunch(
                RemoteTransition transition, Predicate<TransitionInfo> onLaunchStarting) {
            if (DEBUG) {
                Log.d(TAG, "wrapLaunch wrapping transition: " + transition);
            }
            return new RemoteTransition(
                    new RemoteTransitionDelegate(
                            mContext.getMainExecutor(),
                            transition.getRemoteTransition(),
                            onLaunchStarting),
                    transition.getDebugName());
        }

        private Map<RemoteTransition, TransitionFilter> wrapReturns(
                Map<RemoteTransition, TransitionFilter> transitionMap,
                Predicate<TransitionInfo> onReturnStarting) {
            Map<RemoteTransition, TransitionFilter> wrappedTransitionMap = new HashMap<>();

            RemoteTransition delegate;
            for (Map.Entry<RemoteTransition, TransitionFilter> entry : transitionMap.entrySet()) {
                RemoteTransition t = entry.getKey();
                TransitionFilter f = entry.getValue();
                if (DEBUG) {
                    Log.d(TAG, "wrapReturn wrapping transition: " + t + " with filter: " + f);
                }
                delegate = new RemoteTransition(
                        new RemoteTransitionDelegate(
                                mContext.getMainExecutor(),
                                t.getRemoteTransition(),
                                onReturnStarting),
                        t.getDebugName());

                wrappedTransitionMap.put(delegate, f);
            }

            return wrappedTransitionMap;
        }

        /**
         * Update the provided transition filter with applicable details from the current transition
         * info from a given launch. The updated filter will have TopActivity and/or LaunchCookie
         * details added to specific requirements as appropriate which can be used for matching
         * app launches with their corresponding returns. If the update fails or is skipped for
         * whatever reason, it will return null and no return animation will be registered for
         * the launch.
         *
         * @param filter the TransitionFilter to be updated.
         * @param info the TransitionInfo associated with a given app launch.
         * @return the updated transition filter or null if the update failed.
         */
        @Nullable
        private static TransitionFilter updateTransitionFilterForInfo(
                TransitionFilter filter,
                TransitionInfoContainer info) {

            if (DEBUG) {
                Log.d(
                        TAG,
                        "updateTransitionFilterForInfo:"
                                + "\n\tfilter=" + filter
                                + "\n\tlaunchingTaskInfo=" + info.launchingTaskInfo
                                + "\n\tlaunchingActivity=" + info.launchingActivity
                                + "\n\tlaunchedTaskInfo=" + info.launchedTaskInfo
                                + "\n\tlaunchedActivity=" + info.launchedActivity);
            }
            if (info.launchingTaskInfo == null && info.launchingActivity == null) {
                Log.w(
                        TAG,
                        "updateTransitionFilterForInfo: unable to find launching task or"
                                + " launching activity!");
                return null;
            }
            if (info.launchedTaskInfo == null && info.launchedActivity == null) {
                Log.w(
                        TAG,
                        "updateTransitionFilterForInfo: unable to find launched task or launched"
                                + " activity!");
                return null;
            }
            if (info.launchedTaskInfo != null && info.launchedTaskInfo.launchCookies.isEmpty()) {
                Log.w(
                        TAG,
                        "updateTransitionFilterForInfo: skipped - launched task has no launch"
                                + " cookie!");
                return null;
            }
            if (filter.mTypeSet == null || filter.mRequirements == null) {
                Log.w(
                        TAG,
                        "updateTransitionFilterForInfo: skipped - invalid transition filter.");
                return null;
            }

            boolean forPredictiveBackTakeover = isFilterForTakeover(filter);

            if (forPredictiveBackTakeover && info.launchedTaskInfo == null) {
                // Predictive back take over currently only support cross-task transition.
                Log.d(
                        TAG,
                        "updateTransitionFilterForInfo: skipped - unable to find launched task"
                                + " for predictive back takeover");
                return null;
            }

            boolean hasOpeningModeRequirement = false;
            boolean hasClosingChangeModeRequirement = false;
            for (int i = 0; i < filter.mRequirements.length; i++) {
                TransitionFilter.Requirement req = filter.mRequirements[i];
                if (req.mNot) {
                    Log.d(TAG, "updateTransitionFilterForInfo skipping exclusion req: " + req);
                    continue;
                }
                if (isFilterModeOpening(req.mModes)) {
                    req.mTopActivity =
                            info.launchingActivity == null
                                    ? info.launchingTaskInfo.topActivity : info.launchingActivity;
                    Log.d(TAG, "updateTransitionFilterForInfo: "
                            + "opening change expects topActivity: " + req.mTopActivity);
                    hasOpeningModeRequirement = true;
                } else if (isFilterModeClosingOrChange(req.mModes)) {
                    if (info.launchedTaskInfo != null) {
                        // For task transitions, the closing task's cookie must match the task we
                        // just launched.
                        req.mLaunchCookie = info.launchedTaskInfo.launchCookies.get(0);
                        Log.d(TAG, "updateTransitionFilterForInfo: "
                                + "closing change expects launch cookie: " + req.mLaunchCookie);
                    } else {
                        // For activity transitions, the closing activity of the return transition
                        // must match the activity we just launched.
                        req.mTopActivity = info.launchedActivity;
                        Log.d(TAG, "updateTransitionFilterForInfo: "
                                + "closing change expects top activity: " + req.mTopActivity);
                    }
                    hasClosingChangeModeRequirement = true;
                }
            }
            if (hasOpeningModeRequirement && hasClosingChangeModeRequirement) {
                return filter;
            }
            Log.w(TAG, "updateTransitionFilterForInfo failed - filter missing required modes");
            return null;
        }
    }

    private static TransitionFilter createReturnTransitionFilter(boolean forTakeover) {
        TransitionFilter filter = new TransitionFilter();
        if (forTakeover) {
            filter.mTypeSet = new int[] {TRANSIT_PREPARE_BACK_NAVIGATION};
        } else {
            filter.mTypeSet =
                    new int[] {TRANSIT_CLOSE, TRANSIT_TO_BACK, TRANSIT_OPEN, TRANSIT_TO_FRONT};
        }

        // The opening activity of the return transition must match the activity we just closed.
        TransitionFilter.Requirement req1 = new TransitionFilter.Requirement();
        req1.mModes = new int[] {TRANSIT_OPEN, TRANSIT_TO_FRONT};

        TransitionFilter.Requirement req2 = new TransitionFilter.Requirement();
        if (forTakeover) {
            req2.mModes = new int[] {TRANSIT_CHANGE};
        } else {
            req2.mModes = new int[] {TRANSIT_CLOSE, TRANSIT_TO_BACK};
        }

        filter.mRequirements = new TransitionFilter.Requirement[] {req1, req2};
        return filter;
    }

    private static boolean isFilterForTakeover(TransitionFilter filter) {
        for (int i = 0; i < filter.mTypeSet.length; i++) {
            if (filter.mTypeSet[i] == TRANSIT_PREPARE_BACK_NAVIGATION) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFilterModeOpening(int[] modes) {
        boolean hasOpen = false;
        boolean hasToFront = false;

        for (int mode : modes) {
            if (mode == TRANSIT_OPEN) {
                hasOpen = true;
            } else if (mode == TRANSIT_TO_FRONT) {
                hasToFront = true;
            }
        }
        return hasOpen && hasToFront;
    }

    private static boolean isFilterModeClosingOrChange(int[] modes) {
        boolean hasClosing = false;
        boolean hasToBack = false;
        boolean hasChange = false;

        for (int mode : modes) {
            if (mode == TRANSIT_CLOSE) {
                hasClosing = true;
            } else if (mode == TRANSIT_TO_BACK) {
                hasToBack = true;
            } else if (mode == TRANSIT_CHANGE) {
                hasChange = true;
            }
        }
        return (hasClosing && hasToBack) || hasChange;
    }
    /**
     * A data container to hold the extracted transition information.
     */
    public static final class TransitionInfoContainer {
        public final TaskInfo launchedTaskInfo;
        public final TaskInfo launchingTaskInfo;
        public final ComponentName launchedActivity;
        public final ComponentName launchingActivity;

        @VisibleForTesting
        TransitionInfoContainer(TaskInfo launchedTaskInfo, TaskInfo launchingTaskInfo,
                ComponentName launchedActivity, ComponentName launchingActivity) {
            this.launchedTaskInfo = launchedTaskInfo;
            this.launchingTaskInfo = launchingTaskInfo;
            this.launchedActivity = launchedActivity;
            this.launchingActivity = launchingActivity;
        }

        /**
         * Extracts transition information into a container object.
         *
         * @param info The TransitionInfo object to process.
         * @return A TransitionInfoContainer with the extracted data, or null if no data is found.
         */
        public static TransitionInfoContainer extractInfo(TransitionInfo info) {
            TaskInfo launchingTaskInfo = null;
            TaskInfo launchedTaskInfo = null;
            ComponentName launchingActivity = null;
            ComponentName launchedActivity = null;

            for (Change change : info.getChanges()) {
                int mode = change.getMode();
                TaskInfo taskInfo = change.getTaskInfo();
                ComponentName activity = change.getActivityComponent();

                if (launchingTaskInfo == null && taskInfo != null
                        && TransitionUtil.isClosingMode(mode)) {
                    launchingTaskInfo = taskInfo;
                } else if (launchedTaskInfo == null && taskInfo != null
                        && TransitionUtil.isOpeningMode(mode)) {
                    launchedTaskInfo = taskInfo;
                } else if (launchingActivity == null && activity != null
                        && TransitionUtil.isClosingMode(mode)) {
                    launchingActivity = activity;
                } else if (launchedActivity == null && activity != null
                        && TransitionUtil.isOpeningMode(mode)) {
                    launchedActivity = activity;
                }
            }

            return new TransitionInfoContainer(
                    launchedTaskInfo, launchingTaskInfo, launchedActivity, launchingActivity);
        }
    }
}
