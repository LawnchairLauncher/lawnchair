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

import static android.view.View.LAYER_TYPE_HARDWARE;
import static android.view.View.LAYER_TYPE_NONE;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

import static com.android.wm.shell.onehanded.OneHandedState.STATE_ACTIVE;
import static com.android.wm.shell.onehanded.OneHandedState.STATE_ENTERING;
import static com.android.wm.shell.onehanded.OneHandedState.STATE_EXITING;
import static com.android.wm.shell.onehanded.OneHandedState.STATE_NONE;

import android.animation.ValueAnimator;
import android.annotation.Nullable;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.android.internal.annotations.VisibleForTesting;
import com.android.wm.shell.R;
import com.android.wm.shell.common.DisplayLayout;

import java.io.PrintWriter;

/**
 * Handles tutorial visibility and synchronized transition for One Handed operations,
 * TargetViewContainer only be created and always attach to window,
 * detach TargetViewContainer from window after exiting one handed mode.
 */
public class OneHandedTutorialHandler implements OneHandedTransitionCallback,
        OneHandedState.OnStateChangedListener, OneHandedAnimationCallback {
    private static final String TAG = "OneHandedTutorialHandler";
    private static final float START_TRANSITION_FRACTION = 0.6f;

    private final float mTutorialHeightRatio;
    private final WindowManager mWindowManager;
    private final BackgroundWindowManager mBackgroundWindowManager;

    private @OneHandedState.State int mCurrentState;
    private int mTutorialAreaHeight;

    private Context mContext;
    private Rect mDisplayBounds;
    private ValueAnimator mAlphaAnimator;
    private @Nullable View mTutorialView;
    private @Nullable ViewGroup mTargetViewContainer;

    private float mAlphaTransitionStart;
    private int mAlphaAnimationDurationMs;

    public OneHandedTutorialHandler(Context context, OneHandedSettingsUtil settingsUtil,
            WindowManager windowManager, BackgroundWindowManager backgroundWindowManager) {
        mContext = context;
        mWindowManager = windowManager;
        mBackgroundWindowManager = backgroundWindowManager;
        mTutorialHeightRatio = settingsUtil.getTranslationFraction(context);
        mAlphaAnimationDurationMs = settingsUtil.getTransitionDuration(context);
    }

    @Override
    public void onOneHandedAnimationCancel(
            OneHandedAnimationController.OneHandedTransitionAnimator animator) {
        if (mAlphaAnimator != null) {
            mAlphaAnimator.cancel();
        }
    }

    @Override
    public void onAnimationUpdate(SurfaceControl.Transaction tx, float xPos, float yPos) {
        if (!isAttached()) {
            return;
        }
        if (yPos < mAlphaTransitionStart) {
            checkTransitionEnd();
            return;
        }
        if (mAlphaAnimator == null || mAlphaAnimator.isStarted() || mAlphaAnimator.isRunning()) {
            return;
        }
        mAlphaAnimator.start();
    }

    @Override
    public void onStartFinished(Rect bounds) {
        fillBackgroundColor();
    }

    @Override
    public void onStopFinished(Rect bounds) {
        removeBackgroundSurface();
    }

    @Override
    public void onStateChanged(int newState) {
        mCurrentState = newState;
        mBackgroundWindowManager.onStateChanged(newState);
        switch (newState) {
            case STATE_ENTERING:
                createViewAndAttachToWindow(mContext);
                updateThemeColor();
                setupAlphaTransition(true /* isEntering */);
                break;
            case STATE_ACTIVE:
                checkTransitionEnd();
                setupAlphaTransition(false /* isEntering */);
                break;
            case STATE_EXITING:
            case STATE_NONE:
                checkTransitionEnd();
                removeTutorialFromWindowManager();
            default:
                break;
        }
    }

    /**
     * Called when onDisplayAdded() or onDisplayRemoved() callback.
     *
     * @param displayLayout The latest {@link DisplayLayout} representing current displayId
     */
    public void onDisplayChanged(DisplayLayout displayLayout) {
        mDisplayBounds = new Rect(0, 0, displayLayout.width(), displayLayout.height());
        mTutorialAreaHeight = Math.round(mDisplayBounds.height() * mTutorialHeightRatio);
        mAlphaTransitionStart = mTutorialAreaHeight * START_TRANSITION_FRACTION;
        mBackgroundWindowManager.onDisplayChanged(displayLayout);
    }

    @VisibleForTesting
    void createViewAndAttachToWindow(Context context) {
        if (isAttached()) {
            return;
        }
        mTutorialView = LayoutInflater.from(context).inflate(R.layout.one_handed_tutorial, null);
        mTargetViewContainer = new FrameLayout(context);
        mTargetViewContainer.setClipChildren(false);
        mTargetViewContainer.setAlpha(mCurrentState == STATE_ACTIVE ? 1.0f : 0.0f);
        mTargetViewContainer.addView(mTutorialView);
        mTargetViewContainer.setLayerType(LAYER_TYPE_HARDWARE, null);

        attachTargetToWindow();
    }

    /**
     * Adds the tutorial target view to the WindowManager and update its layout.
     */
    private void attachTargetToWindow() {
        try {
            mWindowManager.addView(mTargetViewContainer, getTutorialTargetLayoutParams());
            mBackgroundWindowManager.showBackgroundLayer();
        } catch (IllegalStateException e) {
            // This shouldn't happen, but if the target is already added, just update its
            // layout params.
            mWindowManager.updateViewLayout(mTargetViewContainer, getTutorialTargetLayoutParams());
        }
    }

    @VisibleForTesting
    void removeTutorialFromWindowManager() {
        if (!isAttached()) {
            return;
        }
        mTargetViewContainer.setLayerType(LAYER_TYPE_NONE, null);
        mWindowManager.removeViewImmediate(mTargetViewContainer);
        mTargetViewContainer = null;
    }

    @VisibleForTesting
    void removeBackgroundSurface() {
        mBackgroundWindowManager.removeBackgroundLayer();
    }

    /**
     * Returns layout params for the dismiss target, using the latest display metrics.
     */
    private WindowManager.LayoutParams getTutorialTargetLayoutParams() {
        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                mDisplayBounds.width(), mTutorialAreaHeight, 0, 0,
                WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        lp.privateFlags |= WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS;
        lp.setFitInsetsTypes(0 /* types */);
        lp.setTitle("one-handed-tutorial-overlay");
        return lp;
    }

    @VisibleForTesting
    boolean isAttached() {
        return mTargetViewContainer != null && mTargetViewContainer.isAttachedToWindow();
    }

    /**
     * onConfigurationChanged events for updating tutorial text.
     */
    public void onConfigurationChanged() {
        mBackgroundWindowManager.onConfigurationChanged();

        removeTutorialFromWindowManager();
        if (mCurrentState == STATE_ENTERING || mCurrentState == STATE_ACTIVE) {
            createViewAndAttachToWindow(mContext);
            fillBackgroundColor();
            updateThemeColor();
            checkTransitionEnd();
        }
    }

    private void updateThemeColor() {
        if (mTutorialView == null) {
            return;
        }

        final Context themedContext = new ContextThemeWrapper(mTutorialView.getContext(),
                com.android.internal.R.style.Theme_DeviceDefault_DayNight);
        final int textColorPrimary;
        final int themedTextColorSecondary;
        TypedArray ta = themedContext.obtainStyledAttributes(new int[]{
                com.android.internal.R.attr.textColorPrimary,
                com.android.internal.R.attr.textColorSecondary});
        textColorPrimary = ta.getColor(0, 0);
        themedTextColorSecondary = ta.getColor(1, 0);
        ta.recycle();

        final ImageView iconView = mTutorialView.findViewById(R.id.one_handed_tutorial_image);
        iconView.setImageTintList(ColorStateList.valueOf(textColorPrimary));

        final TextView tutorialTitle = mTutorialView.findViewById(R.id.one_handed_tutorial_title);
        final TextView tutorialDesc = mTutorialView.findViewById(
                R.id.one_handed_tutorial_description);
        tutorialTitle.setTextColor(textColorPrimary);
        tutorialDesc.setTextColor(themedTextColorSecondary);
    }

    private void fillBackgroundColor() {
        if (mTargetViewContainer == null || mBackgroundWindowManager == null) {
            return;
        }
        mTargetViewContainer.setBackgroundColor(
                mBackgroundWindowManager.getThemeColorForBackground());
    }

    private void setupAlphaTransition(boolean isEntering) {
        final float start = isEntering ? 0.0f : 1.0f;
        final float end = isEntering ? 1.0f : 0.0f;
        final int duration = isEntering ? mAlphaAnimationDurationMs : Math.round(
                mAlphaAnimationDurationMs * (1.0f - mTutorialHeightRatio));
        mAlphaAnimator = ValueAnimator.ofFloat(start, end);
        mAlphaAnimator.setInterpolator(new LinearInterpolator());
        mAlphaAnimator.setDuration(duration);
        mAlphaAnimator.addUpdateListener(
                animator -> mTargetViewContainer.setAlpha((float) animator.getAnimatedValue()));
    }

    private void checkTransitionEnd() {
        if (mAlphaAnimator != null && (mAlphaAnimator.isRunning() || mAlphaAnimator.isStarted())) {
            mAlphaAnimator.end();
            mAlphaAnimator.removeAllUpdateListeners();
            mAlphaAnimator = null;
        }
    }

    void dump(@NonNull PrintWriter pw) {
        final String innerPrefix = "  ";
        pw.println(TAG);
        pw.print(innerPrefix + "isAttached=");
        pw.println(isAttached());
        pw.print(innerPrefix + "mCurrentState=");
        pw.println(mCurrentState);
        pw.print(innerPrefix + "mDisplayBounds=");
        pw.println(mDisplayBounds);
        pw.print(innerPrefix + "mTutorialAreaHeight=");
        pw.println(mTutorialAreaHeight);
        pw.print(innerPrefix + "mAlphaTransitionStart=");
        pw.println(mAlphaTransitionStart);
        pw.print(innerPrefix + "mAlphaAnimationDurationMs=");
        pw.println(mAlphaAnimationDurationMs);

        if (mBackgroundWindowManager != null) {
            mBackgroundWindowManager.dump(pw);
        }
    }
}
