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
package com.android.launcher3.views;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
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
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.InsettableFrameLayout.LayoutParams;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.folder.FolderShape;
import com.android.launcher3.graphics.ShiftedBitmapDrawable;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.popup.SystemShortcut;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import static com.android.launcher3.config.FeatureFlags.ADAPTIVE_ICON_WINDOW_ANIM;

/**
 * A view that is created to look like another view with the purpose of creating fluid animations.
 */

public class FloatingIconView extends View implements Animator.AnimatorListener, ClipPathView {

    private static final Rect sTmpRect = new Rect();

    private Runnable mStartRunnable;
    private Runnable mEndRunnable;

    private int mOriginalHeight;
    private final int mBlurSizeOutline;

    private boolean mIsAdaptiveIcon = false;

    private @Nullable Drawable mForeground;
    private @Nullable Drawable mBackground;
    private ValueAnimator mRevealAnimator;
    private final Rect mStartRevealRect = new Rect();
    private final Rect mEndRevealRect = new Rect();
    private Path mClipPath;
    protected final Rect mOutline = new Rect();
    private final float mTaskCornerRadius;

    private final Rect mFinalDrawableBounds = new Rect();
    private final Rect mBgDrawableBounds = new Rect();
    private float mBgDrawableStartScale = 1f;

    private FloatingIconView(Context context) {
        super(context);

        mBlurSizeOutline = context.getResources().getDimensionPixelSize(
                R.dimen.blur_size_medium_outline);

        mTaskCornerRadius = 0; // TODO
    }

