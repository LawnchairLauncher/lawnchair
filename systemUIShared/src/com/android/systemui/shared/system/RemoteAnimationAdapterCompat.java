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
 * limitations under the License
 */

package com.android.systemui.shared.system;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_OLD_NONE;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.view.WindowManager.TRANSIT_TO_FRONT;
import static android.view.WindowManager.TransitionOldType;
import static android.window.TransitionInfo.FLAG_IS_WALLPAPER;

import android.annotation.SuppressLint;
import android.app.IApplicationThread;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.Log;
import android.view.IRemoteAnimationFinishedCallback;
import android.view.IRemoteAnimationRunner;
import android.view.RemoteAnimationAdapter;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.window.IRemoteTransition;
import android.window.IRemoteTransitionFinishedCallback;
import android.window.RemoteTransition;
import android.window.TransitionInfo;

import com.android.wm.shell.util.CounterRotator;

/**
 * @see RemoteAnimationAdapter
 */
public class RemoteAnimationAdapterCompat {

    private final RemoteAnimationAdapter mWrapped;
    private final RemoteTransitionCompat mRemoteTransition;

    public RemoteAnimationAdapterCompat(RemoteAnimationRunnerCompat runner, long duration,
            long statusBarTransitionDelay, IApplicationThread appThread) {
        mWrapped = new RemoteAnimationAdapter(wrapRemoteAnimationRunner(runner), duration,
                statusBarTransitionDelay);
        mRemoteTransition = buildRemoteTransition(runner, appThread);
    }

    RemoteAnimationAdapter getWrapped() {
        return mWrapped;
    }

    /** Helper to just build a remote transition. Use this if the legacy adapter isn't needed. */
    public static RemoteTransitionCompat buildRemoteTransition(RemoteAnimationRunnerCompat runner,
            IApplicationThread appThread) {
        return new RemoteTransitionCompat(
                new RemoteTransition(wrapRemoteTransition(runner), appThread));
    }

    public RemoteTransitionCompat getRemoteTransition() {
        return mRemoteTransition;
    }

