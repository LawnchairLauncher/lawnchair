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

package com.android.wm.shell.draganddrop;

import static com.android.wm.shell.shared.animation.Interpolators.FAST_OUT_SLOW_IN;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.internal.policy.ScreenDecorationsUtils;
import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.R;
import com.android.wm.shell.protolog.ShellProtoLogGroup;

/**
 * Renders a drop zone area for items being dragged.
 */
public class DropZoneView extends FrameLayout {

    private static final boolean DEBUG_LAYOUT = false;
    private static final float SPLASHSCREEN_ALPHA = 0.90f;
    private static final float HIGHLIGHT_ALPHA = 1f;
    private static final int MARGIN_ANIMATION_ENTER_DURATION = 400;
    private static final int MARGIN_ANIMATION_EXIT_DURATION = 250;

    private static final FloatProperty<DropZoneView> INSETS =
            new FloatProperty<DropZoneView>("insets") {
                @Override
                public void setValue(DropZoneView v, float percent) {
                    v.setMarginPercent(percent);
                }

                @Override
                public Float get(DropZoneView v) {
                    return v.getMarginPercent();
                }
            };

    private final Path mPath = new Path();
    private final float[] mContainerMargin = new float[4];
    private float mCornerRadius;
    private float mBottomInset;
    private boolean mIgnoreBottomMargin;
    private int mMarginColor; // i.e. color used for negative space like the container insets

    private boolean mShowingHighlight;
    private boolean mShowingSplash;
    private boolean mShowingMargin;

    private int mSplashScreenColor;
    private int mHighlightColor;

    private ObjectAnimator mBackgroundAnimator;
    private int mTargetBackgroundColor;
    private ObjectAnimator mMarginAnimator;
    private float mMarginPercent;
    private TextView mDebugIndex;

    // Renders a highlight or neutral transparent color
    private ColorDrawable mColorDrawable;
    // Renders the translucent splashscreen with the app icon in the middle
    private ImageView mSplashScreenView;
    // Renders the margin / insets around the dropzone container
    private MarginView mMarginView;

    public DropZoneView(Context context) {
        this(context, null);
    }

    public DropZoneView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DropZoneView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public DropZoneView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setContainerMargin(0, 0, 0, 0); // make sure it's populated

        mCornerRadius = ScreenDecorationsUtils.getWindowCornerRadius(context);
        mMarginColor = getResources().getColor(R.color.taskbar_background_dark);
        int c = getResources().getColor(android.R.color.system_accent1_500);
        mHighlightColor =  Color.argb(HIGHLIGHT_ALPHA, Color.red(c), Color.green(c), Color.blue(c));
        mSplashScreenColor = Color.argb(SPLASHSCREEN_ALPHA, 0, 0, 0);
        mColorDrawable = new ColorDrawable();
        setBackgroundDrawable(mColorDrawable);

