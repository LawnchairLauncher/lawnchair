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

package com.android.wm.shell.onehanded;

import static com.android.internal.jank.Cuj.CUJ_ONE_HANDED_ENTER_TRANSITION;
import static com.android.internal.jank.Cuj.CUJ_ONE_HANDED_EXIT_TRANSITION;
import static com.android.wm.shell.onehanded.OneHandedAnimationController.TRANSITION_DIRECTION_EXIT;
import static com.android.wm.shell.onehanded.OneHandedAnimationController.TRANSITION_DIRECTION_TRIGGER;

import android.content.Context;
import android.graphics.Rect;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.view.SurfaceControl;
import android.window.DisplayAreaAppearedInfo;
import android.window.DisplayAreaInfo;
import android.window.DisplayAreaOrganizer;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.internal.jank.Cuj.CujType;
import com.android.internal.jank.InteractionJankMonitor;
import com.android.wm.shell.R;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.ShellExecutor;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Manages OneHanded display areas such as offset.
 *
 * This class listens on {@link DisplayAreaOrganizer} callbacks for windowing mode change
 * both to and from OneHanded and issues corresponding animation if applicable.
 * Normally, we apply series of {@link SurfaceControl.Transaction} when the animator is running
 * and files a final {@link WindowContainerTransaction} at the end of the transition.
 *
 * This class is also responsible for translating one handed operations within SysUI component
 */
public class OneHandedDisplayAreaOrganizer extends DisplayAreaOrganizer {
    private static final String TAG = "OneHandedDisplayAreaOrganizer";
    private static final String ONE_HANDED_MODE_TRANSLATE_ANIMATION_DURATION =
            "persist.debug.one_handed_translate_animation_duration";

    private DisplayLayout mDisplayLayout = new DisplayLayout();

    private final Rect mLastVisualDisplayBounds = new Rect();
    private final Rect mDefaultDisplayBounds = new Rect();
    private final OneHandedSettingsUtil mOneHandedSettingsUtil;
    private final InteractionJankMonitor mJankMonitor;
    private final Context mContext;

    private boolean mIsReady;
    private float mLastVisualOffset = 0;
    private int mEnterExitAnimationDurationMs;

    private ArrayMap<WindowContainerToken, SurfaceControl> mDisplayAreaTokenMap = new ArrayMap();
    private OneHandedAnimationController mAnimationController;
    private OneHandedSurfaceTransactionHelper.SurfaceControlTransactionFactory
            mSurfaceControlTransactionFactory;
    private OneHandedTutorialHandler mTutorialHandler;
    private List<OneHandedTransitionCallback> mTransitionCallbacks = new ArrayList<>();

    @VisibleForTesting
    OneHandedAnimationCallback mOneHandedAnimationCallback =
            new OneHandedAnimationCallback() {
                @Override
                public void onOneHandedAnimationStart(
                        OneHandedAnimationController.OneHandedTransitionAnimator animator) {
                    final boolean isEntering = animator.getTransitionDirection()
                            == TRANSITION_DIRECTION_TRIGGER;
                    if (!mTransitionCallbacks.isEmpty()) {
                        for (int i = mTransitionCallbacks.size() - 1; i >= 0; i--) {
                            final OneHandedTransitionCallback cb = mTransitionCallbacks.get(i);
                            cb.onStartTransition(isEntering);
                        }
                    }
                }

                @Override
                public void onOneHandedAnimationEnd(SurfaceControl.Transaction tx,
                        OneHandedAnimationController.OneHandedTransitionAnimator animator) {
                    mAnimationController.removeAnimator(animator.getToken());
                    final boolean isEntering = animator.getTransitionDirection()
                            == TRANSITION_DIRECTION_TRIGGER;
                    if (mAnimationController.isAnimatorsConsumed()) {
                        endCUJTracing(isEntering ? CUJ_ONE_HANDED_ENTER_TRANSITION
                                : CUJ_ONE_HANDED_EXIT_TRANSITION);
                        finishOffset((int) animator.getDestinationOffset(),
                                animator.getTransitionDirection());
                    }
                }

                @Override
                public void onOneHandedAnimationCancel(
                        OneHandedAnimationController.OneHandedTransitionAnimator animator) {
                    mAnimationController.removeAnimator(animator.getToken());
                    final boolean isEntering = animator.getTransitionDirection()
                            == TRANSITION_DIRECTION_TRIGGER;
                    if (mAnimationController.isAnimatorsConsumed()) {
                        cancelCUJTracing(isEntering ? CUJ_ONE_HANDED_ENTER_TRANSITION
                                : CUJ_ONE_HANDED_EXIT_TRANSITION);
                        finishOffset((int) animator.getDestinationOffset(),
                                animator.getTransitionDirection());
                    }
                }
            };

