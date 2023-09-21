/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.shared.system;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_RECENTS;
import static android.view.RemoteAnimationTarget.MODE_CLOSING;
import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_FLAG_KEYGUARD_LOCKED;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.view.WindowManager.TRANSIT_TO_FRONT;

import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.newTarget;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.IApplicationThread;
import android.graphics.Rect;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.Log;
import android.util.SparseArray;
import android.view.IRecentsAnimationController;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.window.IRemoteTransition;
import android.window.IRemoteTransitionFinishedCallback;
import android.window.PictureInPictureSurfaceTransaction;
import android.window.RemoteTransition;
import android.window.TaskSnapshot;
import android.window.TransitionInfo;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import com.android.internal.annotations.VisibleForTesting;
import com.android.systemui.shared.recents.model.ThumbnailData;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * Helper class to build {@link RemoteTransition} objects
 */
public class RemoteTransitionCompat {
    private static final String TAG = "RemoteTransitionCompat";

    /** Constructor specifically for recents animation */
    public static RemoteTransition newRemoteTransition(RecentsAnimationListener recents,
            RecentsAnimationControllerCompat controller, IApplicationThread appThread) {
        IRemoteTransition remote = new IRemoteTransition.Stub() {
            final RecentsControllerWrap mRecentsSession = new RecentsControllerWrap();
            IBinder mToken = null;

            @Override
            public void startAnimation(IBinder transition, TransitionInfo info,
                    SurfaceControl.Transaction t,
                    IRemoteTransitionFinishedCallback finishedCallback) {
                final ArrayMap<SurfaceControl, SurfaceControl> leashMap = new ArrayMap<>();
                final RemoteAnimationTarget[] apps =
                        RemoteAnimationTargetCompat.wrapApps(info, t, leashMap);
                final RemoteAnimationTarget[] wallpapers =
                        RemoteAnimationTargetCompat.wrapNonApps(
                                info, true /* wallpapers */, t, leashMap);
                // TODO(b/177438007): Move this set-up logic into launcher's animation impl.
                mToken = transition;
                // This transition is for opening recents, so recents is on-top. We want to draw
                // the current going-away tasks on top of recents, though, so move them to front.
                // Note that we divide up the "layer space" into 3 regions each the size of
                // the change count. This way we can easily move changes into above/below/between
                // while maintaining their relative ordering.
                final ArrayList<WindowContainerToken> pausingTasks = new ArrayList<>();
                WindowContainerToken pipTask = null;
                WindowContainerToken recentsTask = null;
                for (int i = apps.length - 1; i >= 0; --i) {
                    final ActivityManager.RunningTaskInfo taskInfo = apps[i].taskInfo;
                    if (apps[i].mode == MODE_CLOSING) {
                        t.setLayer(apps[i].leash, info.getChanges().size() * 3 - i);
                        if (taskInfo == null) {
                            continue;
                        }
                        // Add to front since we are iterating backwards.
                        pausingTasks.add(0, taskInfo.token);
                        if (taskInfo.pictureInPictureParams != null
                                && taskInfo.pictureInPictureParams.isAutoEnterEnabled()) {
                            pipTask = taskInfo.token;
                        }
                    } else if (taskInfo != null
                            && taskInfo.topActivityType == ACTIVITY_TYPE_RECENTS) {
                        // This task is for recents, keep it on top.
                        t.setLayer(apps[i].leash, info.getChanges().size() * 3 - i);
                        recentsTask = taskInfo.token;
                    } else if (taskInfo != null && taskInfo.topActivityType == ACTIVITY_TYPE_HOME) {
                        recentsTask = taskInfo.token;
                    }
                }
                // Also make all the wallpapers opaque since we want the visible from the start
                for (int i = wallpapers.length - 1; i >= 0; --i) {
                    t.setAlpha(wallpapers[i].leash, 1);
                }
                t.apply();
                mRecentsSession.setup(controller, info, finishedCallback, pausingTasks, pipTask,
                        recentsTask, leashMap, mToken,
                        (info.getFlags() & TRANSIT_FLAG_KEYGUARD_LOCKED) != 0);
                recents.onAnimationStart(mRecentsSession, apps, wallpapers, new Rect(0, 0, 0, 0),
                        new Rect());
            }

            @Override
            public void mergeAnimation(IBinder transition, TransitionInfo info,
                    SurfaceControl.Transaction t, IBinder mergeTarget,
                    IRemoteTransitionFinishedCallback finishedCallback) {
                if (mergeTarget.equals(mToken) && mRecentsSession.merge(info, t, recents)) {
                    try {
                        finishedCallback.onTransitionFinished(null /* wct */, null /* sct */);
                    } catch (RemoteException e) {
                        Log.e(TAG, "Error merging transition.", e);
                    }
                    // commit taskAppeared after merge transition finished.
                    mRecentsSession.commitTasksAppearedIfNeeded(recents);
                } else {
                    try {
                        Method method = info.getClass ().getMethod ("releaseAllSurfaces");
                        method.invoke (info);
                    } catch (NoSuchMethodException e) {
                        Log.e (TAG , "mergeAnimation: ", e);
                    } catch (InvocationTargetException | IllegalAccessException e) {
                        throw new RuntimeException (e);
                    }
                    t.close();

                }
            }
        };
        return new RemoteTransition(remote, appThread);
    }

