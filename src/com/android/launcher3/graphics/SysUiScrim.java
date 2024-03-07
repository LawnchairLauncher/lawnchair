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
package com.android.launcher3.graphics;

import static android.graphics.Paint.DITHER_FLAG;
import static android.graphics.Paint.FILTER_BITMAP_FLAG;

import static com.android.launcher3.config.FeatureFlags.KEYGUARD_ANIMATION;

import android.animation.ObjectAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.DisplayMetrics;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.testing.shared.ResourceUtils;
import com.android.launcher3.util.ScreenOnTracker;
import com.android.launcher3.util.ScreenOnTracker.ScreenOnListener;
import com.android.launcher3.util.Themes;

/**
 * View scrim which draws behind hotseat and workspace
 */
public class SysUiScrim implements View.OnAttachStateChangeListener {

    /**
     * Receiver used to get a signal that the user unlocked their device.
     */
    private final ScreenOnListener mScreenOnListener = new ScreenOnListener() {
        @Override
        public void onScreenOnChanged(boolean isOn) {
            if (!isOn) {
                mAnimateScrimOnNextDraw = true;
            }
        }

        @Override
        public void onUserPresent() {
            // ACTION_USER_PRESENT is sent after onStart/onResume. This covers the case where
            // the user unlocked and the Launcher is not in the foreground.
            mAnimateScrimOnNextDraw = false;
        }
    };

    private static final int MAX_SYSUI_SCRIM_ALPHA = 255;
    private static final int ALPHA_MASK_BITMAP_WIDTH_DP = 2;

    private static final int BOTTOM_MASK_HEIGHT_DP = 200;
    private static final int TOP_MASK_HEIGHT_DP = 70;

    private boolean mDrawTopScrim, mDrawBottomScrim;

    private final RectF mTopMaskRect = new RectF();
    private final Paint mTopMaskPaint = new Paint(FILTER_BITMAP_FLAG | DITHER_FLAG);
    private final Bitmap mTopMaskBitmap;
    private final int mTopMaskHeight;

    private final RectF mBottomMaskRect = new RectF();
    private final Paint mBottomMaskPaint = new Paint(FILTER_BITMAP_FLAG | DITHER_FLAG);
    private final Bitmap mBottomMaskBitmap;
    private final int mBottomMaskHeight;

    private final View mRoot;
    private final BaseDraggingActivity mActivity;
    private final boolean mHideSysUiScrim;
    private boolean mSkipScrimAnimationForTest = false;

    private boolean mAnimateScrimOnNextDraw = false;
    private final AnimatedFloat mSysUiAnimMultiplier = new AnimatedFloat(this::reapplySysUiAlpha);
    private final AnimatedFloat mSysUiProgress = new AnimatedFloat(this::reapplySysUiAlpha);

    public SysUiScrim(View view) {
        mRoot = view;
        mActivity = BaseDraggingActivity.fromContext(view.getContext());
        DisplayMetrics dm = mActivity.getResources().getDisplayMetrics();

        mTopMaskHeight = ResourceUtils.pxFromDp(TOP_MASK_HEIGHT_DP, dm);
        mBottomMaskHeight = ResourceUtils.pxFromDp(BOTTOM_MASK_HEIGHT_DP, dm);
        mHideSysUiScrim = Themes.getAttrBoolean(view.getContext(), R.attr.isWorkspaceDarkText);

        mTopMaskBitmap = mHideSysUiScrim ? null : createDitheredAlphaMask(mTopMaskHeight,
                new int[]{0x3DFFFFFF, 0x0AFFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.7f, 1f});
        mTopMaskPaint.setColor(0xFF222222);
        mBottomMaskBitmap = mHideSysUiScrim ? null : createDitheredAlphaMask(mBottomMaskHeight,
                new int[]{0x00FFFFFF, 0x2FFFFFFF},
                new float[]{0f, 1f});

        if (!KEYGUARD_ANIMATION.get() && !mHideSysUiScrim) {
            view.addOnAttachStateChangeListener(this);
        }
    }

