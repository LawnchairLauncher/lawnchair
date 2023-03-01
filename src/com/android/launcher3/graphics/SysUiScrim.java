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

import static com.android.launcher3.config.FeatureFlags.KEYGUARD_ANIMATION;
import static com.android.launcher3.icons.GraphicsUtils.setColorAlphaBound;

import android.animation.ObjectAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.util.FloatProperty;
import android.view.View;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.testing.shared.ResourceUtils;
import com.android.launcher3.util.DynamicResource;
import com.android.launcher3.util.ScreenOnTracker;
import com.android.launcher3.util.ScreenOnTracker.ScreenOnListener;
import com.android.launcher3.util.Themes;
import com.android.systemui.plugins.ResourceProvider;

/**
 * View scrim which draws behind hotseat and workspace
 */
public class SysUiScrim implements View.OnAttachStateChangeListener {

    public static final FloatProperty<SysUiScrim> SYSUI_PROGRESS =
            new FloatProperty<SysUiScrim>("sysUiProgress") {
                @Override
                public Float get(SysUiScrim scrim) {
                    return scrim.mSysUiProgress;
                }

                @Override
                public void setValue(SysUiScrim scrim, float value) {
                    scrim.setSysUiProgress(value);
                }
            };

    private static final FloatProperty<SysUiScrim> SYSUI_ANIM_MULTIPLIER =
            new FloatProperty<SysUiScrim>("sysUiAnimMultiplier") {
                @Override
                public Float get(SysUiScrim scrim) {
                    return scrim.mSysUiAnimMultiplier;
                }

                @Override
                public void setValue(SysUiScrim scrim, float value) {
                    scrim.mSysUiAnimMultiplier = value;
                    scrim.reapplySysUiAlpha();
                }
            };

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

    private static final int MAX_HOTSEAT_SCRIM_ALPHA = 100;
    private static final int ALPHA_MASK_HEIGHT_DP = 500;
    private static final int ALPHA_MASK_BITMAP_DP = 200;
    private static final int ALPHA_MASK_WIDTH_DP = 2;

    private boolean mDrawTopScrim, mDrawBottomScrim, mDrawWallpaperScrim;

    private final RectF mWallpaperScrimRect = new RectF();
    private final Paint mWallpaperScrimPaint = new Paint();
    private final RectF mFinalMaskRect = new RectF();
    private final Paint mBottomMaskPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Bitmap mBottomMask;
    private final int mMaskHeight;

    private final View mRoot;
    private final BaseDraggingActivity mActivity;
    private final Drawable mTopScrim;

    private float mSysUiProgress = 1;
    private boolean mHideSysUiScrim;

    private boolean mAnimateScrimOnNextDraw = false;
    private float mSysUiAnimMultiplier = 1;
    private int mWallpaperScrimMaxAlpha;

    public SysUiScrim(View view) {
        mRoot = view;
        mActivity = BaseDraggingActivity.fromContext(view.getContext());
        mMaskHeight = ResourceUtils.pxFromDp(ALPHA_MASK_BITMAP_DP,
                view.getResources().getDisplayMetrics());
        mTopScrim = Themes.getAttrDrawable(view.getContext(), R.attr.workspaceStatusBarScrim);
        if (mTopScrim != null) {
            mTopScrim.setDither(true);
            mBottomMask = createDitheredAlphaMask();
            mHideSysUiScrim = false;
        } else {
            mBottomMask = null;
            mHideSysUiScrim = true;
        }

        mDrawWallpaperScrim = FeatureFlags.ENABLE_WALLPAPER_SCRIM.get()
                && !Themes.getAttrBoolean(view.getContext(), R.attr.isMainColorDark)
                && !Themes.getAttrBoolean(view.getContext(), R.attr.isWorkspaceDarkText);
        ResourceProvider rp = DynamicResource.provider(view.getContext());
        int wallpaperScrimColor = rp.getColor(R.color.wallpaper_scrim_color);
        mWallpaperScrimMaxAlpha = Color.alpha(wallpaperScrimColor);
        mWallpaperScrimPaint.setColor(wallpaperScrimColor);

        view.addOnAttachStateChangeListener(this);
    }

