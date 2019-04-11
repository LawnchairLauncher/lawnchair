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

import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.config.FeatureFlags.ADAPTIVE_ICON_WINDOW_ANIM;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
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
import android.os.CancellationSignal;
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
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.dragndrop.FolderAdaptiveIcon;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.folder.FolderShape;
import com.android.launcher3.graphics.ShiftedBitmapDrawable;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.shortcuts.DeepShortcutView;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import static com.android.launcher3.Utilities.mapToRange;

/**
 * A view that is created to look like another view with the purpose of creating fluid animations.
 */

public class FloatingIconView extends View implements Animator.AnimatorListener, ClipPathView {

    public static final float SHAPE_PROGRESS_DURATION = 0.15f;

    private static final Rect sTmpRect = new Rect();

    private Runnable mEndRunnable;
    private CancellationSignal mLoadIconSignal;

    private final int mBlurSizeOutline;

    private boolean mIsAdaptiveIcon = false;

    private @Nullable Drawable mForeground;
    private @Nullable Drawable mBackground;
    private ValueAnimator mRevealAnimator;
    private final Rect mStartRevealRect = new Rect();
    private final Rect mEndRevealRect = new Rect();
    private Path mClipPath;
    private float mTaskCornerRadius;

    private final Rect mFinalDrawableBounds = new Rect();
    private final Rect mBgDrawableBounds = new Rect();
    private float mBgDrawableStartScale = 1f;
    private float mBgDrawableEndScale = 1f;

    private FloatingIconView(Context context) {
        super(context);
        mBlurSizeOutline = context.getResources().getDimensionPixelSize(
                R.dimen.blur_size_medium_outline);
    }

    /**
     * Positions this view to match the size and location of {@param rect}.
     * @param alpha The alpha to set this view.
     * @param progress A value from [0, 1] that represents the animation progress.
     * @param shapeProgressStart The progress value at which to start the shape reveal.
     * @param cornerRadius The corner radius of {@param rect}.
     */
    public void update(RectF rect, float alpha, float progress, float shapeProgressStart,
            float cornerRadius, boolean isOpening) {
        setAlpha(alpha);

        LayoutParams lp = (LayoutParams) getLayoutParams();
        float dX = rect.left - lp.leftMargin;
        float dY = rect.top - lp.topMargin;
        setTranslationX(dX);
        setTranslationY(dY);

        float scaleX = rect.width() / (float) lp.width;
        float scaleY = rect.height() / (float) lp.height;
        float scale = mIsAdaptiveIcon && !isOpening ? Math.max(scaleX, scaleY)
                : Math.min(scaleX, scaleY);
        scale = Math.max(1f, scale);

        setPivotX(0);
        setPivotY(0);
        setScaleX(scale);
        setScaleY(scale);

        // shapeRevealProgress = 1 when progress = shapeProgressStart + SHAPE_PROGRESS_DURATION
        float toMax = isOpening ? 1 / SHAPE_PROGRESS_DURATION : 1f;
        float shapeRevealProgress = Utilities.boundToRange(mapToRange(
                Math.max(shapeProgressStart, progress), shapeProgressStart, 1f, 0, toMax,
                LINEAR), 0, 1);

        mTaskCornerRadius = cornerRadius;
        if (mIsAdaptiveIcon && shapeRevealProgress >= 0) {
            if (mRevealAnimator == null) {
                mRevealAnimator = (ValueAnimator) FolderShape.getShape().createRevealAnimator(this,
                        mStartRevealRect, mEndRevealRect, mTaskCornerRadius / scale, !isOpening);
                mRevealAnimator.start();
                // We pause here so we can set the current fraction ourselves.
                mRevealAnimator.pause();
            }

            mRevealAnimator.setCurrentFraction(shapeRevealProgress);

            float bgScale = (mBgDrawableEndScale * shapeRevealProgress) + mBgDrawableStartScale
                    * (1 - shapeRevealProgress);
            setBackgroundDrawableBounds(bgScale);
        }
        invalidate();
        invalidateOutline();
    }