    /**
     * Wrapper to hook up parts of recents animation to shell transition.
     * TODO(b/177438007): Remove this once Launcher handles shell transitions directly.
     */
    @VisibleForTesting
    static class RecentsControllerWrap extends RecentsAnimationControllerCompat {
        private RecentsAnimationControllerCompat mWrapped = null;
        private IRemoteTransitionFinishedCallback mFinishCB = null;
        private ArrayList<WindowContainerToken> mPausingTasks = null;
        private WindowContainerToken mPipTask = null;
        private WindowContainerToken mRecentsTask = null;
        private TransitionInfo mInfo = null;
        private ArrayList<SurfaceControl> mOpeningLeashes = null;
        private boolean mOpeningHome = false;
        private ArrayMap<SurfaceControl, SurfaceControl> mLeashMap = null;
        private PictureInPictureSurfaceTransaction mPipTransaction = null;
        private IBinder mTransition = null;
        private boolean mKeyguardLocked = false;
        private RemoteAnimationTarget[] mAppearedTargets;
        private boolean mWillFinishToHome = false;

        void setup(RecentsAnimationControllerCompat wrapped, TransitionInfo info,
                IRemoteTransitionFinishedCallback finishCB,
                ArrayList<WindowContainerToken> pausingTasks, WindowContainerToken pipTask,
                WindowContainerToken recentsTask, ArrayMap<SurfaceControl, SurfaceControl> leashMap,
                IBinder transition, boolean keyguardLocked) {
            if (mInfo != null) {
                throw new IllegalStateException("Trying to run a new recents animation while"
                        + " recents is already active.");
            }
            mWrapped = wrapped;
            mInfo = info;
            mFinishCB = finishCB;
            mPausingTasks = pausingTasks;
            mPipTask = pipTask;
            mRecentsTask = recentsTask;
            mLeashMap = leashMap;
            mTransition = transition;
            mKeyguardLocked = keyguardLocked;
        }