    /** Wraps a RemoteAnimationRunnerCompat in an IRemoteAnimationRunner. */
    public static IRemoteAnimationRunner.Stub wrapRemoteAnimationRunner(
            final RemoteAnimationRunnerCompat remoteAnimationAdapter) {
        return new IRemoteAnimationRunner.Stub() {
            @Override
            public void onAnimationStart(@TransitionOldType int transit,
                    RemoteAnimationTarget[] apps,
                    RemoteAnimationTarget[] wallpapers,
                    RemoteAnimationTarget[] nonApps,
                    final IRemoteAnimationFinishedCallback finishedCallback) {
                final RemoteAnimationTargetCompat[] appsCompat =
                        RemoteAnimationTargetCompat.wrap(apps);
                final RemoteAnimationTargetCompat[] wallpapersCompat =
                        RemoteAnimationTargetCompat.wrap(wallpapers);
                final RemoteAnimationTargetCompat[] nonAppsCompat =
                        RemoteAnimationTargetCompat.wrap(nonApps);
                final Runnable animationFinishedCallback = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            finishedCallback.onAnimationFinished();
                        } catch (RemoteException e) {
                            Log.e("ActivityOptionsCompat", "Failed to call app controlled animation"
                                    + " finished callback", e);
                        }
                    }
                };
                remoteAnimationAdapter.onAnimationStart(transit, appsCompat, wallpapersCompat,
                        nonAppsCompat, animationFinishedCallback);
            }

            @Override
            public void onAnimationCancelled() {
                remoteAnimationAdapter.onAnimationCancelled();
            }
        };
    }

    private static IRemoteTransition.Stub wrapRemoteTransition(
            final RemoteAnimationRunnerCompat remoteAnimationAdapter) {
        return new IRemoteTransition.Stub() {
            @Override
            public void startAnimation(IBinder token, TransitionInfo info,
                    SurfaceControl.Transaction t,
                    IRemoteTransitionFinishedCallback finishCallback) {
                final ArrayMap<SurfaceControl, SurfaceControl> leashMap = new ArrayMap<>();
                final RemoteAnimationTargetCompat[] appsCompat =
                        RemoteAnimationTargetCompat.wrap(info, false /* wallpapers */, t, leashMap);
                final RemoteAnimationTargetCompat[] wallpapersCompat =
                        RemoteAnimationTargetCompat.wrap(info, true /* wallpapers */, t, leashMap);
                // TODO(bc-unlock): Build wrapped object for non-apps target.
                final RemoteAnimationTargetCompat[] nonAppsCompat =
                        new RemoteAnimationTargetCompat[0];

                // TODO(b/177438007): Move this set-up logic into launcher's animation impl.
                boolean isReturnToHome = false;
                TransitionInfo.Change launcherTask = null;
                TransitionInfo.Change wallpaper = null;
                int launcherLayer = 0;
                int rotateDelta = 0;
                float displayW = 0;
                float displayH = 0;
                for (int i = info.getChanges().size() - 1; i >= 0; --i) {
                    final TransitionInfo.Change change = info.getChanges().get(i);
                    if (change.getTaskInfo() != null
                            && change.getTaskInfo().getActivityType() == ACTIVITY_TYPE_HOME) {
                        isReturnToHome = change.getMode() == TRANSIT_OPEN
                                || change.getMode() == TRANSIT_TO_FRONT;
                        launcherTask = change;
                        launcherLayer = info.getChanges().size() - i;
                    } else if ((change.getFlags() & FLAG_IS_WALLPAPER) != 0) {
                        wallpaper = change;
                    }
                    if (change.getParent() == null && change.getEndRotation() >= 0
                            && change.getEndRotation() != change.getStartRotation()) {
                        rotateDelta = change.getEndRotation() - change.getStartRotation();
                        displayW = change.getEndAbsBounds().width();
                        displayH = change.getEndAbsBounds().height();
                    }
                }

                // Prepare for rotation if there is one
                final CounterRotator counterLauncher = new CounterRotator();
                final CounterRotator counterWallpaper = new CounterRotator();
                if (launcherTask != null && rotateDelta != 0 && launcherTask.getParent() != null) {
                    counterLauncher.setup(t, info.getChange(launcherTask.getParent()).getLeash(),
                            rotateDelta, displayW, displayH);
                    if (counterLauncher.getSurface() != null) {
                        t.setLayer(counterLauncher.getSurface(), launcherLayer);
                    }
                }

                if (isReturnToHome) {
                    if (counterLauncher.getSurface() != null) {
                        t.setLayer(counterLauncher.getSurface(), info.getChanges().size() * 3);
                    }
                    // Need to "boost" the closing things since that's what launcher expects.
                    for (int i = info.getChanges().size() - 1; i >= 0; --i) {
                        final TransitionInfo.Change change = info.getChanges().get(i);
                        final SurfaceControl leash = leashMap.get(change.getLeash());
                        final int mode = info.getChanges().get(i).getMode();
                        // Only deal with independent layers
                        if (!TransitionInfo.isIndependent(change, info)) continue;
                        if (mode == TRANSIT_CLOSE || mode == TRANSIT_TO_BACK) {
                            t.setLayer(leash, info.getChanges().size() * 3 - i);
                            counterLauncher.addChild(t, leash);
                        }
                    }
                    // Make wallpaper visible immediately since launcher apparently won't do this.
                    for (int i = wallpapersCompat.length - 1; i >= 0; --i) {
                        t.show(wallpapersCompat[i].leash.getSurfaceControl());
                        t.setAlpha(wallpapersCompat[i].leash.getSurfaceControl(), 1.f);
                    }
                } else {
                    if (launcherTask != null) {
                        counterLauncher.addChild(t, leashMap.get(launcherTask.getLeash()));
                    }
                    if (wallpaper != null && rotateDelta != 0 && wallpaper.getParent() != null) {
                        counterWallpaper.setup(t, info.getChange(wallpaper.getParent()).getLeash(),
                                rotateDelta, displayW, displayH);
                        if (counterWallpaper.getSurface() != null) {
                            t.setLayer(counterWallpaper.getSurface(), -1);
                            counterWallpaper.addChild(t, leashMap.get(wallpaper.getLeash()));
                        }
                    }
                }
                t.apply();

                final Runnable animationFinishedCallback = new Runnable() {
                    @Override
                    @SuppressLint("NewApi")
                    public void run() {
                        try {
                            counterLauncher.cleanUp(info.getRootLeash());
                            counterWallpaper.cleanUp(info.getRootLeash());
                            // Release surface references now. This is apparently to free GPU
                            // memory while doing quick operations (eg. during CTS).
                            for (int i = 0; i < info.getChanges().size(); ++i) {
                                info.getChanges().get(i).getLeash().release();
                            }
                            SurfaceControl.Transaction t = new SurfaceControl.Transaction();
                            for (int i = 0; i < leashMap.size(); ++i) {
                                if (leashMap.keyAt(i) == leashMap.valueAt(i)) continue;
                                t.remove(leashMap.valueAt(i));
                            }
                            t.apply();
                            finishCallback.onTransitionFinished(null /* wct */, null /* sct */);
                        } catch (RemoteException e) {
                            Log.e("ActivityOptionsCompat", "Failed to call app controlled animation"
                                    + " finished callback", e);
                        }
                    }
                };
                // TODO(bc-unlcok): Pass correct transit type.
                remoteAnimationAdapter.onAnimationStart(
                        TRANSIT_OLD_NONE,
                        appsCompat, wallpapersCompat, nonAppsCompat,
                        animationFinishedCallback);
            }

            @Override
            public void mergeAnimation(IBinder token, TransitionInfo info,
                    SurfaceControl.Transaction t, IBinder mergeTarget,
                    IRemoteTransitionFinishedCallback finishCallback) {
                // TODO: hook up merge to recents onTaskAppeared if applicable. Until then, ignore
                //       any incoming merges.
            }
        };
    }
}
