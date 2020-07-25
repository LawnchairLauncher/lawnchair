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
package com.android.launcher3.views;

import static com.android.launcher3.Utilities.mapToRange;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.views.FloatingIconView.SHAPE_PROGRESS_DURATION;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InsettableFrameLayout.LayoutParams;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.dragndrop.FolderAdaptiveIcon;
import com.android.launcher3.graphics.IconShape;

/**
 * A view used to draw both layers of an {@link AdaptiveIconDrawable}.
 * Supports springing just the foreground layer.
 * Supports clipping the icon to/from its icon shape.
 */
@TargetApi(Build.VERSION_CODES.Q)
public class ClipIconView extends View implements ClipPathView {

    private static final Rect sTmpRect = new Rect();

    // We spring the foreground drawable relative to the icon's movement in the DragLayer.
    // We then use these two factor values to scale the movement of the fg within this view.
    private static final int FG_TRANS_X_FACTOR = 60;
    private static final int FG_TRANS_Y_FACTOR = 75;

    private static final FloatPropertyCompat<ClipIconView> mFgTransYProperty =
            new FloatPropertyCompat<ClipIconView>("ClipIconViewFgTransY") {
                @Override
                public float getValue(ClipIconView view) {
                    return view.mFgTransY;
                }

                @Override
                public void setValue(ClipIconView view, float transY) {
                    view.mFgTransY = transY;
                    view.invalidate();
                }
            };

    private static final FloatPropertyCompat<ClipIconView> mFgTransXProperty =
            new FloatPropertyCompat<ClipIconView>("ClipIconViewFgTransX") {
                @Override
                public float getValue(ClipIconView view) {
                    return view.mFgTransX;
                }

                @Override
                public void setValue(ClipIconView view, float transX) {
                    view.mFgTransX = transX;
                    view.invalidate();
                }
            };

    private final Launcher mLauncher;
    private final int mBlurSizeOutline;
    private final boolean mIsRtl;

    private @Nullable Drawable mForeground;
    private @Nullable Drawable mBackground;

    private boolean mIsAdaptiveIcon = false;

    private ValueAnimator mRevealAnimator;

    private final Rect mStartRevealRect = new Rect();
    private final Rect mEndRevealRect = new Rect();
    private Path mClipPath;
    private float mTaskCornerRadius;

    private final Rect mOutline = new Rect();
    private final Rect mFinalDrawableBounds = new Rect();

    private final SpringAnimation mFgSpringY;
    private float mFgTransY;
    private final SpringAnimation mFgSpringX;
    private float mFgTransX;

    public ClipIconView(Context context) {
        this(context, null);
    }

    public ClipIconView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ClipIconView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mLauncher = Launcher.getLauncher(context);
        mBlurSizeOutline = getResources().getDimensionPixelSize(
                R.dimen.blur_size_medium_outline);
        mIsRtl = Utilities.isRtl(getResources());

