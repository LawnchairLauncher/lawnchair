/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.taskbar;

import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_NAV_BAR_HIDDEN;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Outline;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewOutlineProvider;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.anim.RevealOutlineAnimation;
import com.android.launcher3.anim.RoundedRectRevealOutlineProvider;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.MultiPropertyFactory;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.quickstep.NavHandle;
import com.android.systemui.shared.navigationbar.RegionSamplingHelper;
import com.android.systemui.shared.system.QuickStepContract.SystemUiStateFlags;

import java.io.PrintWriter;

/**
 * Handles properties/data collection, then passes the results to our stashed handle View to render.
 */
public class StashedHandleViewController implements TaskbarControllers.LoggableTaskbarController,
        NavHandle {

    public static final int ALPHA_INDEX_STASHED = 0;
    public static final int ALPHA_INDEX_HOME_DISABLED = 1;
    public static final int ALPHA_INDEX_ASSISTANT_INVOKED = 2;
    public static final int ALPHA_INDEX_HIDDEN_WHILE_DREAMING = 3;
    private static final int NUM_ALPHA_CHANNELS = 4;

    // Values for long press animations, picked to most closely match navbar spec.
    private static final float SCALE_TOUCH_ANIMATION_SHRINK = 0.85f;
    private static final float SCALE_TOUCH_ANIMATION_EXPAND = 1.18f;

    /**
     * The SharedPreferences key for whether the stashed handle region is dark.
     */
    private static final String SHARED_PREFS_STASHED_HANDLE_REGION_DARK_KEY =
            "stashed_handle_region_is_dark";

    private final TaskbarActivityContext mActivity;
    private final SharedPreferences mPrefs;
    private final StashedHandleView mStashedHandleView;
    private int mStashedHandleWidth;
    private final int mStashedHandleHeight;
    private RegionSamplingHelper mRegionSamplingHelper;
    private final MultiValueAlpha mTaskbarStashedHandleAlpha;
    private final AnimatedFloat mTaskbarStashedHandleHintScale = new AnimatedFloat(
            this::updateStashedHandleHintScale);

    // Initialized in init.
    private TaskbarControllers mControllers;
    private int mTaskbarSize;

    // The bounds we want to clip to in the settled state when showing the stashed handle.
    private final Rect mStashedHandleBounds = new Rect();
    private float mStashedHandleRadius;

    // When the reveal animation is cancelled, we can assume it's about to create a new animation,
    // which should start off at the same point the cancelled one left off.
    private float mStartProgressForNextRevealAnim;
    private boolean mWasLastRevealAnimReversed;

    // States that affect whether region sampling is enabled or not
    private boolean mIsStashed;
    private boolean mIsLumaSamplingEnabled;
    private boolean mTaskbarHidden;

    private float mTranslationYForSwipe;
    private float mTranslationYForStash;

    public StashedHandleViewController(TaskbarActivityContext activity,
            StashedHandleView stashedHandleView) {
        mActivity = activity;
        mPrefs = LauncherPrefs.getPrefs(mActivity);
        mStashedHandleView = stashedHandleView;
        mTaskbarStashedHandleAlpha = new MultiValueAlpha(mStashedHandleView, NUM_ALPHA_CHANNELS);
        mTaskbarStashedHandleAlpha.setUpdateVisibility(true);
        mStashedHandleView.updateHandleColor(
                mPrefs.getBoolean(SHARED_PREFS_STASHED_HANDLE_REGION_DARK_KEY, false),
                false /* animate */);
        final Resources resources = mActivity.getResources();
        mStashedHandleHeight = resources.getDimensionPixelSize(
                R.dimen.taskbar_stashed_handle_height);
    }

    public void init(TaskbarControllers controllers) {
        mControllers = controllers;
        DeviceProfile deviceProfile = mActivity.getDeviceProfile();
        Resources resources = mActivity.getResources();
        if (mActivity.isPhoneGestureNavMode() || mActivity.isTinyTaskbar()) {
            mTaskbarSize = resources.getDimensionPixelSize(R.dimen.taskbar_phone_size);
            mStashedHandleWidth =
                    resources.getDimensionPixelSize(R.dimen.taskbar_stashed_small_screen);
        } else {
            mTaskbarSize = deviceProfile.taskbarHeight;
            mStashedHandleWidth = resources
                    .getDimensionPixelSize(R.dimen.taskbar_stashed_handle_width);
        }
        int taskbarBottomMargin = deviceProfile.taskbarBottomMargin;
        mStashedHandleView.getLayoutParams().height = mTaskbarSize + taskbarBottomMargin;

        mTaskbarStashedHandleAlpha.get(ALPHA_INDEX_STASHED).setValue(
                mActivity.isPhoneGestureNavMode() ? 1 : 0);
        mTaskbarStashedHandleHintScale.updateValue(1f);

        final int stashedTaskbarHeight = mControllers.taskbarStashController.getStashedHeight();
        mStashedHandleView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                final int stashedCenterX = view.getWidth() / 2;
                final int stashedCenterY = view.getHeight() - stashedTaskbarHeight / 2;
                mStashedHandleBounds.set(
                        stashedCenterX - mStashedHandleWidth / 2,
                        stashedCenterY - mStashedHandleHeight / 2,
                        stashedCenterX + mStashedHandleWidth / 2,
                        stashedCenterY + mStashedHandleHeight / 2);
                mStashedHandleView.updateSampledRegion(mStashedHandleBounds);
                mStashedHandleRadius = view.getHeight() / 2f;
                outline.setRoundRect(mStashedHandleBounds, mStashedHandleRadius);
            }
        });

        mStashedHandleView.addOnLayoutChangeListener((view, i, i1, i2, i3, i4, i5, i6, i7) -> {
            final int stashedCenterX = view.getWidth() / 2;
            final int stashedCenterY = view.getHeight() - stashedTaskbarHeight / 2;

            view.setPivotX(stashedCenterX);
            view.setPivotY(stashedCenterY);
        });
        initRegionSampler();
        if (mActivity.isPhoneGestureNavMode()) {
            onIsStashedChanged(true);
        }
    }

    /**
     * Returns the stashed handle bounds.
     * @param out The destination rect.
     */
    public void getStashedHandleBounds(Rect out) {
        out.set(mStashedHandleBounds);
    }

    private void initRegionSampler() {
        mRegionSamplingHelper = new RegionSamplingHelper(mStashedHandleView,
                new RegionSamplingHelper.SamplingCallback() {
                    @Override
                    public void onRegionDarknessChanged(boolean isRegionDark) {
                        mStashedHandleView.updateHandleColor(isRegionDark, true /* animate */);
                        mPrefs.edit().putBoolean(SHARED_PREFS_STASHED_HANDLE_REGION_DARK_KEY,
                                isRegionDark).apply();
                    }

                    @Override
                    public Rect getSampledRegion(View sampledView) {
                        return mStashedHandleView.getSampledRegion();
                    }
                }, Executors.UI_HELPER_EXECUTOR);
    }


    public void onDestroy() {
        mRegionSamplingHelper.stopAndDestroy();
        mRegionSamplingHelper = null;
    }

    public MultiPropertyFactory<View> getStashedHandleAlpha() {
        return mTaskbarStashedHandleAlpha;
    }

    public AnimatedFloat getStashedHandleHintScale() {
        return mTaskbarStashedHandleHintScale;
    }

    /**
     * Creates and returns a {@link RevealOutlineAnimation} Animator that updates the stashed handle
     * shape and size. When stashed, the shape is a thin rounded pill. When unstashed, the shape
     * morphs into the size of where the taskbar icons will be.
     */
    public Animator createRevealAnimToIsStashed(boolean isStashed) {
        Rect visualBounds = new Rect(mControllers.taskbarViewController.getIconLayoutBounds());
        float startRadius = mStashedHandleRadius;

        if (DisplayController.isTransientTaskbar(mActivity)) {
            // Account for the full visual height of the transient taskbar.
            int heightDiff = (mTaskbarSize - visualBounds.height()) / 2;
            visualBounds.top -= heightDiff;
            visualBounds.bottom += heightDiff;

            startRadius = visualBounds.height() / 2f;
        }

        final RevealOutlineAnimation handleRevealProvider = new RoundedRectRevealOutlineProvider(
                startRadius, mStashedHandleRadius, visualBounds, mStashedHandleBounds);

        boolean isReversed = !isStashed;
        boolean changingDirection = mWasLastRevealAnimReversed != isReversed;
        mWasLastRevealAnimReversed = isReversed;
        if (changingDirection) {
            mStartProgressForNextRevealAnim = 1f - mStartProgressForNextRevealAnim;
        }

        ValueAnimator revealAnim = handleRevealProvider.createRevealAnimator(mStashedHandleView,
                isReversed, mStartProgressForNextRevealAnim);
        revealAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mStartProgressForNextRevealAnim = ((ValueAnimator) animation).getAnimatedFraction();
            }
        });
        return revealAnim;
    }

    /** Called when taskbar is stashed or unstashed. */
    public void onIsStashedChanged(boolean isStashed) {
        mIsStashed = isStashed;
        updateSamplingState();
    }

    public void onNavigationBarLumaSamplingEnabled(int displayId, boolean enable) {
        if (DEFAULT_DISPLAY != displayId) {
            return;
        }

        mIsLumaSamplingEnabled = enable;
        updateSamplingState();
    }

    private void updateSamplingState() {
        updateRegionSamplingWindowVisibility();
        if (shouldSample()) {
            mStashedHandleView.updateSampledRegion(mStashedHandleBounds);
            mRegionSamplingHelper.start(mStashedHandleView.getSampledRegion());
        } else {
            mRegionSamplingHelper.stop();
        }
    }

    private boolean shouldSample() {
        return mIsStashed && mIsLumaSamplingEnabled;
    }

    protected void updateStashedHandleHintScale() {
        mStashedHandleView.setScaleX(mTaskbarStashedHandleHintScale.value);
        mStashedHandleView.setScaleY(mTaskbarStashedHandleHintScale.value);
    }

    /**
     * Sets the translation of the stashed handle during the swipe up gesture.
     */
    protected void setTranslationYForSwipe(float transY) {
        mTranslationYForSwipe = transY;
        updateTranslationY();
    }

    /**
     * Sets the translation of the stashed handle during the spring on stash animation.
     */
    protected void setTranslationYForStash(float transY) {
        mTranslationYForStash = transY;
        updateTranslationY();
    }

    private void updateTranslationY() {
        mStashedHandleView.setTranslationY(mTranslationYForSwipe + mTranslationYForStash);
    }

    /**
     * Should be called when the home button is disabled, so we can hide this handle as well.
     */
    public void setIsHomeButtonDisabled(boolean homeDisabled) {
        mTaskbarStashedHandleAlpha.get(ALPHA_INDEX_HOME_DISABLED).setValue(
                homeDisabled ? 0 : 1);
    }

    public void updateStateForSysuiFlags(@SystemUiStateFlags long systemUiStateFlags) {
        mTaskbarHidden = (systemUiStateFlags & SYSUI_STATE_NAV_BAR_HIDDEN) != 0;
        updateRegionSamplingWindowVisibility();
    }

    private void updateRegionSamplingWindowVisibility() {
        mRegionSamplingHelper.setWindowVisible(shouldSample() && !mTaskbarHidden);
    }

    public boolean isStashedHandleVisible() {
        return mStashedHandleView.getVisibility() == View.VISIBLE;
    }

    @Override
    public void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "StashedHandleViewController:");

        pw.println(prefix + "\tisStashedHandleVisible=" + isStashedHandleVisible());
        pw.println(prefix + "\tmStashedHandleWidth=" + mStashedHandleWidth);
        pw.println(prefix + "\tmStashedHandleHeight=" + mStashedHandleHeight);
        mRegionSamplingHelper.dump(prefix, pw);
    }

    @Override
    public void animateNavBarLongPress(boolean isTouchDown, boolean shrink, long durationMs) {
        float targetScale;
        if (isTouchDown) {
            targetScale = shrink ? SCALE_TOUCH_ANIMATION_SHRINK : SCALE_TOUCH_ANIMATION_EXPAND;
        } else {
            targetScale = 1f;
        }
        mStashedHandleView.animateScale(targetScale, durationMs);
    }

    @Override
    public boolean isNavHandleStashedTaskbar() {
        return true;
    }

    @Override
    public boolean canNavHandleBeLongPressed() {
        return isStashedHandleVisible();
    }

    @Override
    public int getNavHandleWidth(Context context) {
        return mStashedHandleWidth;
    }
}