        final int iconSize = context.getResources().getDimensionPixelSize(R.dimen.split_icon_size);
        mSplashScreenView = new ImageView(context);
        mSplashScreenView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        addView(mSplashScreenView,
                new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER));
        mSplashScreenView.setAlpha(0f);

        mMarginView = new MarginView(context);
        addView(mMarginView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        if (DEBUG_LAYOUT) {
            mDebugIndex = new TextView(context);
            mDebugIndex.setVisibility(GONE);
            mDebugIndex.setTextColor(Color.YELLOW);
            addView(mDebugIndex, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START | Gravity.TOP));

            View borderView = new View(context);
            addView(borderView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            GradientDrawable border = new GradientDrawable();
            border.setShape(GradientDrawable.RECTANGLE);
            border.setStroke(5, Color.RED);
            borderView.setBackground(border);
        }
    }

    public void onThemeChange() {
        mCornerRadius = ScreenDecorationsUtils.getWindowCornerRadius(getContext());
        mMarginColor = getResources().getColor(R.color.taskbar_background_dark);
        mHighlightColor = getResources().getColor(android.R.color.system_accent1_500);

        if (mMarginPercent > 0) {
            mMarginView.invalidate();
        }
    }

    /** Sets the desired margins around the drop zone container when fully showing. */
    public void setContainerMargin(float left, float top, float right, float bottom) {
        mContainerMargin[0] = left;
        mContainerMargin[1] = top;
        mContainerMargin[2] = right;
        mContainerMargin[3] = bottom;
        if (mMarginPercent > 0) {
            mMarginView.invalidate();
        }
    }

    /** Ignores the bottom margin provided by the insets. */
    public void setForceIgnoreBottomMargin(boolean ignoreBottomMargin) {
        if (DEBUG_LAYOUT) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_DRAG_AND_DROP,
                    "setForceIgnoreBottomMargin: ignore=%b", ignoreBottomMargin);
        }
        mIgnoreBottomMargin = ignoreBottomMargin;
        if (mMarginPercent > 0) {
            mMarginView.invalidate();
        }
    }

    /** Sets the bottom inset so the drop zones are above bottom navigation. */
    public void setBottomInset(float bottom) {
        if (DEBUG_LAYOUT) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_DRAG_AND_DROP, "setBottomInset: inset=%f",
                    bottom);
        }
        mBottomInset = bottom;
        final LayoutParams lp = (LayoutParams) mSplashScreenView.getLayoutParams();
        lp.bottomMargin = (int) bottom;
        mSplashScreenView.setLayoutParams(lp);
        if (mMarginPercent > 0) {
            mMarginView.invalidate();
        }
    }

    /** Sets the color and icon to use for the splashscreen when shown. */
    public void setAppInfo(int color, Drawable appIcon) {
        Color c = Color.valueOf(color);
        mSplashScreenColor = Color.argb(SPLASHSCREEN_ALPHA, c.red(), c.green(), c.blue());
        mSplashScreenView.setImageDrawable(appIcon);
    }

    /** @return an active animator for this view if one exists. */
    @Nullable
    public Animator getAnimator() {
        if (mMarginAnimator != null && mMarginAnimator.isRunning()) {
            return mMarginAnimator;
        } else if (mBackgroundAnimator != null && mBackgroundAnimator.isRunning()) {
            return mBackgroundAnimator;
        }
        return null;
    }

    /** Animates between highlight and splashscreen depending on current state. */
    public void animateSwitch() {
        if (DEBUG_LAYOUT) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_DRAG_AND_DROP, "animateSwitch");
        }
        mShowingHighlight = !mShowingHighlight;
        mShowingSplash = !mShowingHighlight;
        final int newColor = mShowingHighlight ? mHighlightColor : mSplashScreenColor;
        animateBackground(mColorDrawable.getColor(), newColor);
        animateSplashScreenIcon();
    }

    /** Animates the highlight indicating the zone is hovered on or not. */
    public void setShowingHighlight(boolean showingHighlight) {
        if (DEBUG_LAYOUT) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_DRAG_AND_DROP, "setShowingHighlight: showing=%b",
                    showingHighlight);
        }
        mShowingHighlight = showingHighlight;
        mShowingSplash = !mShowingHighlight;
        final int newColor = mShowingHighlight ? mHighlightColor : mSplashScreenColor;
        animateBackground(Color.TRANSPARENT, newColor);
        animateSplashScreenIcon();
    }

    /** Animates the margins around the drop zone to show or hide. */
    public void setShowingMargin(boolean visible) {
        if (DEBUG_LAYOUT) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_DRAG_AND_DROP, "setShowingMargin: visible=%b",
                    visible);
        }
        if (mShowingMargin != visible) {
            mShowingMargin = visible;
            animateMarginToState();
        }
        if (!mShowingMargin) {
            mShowingHighlight = false;
            mShowingSplash = false;
            animateBackground(mColorDrawable.getColor(), Color.TRANSPARENT);
            animateSplashScreenIcon();
        }
    }

    @SuppressLint("SetTextI18n")
    public void setDebugIndex(int index) {
        if (!DEBUG_LAYOUT) {
            return;
        }

        mDebugIndex.setText("Index:\n" + index);
        mDebugIndex.setVisibility(VISIBLE);
    }

    private void animateBackground(int startColor, int endColor) {
        if (DEBUG_LAYOUT) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_DRAG_AND_DROP,
                    "animateBackground: start=%s end=%s",
                    Integer.toHexString(startColor), Integer.toHexString(endColor));
        }
        if (endColor == mTargetBackgroundColor) {
            // Already at, or animating to, that background color
            return;
        }
        if (mBackgroundAnimator != null) {
            mBackgroundAnimator.cancel();
        }
        mBackgroundAnimator = ObjectAnimator.ofArgb(mColorDrawable,
                "color",
                startColor,
                endColor);
        if (!mShowingSplash && !mShowingHighlight) {
            mBackgroundAnimator.setInterpolator(FAST_OUT_SLOW_IN);
        }
        mBackgroundAnimator.start();
        mTargetBackgroundColor = endColor;
    }

    private void animateSplashScreenIcon() {
        mSplashScreenView.animate().alpha(mShowingSplash ? 1f : 0f).start();
    }

    private void animateMarginToState() {
        if (mMarginAnimator != null) {
            mMarginAnimator.cancel();
        }
        mMarginAnimator = ObjectAnimator.ofFloat(this, INSETS,
                mMarginPercent,
                mShowingMargin ? 1f : 0f);
        mMarginAnimator.setInterpolator(FAST_OUT_SLOW_IN);
        mMarginAnimator.setDuration(mShowingMargin
                ? MARGIN_ANIMATION_ENTER_DURATION
                : MARGIN_ANIMATION_EXIT_DURATION);
        mMarginAnimator.start();
    }

    private void setMarginPercent(float percent) {
        if (percent != mMarginPercent) {
            mMarginPercent = percent;
            mMarginView.invalidate();
        }
    }

    private float getMarginPercent() {
        return mMarginPercent;
    }

    /** Simple view that draws a rounded rect margin around its contents. **/
    private class MarginView extends View {

        MarginView(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            mPath.reset();
            mPath.addRoundRect(mContainerMargin[0] * mMarginPercent,
                    mContainerMargin[1] * mMarginPercent,
                    getWidth() - (mContainerMargin[2] * mMarginPercent),
                    getHeight() - (mContainerMargin[3] * mMarginPercent)
                            - (mIgnoreBottomMargin ? 0 : mBottomInset),
                    mCornerRadius * mMarginPercent,
                    mCornerRadius * mMarginPercent,
                    Path.Direction.CW);
            mPath.setFillType(Path.FillType.INVERSE_EVEN_ODD);
            canvas.clipPath(mPath);
            canvas.drawColor(mMarginColor);
        }
    }
}