        @SuppressLint("NewApi")
        boolean merge(TransitionInfo info, SurfaceControl.Transaction t,
                RecentsAnimationListener recents) {
            SparseArray<TransitionInfo.Change> openingTasks = null;
            mAppearedTargets = null;
            boolean cancelRecents = false;
            boolean homeGoingAway = false;
            boolean hasChangingApp = false;
            for (int i = info.getChanges().size() - 1; i >= 0; --i) {
                final TransitionInfo.Change change = info.getChanges().get(i);
                if (change.getMode() == TRANSIT_OPEN || change.getMode() == TRANSIT_TO_FRONT) {
                    final ActivityManager.RunningTaskInfo taskInfo = change.getTaskInfo();
                    if (taskInfo != null) {
                        if (taskInfo.topActivityType == ACTIVITY_TYPE_HOME) {
                            // canceling recents animation
                            cancelRecents = true;
                        }
                        if (openingTasks == null) {
                            openingTasks = new SparseArray<>();
                        }
                        if (taskInfo.hasParentTask()) {
                            // Collects opening leaf tasks only since Launcher monitors leaf task
                            // ids to perform recents animation.
                            openingTasks.remove(taskInfo.parentTaskId);
                        }
                        openingTasks.put(taskInfo.taskId, change);
                    }
                } else if (change.getMode() == TRANSIT_CLOSE
                        || change.getMode() == TRANSIT_TO_BACK) {
                    if (mRecentsTask.equals(change.getContainer())) {
                        homeGoingAway = true;
                    }
                } else if (change.getMode() == TRANSIT_CHANGE) {
                    hasChangingApp = true;
                }
            }
            if (hasChangingApp && homeGoingAway) {
                // This happens when a visible app is expanding (usually PiP). In this case,
                // The transition probably has a special-purpose animation, so finish recents
                // now and let it do its animation (since recents is going to be occluded).
                if (!recents.onSwitchToScreenshot(() -> {
                    finish(true /* toHome */, false /* userLeaveHint */);
                })) {
                    Log.w(TAG, "Recents callback doesn't support support switching to screenshot"
                            + ", there might be a flicker.");
                    finish(true /* toHome */, false /* userLeaveHint */);
                }
                return false;
            }
            if (openingTasks == null) return false;
            int pauseMatches = 0;
            if (!cancelRecents) {
                for (int i = 0; i < openingTasks.size(); ++i) {
                    if (mPausingTasks.contains(openingTasks.valueAt(i).getContainer())) {
                        ++pauseMatches;
                    }
                }
            }
            if (pauseMatches > 0) {
                if (pauseMatches != mPausingTasks.size()) {
                    // We are not really "returning" properly... something went wrong.
                    throw new IllegalStateException("\"Concelling\" a recents transitions by "
                            + "unpausing " + pauseMatches + " apps after pausing "
                            + mPausingTasks.size() + " apps.");
                }
                // In this case, we are "returning" to an already running app, so just consume
                // the merge and do nothing.
                try {
                    Method method = info.getClass ().getMethod ("releaseAnimSurfaces");
                    method.invoke (info);
                } catch (NoSuchMethodException e) {
                    Log.e (TAG , "mergeAnimation: ", e);
                } catch (InvocationTargetException | IllegalAccessException e) {
                    throw new RuntimeException (e);
                }
                t.close();
                return true;
            }
            final int layer = mInfo.getChanges().size() * 3;
            mOpeningLeashes = new ArrayList<>();
            mOpeningHome = cancelRecents;
            final RemoteAnimationTarget[] targets =
                    new RemoteAnimationTarget[openingTasks.size()];
            for (int i = 0; i < openingTasks.size(); ++i) {
                final TransitionInfo.Change change = openingTasks.valueAt(i);
                mOpeningLeashes.add(change.getLeash());
                // We are receiving new opening tasks, so convert to onTasksAppeared.
                targets[i] = newTarget(change, layer, info, t, mLeashMap);
                t.reparent(targets[i].leash, mInfo.getRootLeash());
                t.setLayer(targets[i].leash, layer);
            }
            t.apply();
            // not using the incoming anim-only surfaces
            try {
                Method method = info.getClass ().getMethod ("releaseAnimSurfaces");
                method.invoke (info);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                Log.e (TAG , "mergeAnimation: ", e);
            }
            mAppearedTargets = targets;
            return true;
        }

        private void commitTasksAppearedIfNeeded(RecentsAnimationListener recents) {
            if (mAppearedTargets != null) {
                recents.onTasksAppeared(mAppearedTargets);
                mAppearedTargets = null;
            }
        }