    /**
     * Constructor of OneHandedDisplayAreaOrganizer
     */
    public OneHandedDisplayAreaOrganizer(Context context,
            DisplayLayout displayLayout,
            OneHandedSettingsUtil oneHandedSettingsUtil,
            OneHandedAnimationController animationController,
            OneHandedTutorialHandler tutorialHandler,
            InteractionJankMonitor jankMonitor,
            ShellExecutor mainExecutor) {
        super(mainExecutor);
        mContext = context;
        setDisplayLayout(displayLayout);
        mOneHandedSettingsUtil = oneHandedSettingsUtil;
        mAnimationController = animationController;
        mJankMonitor = jankMonitor;
        final int animationDurationConfig = context.getResources().getInteger(
                R.integer.config_one_handed_translate_animation_duration);
        mEnterExitAnimationDurationMs =
                SystemProperties.getInt(ONE_HANDED_MODE_TRANSLATE_ANIMATION_DURATION,
                        animationDurationConfig);
        mSurfaceControlTransactionFactory = SurfaceControl.Transaction::new;
        mTutorialHandler = tutorialHandler;
    }

    @Override
    public void onDisplayAreaAppeared(@NonNull DisplayAreaInfo displayAreaInfo,
            @NonNull SurfaceControl leash) {
        leash.setUnreleasedWarningCallSite(
                "OneHandedSiaplyAreaOrganizer.onDisplayAreaAppeared");
        mDisplayAreaTokenMap.put(displayAreaInfo.token, leash);
    }

    @Override
    public void onDisplayAreaVanished(@NonNull DisplayAreaInfo displayAreaInfo) {
        final SurfaceControl leash = mDisplayAreaTokenMap.get(displayAreaInfo.token);
        if (leash != null) {
            leash.release();
        }
        mDisplayAreaTokenMap.remove(displayAreaInfo.token);
    }

    @Override
    public List<DisplayAreaAppearedInfo> registerOrganizer(int displayAreaFeature) {
        final List<DisplayAreaAppearedInfo> displayAreaInfos =
                super.registerOrganizer(displayAreaFeature);
        for (int i = 0; i < displayAreaInfos.size(); i++) {
            final DisplayAreaAppearedInfo info = displayAreaInfos.get(i);
            onDisplayAreaAppeared(info.getDisplayAreaInfo(), info.getLeash());
        }
        mIsReady = true;
        updateDisplayBounds();
        return displayAreaInfos;
    }

    @Override
    public void unregisterOrganizer() {
        super.unregisterOrganizer();
        mIsReady = false;
        resetWindowsOffset();
    }

    boolean isReady() {
        return mIsReady;
    }

    /**
     * Handler for display rotation changes by {@link DisplayLayout}
     *
     * @param context    Any context
     * @param toRotation target rotation of the display (after rotating).
     * @param wct        A task transaction {@link WindowContainerTransaction} from
     *                   {@link DisplayChangeController} to populate.
     */
    public void onRotateDisplay(Context context, int toRotation, WindowContainerTransaction wct) {
        if (mDisplayLayout.rotation() == toRotation) {
            return;
        }
        mDisplayLayout.rotateTo(context.getResources(), toRotation);
        updateDisplayBounds();
        finishOffset(0, TRANSITION_DIRECTION_EXIT);
    }

    /**
     * Offset the windows by a given offset on Y-axis, triggered also from screen rotation.
     * Directly perform manipulation/offset on the leash.
     */
    public void scheduleOffset(int xOffset, int yOffset) {
        final float fromPos = mLastVisualOffset;
        final int direction = yOffset > 0
                ? TRANSITION_DIRECTION_TRIGGER
                : TRANSITION_DIRECTION_EXIT;
        if (direction == TRANSITION_DIRECTION_TRIGGER) {
            beginCUJTracing(CUJ_ONE_HANDED_ENTER_TRANSITION, "enterOneHanded");
        } else {
            beginCUJTracing(CUJ_ONE_HANDED_EXIT_TRANSITION, "stopOneHanded");
        }
        mDisplayAreaTokenMap.forEach(
                (token, leash) -> {
                    animateWindows(token, leash, fromPos, yOffset, direction,
                            mEnterExitAnimationDurationMs);
                });
        mLastVisualOffset = yOffset;
    }

    @VisibleForTesting
    void resetWindowsOffset() {
        final SurfaceControl.Transaction tx =
                mSurfaceControlTransactionFactory.getTransaction();
        mDisplayAreaTokenMap.forEach(
                (token, leash) -> {
                    final OneHandedAnimationController.OneHandedTransitionAnimator animator =
                            mAnimationController.getAnimatorMap().remove(token);
                    if (animator != null && animator.isRunning()) {
                        animator.cancel();
                    }
                    tx.setPosition(leash, 0, 0)
                            .setWindowCrop(leash, -1, -1)
                            .setCornerRadius(leash, -1);
                });
        tx.apply();
        mLastVisualOffset = 0;
        mLastVisualDisplayBounds.offsetTo(0, 0);
    }