    @Override
    public void onAnimationEnd(Animator animator) {
        if (mLoadIconSignal != null) {
            mLoadIconSignal.cancel();
        }
        if (mEndRunnable != null) {
            mEndRunnable.run();
        } else {
            // End runnable also ends the reveal animator, so we manually handle it here.
            if (mRevealAnimator != null) {
                mRevealAnimator.end();
            }
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
    private void getIcon(Launcher launcher, View v, ItemInfo info, boolean isOpening,
            Runnable onIconLoadedRunnable, CancellationSignal loadIconSignal) {
        final LayoutParams lp = (LayoutParams) getLayoutParams();
        Drawable drawable = null;
        boolean supportsAdaptiveIcons = ADAPTIVE_ICON_WINDOW_ANIM.get()
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
        if (!supportsAdaptiveIcons && v instanceof BubbleTextView) {
            // Similar to DragView, we simply use the BubbleTextView icon here.
            drawable = ((BubbleTextView) v).getIcon();
        }
        if (info instanceof SystemShortcut) {
            if (v instanceof ImageView) {
                drawable = ((ImageView) v).getDrawable();
            } else if (v instanceof DeepShortcutView) {
                drawable = ((DeepShortcutView) v).getIconView().getBackground();
            } else {
                drawable = v.getBackground();
            }
        }
        if (drawable == null) {
            drawable = Utilities.getFullDrawable(launcher, info, lp.width, lp.height,
                    false, new Object[1]);
        }

        Drawable finalDrawable = drawable == null ? null
                : drawable.getConstantState().newDrawable();
        boolean isAdaptiveIcon = supportsAdaptiveIcons
                && finalDrawable instanceof AdaptiveIconDrawable;
        int iconOffset = getOffsetForIconBounds(finalDrawable);

        new Handler(Looper.getMainLooper()).post(() -> {
            if (isAdaptiveIcon) {
                mIsAdaptiveIcon = true;
                boolean isFolderIcon = finalDrawable instanceof FolderAdaptiveIcon;

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

                if (mForeground instanceof ShiftedBitmapDrawable && v instanceof FolderIcon) {
                    ShiftedBitmapDrawable sbd = (ShiftedBitmapDrawable) mForeground;
                    ((FolderIcon) v).getPreviewBounds(sTmpRect);
                    sbd.setShiftX(sbd.getShiftX() - sTmpRect.left);
                    sbd.setShiftY(sbd.getShiftY() - sTmpRect.top);
                }

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
                    mStartRevealRect.inset(mBlurSizeOutline, mBlurSizeOutline);
                }

                float aspectRatio = launcher.getDeviceProfile().aspectRatio;
                if (launcher.getDeviceProfile().isVerticalBarLayout()) {
                    lp.width = (int) Math.max(lp.width, lp.height * aspectRatio);
                } else {
                    lp.height = (int) Math.max(lp.height, lp.width * aspectRatio);
                }
                layout(lp.leftMargin, lp.topMargin, lp.leftMargin + lp.width, lp.topMargin
                        + lp.height);

                Rect rectOutline = new Rect();
                float scale = Math.max((float) lp.height / originalHeight,
                        (float) lp.width / originalWidth);
                if (isOpening) {
                    mBgDrawableStartScale = 1f;
                    mBgDrawableEndScale = scale;
                    rectOutline.set(0, 0, originalWidth, originalHeight);
                } else {
                    mBgDrawableStartScale = scale;
                    mBgDrawableEndScale = 1f;
                    rectOutline.set(0, 0, lp.width, lp.height);
                }
                mEndRevealRect.set(0, 0, lp.width, lp.height);
                setBackgroundDrawableBounds(mBgDrawableStartScale);
                setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setRoundRect(rectOutline, mTaskCornerRadius);
                    }
                });
                setClipToOutline(true);
            } else {
                setBackground(finalDrawable);
            }

            if (!loadIconSignal.isCanceled()) {
                onIconLoadedRunnable.run();
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
    public void onAnimationStart(Animator animator) {}

    @Override
    public void onAnimationCancel(Animator animator) {}

    @Override
    public void onAnimationRepeat(Animator animator) {}

    /**
     * Creates a floating icon view for {@param originalView}.
     * @param originalView The view to copy
     * @param hideOriginal If true, it will hide {@param originalView} while this view is visible.
     * @param positionOut Rect that will hold the size and position of v.
     * @param isOpening True if this view replaces the icon for app open animation.
     */
    public static FloatingIconView getFloatingIconView(Launcher launcher, View originalView,
            boolean hideOriginal, Rect positionOut, boolean isOpening, FloatingIconView recycle) {
        if (recycle != null) {
            recycle.recycle();
        }
        FloatingIconView view = recycle != null ? recycle : new FloatingIconView(launcher);

        // Match the position of the original view.
        view.matchPositionOf(launcher, originalView, positionOut);

        // Get the drawable on the background thread
        // Must be called after matchPositionOf so that we know what size to load.
        if (originalView.getTag() instanceof ItemInfo) {
            view.mLoadIconSignal = new CancellationSignal();
            Runnable onIconLoaded = () -> {
                // Delay swapping views until the icon is loaded to prevent a flash.
                view.setVisibility(VISIBLE);
                if (hideOriginal) {
                    originalView.setVisibility(INVISIBLE);
                }
            };
            CancellationSignal loadIconSignal = view.mLoadIconSignal;
            new Handler(LauncherModel.getWorkerLooper()).postAtFrontOfQueue(() -> {
                view.getIcon(launcher, originalView, (ItemInfo) originalView.getTag(), isOpening,
                        onIconLoaded, loadIconSignal);
            });
        }

        // We need to add it to the overlay, but keep it invisible until animation starts..
        final DragLayer dragLayer = launcher.getDragLayer();
        view.setVisibility(INVISIBLE);
        ((ViewGroup) dragLayer.getParent()).getOverlay().add(view);

        if (hideOriginal) {
            view.mEndRunnable = () -> {
                AnimatorSet fade = new AnimatorSet();
                fade.setDuration(200);
                fade.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        originalView.setVisibility(VISIBLE);

                        if (originalView instanceof FolderIcon) {
                            FolderIcon folderIcon = (FolderIcon) originalView;
                            folderIcon.setBackgroundVisible(false);
                            folderIcon.getFolderName().setTextVisibility(false);
                        }
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        ((ViewGroup) dragLayer.getParent()).getOverlay().remove(view);

                        if (view.mRevealAnimator != null) {
                            view.mRevealAnimator.end();
                        }
                    }
                });

                if (originalView instanceof FolderIcon) {
                    FolderIcon folderIcon = (FolderIcon) originalView;
                    fade.play(folderIcon.getFolderName().createTextAlphaAnimator(true));
                    fade.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            folderIcon.setBackgroundVisible(true);
                            folderIcon.animateBgShadowAndStroke();
                            if (folderIcon.hasDot()) {
                                folderIcon.animateDotScale(0, 1f);
                            }
                        }
                    });
                } else {
                    fade.play(ObjectAnimator.ofFloat(originalView, ALPHA, 0f, 1f));
                }
                fade.start();
                // TODO: Do not run fade animation until we fix b/129421279.
                fade.end();
            };
        }
        return view;
    }

    private void recycle() {
        setTranslationX(0);
        setTranslationY(0);
        setScaleX(1);
        setScaleY(1);
        setAlpha(1);
        setBackground(null);
        if (mLoadIconSignal != null) {
            mLoadIconSignal.cancel();
        }
        mLoadIconSignal = null;
        mEndRunnable = null;
        mIsAdaptiveIcon = false;
        mForeground = null;
        mBackground = null;
        mClipPath = null;
        mFinalDrawableBounds.setEmpty();
        mBgDrawableBounds.setEmpty();;
        if (mRevealAnimator != null) {
            mRevealAnimator.cancel();
        }
        mRevealAnimator = null;
    }
}
