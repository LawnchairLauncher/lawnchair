/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.launcher3.QuickstepAppTransitionManagerImpl.RECENTS_LAUNCH_DURATION;
import static com.android.launcher3.QuickstepAppTransitionManagerImpl
        .STATUS_BAR_TRANSITION_DURATION;
import static com.android.launcher3.QuickstepAppTransitionManagerImpl
        .STATUS_BAR_TRANSITION_PRE_DELAY;
import static com.android.quickstep.TaskViewUtils.getRecentsWindowAnimator;
import static com.android.quickstep.TaskUtils.taskIsATargetWithMode;
import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.MODE_CLOSING;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.app.ActivityOptions;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAnimationRunner;
import com.android.launcher3.R;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.views.BaseDragLayer;
import com.android.quickstep.fallback.FallbackRecentsView;
import com.android.quickstep.fallback.RecentsRootView;
import com.android.quickstep.util.ClipAnimationHelper;
import com.android.quickstep.util.ObjectWrapper;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.ActivityOptionsCompat;
import com.android.systemui.shared.system.RemoteAnimationAdapterCompat;
import com.android.systemui.shared.system.RemoteAnimationRunnerCompat;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

/**
 * A recents activity that shows the recently launched tasks as swipable task cards.
 * See {@link com.android.quickstep.views.RecentsView}.
 */
public final class RecentsActivity extends BaseRecentsActivity {

    public static final String EXTRA_THUMBNAIL = "thumbnailData";
    public static final String EXTRA_TASK_ID = "taskID";

    private Handler mUiHandler = new Handler(Looper.getMainLooper());
    private RecentsRootView mRecentsRootView;
    private FallbackRecentsView mFallbackRecentsView;

    @Override
    protected void initViews() {
        setContentView(R.layout.fallback_recents_activity);
        mRecentsRootView = findViewById(R.id.drag_layer);
        mFallbackRecentsView = findViewById(R.id.overview_panel);
        mRecentsRootView.setup();
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode, Configuration newConfig) {
        onHandleConfigChanged();
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig);
    }

    public void onRootViewSizeChanged() {
        if (isInMultiWindowMode()) {
            onHandleConfigChanged();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (intent.getExtras() != null) {
            int taskID = intent.getIntExtra(EXTRA_TASK_ID, 0);
            IBinder thumbnail = intent.getExtras().getBinder(EXTRA_THUMBNAIL);
            if (taskID != 0 && thumbnail instanceof ObjectWrapper) {
                ThumbnailData thumbnailData = ((ObjectWrapper<ThumbnailData>) thumbnail).get();
                mFallbackRecentsView.showCurrentTask(taskID);
                mFallbackRecentsView.updateThumbnail(taskID, thumbnailData);
            }
        }
        intent.removeExtra(EXTRA_TASK_ID);
        intent.removeExtra(EXTRA_THUMBNAIL);
        super.onNewIntent(intent);
    }

    @Override
    protected void onHandleConfigChanged() {
        super.onHandleConfigChanged();
        mRecentsRootView.setup();
    }

    @Override
    protected void reapplyUi() {
        mRecentsRootView.dispatchInsets();
    }

    @Override
    protected DeviceProfile createDeviceProfile() {
        DeviceProfile dp = InvariantDeviceProfile.INSTANCE.get(this).getDeviceProfile(this);
        return (mRecentsRootView != null) && isInMultiWindowMode()
                ? dp.getMultiWindowProfile(this, mRecentsRootView.getLastKnownSize())
                : super.createDeviceProfile();
    }

    @Override
    public BaseDragLayer getDragLayer() {
        return mRecentsRootView;
    }

    @Override
    public View getRootView() {
        return mRecentsRootView;
    }

    @Override
    public <T extends View> T getOverviewPanel() {
        return (T) mFallbackRecentsView;
    }

    @Override
    public void returnToHomescreen() {
        super.returnToHomescreen();
        // TODO(b/137318995) This should go home, but doing so removes freeform windows
    }

    @Override
    public ActivityOptions getActivityLaunchOptions(final View v) {
        if (!(v instanceof TaskView)) {
            return null;
        }

        final TaskView taskView = (TaskView) v;
        RemoteAnimationRunnerCompat runner = new LauncherAnimationRunner(mUiHandler,
                true /* startAtFrontOfQueue */) {

            @Override
            public void onCreateAnimation(RemoteAnimationTargetCompat[] targetCompats,
                    AnimationResult result) {
                AnimatorSet anim = composeRecentsLaunchAnimator(taskView, targetCompats);
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mFallbackRecentsView.resetViewUI();
                    }
                });
                result.setAnimation(anim, RecentsActivity.this);
            }
        };
        return ActivityOptionsCompat.makeRemoteAnimation(new RemoteAnimationAdapterCompat(
                runner, RECENTS_LAUNCH_DURATION,
                RECENTS_LAUNCH_DURATION - STATUS_BAR_TRANSITION_DURATION
                        - STATUS_BAR_TRANSITION_PRE_DELAY));
    }

    /**
     * Composes the animations for a launch from the recents list if possible.
     */
    private AnimatorSet composeRecentsLaunchAnimator(TaskView taskView,
            RemoteAnimationTargetCompat[] targets) {
        AnimatorSet target = new AnimatorSet();
        boolean activityClosing = taskIsATargetWithMode(targets, getTaskId(), MODE_CLOSING);
        ClipAnimationHelper helper = new ClipAnimationHelper(this);
        target.play(getRecentsWindowAnimator(taskView, !activityClosing, targets, helper)
                .setDuration(RECENTS_LAUNCH_DURATION));

        // Found a visible recents task that matches the opening app, lets launch the app from there
        if (activityClosing) {
            Animator adjacentAnimation = mFallbackRecentsView
                    .createAdjacentPageAnimForTaskLaunch(taskView, helper);
            adjacentAnimation.setInterpolator(Interpolators.TOUCH_RESPONSE_INTERPOLATOR);
            adjacentAnimation.setDuration(RECENTS_LAUNCH_DURATION);
            adjacentAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mFallbackRecentsView.resetTaskVisuals();
                }
            });
            target.play(adjacentAnimation);
        }
        return target;
    }

    @Override
    protected void onStart() {
        // Set the alpha to 1 before calling super, as it may get set back to 0 due to
        // onActivityStart callback.
        mFallbackRecentsView.setContentAlpha(1);
        super.onStart();
        mFallbackRecentsView.resetTaskVisuals();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mFallbackRecentsView.reset();
    }

    public void onTaskLaunched() {
        mFallbackRecentsView.resetTaskVisuals();
    }
}
