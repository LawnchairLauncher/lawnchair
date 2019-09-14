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
import static com.android.launcher3.Utilities.getFullDrawable;
import static com.android.launcher3.Utilities.mapToRange;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.config.FeatureFlags.ADAPTIVE_ICON_WINDOW_ANIM;
import static com.android.launcher3.states.RotationHelper.REQUEST_LOCK;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
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
import android.os.CancellationSignal;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
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
import androidx.annotation.UiThread;
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

    private static final String TAG = FloatingIconView.class.getSimpleName();

    // Manages loading the icon on a worker thread
    private static @Nullable IconLoadResult sIconLoadResult;

    public static final float SHAPE_PROGRESS_DURATION = 0.10f;
    private static final int FADE_DURATION_MS = 200;
    private static final Rect sTmpRect = new Rect();
    private static final RectF sTmpRectF = new RectF();
    private static final Object[] sTmpObjArray = new Object[1];

    // We spring the foreground drawable relative to the icon's movement in the DragLayer.
    // We then use these two factor values to scale the movement of the fg within this view.
    private static final int FG_TRANS_X_FACTOR = 60;
    private static final int FG_TRANS_Y_FACTOR = 75;

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
    private final boolean mIsRtl;

    private boolean mIsVerticalBarLayout = false;
    private boolean mIsAdaptiveIcon = false;
    private boolean mIsOpening;

    private IconLoadResult mIconLoadResult;

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

    public FloatingIconView(Context context) {
        this(context, null);
    }

    public FloatingIconView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FloatingIconView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mLauncher = Launcher.getLauncher(context);
        mBlurSizeOutline = getResources().getDimensionPixelSize(
                R.dimen.blur_size_medium_outline);
        mIsRtl = Utilities.isRtl(getResources());
        mListenerView = new ListenerView(context, attrs);

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
        if (!mIsOpening) {
            getViewTreeObserver().addOnGlobalLayoutListener(this);
            mLauncher.getRotationHelper().setCurrentTransitionRequest(REQUEST_LOCK);
        }
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
        float dX = mIsRtl
                ? rect.left
                - (mLauncher.getDeviceProfile().widthPx - lp.getMarginStart() - lp.width)
                : rect.left - lp.getMarginStart();
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
    private void matchPositionOf(Launcher launcher, View v, boolean isOpening, RectF positionOut) {
        float rotation = getLocationBoundsForView(launcher, v, isOpening, positionOut);
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
        lp.topMargin = Math.round(position.top);
        if (mIsRtl) {
            lp.setMarginStart(Math.round(mLauncher.getDeviceProfile().widthPx - position.right));
        } else {
            lp.setMarginStart(Math.round(position.left));
        }
        // Set the properties here already to make sure they are available when running the first
        // animation frame.
        int left = mIsRtl
                ? mLauncher.getDeviceProfile().widthPx - lp.getMarginStart() - lp.width
                : lp.leftMargin;
        layout(left, lp.topMargin, left + lp.width, lp.topMargin + lp.height);
    }

    /**
     * Gets the location bounds of a view and returns the overall rotation.
     * - For DeepShortcutView, we return the bounds of the icon view.
     * - For BubbleTextView, we return the icon bounds.
     */
    private static float getLocationBoundsForView(Launcher launcher, View v, boolean isOpening,
            RectF outRect) {
        boolean ignoreTransform = !isOpening;
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
        Utilities.getDescendantCoordRelativeToAncestor(v, launcher.getDragLayer(), points,
                false, ignoreTransform, rotation);
        outRect.set(
                Math.min(points[0], points[2]),
                Math.min(points[1], points[3]),
                Math.max(points[0], points[2]),
                Math.max(points[1], points[3]));
        return rotation[0];
    }

    /**
     * Loads the icon and saves the results to {@link #sIconLoadResult}.
     * Runs onIconLoaded callback (if any), which signifies that the FloatingIconView is
     * ready to display the icon. Otherwise, the FloatingIconView will grab the results when its
     * initialized.
     *
     * @param originalView The View that the FloatingIconView will replace.
     * @param info ItemInfo of the originalView
     * @param pos The position of the view.
     */
    @WorkerThread
    @SuppressWarnings("WrongThread")
    private static void getIconResult(Launcher l, View originalView, ItemInfo info, RectF pos,
            IconLoadResult iconLoadResult) {
        Drawable drawable = null;
        Drawable badge = null;
        boolean supportsAdaptiveIcons = ADAPTIVE_ICON_WINDOW_ANIM.get()
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
        Drawable btvIcon = originalView instanceof BubbleTextView
                ? ((BubbleTextView) originalView).getIcon() : null;
        if (info instanceof SystemShortcut) {
            if (originalView instanceof ImageView) {
                drawable = ((ImageView) originalView).getDrawable();
            } else if (originalView instanceof DeepShortcutView) {
                drawable = ((DeepShortcutView) originalView).getIconView().getBackground();
            } else {
                drawable = originalView.getBackground();
            }
        } else {
            boolean isFolderIcon = originalView instanceof FolderIcon;
            int width = isFolderIcon ? originalView.getWidth() : (int) pos.width();
            int height = isFolderIcon ? originalView.getHeight() : (int) pos.height();
            if (supportsAdaptiveIcons) {
                drawable = getFullDrawable(l, info, width, height, false, sTmpObjArray);
                if (drawable instanceof AdaptiveIconDrawable) {
                    badge = getBadge(l, info, sTmpObjArray[0]);
                } else {
                    // The drawable we get back is not an adaptive icon, so we need to use the
                    // BubbleTextView icon that is already legacy treated.
                    drawable = btvIcon;
                }
            } else {
                if (originalView instanceof BubbleTextView) {
                    // Similar to DragView, we simply use the BubbleTextView icon here.
                    drawable = btvIcon;
                } else {
                    drawable = getFullDrawable(l, info, width, height, false, sTmpObjArray);
                }
            }
        }

        drawable = drawable == null ? null : drawable.getConstantState().newDrawable();
        int iconOffset = getOffsetForIconBounds(l, drawable, pos);
        synchronized (iconLoadResult) {
            iconLoadResult.drawable = drawable;
            iconLoadResult.badge = badge;
            iconLoadResult.iconOffset = iconOffset;
            if (iconLoadResult.onIconLoaded != null) {
                l.getMainExecutor().execute(iconLoadResult.onIconLoaded);
                iconLoadResult.onIconLoaded = null;
            }
            iconLoadResult.isIconLoaded = true;
        }
    }

    /**
     * Sets the drawables of the {@param originalView} onto this view.
     *
     * @param originalView The View that the FloatingIconView will replace.
     * @param drawable The drawable of the original view.
     * @param badge The badge of the original view.
     * @param iconOffset The amount of offset needed to match this view with the original view.
     */
    @UiThread
    private void setIcon(View originalView, @Nullable Drawable drawable, @Nullable Drawable badge,
            int iconOffset) {
        mBadge = badge;

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

            final LayoutParams lp = (LayoutParams) getLayoutParams();
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
                if (!mIsOpening && !isFolderIcon) {
                    DRAWABLE_ALPHA.set(mBadge, 0);
                }
            }

            if (isFolderIcon) {
                ((FolderIcon) originalView).getPreviewBounds(sTmpRect);
                float bgStroke = ((FolderIcon) originalView).getBackgroundStrokeWidth();
                if (mForeground instanceof ShiftedBitmapDrawable) {
                    ShiftedBitmapDrawable sbd = (ShiftedBitmapDrawable) mForeground;
                    sbd.setShiftX(sbd.getShiftX() - sTmpRect.left - bgStroke);
                    sbd.setShiftY(sbd.getShiftY() - sTmpRect.top - bgStroke);
                }
                if (mBadge instanceof ShiftedBitmapDrawable) {
                    ShiftedBitmapDrawable sbd = (ShiftedBitmapDrawable) mBadge;
                    sbd.setShiftX(sbd.getShiftX() - sTmpRect.left - bgStroke);
                    sbd.setShiftY(sbd.getShiftY() - sTmpRect.top - bgStroke);
                }
            } else {
                Utilities.scaleRectAboutCenter(mStartRevealRect,
                        IconShape.getNormalizationScale());
            }

            float aspectRatio = mLauncher.getDeviceProfile().aspectRatio;
            if (mIsVerticalBarLayout) {
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
            if (mIsOpening) {
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
            setBackground(drawable);
            setClipToOutline(false);
        }

        invalidate();
        invalidateOutline();
    }

    /**
     * Checks if the icon result is loaded. If true, we set the icon immediately. Else, we add a
     * callback to set the icon once the icon result is loaded.
     */
    private void checkIconResult(View originalView, boolean isOpening) {
        CancellationSignal cancellationSignal = new CancellationSignal();

        if (mIconLoadResult == null) {
            Log.w(TAG, "No icon load result found in checkIconResult");
            return;
        }

        synchronized (mIconLoadResult) {
            if (mIconLoadResult.isIconLoaded) {
                setIcon(originalView, mIconLoadResult.drawable, mIconLoadResult.badge,
                        mIconLoadResult.iconOffset);
                if (isOpening) {
                    hideOriginalView(originalView);
                }
            } else {
                mIconLoadResult.onIconLoaded = () -> {
                    if (cancellationSignal.isCanceled()) {
                        return;
                    }

                    setIcon(originalView, mIconLoadResult.drawable, mIconLoadResult.badge,
                            mIconLoadResult.iconOffset);

                    setVisibility(VISIBLE);
                    if (isOpening) {
                        // Delay swapping views until the icon is loaded to prevent a flash.
                        hideOriginalView(originalView);
                    }
                };
                mLoadIconSignal = cancellationSignal;
            }
        }
    }

    private void hideOriginalView(View originalView) {
        if (originalView instanceof BubbleTextView) {
            ((BubbleTextView) originalView).setIconVisible(false);
            ((BubbleTextView) originalView).setForceHideDot(true);
        } else {
            originalView.setVisibility(INVISIBLE);
        }
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
    @SuppressWarnings("WrongThread")
    private static int getOffsetForIconBounds(Launcher l, Drawable drawable, RectF position) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                !(drawable instanceof AdaptiveIconDrawable)) {
            return 0;
        }
        int blurSizeOutline =
                l.getResources().getDimensionPixelSize(R.dimen.blur_size_medium_outline);

        Rect bounds = new Rect(0, 0, (int) position.width() + blurSizeOutline,
                (int) position.height() + blurSizeOutline);
        bounds.inset(blurSizeOutline / 2, blurSizeOutline / 2);

        try (LauncherIcons li = LauncherIcons.obtain(l)) {
            Utilities.scaleRectAboutCenter(bounds, li.getNormalizer().getScale(drawable, null,
                    null, null));
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

    public void fastFinish() {
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
    public void onAnimationStart(Animator animator) {
        if (mIconLoadResult != null && mIconLoadResult.isIconLoaded) {
            setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onAnimationCancel(Animator animator) {}

    @Override
    public void onAnimationRepeat(Animator animator) {}

    @Override
    public void onGlobalLayout() {
        if (mOriginalIcon.isAttachedToWindow() && mPositionOut != null) {
            float rotation = getLocationBoundsForView(mLauncher, mOriginalIcon, mIsOpening,
                    sTmpRectF);
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
     * Loads the icon drawable on a worker thread to reduce latency between swapping views.
     */
    @UiThread
    public static IconLoadResult fetchIcon(Launcher l, View v, ItemInfo info, boolean isOpening) {
        IconLoadResult result = new IconLoadResult(info);
        new Handler(LauncherModel.getWorkerLooper()).postAtFrontOfQueue(() -> {
            RectF position = new RectF();
            getLocationBoundsForView(l, v, isOpening, position);
            getIconResult(l, v, info, position, result);
        });

        sIconLoadResult = result;
        return result;
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
            boolean hideOriginal, RectF positionOut, boolean isOpening) {
        final DragLayer dragLayer = launcher.getDragLayer();
        ViewGroup parent = (ViewGroup) dragLayer.getParent();
        FloatingIconView view = launcher.getViewCache().getView(R.layout.floating_icon_view,
                launcher, parent);
        view.recycle();

        // Get the drawable on the background thread
        boolean shouldLoadIcon = originalView.getTag() instanceof ItemInfo && hideOriginal;
        if (shouldLoadIcon) {
            if (sIconLoadResult != null && sIconLoadResult.itemInfo == originalView.getTag()) {
                view.mIconLoadResult = sIconLoadResult;
            } else {
                view.mIconLoadResult = fetchIcon(launcher, originalView,
                        (ItemInfo) originalView.getTag(), isOpening);
            }
        }
        sIconLoadResult = null;

        view.mIsVerticalBarLayout = launcher.getDeviceProfile().isVerticalBarLayout();
        view.mIsOpening = isOpening;
        view.mOriginalIcon = originalView;
        view.mPositionOut = positionOut;

        // Match the position of the original view.
        view.matchPositionOf(launcher, originalView, isOpening, positionOut);

        // Must be called after matchPositionOf so that we know what size to load.
        if (shouldLoadIcon) {
            view.checkIconResult(originalView, isOpening);
        }

        // We need to add it to the overlay, but keep it invisible until animation starts..
        view.setVisibility(INVISIBLE);
        parent.addView(view);
        dragLayer.addView(view.mListenerView);
        view.mListenerView.setListener(view::fastFinish);

        view.mEndRunnable = () -> {
            view.mEndRunnable = null;

            if (hideOriginal) {
                if (isOpening) {
                    if (originalView instanceof BubbleTextView) {
                        ((BubbleTextView) originalView).setIconVisible(true);
                        ((BubbleTextView) originalView).setForceHideDot(false);
                    } else {
                        originalView.setVisibility(VISIBLE);
                    }
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

        if (originalView instanceof IconLabelDotView) {
            IconLabelDotView view = (IconLabelDotView) originalView;
            fade.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    view.setIconVisible(true);
                    view.setForceHideDot(false);
                }
            });
        }

        if (originalView instanceof BubbleTextView) {
            BubbleTextView btv = (BubbleTextView) originalView;
            fade.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    btv.setIconVisible(true);
                }
            });
            fade.play(ObjectAnimator.ofInt(btv.getIcon(), DRAWABLE_ALPHA, 0, 255));
        } else if (!(originalView instanceof FolderIcon)) {
            fade.play(ObjectAnimator.ofFloat(originalView, ALPHA, 0f, 1f));
        }

        return fade;
    }

    private void finish(DragLayer dragLayer) {
        ((ViewGroup) dragLayer.getParent()).removeView(this);
        dragLayer.removeView(mListenerView);
        recycle();
        mLauncher.getViewCache().recycleView(R.layout.floating_icon_view, this);
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
        sTmpObjArray[0] = null;
        mIconLoadResult = null;
    }

    private static class IconLoadResult {
        final ItemInfo itemInfo;
        Drawable drawable;
        Drawable badge;
        int iconOffset;
        Runnable onIconLoaded;
        boolean isIconLoaded;

        public IconLoadResult(ItemInfo itemInfo) {
            this.itemInfo = itemInfo;
        }
    }
}
