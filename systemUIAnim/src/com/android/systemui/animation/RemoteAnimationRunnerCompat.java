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

package com.android.systemui.animation;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_OLD_NONE;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.view.WindowManager.TRANSIT_TO_FRONT;
import static android.window.TransitionInfo.FLAG_IS_WALLPAPER;

import android.os.IBinder;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.Log;
import android.view.IRemoteAnimationFinishedCallback;
import android.view.IRemoteAnimationRunner;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.view.WindowManager.TransitionOldType;
import android.window.IRemoteTransition;
import android.window.IRemoteTransitionFinishedCallback;
import android.window.RemoteTransitionStub;
import android.window.TransitionInfo;

import com.android.wm.shell.shared.CounterRotator;

public abstract class RemoteAnimationRunnerCompat extends IRemoteAnimationRunner.Stub {
    private static final String TAG = "RemoteAnimRunnerCompat";

    public abstract void onAnimationStart(@WindowManager.TransitionOldType int transit,
            RemoteAnimationTarget[] apps, RemoteAnimationTarget[] wallpapers,
            RemoteAnimationTarget[] nonApps, Runnable finishedCallback);

    @Override
    public final void onAnimationStart(@TransitionOldType int transit,
            RemoteAnimationTarget[] apps,
            RemoteAnimationTarget[] wallpapers,
            RemoteAnimationTarget[] nonApps,
            final IRemoteAnimationFinishedCallback finishedCallback) {

        onAnimationStart(transit, apps, wallpapers,
                nonApps, () -> {
                    try {
                        finishedCallback.onAnimationFinished();
                    } catch (RemoteException e) {
                        Log.e(TAG, "Failed to call app controlled animation finished callback", e);
                    }
                });
    }

    public IRemoteTransition toRemoteTransition() {
        return wrap(this);
    }

    /** Wraps a remote animation runner in a remote-transition. */
    public static RemoteTransitionStub wrap(IRemoteAnimationRunner runner) {
        return new RemoteTransitionStub() {
            final ArrayMap<IBinder, Runnable> mFinishRunnables = new ArrayMap<>();

            @Override
            public void startAnimation(IBinder token, TransitionInfo info,
                    SurfaceControl.Transaction t,
                    IRemoteTransitionFinishedCallback finishCallback) throws RemoteException {
                final ArrayMap<SurfaceControl, SurfaceControl> leashMap = new ArrayMap<>();
                final RemoteAnimationTarget[] apps =
                        RemoteAnimationTargetCompat.wrapApps(info, t, leashMap);
                final RemoteAnimationTarget[] wallpapers =
                        RemoteAnimationTargetCompat.wrapNonApps(
                                info, true /* wallpapers */, t, leashMap);
                final RemoteAnimationTarget[] nonApps =
                        RemoteAnimationTargetCompat.wrapNonApps(
                                info, false /* wallpapers */, t, leashMap);

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
                    // skip changes that we didn't wrap
                    if (!leashMap.containsKey(change.getLeash())) continue;
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
                    final TransitionInfo.Change parent = info.getChange(launcherTask.getParent());
                    if (parent != null) {
                        counterLauncher.setup(t, parent.getLeash(), rotateDelta, displayW,
                                displayH);
                    } else {
                        Log.e(TAG, "Malformed: " + launcherTask + " has parent="
                                + launcherTask.getParent() + " but it's not in info.");
                    }
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
                        // skip changes that we didn't wrap
                        if (leash == null) continue;
                        final int mode = info.getChanges().get(i).getMode();
                        // Only deal with independent layers
                        if (!TransitionInfo.isIndependent(change, info)) continue;
                        if (mode == TRANSIT_CLOSE || mode == TRANSIT_TO_BACK) {
                            t.setLayer(leash, info.getChanges().size() * 3 - i);
                            counterLauncher.addChild(t, leash);
                        }
                    }
                    // Make wallpaper visible immediately since launcher apparently won't do this.
                    for (int i = wallpapers.length - 1; i >= 0; --i) {
                        t.show(wallpapers[i].leash);
                        t.setAlpha(wallpapers[i].leash, 1.f);
                    }
                } else {
                    if (launcherTask != null) {
                        counterLauncher.addChild(t, leashMap.get(launcherTask.getLeash()));
                    }
                    if (wallpaper != null && rotateDelta != 0 && wallpaper.getParent() != null) {
                        final TransitionInfo.Change parent = info.getChange(wallpaper.getParent());
                        if (parent != null) {
                            counterWallpaper.setup(t, parent.getLeash(), rotateDelta, displayW,
                                    displayH);
                        } else {
                            Log.e(TAG, "Malformed: " + wallpaper + " has parent="
                                    + wallpaper.getParent() + " but it's not in info.");
                        }
                        if (counterWallpaper.getSurface() != null) {
                            t.setLayer(counterWallpaper.getSurface(), -1);
                            counterWallpaper.addChild(t, leashMap.get(wallpaper.getLeash()));
                        }
                    }
                }
                t.apply();

                final Runnable animationFinishedCallback = () -> {
                    final SurfaceControl.Transaction finishTransaction =
                            new SurfaceControl.Transaction();
                    counterLauncher.cleanUp(finishTransaction);
                    counterWallpaper.cleanUp(finishTransaction);
                    // Release surface references now. This is apparently to free GPU memory
                    // before GC would.
                    info.releaseAllSurfaces();
                    // Don't release here since launcher might still be using them. Instead
                    // let launcher release them (eg. via RemoteAnimationTargets)
                    leashMap.clear();
                    try {
                        finishCallback.onTransitionFinished(null /* wct */, finishTransaction);
                        finishTransaction.close();
                    } catch (RemoteException e) {
                        Log.e(TAG, "Failed to call app controlled animation finished callback", e);
                    }
                };
                synchronized (mFinishRunnables) {
                    mFinishRunnables.put(token, animationFinishedCallback);
                }
                // TODO(bc-unlcok): Pass correct transit type.
                runner.onAnimationStart(TRANSIT_OLD_NONE,
                        apps, wallpapers, nonApps, new IRemoteAnimationFinishedCallback() {
                            @Override
                            public void onAnimationFinished() {
                                synchronized (mFinishRunnables) {
                                    if (mFinishRunnables.remove(token) == null) return;
                                }
                                animationFinishedCallback.run();
                            }

                            @Override
                            public IBinder asBinder() {
                                return null;
                            }
                        });
            }

            @Override
            public void mergeAnimation(IBinder token, TransitionInfo info,
                    SurfaceControl.Transaction t, IBinder mergeTarget,
                    IRemoteTransitionFinishedCallback finishCallback) throws RemoteException {
                // TODO: hook up merge to recents onTaskAppeared if applicable. Until then, adapt
                //       to legacy cancel.
                final Runnable finishRunnable;
                synchronized (mFinishRunnables) {
                    finishRunnable = mFinishRunnables.remove(mergeTarget);
                }
                // Since we're not actually animating, release native memory now
                t.close();
                info.releaseAllSurfaces();
                if (finishRunnable == null) return;
                runner.onAnimationCancelled();
                finishRunnable.run();
            }
        };
    }
}