    /**
     * Draw the top and bottom scrims
     */
    public void draw(Canvas canvas) {
        if (!mHideSysUiScrim) {
            if (mSysUiProgress <= 0) {
                mAnimateScrimOnNextDraw = false;
                return;
            }

            if (mAnimateScrimOnNextDraw) {
                mSysUiAnimMultiplier = 0;
                reapplySysUiAlphaNoInvalidate();

                ObjectAnimator oa = createSysuiMultiplierAnim(1);
                oa.setDuration(600);
                oa.setStartDelay(mActivity.getWindow().getTransitionBackgroundFadeDuration());
                oa.start();
                mAnimateScrimOnNextDraw = false;
            }

            if (mDrawWallpaperScrim) {
                canvas.drawRect(mWallpaperScrimRect, mWallpaperScrimPaint);
            }
            if (mDrawTopScrim) {
                mTopScrim.draw(canvas);
            }
            if (mDrawBottomScrim) {
                canvas.drawBitmap(mBottomMask, null, mFinalMaskRect, mBottomMaskPaint);
            }
        }
    }

    /**
     * @return an ObjectAnimator that controls the fade in/out of the sys ui scrim.
     */
    public ObjectAnimator createSysuiMultiplierAnim(float... values) {
        ObjectAnimator anim = ObjectAnimator.ofFloat(this, SYSUI_ANIM_MULTIPLIER, values);
        anim.setAutoCancel(true);
        return anim;
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
        mDrawTopScrim = mTopScrim != null && insets.top > 0;
        mDrawBottomScrim = mBottomMask != null
                && !dp.isVerticalBarLayout()
                && !dp.isGestureMode
                && !dp.isTaskbarPresent;
    }

    @Override
    public void onViewAttachedToWindow(View view) {
        if (!KEYGUARD_ANIMATION.get() && mTopScrim != null) {
            ScreenOnTracker.INSTANCE.get(mActivity).addListener(mScreenOnListener);
        }
    }

    @Override
    public void onViewDetachedFromWindow(View view) {
        if (!KEYGUARD_ANIMATION.get() && mTopScrim != null) {
            ScreenOnTracker.INSTANCE.get(mActivity).removeListener(mScreenOnListener);
        }
    }

    /**
     * Set the width and height of the view being scrimmed
     * @param w
     * @param h
     */
    public void setSize(int w, int h) {
        if (mTopScrim != null) {
            mTopScrim.setBounds(0, 0, w, h);
            mFinalMaskRect.set(0, h - mMaskHeight, w, h);
        }
        mWallpaperScrimRect.set(0, 0, w, h);
    }

    private void setSysUiProgress(float progress) {
        if (progress != mSysUiProgress) {
            mSysUiProgress = progress;
            reapplySysUiAlpha();
        }
    }

    private void reapplySysUiAlpha() {
        reapplySysUiAlphaNoInvalidate();
        if (!mHideSysUiScrim) {
            mRoot.invalidate();
        }
    }

    private void reapplySysUiAlphaNoInvalidate() {
        float factor = mSysUiProgress * mSysUiAnimMultiplier;
        mBottomMaskPaint.setAlpha(Math.round(MAX_HOTSEAT_SCRIM_ALPHA * factor));
        if (mTopScrim != null) {
            mTopScrim.setAlpha(Math.round(255 * factor));
        }
        mWallpaperScrimPaint.setAlpha(Math.round(mWallpaperScrimMaxAlpha * factor));
    }

    private Bitmap createDitheredAlphaMask() {
        DisplayMetrics dm = mActivity.getResources().getDisplayMetrics();
        int width = ResourceUtils.pxFromDp(ALPHA_MASK_WIDTH_DP, dm);
        int gradientHeight = ResourceUtils.pxFromDp(ALPHA_MASK_HEIGHT_DP, dm);
        Bitmap dst = Bitmap.createBitmap(width, mMaskHeight, Bitmap.Config.ALPHA_8);
        Canvas c = new Canvas(dst);
        Paint paint = new Paint(Paint.DITHER_FLAG);
        LinearGradient lg = new LinearGradient(0, 0, 0, gradientHeight,
                new int[]{
                        0x00FFFFFF,
                        setColorAlphaBound(Color.WHITE, (int) (0xFF * 0.95)),
                        0xFFFFFFFF},
                new float[]{0f, 0.8f, 1f},
                Shader.TileMode.CLAMP);
        paint.setShader(lg);
        c.drawRect(0, 0, width, gradientHeight, paint);
        return dst;
    }
}
