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
package com.android.quickstep;

import static com.android.launcher3.LauncherState.OVERVIEW;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.ActivityManager.RecentTaskInfo;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.UserHandle;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.util.SparseArray;
import android.view.ViewConfiguration;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.MainThreadExecutor;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.states.InternalStateHandler;
import com.android.launcher3.util.TraceHelper;
import com.android.quickstep.ActivityControlHelper.FallbackActivityControllerHelper;
import com.android.quickstep.ActivityControlHelper.LauncherActivityControllerHelper;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.view.AppTransitionAnimationSpecCompat;
import com.android.systemui.shared.recents.view.AppTransitionAnimationSpecsFuture;
import com.android.systemui.shared.recents.view.RecentsTransition;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.AssistDataReceiver;
import com.android.systemui.shared.system.BackgroundExecutor;
import com.android.systemui.shared.system.RecentsAnimationControllerCompat;
import com.android.systemui.shared.system.RecentsAnimationListener;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Helper class to handle various atomic commands for switching between Overview.
 */
@TargetApi(Build.VERSION_CODES.P)
public class OverviewCommandHelper extends InternalStateHandler {

    private static final int RID_RESET_SWIPE_HANDLER = 0;
    private static final int RID_CANCEL_CONTROLLER = 1;
    private static final int RID_CANCEL_ZOOM_OUT_ANIMATION = 2;

    private static final long RECENTS_LAUNCH_DURATION = 150;

    private static final String TAG = "OverviewCommandHelper";
    private static final boolean DEBUG_START_FALLBACK_ACTIVITY = false;

    private final Context mContext;
    private final ActivityManagerWrapper mAM;
    private final RecentsModel mRecentsModel;
    private final MainThreadExecutor mMainThreadExecutor;

    public final Intent homeIntent;
    public final ComponentName launcher;

    private final SparseArray<Runnable> mCurrentCommandFinishRunnables = new SparseArray<>();
    // Monotonically increasing command ids.
    private int mCurrentCommandId = 0;

    private long mLastToggleTime;
    private WindowTransformSwipeHandler mWindowTransformSwipeHandler;

    private final Point mWindowSize = new Point();
    private final Rect mTaskTargetRect = new Rect();
    private final RectF mTempTaskTargetRect = new RectF();

    public OverviewCommandHelper(Context context) {
        mContext = context;
        mAM = ActivityManagerWrapper.getInstance();
        mMainThreadExecutor = new MainThreadExecutor();
        mRecentsModel = RecentsModel.getInstance(mContext);

        homeIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(context.getPackageName())
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ResolveInfo info = context.getPackageManager().resolveActivity(homeIntent, 0);

        if (DEBUG_START_FALLBACK_ACTIVITY) {
            launcher = new ComponentName(context, RecentsActivity.class);
            homeIntent.addCategory(Intent.CATEGORY_DEFAULT)
                    .removeCategory(Intent.CATEGORY_HOME);
        } else {
            launcher = new ComponentName(context.getPackageName(), info.activityInfo.name);
        }

        // Clear the packageName as system can fail to dedupe it b/64108432
        homeIntent.setComponent(launcher).setPackage(null);
    }

    private void openRecents() {
        Intent intent = addToIntent(new Intent(homeIntent));
        mContext.startActivity(intent);
        initWhenReady();
    }

    @UiThread
    private void addFinishCommand(int requestId, int id, Runnable action) {
        if (requestId < mCurrentCommandId) {
            action.run();
        } else {
            mCurrentCommandFinishRunnables.put(id, action);
        }
    }

    @UiThread
    private void clearFinishCommand(int requestId, int id) {
        if (requestId == mCurrentCommandId) {
            mCurrentCommandFinishRunnables.remove(id);
        }
    }

