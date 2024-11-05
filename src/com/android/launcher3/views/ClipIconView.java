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

import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.launcher3.Flags.enableAdditionalHomeAnimations;
import static com.android.launcher3.Utilities.boundToRange;
import static com.android.launcher3.Utilities.mapToRange;
import static com.android.launcher3.anim.AnimatorListeners.forEndCallback;
import static com.android.launcher3.views.FloatingIconView.SHAPE_PROGRESS_DURATION;

import static java.lang.Math.max;

import android.animation.ValueAnimator;
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
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.ViewOutlineProvider;

import androidx.annotation.Nullable;
import androidx.core.util.Consumer;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.dragndrop.FolderAdaptiveIcon;
import com.android.launcher3.graphics.IconShape;

/**
 * A view used to draw both layers of an {@link AdaptiveIconDrawable}.
 * Supports springing just the foreground layer.
 * Supports clipping the icon to/from its icon shape.
 */
public class ClipIconView extends View implements ClipPathView {

    private static final Rect sTmpRect = new Rect();

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

    @Nullable private TaskViewArtist mTaskViewArtist;

    public ClipIconView(Context context) {
        this(context, null);
    }

    public ClipIconView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ClipIconView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mBlurSizeOutline = getResources().getDimensionPixelSize(
                R.dimen.blur_size_medium_outline);
        mIsRtl = Utilities.isRtl(getResources());
    }

    /**
     * Sets a {@link TaskViewArtist} that will draw a {@link com.android.quickstep.views.TaskView}
     * within the clip bounds of this view.
     */
    public void setTaskViewArtist(TaskViewArtist taskViewArtist) {
        if (!enableAdditionalHomeAnimations()) {
            return;
        }
        mTaskViewArtist = taskViewArtist;
        invalidate();
    }

    /**
     * Update the icon UI to match the provided parameters during an animation frame
     */
    public void update(RectF rect, float progress, float shapeProgressStart, float cornerRadius,
            boolean isOpening, View container, DeviceProfile dp) {
        update(rect, progress, shapeProgressStart, cornerRadius, isOpening, container, dp, 255);
    }

    /**
     * Update the icon UI to match the provided parameters during an animation frame, optionally
     * varying the alpha of the {@link TaskViewArtist}
     */
    public void update(RectF rect, float progress, float shapeProgressStart, float cornerRadius,
            boolean isOpening, View container, DeviceProfile dp, int taskViewDrawAlpha) {
        MarginLayoutParams lp = (MarginLayoutParams) container.getLayoutParams();

        float dX = mIsRtl
                ? rect.left - (dp.widthPx - lp.getMarginStart() - lp.width)
                : rect.left - lp.getMarginStart();
        float dY = rect.top - lp.topMargin;
        container.setTranslationX(dX);
        container.setTranslationY(dY);

        float minSize = Math.min(lp.width, lp.height);
        float scaleX = rect.width() / minSize;
        float scaleY = rect.height() / minSize;
        float scale = Math.max(1f, Math.min(scaleX, scaleY));
        if (mTaskViewArtist != null) {
            mTaskViewArtist.taskViewDrawWidth = lp.width;
            mTaskViewArtist.taskViewDrawHeight = lp.height;
            mTaskViewArtist.taskViewDrawAlpha = taskViewDrawAlpha;
            mTaskViewArtist.taskViewDrawScale = (mTaskViewArtist.drawForPortraitLayout
                    ? Math.min(lp.height, lp.width) : Math.max(lp.height, lp.width))
                    / mTaskViewArtist.taskViewMinSize;
        }

        if (Float.isNaN(scale) || Float.isInfinite(scale)) {
            // Views are no longer laid out, do not update.
            return;
        }

        update(rect, progress, shapeProgressStart, cornerRadius, isOpening, scale, minSize, dp);

        container.setPivotX(0);
        container.setPivotY(0);
        container.setScaleX(scale);
        container.setScaleY(scale);

        container.invalidate();
    }

    private void update(RectF rect, float progress, float shapeProgressStart, float cornerRadius,
            boolean isOpening, float scale, float minSize, DeviceProfile dp) {
        // shapeRevealProgress = 1 when progress = shapeProgressStart + SHAPE_PROGRESS_DURATION
        float toMax = isOpening ? 1 / SHAPE_PROGRESS_DURATION : 1f;

        float shapeRevealProgress = boundToRange(mapToRange(max(shapeProgressStart, progress),
                shapeProgressStart, 1f, 0, toMax, LINEAR), 0, 1);

        if (dp.isLandscape) {
            mOutline.right = (int) (rect.width() / scale);
        } else {
            mOutline.bottom = (int) (rect.height() / scale);
        }

        mTaskCornerRadius = cornerRadius / scale;
        if (mIsAdaptiveIcon) {
            if (!isOpening && progress >= shapeProgressStart) {
                if (mRevealAnimator == null) {
                    mRevealAnimator = IconShape.INSTANCE.get(getContext()).getShape()
                            .createRevealAnimator(this, mStartRevealRect,
                                    mOutline, mTaskCornerRadius, !isOpening);
                    mRevealAnimator.addListener(forEndCallback(() -> mRevealAnimator = null));
                    mRevealAnimator.start();
                    // We pause here so we can set the current fraction ourselves.
                    mRevealAnimator.pause();
                }
                mRevealAnimator.setCurrentFraction(shapeRevealProgress);
            }

            float drawableScale = (dp.isLandscape ? mOutline.width() : mOutline.height())
                    / minSize;
            setBackgroundDrawableBounds(drawableScale, dp.isLandscape);

            // Center align foreground
            int height = mFinalDrawableBounds.height();
            int width = mFinalDrawableBounds.width();
            int diffY = dp.isLandscape ? 0
                    : (int) (((height * drawableScale) - height) / 2);
            int diffX = dp.isLandscape ? (int) (((width * drawableScale) - width) / 2)
                    : 0;
            sTmpRect.set(mFinalDrawableBounds);
            sTmpRect.offset(diffX, diffY);
            mForeground.setBounds(sTmpRect);
        }
        invalidate();
        invalidateOutline();
    }

    private void setBackgroundDrawableBounds(float scale, boolean isLandscape) {
        sTmpRect.set(mFinalDrawableBounds);
        Utilities.scaleRectAboutCenter(sTmpRect, scale);
        // Since the drawable is at the top of the view, we need to offset to keep it centered.
        if (isLandscape) {
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

    /**
     * Sets the icon for this view as part of initial setup
     */
    public void setIcon(@Nullable Drawable drawable, int iconOffset, MarginLayoutParams lp,
            boolean isOpening, DeviceProfile dp) {
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
                Utilities.scaleRectAboutCenter(mStartRevealRect,
                        IconShape.INSTANCE.get(getContext()).getNormalizationScale());
            }

            if (dp.isLandscape) {
                lp.width = (int) Math.max(lp.width, lp.height * dp.aspectRatio);
            } else {
                lp.height = (int) Math.max(lp.height, lp.width * dp.aspectRatio);
            }

            int left = mIsRtl
                    ? dp.widthPx - lp.getMarginStart() - lp.width
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
            setBackgroundDrawableBounds(bgDrawableStartScale, dp.isLandscape);
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
            mForeground.draw(canvas);
        }
        if (mTaskViewArtist != null) {
            canvas.saveLayerAlpha(
                    0,
                    0,
                    mTaskViewArtist.taskViewDrawWidth,
                    mTaskViewArtist.taskViewDrawHeight,
                    mTaskViewArtist.taskViewDrawAlpha);
            float drawScale = mTaskViewArtist.taskViewDrawScale;
            canvas.translate(drawScale * mTaskViewArtist.taskViewTranslationX,
                    drawScale * mTaskViewArtist.taskViewTranslationY);
            canvas.scale(drawScale, drawScale);
            mTaskViewArtist.taskViewDrawCallback.accept(canvas);
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
        mTaskViewArtist = null;
    }

    /**
     * Utility class to help draw a {@link com.android.quickstep.views.TaskView} within
     * a {@link ClipIconView} bounds.
     */
    public static class TaskViewArtist {

        public final Consumer<Canvas> taskViewDrawCallback;
        public final float taskViewTranslationX;
        public final float taskViewTranslationY;
        public final float taskViewMinSize;
        public final boolean drawForPortraitLayout;

        public int taskViewDrawAlpha;
        public float taskViewDrawScale;
        public int taskViewDrawWidth;
        public int taskViewDrawHeight;

        public TaskViewArtist(
                Consumer<Canvas> taskViewDrawCallback,
                float taskViewTranslationX,
                float taskViewTranslationY,
                float taskViewMinSize,
                boolean drawForPortraitLayout) {
            this.taskViewDrawCallback = taskViewDrawCallback;
            this.taskViewTranslationX = taskViewTranslationX;
            this.taskViewTranslationY = taskViewTranslationY;
            this.taskViewMinSize = taskViewMinSize;
            this.drawForPortraitLayout = drawForPortraitLayout;
        }
    }
}