        mFgSpringX = new SpringAnimation(this, mFgTransXProperty)
                .setSpring(new SpringForce()
                        .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY)
                        .setStiffness(SpringForce.STIFFNESS_LOW));
        mFgSpringY = new SpringAnimation(this, mFgTransYProperty)
                .setSpring(new SpringForce()
                        .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY)
                        .setStiffness(SpringForce.STIFFNESS_LOW));
    }

    void update(RectF rect, float progress, float shapeProgressStart, float cornerRadius,
            boolean isOpening, float scale, float minSize, LayoutParams parentLp,
            boolean isVerticalBarLayout) {
        DeviceProfile dp = mLauncher.getDeviceProfile();
        float dX = mIsRtl
                ? rect.left - (dp.widthPx - parentLp.getMarginStart() - parentLp.width)
                : rect.left - parentLp.getMarginStart();
        float dY = rect.top - parentLp.topMargin;

        // shapeRevealProgress = 1 when progress = shapeProgressStart + SHAPE_PROGRESS_DURATION
        float toMax = isOpening ? 1 / SHAPE_PROGRESS_DURATION : 1f;
        float shapeRevealProgress = Utilities.boundToRange(mapToRange(
                Math.max(shapeProgressStart, progress), shapeProgressStart, 1f, 0, toMax,
                LINEAR), 0, 1);

        if (isVerticalBarLayout) {
            mOutline.right = (int) (rect.width() / scale);
        } else {
            mOutline.bottom = (int) (rect.height() / scale);
        }

        mTaskCornerRadius = cornerRadius / scale;
        if (mIsAdaptiveIcon) {
            if (!isOpening && progress >= shapeProgressStart) {
                if (mRevealAnimator == null) {
                    mRevealAnimator = (ValueAnimator) IconShape.getShape().createRevealAnimator(
                            this, mStartRevealRect, mOutline, mTaskCornerRadius, !isOpening);
                    mRevealAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            mRevealAnimator = null;
                        }
                    });
                    mRevealAnimator.start();
                    // We pause here so we can set the current fraction ourselves.
                    mRevealAnimator.pause();
                }
                mRevealAnimator.setCurrentFraction(shapeRevealProgress);
            }

            float drawableScale = (isVerticalBarLayout ? mOutline.width() : mOutline.height())
                    / minSize;
            setBackgroundDrawableBounds(drawableScale, isVerticalBarLayout);
            if (isOpening) {
                // Center align foreground
                int height = mFinalDrawableBounds.height();
                int width = mFinalDrawableBounds.width();
                int diffY = isVerticalBarLayout ? 0
                        : (int) (((height * drawableScale) - height) / 2);
                int diffX = isVerticalBarLayout ? (int) (((width * drawableScale) - width) / 2)
                        : 0;
                sTmpRect.set(mFinalDrawableBounds);
                sTmpRect.offset(diffX, diffY);
                mForeground.setBounds(sTmpRect);
            } else {
                // Spring the foreground relative to the icon's movement within the DragLayer.
                int diffX = (int) (dX / dp.availableWidthPx * FG_TRANS_X_FACTOR);
                int diffY = (int) (dY / dp.availableHeightPx * FG_TRANS_Y_FACTOR);

                mFgSpringX.animateToFinalPosition(diffX);
                mFgSpringY.animateToFinalPosition(diffY);
            }
        }
        invalidate();
        invalidateOutline();
    }

    private void setBackgroundDrawableBounds(float scale, boolean isVerticalBarLayout) {
        sTmpRect.set(mFinalDrawableBounds);
        Utilities.scaleRectAboutCenter(sTmpRect, scale);
        // Since the drawable is at the top of the view, we need to offset to keep it centered.
        if (isVerticalBarLayout) {
            sTmpRect.offsetTo((int) (mFinalDrawableBounds.left * scale), sTmpRect.top);
        } else {
            sTmpRect.offsetTo(sTmpRect.left, (int) (mFinalDrawableBounds.top * scale));
        }
        mBackground.setBounds(sTmpRect);
    }

    protected void endReveal() {
        if (mRevealAnimator != null) {
            mRevealAnimator.end();
        }
    }

    void setIcon(@Nullable Drawable drawable, int iconOffset, LayoutParams lp, boolean isOpening,
            boolean isVerticalBarLayout) {
        mIsAdaptiveIcon = drawable instanceof AdaptiveIconDrawable;
        if (mIsAdaptiveIcon) {
            boolean isFolderIcon = drawable instanceof FolderAdaptiveIcon;

            AdaptiveIconDrawable adaptiveIcon = (AdaptiveIconDrawable) drawable;
            Drawable background = adaptiveIcon.getBackground();
            if (background == null) {
                background = new ColorDrawable(Color.TRANSPARENT);
            }
            mBackground = background;
            Drawable foreground = adaptiveIcon.getForeground();
            if (foreground == null) {
                foreground = new ColorDrawable(Color.TRANSPARENT);
            }
            mForeground = foreground;

            final int originalHeight = lp.height;
            final int originalWidth = lp.width;

            int blurMargin = mBlurSizeOutline / 2;
            mFinalDrawableBounds.set(0, 0, originalWidth, originalHeight);

            if (!isFolderIcon) {
                mFinalDrawableBounds.inset(iconOffset - blurMargin, iconOffset - blurMargin);
            }
            mForeground.setBounds(mFinalDrawableBounds);
            mBackground.setBounds(mFinalDrawableBounds);

            mStartRevealRect.set(0, 0, originalWidth, originalHeight);

            if (!isFolderIcon) {
                Utilities.scaleRectAboutCenter(mStartRevealRect, IconShape.getNormalizationScale());
            }

            float aspectRatio = mLauncher.getDeviceProfile().aspectRatio;
            if (isVerticalBarLayout) {
                lp.width = (int) Math.max(lp.width, lp.height * aspectRatio);
            } else {
                lp.height = (int) Math.max(lp.height, lp.width * aspectRatio);
            }

            int left = mIsRtl
                    ? mLauncher.getDeviceProfile().widthPx - lp.getMarginStart() - lp.width
                    : lp.leftMargin;
            layout(left, lp.topMargin, left + lp.width, lp.topMargin + lp.height);

            float scale = Math.max((float) lp.height / originalHeight,
                    (float) lp.width / originalWidth);
            float bgDrawableStartScale;
            if (isOpening) {
                bgDrawableStartScale = 1f;
                mOutline.set(0, 0, originalWidth, originalHeight);
            } else {
                bgDrawableStartScale = scale;
                mOutline.set(0, 0, lp.width, lp.height);
            }
            setBackgroundDrawableBounds(bgDrawableStartScale, isVerticalBarLayout);
            mEndRevealRect.set(0, 0, lp.width, lp.height);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(mOutline, mTaskCornerRadius);
                }
            });
            setClipToOutline(true);
        } else {
            setBackground(drawable);
            setClipToOutline(false);
        }

        invalidate();
        invalidateOutline();
    }

    @Override
    public void setClipPath(Path clipPath) {
        mClipPath = clipPath;
        invalidate();
    }

    @Override
    public void draw(Canvas canvas) {
        int count = canvas.save();
        if (mClipPath != null) {
            canvas.clipPath(mClipPath);
        }
        super.draw(canvas);
        if (mBackground != null) {
            mBackground.draw(canvas);
        }
        if (mForeground != null) {
            int count2 = canvas.save();
            canvas.translate(mFgTransX, mFgTransY);
            mForeground.draw(canvas);
            canvas.restoreToCount(count2);
        }
        canvas.restoreToCount(count);
    }

    void recycle() {
        setBackground(null);
        mIsAdaptiveIcon = false;
        mForeground = null;
        mBackground = null;
        mClipPath = null;
        mFinalDrawableBounds.setEmpty();
        if (mRevealAnimator != null) {
            mRevealAnimator.cancel();
        }
        mRevealAnimator = null;
        mTaskCornerRadius = 0;
        mOutline.setEmpty();
        mFgTransY = 0;
        mFgSpringX.cancel();
        mFgTransX = 0;
        mFgSpringY.cancel();
    }
}