    private void animateWindows(WindowContainerToken token, SurfaceControl leash, float fromPos,
            float toPos, @OneHandedAnimationController.TransitionDirection int direction,
            int durationMs) {
        final OneHandedAnimationController.OneHandedTransitionAnimator animator =
                mAnimationController.getAnimator(token, leash, fromPos, toPos,
                        mLastVisualDisplayBounds);
        if (animator != null) {
            animator.setTransitionDirection(direction)
                    .addOneHandedAnimationCallback(mOneHandedAnimationCallback)
                    .addOneHandedAnimationCallback(mTutorialHandler)
                    .setDuration(durationMs)
                    .start();
        }
    }

    @VisibleForTesting
    void finishOffset(int offset, @OneHandedAnimationController.TransitionDirection int direction) {
        if (direction == TRANSITION_DIRECTION_EXIT) {
            // We must do this to ensure reset property for leash when exit one handed mode
            resetWindowsOffset();
        }
        mLastVisualOffset = direction == TRANSITION_DIRECTION_TRIGGER ? offset : 0;
        mLastVisualDisplayBounds.offsetTo(0, Math.round(mLastVisualOffset));
        for (int i = mTransitionCallbacks.size() - 1; i >= 0; i--) {
            final OneHandedTransitionCallback cb = mTransitionCallbacks.get(i);
            if (direction == TRANSITION_DIRECTION_TRIGGER) {
                cb.onStartFinished(getLastVisualDisplayBounds());
            } else {
                cb.onStopFinished(getLastVisualDisplayBounds());
            }
        }
    }

    /**
     * The latest visual bounds of displayArea translated
     *
     * @return Rect latest finish_offset
     */
    private Rect getLastVisualDisplayBounds() {
        return mLastVisualDisplayBounds;
    }

    @VisibleForTesting
    @Nullable
    Rect getLastDisplayBounds() {
        return mLastVisualDisplayBounds;
    }

    public DisplayLayout getDisplayLayout() {
        return mDisplayLayout;
    }

    @VisibleForTesting
    void setDisplayLayout(@NonNull DisplayLayout displayLayout) {
        mDisplayLayout.set(displayLayout);
        updateDisplayBounds();
    }

    @VisibleForTesting
    ArrayMap<WindowContainerToken, SurfaceControl> getDisplayAreaTokenMap() {
        return mDisplayAreaTokenMap;
    }

    @VisibleForTesting
    void updateDisplayBounds() {
        mDefaultDisplayBounds.set(0, 0, mDisplayLayout.width(), mDisplayLayout.height());
        mLastVisualDisplayBounds.set(mDefaultDisplayBounds);
    }

    /**
     * Register transition callback
     */
    public void registerTransitionCallback(OneHandedTransitionCallback callback) {
        mTransitionCallbacks.add(callback);
    }

    void beginCUJTracing(@CujType int cujType, @Nullable String tag) {
        final Map.Entry<WindowContainerToken, SurfaceControl> firstEntry =
                getDisplayAreaTokenMap().entrySet().iterator().next();
        final InteractionJankMonitor.Configuration.Builder builder =
                InteractionJankMonitor.Configuration.Builder.withSurface(
                        cujType, mContext, firstEntry.getValue());
        if (!TextUtils.isEmpty(tag)) {
            builder.setTag(tag);
        }
        mJankMonitor.begin(builder);
    }

    void endCUJTracing(@CujType int cujType) {
        mJankMonitor.end(cujType);
    }

    void cancelCUJTracing(@CujType int cujType) {
        mJankMonitor.cancel(cujType);
    }

    void dump(@NonNull PrintWriter pw) {
        final String innerPrefix = "  ";
        pw.println(TAG);
        pw.print(innerPrefix + "mDisplayLayout.rotation()=");
        pw.println(mDisplayLayout.rotation());
        pw.print(innerPrefix + "mDisplayAreaTokenMap=");
        pw.println(mDisplayAreaTokenMap);
        pw.print(innerPrefix + "mDefaultDisplayBounds=");
        pw.println(mDefaultDisplayBounds);
        pw.print(innerPrefix + "mIsReady=");
        pw.println(mIsReady);
        pw.print(innerPrefix + "mLastVisualDisplayBounds=");
        pw.println(mLastVisualDisplayBounds);
        pw.print(innerPrefix + "mLastVisualOffset=");
        pw.println(mLastVisualOffset);

        if (mAnimationController != null) {
            mAnimationController.dump(pw);
        }
    }
}