    @UiThread
    private void initSwipeHandler(ActivityControlHelper helper, long time,
            Consumer<WindowTransformSwipeHandler> onAnimationInitCallback) {
        final int commandId = mCurrentCommandId;
        RunningTaskInfo taskInfo = ActivityManagerWrapper.getInstance().getRunningTask();
        final WindowTransformSwipeHandler handler =
                new WindowTransformSwipeHandler(taskInfo, mContext, time, helper);

        // Preload the plan
        mRecentsModel.loadTasks(taskInfo.id, null);
        mWindowTransformSwipeHandler = handler;

        mTempTaskTargetRect.setEmpty();
        handler.setGestureEndCallback(() -> {
            if (mWindowTransformSwipeHandler == handler) {
                mWindowTransformSwipeHandler = null;
                mTempTaskTargetRect.setEmpty();
            }
            clearFinishCommand(commandId, RID_RESET_SWIPE_HANDLER);
            clearFinishCommand(commandId, RID_CANCEL_CONTROLLER);
        });
        handler.initWhenReady();
        addFinishCommand(commandId, RID_RESET_SWIPE_HANDLER, handler::reset);

        TraceHelper.beginSection(TAG);
        Runnable startActivity = () -> helper.startRecents(mContext, homeIntent,
                new AssistDataReceiver() {
                    @Override
                    public void onHandleAssistData(Bundle bundle) {
                        mRecentsModel.preloadAssistData(taskInfo.id, bundle);
                    }
                },
                new RecentsAnimationListener() {
                    public void onAnimationStart(
                            RecentsAnimationControllerCompat controller,
                            RemoteAnimationTargetCompat[] apps, Rect homeContentInsets,
                            Rect minimizedHomeBounds) {
                        if (mWindowTransformSwipeHandler == handler) {
                            TraceHelper.partitionSection(TAG, "Received");
                            handler.onRecentsAnimationStart(controller, apps, homeContentInsets,
                                    minimizedHomeBounds);
                            mTempTaskTargetRect.set(handler.getTargetRect(mWindowSize));

                            mMainThreadExecutor.execute(() -> {
                                addFinishCommand(commandId,
                                        RID_CANCEL_CONTROLLER, () -> controller.finish(true));
                                if (commandId == mCurrentCommandId) {
                                    onAnimationInitCallback.accept(handler);
                                }
                            });
                        } else {
                            TraceHelper.endSection(TAG, "Finishing no handler");
                            controller.finish(false /* toHome */);
                        }
                    }

                    public void onAnimationCanceled() {
                        TraceHelper.endSection(TAG, "Cancelled: " + handler);
                        if (mWindowTransformSwipeHandler == handler) {
                            handler.onRecentsAnimationCanceled();
                        }
                    }
                });

        // We should almost always get touch-town on background thread. This is an edge case
        // when the background Choreographer has not yet initialized.
        BackgroundExecutor.get().submit(startActivity);
    }

    @UiThread
    private void startZoomOutAnim(final WindowTransformSwipeHandler handler) {
        final int commandId = mCurrentCommandId;
        ValueAnimator anim = ValueAnimator.ofInt(0, -handler.getTransitionLength());
        anim.addUpdateListener((a) -> handler.updateDisplacement((Integer) a.getAnimatedValue()));
        anim.addListener(new AnimationSuccessListener() {
            @Override
            public void onAnimationSuccess(Animator animator) {
                handler.onGestureEnded(0);
                clearFinishCommand(commandId, RID_CANCEL_ZOOM_OUT_ANIMATION);
            }
        });
        handler.onGestureStarted();
        anim.setDuration(RECENTS_LAUNCH_DURATION);
        anim.setInterpolator(Interpolators.AGGRESSIVE_EASE);
        anim.start();
        addFinishCommand(commandId, RID_CANCEL_ZOOM_OUT_ANIMATION, anim::cancel);
    }