        @Override public ThumbnailData screenshotTask(int taskId) {
            try {
                final TaskSnapshot snapshot =
                        ActivityTaskManager.getService().takeTaskSnapshot(taskId);
                if (snapshot != null) {
                    return new ThumbnailData(snapshot);
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to screenshot task", e);
            }
            return null;
        }

        @Override public void setInputConsumerEnabled(boolean enabled) {
            if (mWrapped != null) mWrapped.setInputConsumerEnabled(enabled);
        }

        @Override public void setAnimationTargetsBehindSystemBars(boolean behindSystemBars) {
            if (mWrapped != null) mWrapped.setAnimationTargetsBehindSystemBars(behindSystemBars);
        }

        @Override public void setFinishTaskTransaction(int taskId,
                PictureInPictureSurfaceTransaction finishTransaction, SurfaceControl overlay) {
            mPipTransaction = finishTransaction;
            if (mWrapped != null) {
                mWrapped.setFinishTaskTransaction(taskId, finishTransaction, overlay);
            }
        }

        @Override
        @SuppressLint("NewApi")
        public void finish(boolean toHome, boolean sendUserLeaveHint) {
            if (mFinishCB == null) {
                Log.e(TAG, "Duplicate call to finish", new RuntimeException());
                return;
            }
            if (mWrapped != null) mWrapped.finish(toHome, sendUserLeaveHint);
            final SurfaceControl.Transaction t = new SurfaceControl.Transaction();
            final WindowContainerTransaction wct = new WindowContainerTransaction();

            if (mKeyguardLocked && mRecentsTask != null) {
                if (toHome) wct.reorder(mRecentsTask, true /* toTop */);
                else wct.restoreTransientOrder(mRecentsTask);
            }
            if (!toHome && !mWillFinishToHome && mPausingTasks != null && mOpeningLeashes == null) {
                // The gesture went back to opening the app rather than continuing with
                // recents, so end the transition by moving the app back to the top (and also
                // re-showing it's task).
                for (int i = mPausingTasks.size() - 1; i >= 0; --i) {
                    // reverse order so that index 0 ends up on top
                    wct.reorder(mPausingTasks.get(i), true /* onTop */);
                    t.show(mInfo.getChange(mPausingTasks.get(i)).getLeash());
                }
                if (!mKeyguardLocked && mRecentsTask != null) {
                    wct.restoreTransientOrder(mRecentsTask);
                }
            } else if (toHome && mOpeningHome && mPausingTasks != null) {
                // Special situaition where 3p launcher was changed during recents (this happens
                // during tapltests...). Here we get both "return to home" AND "home opening".
                // This is basically going home, but we have to restore recents order and also
                // treat the home "pausing" task properly.
                for (int i = mPausingTasks.size() - 1; i >= 0; --i) {
                    final TransitionInfo.Change change = mInfo.getChange(mPausingTasks.get(i));
                    final ActivityManager.RunningTaskInfo taskInfo = change.getTaskInfo();
                    if (taskInfo.topActivityType == ACTIVITY_TYPE_HOME) {
                        // Treat as opening (see above)
                        wct.reorder(mPausingTasks.get(i), true /* onTop */);
                        t.show(mInfo.getChange(mPausingTasks.get(i)).getLeash());
                    } else {
                        // Treat as hiding (see below)
                        t.hide(mInfo.getChange(mPausingTasks.get(i)).getLeash());
                    }
                }
                if (!mKeyguardLocked && mRecentsTask != null) {
                    wct.restoreTransientOrder(mRecentsTask);
                }
            } else {
                for (int i = 0; i < mPausingTasks.size(); ++i) {
                    if (!sendUserLeaveHint) {
                        // This means recents is not *actually* finishing, so of course we gotta
                        // do special stuff in WMCore to accommodate.
                        wct.setDoNotPip(mPausingTasks.get(i));
                    }
                    // Since we will reparent out of the leashes, pre-emptively hide the child
                    // surface to match the leash. Otherwise, there will be a flicker before the
                    // visibility gets committed in Core when using split-screen (in splitscreen,
                    // the leaf-tasks are not "independent" so aren't hidden by normal setup).
                    t.hide(mInfo.getChange(mPausingTasks.get(i)).getLeash());
                }
                if (mPipTask != null && mPipTransaction != null && sendUserLeaveHint) {
                    t.show(mInfo.getChange(mPipTask).getLeash());
                    PictureInPictureSurfaceTransaction.apply(mPipTransaction,
                            mInfo.getChange(mPipTask).getLeash(), t);
                    mPipTask = null;
                    mPipTransaction = null;
                }
            }
            try {
                mFinishCB.onTransitionFinished(wct.isEmpty() ? null : wct, t);
            } catch (RemoteException e) {
                Log.e("RemoteTransitionCompat", "Failed to call animation finish callback", e);
                t.apply();
            }
            // Only release the non-local created surface references. The animator is responsible
            // for releasing the leashes created by local.
            try {
                Method method = mInfo.getClass ().getMethod ("releaseAllSurfaces");
                method.invoke (mInfo);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                Log.e (TAG , "mergeAnimation: ", e);
            }
            // Reset all members.
            mWrapped = null;
            mFinishCB = null;
            mPausingTasks = null;
            mInfo = null;
            mOpeningLeashes = null;
            mOpeningHome = false;
            mLeashMap = null;
            mTransition = null;
        }

        @Override public void setDeferCancelUntilNextTransition(boolean defer, boolean screenshot) {
            if (mWrapped != null) mWrapped.setDeferCancelUntilNextTransition(defer, screenshot);
        }

        @Override public void cleanupScreenshot() {
            if (mWrapped != null) mWrapped.cleanupScreenshot();
        }

        @Override public void setWillFinishToHome(boolean willFinishToHome) {
            mWillFinishToHome = willFinishToHome;
            if (mWrapped != null) mWrapped.setWillFinishToHome(willFinishToHome);
        }

        /**
         * @see IRecentsAnimationController#removeTask
         */
        @Override public boolean removeTask(int taskId) {
            return mWrapped != null ? mWrapped.removeTask(taskId) : false;
        }

        /**
         * @see IRecentsAnimationController#detachNavigationBarFromApp
         */
        @Override public void detachNavigationBarFromApp(boolean moveHomeToTop) {
            try {
                ActivityTaskManager.getService().detachNavigationBarFromApp(mTransition);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to detach the navigation bar from app", e);
            }
        }

        /**
         * @see IRecentsAnimationController#animateNavigationBarToApp(long)
         */
        @Override public void animateNavigationBarToApp(long duration) {
        }
    }
}