    /**
     * Draw the top and bottom scrims
     */
    public void draw(Canvas canvas) {
        if (!mHideSysUiScrim) {
            if (mSysUiProgress.value <= 0) {
                mAnimateScrimOnNextDraw = false;
                return;
            }

            if (mAnimateScrimOnNextDraw) {
                mSysUiAnimMultiplier.value = 0;
                reapplySysUiAlphaNoInvalidate();

                ObjectAnimator oa = mSysUiAnimMultiplier.animateToValue(1);
                oa.setDuration(600);
                oa.setStartDelay(mActivity.getWindow().getTransitionBackgroundFadeDuration());
                oa.start();
                mAnimateScrimOnNextDraw = false;
            }

            if (mDrawTopScrim) {
                canvas.drawBitmap(mTopMaskBitmap, null, mTopMaskRect, mTopMaskPaint);
            }
            if (mDrawBottomScrim) {
                canvas.drawBitmap(mBottomMaskBitmap, null, mBottomMaskRect, mBottomMaskPaint);
            }
        }
    }

    /**
     * Returns the sysui multiplier property for controlling fade in/out of the scrim
     */
    public AnimatedFloat getSysUIMultiplier() {
        return mSysUiAnimMultiplier;
    }

    /**
     * Returns the sysui progress property for controlling fade in/out of the scrim
     */
    public AnimatedFloat getSysUIProgress() {
        return mSysUiProgress;
    }

    /**
     * Determines whether to draw the top and/or bottom scrim based on new insets.
     *
     * In order for the bottom scrim to be drawn this 3 condition should be meet at the same time:
     * the device is in 3 button navigation, the taskbar is not present and the Hotseat is
     * horizontal
     */
    public void onInsetsChanged(Rect insets) {
        DeviceProfile dp = mActivity.getDeviceProfile();
        mDrawTopScrim = insets.top > 0;
        mDrawBottomScrim = !dp.isVerticalBarLayout() && !dp.isGestureMode && !dp.isTaskbarPresent;
    }

    @Override
    public void onViewAttachedToWindow(View view) {
        ScreenOnTracker.INSTANCE.get(mActivity).addListener(mScreenOnListener);
    }

    @Override
    public void onViewDetachedFromWindow(View view) {
        ScreenOnTracker.INSTANCE.get(mActivity).removeListener(mScreenOnListener);
    }

    /**
     * Set the width and height of the view being scrimmed
     */
    public void setSize(int w, int h) {
        mTopMaskRect.set(0, 0, w, mTopMaskHeight);
        mBottomMaskRect.set(0, h - mBottomMaskHeight, w, h);
    }

    /**
     * Sets whether the SysUiScrim should hide for testing.
     */
    @VisibleForTesting
    public void skipScrimAnimation() {
        mSkipScrimAnimationForTest = true;
        reapplySysUiAlpha();
    }

    private void reapplySysUiAlpha() {
        reapplySysUiAlphaNoInvalidate();
        if (!mHideSysUiScrim) {
            mRoot.invalidate();
        }
    }

    private void reapplySysUiAlphaNoInvalidate() {
        float factor = mSysUiProgress.value * mSysUiAnimMultiplier.value;
        if (mSkipScrimAnimationForTest) factor = 1f;
        mBottomMaskPaint.setAlpha(Math.round(MAX_SYSUI_SCRIM_ALPHA * factor));
        mTopMaskPaint.setAlpha(Math.round(MAX_SYSUI_SCRIM_ALPHA * factor));
    }

    private Bitmap createDitheredAlphaMask(int height, @ColorInt int[] colors, float[] positions) {
        DisplayMetrics dm = mActivity.getResources().getDisplayMetrics();
        int width = ResourceUtils.pxFromDp(ALPHA_MASK_BITMAP_WIDTH_DP, dm);
        Bitmap dst = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8);
        Canvas c = new Canvas(dst);
        Paint paint = new Paint(DITHER_FLAG);
        LinearGradient lg = new LinearGradient(0, 0, 0, height,
                colors, positions, Shader.TileMode.CLAMP);
        paint.setShader(lg);
        c.drawPaint(paint);
        return dst;
    }
}
