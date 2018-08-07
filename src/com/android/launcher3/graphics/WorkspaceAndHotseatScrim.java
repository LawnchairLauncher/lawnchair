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

import static android.content.Intent.ACTION_SCREEN_OFF;
import static android.content.Intent.ACTION_USER_PRESENT;

import android.animation.ObjectAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.support.v4.graphics.ColorUtils;
import android.util.DisplayMetrics;
import android.util.Property;
import android.view.View;

import com.android.launcher3.CellLayout;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.uioverrides.WallpaperColorInfo;
import com.android.launcher3.util.Themes;

/**
 * View scrim which draws behind hotseat and workspace
 */
public class WorkspaceAndHotseatScrim implements
        View.OnAttachStateChangeListener, WallpaperColorInfo.OnChangeListener {

    public static Property<WorkspaceAndHotseatScrim, Float> SCRIM_PROGRESS =
            new Property<WorkspaceAndHotseatScrim, Float>(Float.TYPE, "scrimProgress") {
                @Override
                public Float get(WorkspaceAndHotseatScrim scrim) {
                    return scrim.mScrimProgress;
                }

                @Override
                public void set(WorkspaceAndHotseatScrim scrim, Float value) {
                    scrim.setScrimProgress(value);
                }
            };

    public static Property<WorkspaceAndHotseatScrim, Float> SYSUI_PROGRESS =
            new Property<WorkspaceAndHotseatScrim, Float>(Float.TYPE, "sysUiProgress") {
                @Override
                public Float get(WorkspaceAndHotseatScrim scrim) {
                    return scrim.mSysUiProgress;
                }

                @Override
                public void set(WorkspaceAndHotseatScrim scrim, Float value) {
                    scrim.setSysUiProgress(value);
                }
            };

    private static Property<WorkspaceAndHotseatScrim, Float> SYSUI_ANIM_MULTIPLIER =
            new Property<WorkspaceAndHotseatScrim, Float>(Float.TYPE, "sysUiAnimMultiplier") {
                @Override
                public Float get(WorkspaceAndHotseatScrim scrim) {
                    return scrim.mSysUiAnimMultiplier;
                }

                @Override
                public void set(WorkspaceAndHotseatScrim scrim, Float value) {
                    scrim.mSysUiAnimMultiplier = value;
                    scrim.reapplySysUiAlpha();
                }
            };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (ACTION_SCREEN_OFF.equals(action)) {
                mAnimateScrimOnNextDraw = true;
            } else if (ACTION_USER_PRESENT.equals(action)) {
                // ACTION_USER_PRESENT is sent after onStart/onResume. This covers the case where
                // the user unlocked and the Launcher is not in the foreground.
                mAnimateScrimOnNextDraw = false;
            }
        }
    };

    private static final int DARK_SCRIM_COLOR = 0x55000000;
    private static final int MAX_HOTSEAT_SCRIM_ALPHA = 100;
    private static final int ALPHA_MASK_HEIGHT_DP = 500;
    private static final int ALPHA_MASK_BITMAP_DP = 200;
    private static final int ALPHA_MASK_WIDTH_DP = 2;

    private final Rect mHighlightRect = new Rect();
    private final Launcher mLauncher;
    private final WallpaperColorInfo mWallpaperColorInfo;
    private final View mRoot;

    private Workspace mWorkspace;

    private final boolean mHasSysUiScrim;
    private boolean mDrawTopScrim, mDrawBottomScrim;

    private final RectF mFinalMaskRect = new RectF();
    private final Paint mBottomMaskPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Bitmap mBottomMask;
    private final int mMaskHeight;

    private final Drawable mTopScrim;

    private int mFullScrimColor;

    private float mScrimProgress;
    private int mScrimAlpha = 0;

    private float mSysUiProgress = 1;
    private boolean mHideSysUiScrim;

    private boolean mAnimateScrimOnNextDraw = false;
    private float mSysUiAnimMultiplier = 1;

    public WorkspaceAndHotseatScrim(View view) {
        mRoot = view;
        mLauncher = Launcher.getLauncher(view.getContext());
        mWallpaperColorInfo = WallpaperColorInfo.getInstance(mLauncher);

        mMaskHeight = Utilities.pxFromDp(ALPHA_MASK_BITMAP_DP,
                view.getResources().getDisplayMetrics());

        mHasSysUiScrim = !mWallpaperColorInfo.supportsDarkText();
        if (mHasSysUiScrim) {
            mTopScrim = Themes.getAttrDrawable(view.getContext(), R.attr.workspaceStatusBarScrim);
            mBottomMask = createDitheredAlphaMask();
        } else {
            mTopScrim = null;
            mBottomMask = null;
        }

        view.addOnAttachStateChangeListener(this);
        onExtractedColorsChanged(mWallpaperColorInfo);
    }

    public void setWorkspace(Workspace workspace)  {
        mWorkspace = workspace;
    }

    public void draw(Canvas canvas) {
        // Draw the background below children.
        if (mScrimAlpha > 0) {
            // Update the scroll position first to ensure scrim cutout is in the right place.
            mWorkspace.computeScrollWithoutInvalidation();
            CellLayout currCellLayout = mWorkspace.getCurrentDragOverlappingLayout();
            canvas.save();
            if (currCellLayout != null && currCellLayout != mLauncher.getHotseat().getLayout()) {
                // Cut a hole in the darkening scrim on the page that should be highlighted, if any.
                mLauncher.getDragLayer()
                        .getDescendantRectRelativeToSelf(currCellLayout, mHighlightRect);
                canvas.clipRect(mHighlightRect, Region.Op.DIFFERENCE);
            }

            canvas.drawColor(ColorUtils.setAlphaComponent(mFullScrimColor, mScrimAlpha));
            canvas.restore();
        }

        if (!mHideSysUiScrim && mHasSysUiScrim) {
            if (mSysUiProgress <= 0) {
                mAnimateScrimOnNextDraw = false;
                return;
            }

            if (mAnimateScrimOnNextDraw) {
                mSysUiAnimMultiplier = 0;
                reapplySysUiAlphaNoInvalidate();

                ObjectAnimator anim = ObjectAnimator.ofFloat(this, SYSUI_ANIM_MULTIPLIER, 1);
                anim.setAutoCancel(true);
                anim.setDuration(600);
                anim.setStartDelay(mLauncher.getWindow().getTransitionBackgroundFadeDuration());
                anim.start();
                mAnimateScrimOnNextDraw = false;
            }

            if (mDrawTopScrim) {
                mTopScrim.draw(canvas);
            }
            if (mDrawBottomScrim) {
                canvas.drawBitmap(mBottomMask, null, mFinalMaskRect, mBottomMaskPaint);
            }
        }
    }

    public void onInsetsChanged(Rect insets) {
        mDrawTopScrim = insets.top > 0;
        mDrawBottomScrim = !mLauncher.getDeviceProfile().isVerticalBarLayout();
    }

    private void setScrimProgress(float progress) {
        if (mScrimProgress != progress) {
            mScrimProgress = progress;
            mScrimAlpha = Math.round(255 * mScrimProgress);
            invalidate();
        }
    }

    @Override
    public void onViewAttachedToWindow(View view) {
        mWallpaperColorInfo.addOnChangeListener(this);
        onExtractedColorsChanged(mWallpaperColorInfo);

        if (mHasSysUiScrim) {
            IntentFilter filter = new IntentFilter(ACTION_SCREEN_OFF);
            filter.addAction(ACTION_USER_PRESENT); // When the device wakes up + keyguard is gone
            mRoot.getContext().registerReceiver(mReceiver, filter);
        }
    }

    @Override
    public void onViewDetachedFromWindow(View view) {
        mWallpaperColorInfo.removeOnChangeListener(this);
        if (mHasSysUiScrim) {
            mRoot.getContext().unregisterReceiver(mReceiver);
        }
    }

    @Override
    public void onExtractedColorsChanged(WallpaperColorInfo wallpaperColorInfo) {
        // for super light wallpaper it needs to be darken for contrast to workspace
        // for dark wallpapers the text is white so darkening works as well
        mBottomMaskPaint.setColor(ColorUtils.compositeColors(DARK_SCRIM_COLOR,
                wallpaperColorInfo.getMainColor()));
        reapplySysUiAlpha();
        mFullScrimColor = wallpaperColorInfo.getMainColor();
        if (mScrimAlpha > 0) {
            invalidate();
        }
    }

    public void setSize(int w, int h) {
        if (mHasSysUiScrim) {
            mTopScrim.setBounds(0, 0, w, h);
            mFinalMaskRect.set(0, h - mMaskHeight, w, h);
        }
    }

    public void hideSysUiScrim(boolean hideSysUiScrim) {
        mHideSysUiScrim = hideSysUiScrim;
        if (!hideSysUiScrim) {
            mAnimateScrimOnNextDraw = true;
        }
        invalidate();
    }

    private void setSysUiProgress(float progress) {
        if (progress != mSysUiProgress) {
            mSysUiProgress = progress;
            reapplySysUiAlpha();
        }
    }

    private void reapplySysUiAlpha() {
        if (mHasSysUiScrim) {
            reapplySysUiAlphaNoInvalidate();
            if (!mHideSysUiScrim) {
                invalidate();
            }
        }
    }

    private void reapplySysUiAlphaNoInvalidate() {
        float factor = mSysUiProgress * mSysUiAnimMultiplier;
        mBottomMaskPaint.setAlpha(Math.round(MAX_HOTSEAT_SCRIM_ALPHA * factor));
        mTopScrim.setAlpha(Math.round(255 * factor));
    }

    public void invalidate() {
        mRoot.invalidate();
    }

    public Bitmap createDitheredAlphaMask() {
        DisplayMetrics dm = mLauncher.getResources().getDisplayMetrics();
        int width = Utilities.pxFromDp(ALPHA_MASK_WIDTH_DP, dm);
        int gradientHeight = Utilities.pxFromDp(ALPHA_MASK_HEIGHT_DP, dm);
        Bitmap dst = Bitmap.createBitmap(width, mMaskHeight, Bitmap.Config.ALPHA_8);
        Canvas c = new Canvas(dst);
        Paint paint = new Paint(Paint.DITHER_FLAG);
        LinearGradient lg = new LinearGradient(0, 0, 0, gradientHeight,
                new int[]{
                        0x00FFFFFF,
                        ColorUtils.setAlphaComponent(Color.WHITE, (int) (0xFF * 0.95)),
                        0xFFFFFFFF},
                new float[]{0f, 0.8f, 1f},
                Shader.TileMode.CLAMP);
        paint.setShader(lg);
        c.drawRect(0, 0, width, gradientHeight, paint);
        return dst;
    }
}
