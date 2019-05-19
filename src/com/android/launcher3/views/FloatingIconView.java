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

import static com.android.launcher3.LauncherAnimUtils.DRAWABLE_ALPHA;
import static com.android.launcher3.Utilities.getBadge;
import static com.android.launcher3.Utilities.mapToRange;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.config.FeatureFlags.ADAPTIVE_ICON_WINDOW_ANIM;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
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
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
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
import com.android.launcher3.graphics.IconShape;
import com.android.launcher3.graphics.ShiftedBitmapDrawable;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.shortcuts.DeepShortcutView;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/**
 * A view that is created to look like another view with the purpose of creating fluid animations.
 */
@TargetApi(Build.VERSION_CODES.Q)
public class FloatingIconView extends View implements
        Animator.AnimatorListener, ClipPathView, OnGlobalLayoutListener {

    public static final float SHAPE_PROGRESS_DURATION = 0.15f;
    private static final int FADE_DURATION_MS = 200;
    private static final Rect sTmpRect = new Rect();
    private static final RectF sTmpRectF = new RectF();
    private static final Object[] sTmpObjArray = new Object[1];

    // We spring the foreground drawable relative to the icon's movement in the DragLayer.
    // We then use these two factor values to scale the movement of the fg within this view.
    private static final int FG_TRANS_X_FACTOR = 80;
    private static final int FG_TRANS_Y_FACTOR = 100;

    private static final FloatPropertyCompat<FloatingIconView> mFgTransYProperty
            = new FloatPropertyCompat<FloatingIconView>("FloatingViewFgTransY") {
        @Override
        public float getValue(FloatingIconView view) {
            return view.mFgTransY;
        }

        @Override
        public void setValue(FloatingIconView view, float transY) {
            view.mFgTransY = transY;
            view.invalidate();
        }
    };

    private static final FloatPropertyCompat<FloatingIconView> mFgTransXProperty
            = new FloatPropertyCompat<FloatingIconView>("FloatingViewFgTransX") {
        @Override
        public float getValue(FloatingIconView view) {
            return view.mFgTransX;
        }

        @Override
        public void setValue(FloatingIconView view, float transX) {
            view.mFgTransX = transX;
            view.invalidate();
        }
    };

    private Runnable mEndRunnable;
    private CancellationSignal mLoadIconSignal;

    private final Launcher mLauncher;
    private final int mBlurSizeOutline;

    private boolean mIsVerticalBarLayout = false;
    private boolean mIsAdaptiveIcon = false;

    private @Nullable Drawable mBadge;
    private @Nullable Drawable mForeground;
    private @Nullable Drawable mBackground;
    private float mRotation;
    private ValueAnimator mRevealAnimator;
    private final Rect mStartRevealRect = new Rect();
    private final Rect mEndRevealRect = new Rect();
    private Path mClipPath;
    private float mTaskCornerRadius;

    private View mOriginalIcon;
    private RectF mPositionOut;
    private Runnable mOnTargetChangeRunnable;

    private final Rect mOutline = new Rect();
    private final Rect mFinalDrawableBounds = new Rect();

    private AnimatorSet mFadeAnimatorSet;
    private ListenerView mListenerView;

    private final SpringAnimation mFgSpringY;
    private float mFgTransY;
    private final SpringAnimation mFgSpringX;
    private float mFgTransX;

    private FloatingIconView(Launcher launcher) {
        super(launcher);
        mLauncher = launcher;
        mBlurSizeOutline = getResources().getDimensionPixelSize(
                R.dimen.blur_size_medium_outline);
        mListenerView = new ListenerView(launcher, null);

        mFgSpringX = new SpringAnimation(this, mFgTransXProperty)
                .setSpring(new SpringForce()
                .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY)
                .setStiffness(SpringForce.STIFFNESS_LOW));
        mFgSpringY = new SpringAnimation(this, mFgTransYProperty)
                .setSpring(new SpringForce()
                        .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY)
                        .setStiffness(SpringForce.STIFFNESS_LOW));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnGlobalLayoutListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnGlobalLayoutListener(this);
        super.onDetachedFromWindow();
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

        float minSize = Math.min(lp.width, lp.height);
        float scaleX = rect.width() / minSize;
        float scaleY = rect.height() / minSize;
        float scale = Math.max(1f, Math.min(scaleX, scaleY));

        setPivotX(0);
        setPivotY(0);
        setScaleX(scale);
        setScaleY(scale);

        // shapeRevealProgress = 1 when progress = shapeProgressStart + SHAPE_PROGRESS_DURATION
        float toMax = isOpening ? 1 / SHAPE_PROGRESS_DURATION : 1f;
        float shapeRevealProgress = Utilities.boundToRange(mapToRange(
                Math.max(shapeProgressStart, progress), shapeProgressStart, 1f, 0, toMax,
                LINEAR), 0, 1);

        if (mIsVerticalBarLayout) {
            mOutline.right = (int) (rect.width() / scale);
        } else {
            mOutline.bottom = (int) (rect.height() / scale);
        }

        mTaskCornerRadius = cornerRadius / scale;
        if (mIsAdaptiveIcon) {
            if (!isOpening && shapeRevealProgress >= 0) {
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

            float drawableScale = (mIsVerticalBarLayout ? mOutline.width() : mOutline.height())
                    / minSize;
            setBackgroundDrawableBounds(drawableScale);
            if (isOpening) {
                // Center align foreground
                int height = mFinalDrawableBounds.height();
                int width = mFinalDrawableBounds.width();
                int diffY = mIsVerticalBarLayout ? 0
                        : (int) (((height * drawableScale) - height) / 2);
                int diffX = mIsVerticalBarLayout ? (int) (((width * drawableScale) - width) / 2)
                        : 0;
                sTmpRect.set(mFinalDrawableBounds);
                sTmpRect.offset(diffX, diffY);
                mForeground.setBounds(sTmpRect);
            } else {
                // Spring the foreground relative to the icon's movement within the DragLayer.
                int diffX = (int) (dX / mLauncher.getDeviceProfile().availableWidthPx
                        * FG_TRANS_X_FACTOR);
                int diffY = (int) (dY / mLauncher.getDeviceProfile().availableHeightPx
                        * FG_TRANS_Y_FACTOR);

                mFgSpringX.animateToFinalPosition(diffX);
                mFgSpringY.animateToFinalPosition(diffY);
            }


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
    private void matchPositionOf(View v, RectF positionOut) {
        float rotation = getLocationBoundsForView(v, positionOut);
        final LayoutParams lp = new LayoutParams(
                Math.round(positionOut.width()),
                Math.round(positionOut.height()));
        updatePosition(rotation, positionOut, lp);
        setLayoutParams(lp);
    }

    private void updatePosition(float rotation, RectF position, LayoutParams lp) {
        mRotation = rotation;
        mPositionOut.set(position);
        lp.ignoreInsets = true;
        // Position the floating view exactly on top of the original
        lp.leftMargin = Math.round(position.left);
        lp.topMargin = Math.round(position.top);

        // Set the properties here already to make sure they are available when running the first
        // animation frame.
        layout(lp.leftMargin, lp.topMargin, lp.leftMargin + lp.width, lp.topMargin
                + lp.height);

    }

    /**
     * Gets the location bounds of a view and returns the overall rotation.
     * - For DeepShortcutView, we return the bounds of the icon view.
     * - For BubbleTextView, we return the icon bounds.
     */
    private float getLocationBoundsForView(View v, RectF outRect) {
        boolean ignoreTransform = true;
        if (v instanceof DeepShortcutView) {
            v = ((DeepShortcutView) v).getBubbleText();
            ignoreTransform = false;
        } else if (v.getParent() instanceof DeepShortcutView) {
            v = ((DeepShortcutView) v.getParent()).getIconView();
            ignoreTransform = false;
        }
        if (v == null) {
            return 0;
        }

        Rect iconBounds = new Rect();
        if (v instanceof BubbleTextView) {
            ((BubbleTextView) v).getIconBounds(iconBounds);
        } else if (v instanceof FolderIcon) {
            ((FolderIcon) v).getPreviewBounds(iconBounds);
        } else {
            iconBounds.set(0, 0, v.getWidth(), v.getHeight());
        }

        float[] points = new float[] {iconBounds.left, iconBounds.top, iconBounds.right,
                iconBounds.bottom};
        float[] rotation = new float[] {0};
        Utilities.getDescendantCoordRelativeToAncestor(v, mLauncher.getDragLayer(), points,
                false, ignoreTransform, rotation);
        outRect.set(
                Math.min(points[0], points[2]),
                Math.min(points[1], points[3]),
                Math.max(points[0], points[2]),
                Math.max(points[1], points[3]));
        return rotation[0];
    }

    @WorkerThread
    private void getIcon(View v, ItemInfo info, boolean isOpening,
            Runnable onIconLoadedRunnable, CancellationSignal loadIconSignal) {
        final LayoutParams lp = (LayoutParams) getLayoutParams();
        Drawable drawable = null;
        boolean supportsAdaptiveIcons = ADAPTIVE_ICON_WINDOW_ANIM.get()
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
        Drawable btvIcon = v instanceof BubbleTextView ? ((BubbleTextView) v).getIcon() : null;
        if (info instanceof SystemShortcut) {
            if (v instanceof ImageView) {
                drawable = ((ImageView) v).getDrawable();
            } else if (v instanceof DeepShortcutView) {
                drawable = ((DeepShortcutView) v).getIconView().getBackground();
            } else {
                drawable = v.getBackground();
            }
        } else {
            if (supportsAdaptiveIcons) {
                drawable = Utilities.getFullDrawable(mLauncher, info, lp.width, lp.height,
                        false, sTmpObjArray);
                if ((drawable instanceof AdaptiveIconDrawable)) {
                    mBadge = getBadge(mLauncher, info, sTmpObjArray[0]);
                } else {
                    // The drawable we get back is not an adaptive icon, so we need to use the
                    // BubbleTextView icon that is already legacy treated.
                    drawable = btvIcon;
                }
            } else {
                if (v instanceof BubbleTextView) {
                    // Similar to DragView, we simply use the BubbleTextView icon here.
                    drawable = btvIcon;
                } else {
                    drawable = Utilities.getFullDrawable(mLauncher, info, lp.width, lp.height,
                            false, sTmpObjArray);
                }
            }
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

                if (mBadge != null) {
                    mBadge.setBounds(mStartRevealRect);
                    if (!isOpening) {
                        DRAWABLE_ALPHA.set(mBadge, 0);
                    }

                }

                if (!isFolderIcon) {
                    Utilities.scaleRectAboutCenter(mStartRevealRect,
                            IconShape.getNormalizationScale());
                }

                float aspectRatio = mLauncher.getDeviceProfile().aspectRatio;
                if (mIsVerticalBarLayout) {
                    lp.width = (int) Math.max(lp.width, lp.height * aspectRatio);
                } else {
                    lp.height = (int) Math.max(lp.height, lp.width * aspectRatio);
                }
                layout(lp.leftMargin, lp.topMargin, lp.leftMargin + lp.width, lp.topMargin
                        + lp.height);

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
                setBackgroundDrawableBounds(bgDrawableStartScale);
                mEndRevealRect.set(0, 0, lp.width, lp.height);
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

            if (!loadIconSignal.isCanceled()) {
                onIconLoadedRunnable.run();
            }
            invalidate();
            invalidateOutline();
        });
    }

    private void setBackgroundDrawableBounds(float scale) {
        sTmpRect.set(mFinalDrawableBounds);
        Utilities.scaleRectAboutCenter(sTmpRect, scale);
        // Since the drawable is at the top of the view, we need to offset to keep it centered.
        if (mIsVerticalBarLayout) {
            sTmpRect.offsetTo((int) (mFinalDrawableBounds.left * scale), sTmpRect.top);
        } else {
            sTmpRect.offsetTo(sTmpRect.left, (int) (mFinalDrawableBounds.top * scale));
        }
        mBackground.setBounds(sTmpRect);
    }

    @WorkerThread
    private int getOffsetForIconBounds(Drawable drawable) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                !(drawable instanceof AdaptiveIconDrawable)) {
            return 0;
        }

        final LayoutParams lp = (LayoutParams) getLayoutParams();
        Rect bounds = new Rect(0, 0, lp.width + mBlurSizeOutline, lp.height + mBlurSizeOutline);
        bounds.inset(mBlurSizeOutline / 2, mBlurSizeOutline / 2);

        try (LauncherIcons li = LauncherIcons.obtain(getContext())) {
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

    @Override
    public void draw(Canvas canvas) {
        int count = canvas.save();
        canvas.rotate(mRotation,
                mFinalDrawableBounds.exactCenterX(), mFinalDrawableBounds.exactCenterY());
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
        if (mBadge != null) {
            mBadge.draw(canvas);
        }
        canvas.restoreToCount(count);
    }

    public void onListenerViewClosed() {
        // Fast finish here.
        if (mEndRunnable != null) {
            mEndRunnable.run();
            mEndRunnable = null;
        }
        if (mFadeAnimatorSet != null) {
            mFadeAnimatorSet.end();
            mFadeAnimatorSet = null;
        }
    }

    @Override
    public void onAnimationStart(Animator animator) {}

    @Override
    public void onAnimationCancel(Animator animator) {}

    @Override
    public void onAnimationRepeat(Animator animator) {}

    @Override
    public void onGlobalLayout() {
        if (mOriginalIcon.isAttachedToWindow() && mPositionOut != null) {
            float rotation = getLocationBoundsForView(mOriginalIcon, sTmpRectF);
            if (rotation != mRotation || !sTmpRectF.equals(mPositionOut)) {
                updatePosition(rotation, sTmpRectF, (LayoutParams) getLayoutParams());
                if (mOnTargetChangeRunnable != null) {
                    mOnTargetChangeRunnable.run();
                }
            }
        }
    }

    public void setOnTargetChangeListener(Runnable onTargetChangeListener) {
        mOnTargetChangeRunnable = onTargetChangeListener;
    }

    /**
     * Creates a floating icon view for {@param originalView}.
     * @param originalView The view to copy
     * @param hideOriginal If true, it will hide {@param originalView} while this view is visible.
     *                     Else, we will not draw anything in this view.
     * @param positionOut Rect that will hold the size and position of v.
     * @param isOpening True if this view replaces the icon for app open animation.
     */
    public static FloatingIconView getFloatingIconView(Launcher launcher, View originalView,
            boolean hideOriginal, RectF positionOut, boolean isOpening, FloatingIconView recycle) {
        if (recycle != null) {
            recycle.recycle();
        }
        FloatingIconView view = recycle != null ? recycle : new FloatingIconView(launcher);
        view.mIsVerticalBarLayout = launcher.getDeviceProfile().isVerticalBarLayout();

        view.mOriginalIcon = originalView;
        view.mPositionOut = positionOut;
        // Match the position of the original view.
        view.matchPositionOf(originalView, positionOut);

        // Get the drawable on the background thread
        // Must be called after matchPositionOf so that we know what size to load.
        if (originalView.getTag() instanceof ItemInfo && hideOriginal) {
            view.mLoadIconSignal = new CancellationSignal();
            Runnable onIconLoaded = () -> {
                // Delay swapping views until the icon is loaded to prevent a flash.
                view.setVisibility(VISIBLE);
                originalView.setVisibility(INVISIBLE);
            };
            CancellationSignal loadIconSignal = view.mLoadIconSignal;
            new Handler(LauncherModel.getWorkerLooper()).postAtFrontOfQueue(() -> {
                view.getIcon(originalView, (ItemInfo) originalView.getTag(), isOpening,
                        onIconLoaded, loadIconSignal);
            });
        }

        // We need to add it to the overlay, but keep it invisible until animation starts..
        final DragLayer dragLayer = launcher.getDragLayer();
        view.setVisibility(INVISIBLE);
        ((ViewGroup) dragLayer.getParent()).addView(view);
        dragLayer.addView(view.mListenerView);
        view.mListenerView.setListener(view::onListenerViewClosed);

        view.mEndRunnable = () -> {
            view.mEndRunnable = null;

            if (hideOriginal) {
                if (isOpening) {
                    originalView.setVisibility(VISIBLE);
                    view.finish(dragLayer);
                } else {
                    view.mFadeAnimatorSet = view.createFadeAnimation(originalView, dragLayer);
                    view.mFadeAnimatorSet.start();
                }
            } else {
                view.finish(dragLayer);
            }
        };
        return view;
    }

    private AnimatorSet createFadeAnimation(View originalView, DragLayer dragLayer) {
        AnimatorSet fade = new AnimatorSet();
        fade.setDuration(FADE_DURATION_MS);
        fade.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                originalView.setVisibility(VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                finish(dragLayer);
            }
        });

        if (mBadge != null) {
            ObjectAnimator badgeFade = ObjectAnimator.ofInt(mBadge, DRAWABLE_ALPHA, 255);
            badgeFade.addUpdateListener(valueAnimator -> invalidate());
            fade.play(badgeFade);
        }

        if (originalView instanceof BubbleTextView) {
            BubbleTextView btv = (BubbleTextView) originalView;
            btv.forceHideDot(true);
            fade.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    btv.forceHideDot(false);
                }
            });
        }

        if (originalView instanceof FolderIcon) {
            FolderIcon folderIcon = (FolderIcon) originalView;
            folderIcon.setBackgroundVisible(false);
            folderIcon.getFolderName().setTextVisibility(false);
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

        return fade;
    }

    private void finish(DragLayer dragLayer) {
        ((ViewGroup) dragLayer.getParent()).removeView(this);
        dragLayer.removeView(mListenerView);
        recycle();
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
        if (mRevealAnimator != null) {
            mRevealAnimator.cancel();
        }
        mRevealAnimator = null;
        if (mFadeAnimatorSet != null) {
            mFadeAnimatorSet.cancel();
        }
        mPositionOut = null;
        mFadeAnimatorSet = null;
        mListenerView.setListener(null);
        mOriginalIcon = null;
        mOnTargetChangeRunnable = null;
        mTaskCornerRadius = 0;
        mOutline.setEmpty();
        mFgTransY = 0;
        mFgSpringX.cancel();
        mFgTransX = 0;
        mFgSpringY.cancel();
        mBadge = null;
    }
}