    public void onOverviewToggle() {
        long time = SystemClock.elapsedRealtime();
        mMainThreadExecutor.execute(() -> {
            long elapsedTime = time - mLastToggleTime;
            mLastToggleTime = time;

            mCurrentCommandId++;
            mTempTaskTargetRect.round(mTaskTargetRect);
            boolean isQuickTap = elapsedTime < ViewConfiguration.getDoubleTapTimeout();
            int runnableCount = mCurrentCommandFinishRunnables.size();
            if (runnableCount > 0) {
                for (int i = 0; i < runnableCount; i++) {
                    mCurrentCommandFinishRunnables.valueAt(i).run();
                }
                mCurrentCommandFinishRunnables.clear();
                isQuickTap = true;
            }

            ActivityControlHelper helper = getActivityControlHelper();
            RecentsView recents = helper.getVisibleRecentsView();
            if (recents != null) {
                int childCount = recents.getChildCount();
                if (childCount != 0) {
                    ((TaskView) recents.getChildAt(childCount >= 2 ? 1 : 0)).launchTask(true);
                }

                // There are not enough tasks. Skip
                return;
            }

            if (isQuickTap) {
                // Focus last task. Start is on background thread so that all ActivityManager calls
                // are serialized
                BackgroundExecutor.get().submit(this::startLastTask);
                return;
            }
            if (helper.switchToRecentsIfVisible()) {
                return;
            }

            initSwipeHandler(helper, time, this::startZoomOutAnim);
        });
    }

    public void onOverviewShown() {
        getLauncher().runOnUiThread(() -> {
                    if (isOverviewAlmostVisible()) {
                        final RecentsView rv = getLauncher().getOverviewPanel();
                        rv.snapToTaskAfterNext();
                    } else {
                        openRecents();
                    }
                }
        );
    }

    public void onOverviewHidden() {
        getLauncher().runOnUiThread(() -> {
                    final RecentsView rv = getLauncher().getOverviewPanel();
                    rv.launchNextTask();
                }
        );
    }

    @WorkerThread
    private void startLastTask() {
        // TODO: This should go through recents model.
        List<RecentTaskInfo> tasks = mAM.getRecentTasks(2, UserHandle.myUserId());
        if (tasks.size() > 1) {
            RecentTaskInfo rti = tasks.get(1);

            final ActivityOptions options;
            if (!mTaskTargetRect.isEmpty()) {
                final Rect targetRect = new Rect(mTaskTargetRect);
                targetRect.offset(Utilities.isRtl(mContext.getResources())
                        ? - mTaskTargetRect.width() : mTaskTargetRect.width(), 0);
                final AppTransitionAnimationSpecCompat specCompat =
                        new AppTransitionAnimationSpecCompat(rti.id, null, targetRect);
                AppTransitionAnimationSpecsFuture specFuture =
                        new AppTransitionAnimationSpecsFuture(mMainThreadExecutor.getHandler()) {

                    @Override
                    public List<AppTransitionAnimationSpecCompat> composeSpecs() {
                        return Collections.singletonList(specCompat);
                    }
                };
                options = RecentsTransition.createAspectScaleAnimation(mContext,
                        mMainThreadExecutor.getHandler(), true /* scaleUp */,
                        specFuture, () -> {});
            } else {
                options = ActivityOptions.makeBasic();
            }
            mAM.startActivityFromRecents(rti.id, options);
        }
    }

    private boolean isOverviewAlmostVisible() {
        if (clearReference()) {
            return true;
        }
        if (!mAM.getRunningTask().topActivity.equals(launcher)) {
            return false;
        }
        Launcher launcher = getLauncher();
        return launcher != null && launcher.isStarted() && launcher.isInState(OVERVIEW);
    }

    private Launcher getLauncher() {
        return (Launcher) LauncherAppState.getInstance(mContext).getModel().getCallback();
    }

    @Override
    protected boolean init(Launcher launcher, boolean alreadyOnHome) {
        AbstractFloatingView.closeAllOpenViews(launcher, alreadyOnHome);
        launcher.getStateManager().goToState(OVERVIEW, alreadyOnHome);
        clearReference();
        return false;
    }

    public boolean isUsingFallbackActivity() {
        return DEBUG_START_FALLBACK_ACTIVITY;
    }

    public ActivityControlHelper getActivityControlHelper() {
        if (DEBUG_START_FALLBACK_ACTIVITY) {
            return new FallbackActivityControllerHelper();
        } else {
            return new LauncherActivityControllerHelper();
        }
    }
}