    /**
     * Positions this view to match the size and location of {@param rect}.
     *
     * @param alpha The alpha to set this view.
     * @param progress A value from [0, 1] that represents the animation progress.
     * @param windowAlphaThreshold The value at which the window alpha is 0.
     */
    public void update(RectF rect, float alpha, float progress, float windowAlphaThreshold) {
        setAlpha(alpha);

        LayoutParams lp = (LayoutParams) getLayoutParams();
        float dX = rect.left - lp.leftMargin;
        float dY = rect.top - lp.topMargin;
        setTranslationX(dX);
        setTranslationY(dY);

        float scaleX = rect.width() / (float) lp.width;
        float scaleY = rect.height() / (float) lp.height;
        float scale = mIsAdaptiveIcon ? Math.max(scaleX, scaleY) : Math.min(scaleX, scaleY);
        setPivotX(0);
        setPivotY(0);
        setScaleX(scale);
        setScaleY(scale);

        // Wait until the window is no longer visible before morphing the icon into its final shape.
        float shapeRevealProgress = Utilities.mapToRange(Math.max(windowAlphaThreshold, progress),
                windowAlphaThreshold, 1f, 0f, 1, Interpolators.LINEAR);
        if (mIsAdaptiveIcon && shapeRevealProgress > 0) {
            if (mRevealAnimator == null) {
                mEndRevealRect.set(mOutline);
                // We play the reveal animation in reverse so that we end with the icon shape.
                mRevealAnimator = (ValueAnimator) FolderShape.getShape().createRevealAnimator(this,
                        mStartRevealRect, mEndRevealRect, mTaskCornerRadius / scale, true);
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

            float bgScale = shapeRevealProgress + mBgDrawableStartScale * (1 - shapeRevealProgress);
            setBackgroundDrawableBounds(bgScale);

            mRevealAnimator.setCurrentFraction(shapeRevealProgress);
            if (Float.compare(shapeRevealProgress, 1f) >= 0f) {
                mRevealAnimator.end();
            }
        }
        invalidate();
        invalidateOutline();
    }

    @Override
    public void onAnimationStart(Animator animator) {
        if (mStartRunnable != null) {
            mStartRunnable.run();
        }
    }

    @Override
    public void onAnimationEnd(Animator animator) {
        if (mEndRunnable != null) {
            mEndRunnable.run();
        }
    }

    /**
     * Sets the size and position of this view to match {@param v}.
     *
     * @param v The view to copy
     * @param positionOut Rect that will hold the size and position of v.
     */
    private void matchPositionOf(Launcher launcher, View v, Rect positionOut) {
        Utilities.getLocationBoundsForView(launcher, v, positionOut);
        final LayoutParams lp = new LayoutParams(positionOut.width(), positionOut.height());
        lp.ignoreInsets = true;
        mOriginalHeight = lp.height;

        // Position the floating view exactly on top of the original
        lp.leftMargin = positionOut.left;
        lp.topMargin = positionOut.top;
        setLayoutParams(lp);
        // Set the properties here already to make sure they are available when running the first
        // animation frame.
        layout(lp.leftMargin, lp.topMargin, lp.leftMargin + lp.width, lp.topMargin
                + lp.height);
    }

    @WorkerThread
    private void getIcon(Launcher launcher, View v, ItemInfo info, boolean useDrawableAsIs,
            float aspectRatio) {
        final LayoutParams lp = (LayoutParams) getLayoutParams();
        Drawable drawable = null;
        boolean supportsAdaptiveIcons = ADAPTIVE_ICON_WINDOW_ANIM.get() && !useDrawableAsIs
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
        if (!supportsAdaptiveIcons && v instanceof BubbleTextView) {
            // Similar to DragView, we simply use the BubbleTextView icon here.
            drawable = ((BubbleTextView) v).getIcon();
        }
        if (v instanceof ImageView && info instanceof SystemShortcut) {
            drawable = ((ImageView) v).getDrawable();
        }
        if (drawable == null) {
            drawable = Utilities.getFullDrawable(launcher, info, lp.width, lp.height,
                    useDrawableAsIs, new Object[1]);
        }

        Drawable finalDrawable = drawable == null ? null
                : drawable.getConstantState().newDrawable();
        boolean isAdaptiveIcon = supportsAdaptiveIcons
                && finalDrawable instanceof AdaptiveIconDrawable;
        int iconOffset = getOffsetForIconBounds(finalDrawable);

        new Handler(Looper.getMainLooper()).post(() -> {
            if (isAdaptiveIcon) {
                mIsAdaptiveIcon = true;

                AdaptiveIconDrawable adaptiveIcon = (AdaptiveIconDrawable) finalDrawable;
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

                mFinalDrawableBounds.set(iconOffset, iconOffset, lp.width -
                        iconOffset, mOriginalHeight - iconOffset);
                if (mForeground instanceof ShiftedBitmapDrawable && v instanceof FolderIcon) {
                    ShiftedBitmapDrawable sbd = (ShiftedBitmapDrawable) mForeground;
                    ((FolderIcon) v).getPreviewBounds(sTmpRect);
                    sbd.setShiftX(sbd.getShiftX() - sTmpRect.left);
                    sbd.setShiftY(sbd.getShiftY() - sTmpRect.top);
                }
                mForeground.setBounds(mFinalDrawableBounds);
                mBackground.setBounds(mFinalDrawableBounds);

                int blurMargin = mBlurSizeOutline / 2;
                mStartRevealRect.set(blurMargin, blurMargin , lp.width - blurMargin,
                        mOriginalHeight - blurMargin);

                if (aspectRatio > 0) {
                    lp.height = (int) Math.max(lp.height, lp.width * aspectRatio);
                    layout(lp.leftMargin, lp.topMargin, lp.leftMargin + lp.width, lp.topMargin
                            + lp.height);
                }
                mBgDrawableStartScale = (float) lp.height / mOriginalHeight;
                setBackgroundDrawableBounds(mBgDrawableStartScale);

                // Set up outline
                mOutline.set(0, 0, lp.width, lp.height);
                setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setRoundRect(mOutline, mTaskCornerRadius);
                    }
                });
                setClipToOutline(true);
            } else {
                setBackground(finalDrawable);
            }

            invalidate();
            invalidateOutline();
        });
    }

    private void setBackgroundDrawableBounds(float scale) {
        mBgDrawableBounds.set(mFinalDrawableBounds);
        Utilities.scaleRectAboutCenter(mBgDrawableBounds, scale);
        // Since the drawable is at the top of the view, we need to offset to keep it centered.
        mBgDrawableBounds.offsetTo(mBgDrawableBounds.left,
                (int) (mFinalDrawableBounds.top * scale));
        mBackground.setBounds(mBgDrawableBounds);
    }

    @WorkerThread
    private int getOffsetForIconBounds(Drawable drawable) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O ||
                !(drawable instanceof AdaptiveIconDrawable)) {
            return 0;
        }

        final LayoutParams lp = (LayoutParams) getLayoutParams();
        Rect bounds = new Rect(0, 0, lp.width + mBlurSizeOutline, lp.height + mBlurSizeOutline);
        bounds.inset(mBlurSizeOutline / 2, mBlurSizeOutline / 2);

        try (LauncherIcons li = LauncherIcons.obtain(Launcher.fromContext(getContext()))) {
            Utilities.scaleRectAboutCenter(bounds, li.getNormalizer().getScale(drawable, null));
        }

        bounds.inset(
                (int) (-bounds.width() * AdaptiveIconDrawable.getExtraInsetFraction()),
                (int) (-bounds.height() * AdaptiveIconDrawable.getExtraInsetFraction())
        );

        return bounds.left;
    }

    @Override
    public void setClipPath(Path clipPath) {
        mClipPath = clipPath;
        invalidate();
    }

    private void drawAdaptiveIconIfExists(Canvas canvas) {
        if (mBackground != null) {
            mBackground.draw(canvas);
        }
        if (mForeground != null) {
            mForeground.draw(canvas);
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (mClipPath == null) {
            super.draw(canvas);
            drawAdaptiveIconIfExists(canvas);
        } else {
            int count = canvas.save();
            canvas.clipPath(mClipPath);
            super.draw(canvas);
            drawAdaptiveIconIfExists(canvas);
            canvas.restoreToCount(count);
        }
    }

    @Override
    public void onAnimationCancel(Animator animator) {}

    @Override
    public void onAnimationRepeat(Animator animator) {}

    /**
     * Creates a floating icon view for {@param originalView}.
     *
     * @param originalView The view to copy
     * @param hideOriginal If true, it will hide {@param originalView} while this view is visible.
     * @param useDrawableAsIs If true, we do not separate the foreground/background of adaptive
     * icons. TODO(b/122843905): We can remove this once app opening uses new animation.
     * @param aspectRatio If >= 0, we will use this aspect ratio for the initial adaptive icon size.
     * @param positionOut Rect that will hold the size and position of v.
     */
    public static FloatingIconView getFloatingIconView(Launcher launcher, View originalView,
            boolean hideOriginal, boolean useDrawableAsIs, float aspectRatio, Rect positionOut,
            FloatingIconView recycle) {
        FloatingIconView view = recycle != null ? recycle : new FloatingIconView(launcher);

        // Match the position of the original view.
        view.matchPositionOf(launcher, originalView, positionOut);

        // Get the drawable on the background thread
        // Must be called after matchPositionOf so that we know what size to load.
        if (originalView.getTag() instanceof ItemInfo) {
            new Handler(LauncherModel.getWorkerLooper()).postAtFrontOfQueue(() -> {
                view.getIcon(launcher, originalView, (ItemInfo) originalView.getTag(),
                        useDrawableAsIs, aspectRatio);
            });
        }

        // We need to add it to the overlay, but keep it invisible until animation starts..
        final DragLayer dragLayer = launcher.getDragLayer();
        view.setVisibility(INVISIBLE);
        ((ViewGroup) dragLayer.getParent()).getOverlay().add(view);

        view.mStartRunnable = () -> {
            view.setVisibility(VISIBLE);
            if (hideOriginal) {
                originalView.setVisibility(INVISIBLE);
            }
        };
        view.mEndRunnable = () -> {
            ((ViewGroup) dragLayer.getParent()).getOverlay().remove(view);
            if (hideOriginal) {
                originalView.setVisibility(VISIBLE);
            }
        };
        return view;
    }
}
