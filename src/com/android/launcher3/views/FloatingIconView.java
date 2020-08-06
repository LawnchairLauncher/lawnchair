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
import static com.android.launcher3.config.FeatureFlags.ADAPTIVE_ICON_WINDOW_ANIM;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;
import static com.android.launcher3.views.IconLabelDotView.setIconAndDotVisible;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.CancellationSignal;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.InsettableFrameLayout;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.dragndrop.FolderAdaptiveIcon;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.shortcuts.DeepShortcutView;
import com.android.launcher3.testing.TestProtocol;

/**
 * A view that is created to look like another view with the purpose of creating fluid animations.
 */
@TargetApi(Build.VERSION_CODES.Q)
public class FloatingIconView extends FrameLayout implements
        Animator.AnimatorListener, OnGlobalLayoutListener {

    private static final String TAG = FloatingIconView.class.getSimpleName();

    // Manages loading the icon on a worker thread
    private static @Nullable IconLoadResult sIconLoadResult;

    public static final float SHAPE_PROGRESS_DURATION = 0.10f;
    private static final int FADE_DURATION_MS = 200;
    private static final RectF sTmpRectF = new RectF();
    private static final Object[] sTmpObjArray = new Object[1];

    private Runnable mEndRunnable;
    private CancellationSignal mLoadIconSignal;

    private final Launcher mLauncher;
    private final boolean mIsRtl;

    private boolean mIsVerticalBarLayout = false;
    private boolean mIsOpening;

    private IconLoadResult mIconLoadResult;

    private ClipIconView mClipIconView;
    private @Nullable Drawable mBadge;

    private View mOriginalIcon;
    private RectF mPositionOut;
    private Runnable mOnTargetChangeRunnable;

    private final Rect mFinalDrawableBounds = new Rect();

    private AnimatorSet mFadeAnimatorSet;
    private ListenerView mListenerView;
    private Runnable mFastFinishRunnable;

    public FloatingIconView(Context context) {
        this(context, null);
    }

    public FloatingIconView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FloatingIconView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mLauncher = Launcher.getLauncher(context);
        mIsRtl = Utilities.isRtl(getResources());
        mListenerView = new ListenerView(context, attrs);
        mClipIconView = new ClipIconView(context, attrs);
        addView(mClipIconView);
        setWillNotDraw(false);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!mIsOpening) {
            getViewTreeObserver().addOnGlobalLayoutListener(this);
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
        mClipIconView.update(rect, progress, shapeProgressStart, cornerRadius, isOpening,
                this, mLauncher.getDeviceProfile(), mIsVerticalBarLayout);
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
            mClipIconView.endReveal();
        }
    }

    /**
     * Sets the size and position of this view to match {@param v}.
     *
     * @param v The view to copy
     * @param positionOut Rect that will hold the size and position of v.
     */
    private void matchPositionOf(Launcher launcher, View v, boolean isOpening, RectF positionOut) {
        getLocationBoundsForView(launcher, v, isOpening, positionOut);
        final InsettableFrameLayout.LayoutParams lp = new InsettableFrameLayout.LayoutParams(
                Math.round(positionOut.width()),
                Math.round(positionOut.height()));
        updatePosition(positionOut, lp);
        setLayoutParams(lp);

        mClipIconView.setLayoutParams(new FrameLayout.LayoutParams(lp.width, lp.height));
    }

    private void updatePosition(RectF pos, InsettableFrameLayout.LayoutParams lp) {
        mPositionOut.set(pos);
        lp.ignoreInsets = true;
        // Position the floating view exactly on top of the original
        lp.topMargin = Math.round(pos.top);
        if (mIsRtl) {
            lp.setMarginStart(Math.round(mLauncher.getDeviceProfile().widthPx - pos.right));
        } else {
            lp.setMarginStart(Math.round(pos.left));
        }
        // Set the properties here already to make sure they are available when running the first
        // animation frame.
        int left = mIsRtl
                ? mLauncher.getDeviceProfile().widthPx - lp.getMarginStart() - lp.width
                : lp.leftMargin;
        layout(left, lp.topMargin, left + lp.width, lp.topMargin + lp.height);
    }

    private static void getLocationBoundsForView(Launcher launcher, View v, boolean isOpening,
            RectF outRect) {
        getLocationBoundsForView(launcher, v, isOpening, outRect, new Rect());
    }

    /**
     * Gets the location bounds of a view and returns the overall rotation.
     * - For DeepShortcutView, we return the bounds of the icon view.
     * - For BubbleTextView, we return the icon bounds.
     */
    public static void getLocationBoundsForView(Launcher launcher, View v, boolean isOpening,
            RectF outRect, Rect outViewBounds) {
        boolean ignoreTransform = !isOpening;
        if (v instanceof DeepShortcutView) {
            v = ((DeepShortcutView) v).getBubbleText();
            ignoreTransform = false;
        } else if (v.getParent() instanceof DeepShortcutView) {
            v = ((DeepShortcutView) v.getParent()).getIconView();
            ignoreTransform = false;
        }
        if (v == null) {
            return;
        }

        if (v instanceof BubbleTextView) {
            ((BubbleTextView) v).getIconBounds(outViewBounds);
        } else if (v instanceof FolderIcon) {
            ((FolderIcon) v).getPreviewBounds(outViewBounds);
        } else {
            outViewBounds.set(0, 0, v.getWidth(), v.getHeight());
        }

        float[] points = new float[] {outViewBounds.left, outViewBounds.top, outViewBounds.right,
                outViewBounds.bottom};
        Utilities.getDescendantCoordRelativeToAncestor(v, launcher.getDragLayer(), points,
                false, ignoreTransform);
        outRect.set(
                Math.min(points[0], points[2]),
                Math.min(points[1], points[3]),
                Math.max(points[0], points[2]),
                Math.max(points[1], points[3]));
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
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !info.isDisabled(); // Use original icon for disabled icons.
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
            int width = (int) pos.width();
            int height = (int) pos.height();
            if (supportsAdaptiveIcons) {
                drawable = getFullDrawable(l, info, width, height, sTmpObjArray);
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
                    drawable = getFullDrawable(l, info, width, height, sTmpObjArray);
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
     * @param drawable The drawable of the original view.
     * @param badge The badge of the original view.
     * @param iconOffset The amount of offset needed to match this view with the original view.
     */
    @UiThread
    private void setIcon(@Nullable Drawable drawable, @Nullable Drawable badge, int iconOffset) {
        final InsettableFrameLayout.LayoutParams lp =
                (InsettableFrameLayout.LayoutParams) getLayoutParams();
        mBadge = badge;
        mClipIconView.setIcon(drawable, iconOffset, lp, mIsOpening, mIsVerticalBarLayout,
                mLauncher.getDeviceProfile());
        if (drawable instanceof AdaptiveIconDrawable) {
            final int originalHeight = lp.height;
            final int originalWidth = lp.width;

            mFinalDrawableBounds.set(0, 0, originalWidth, originalHeight);

            float aspectRatio = mLauncher.getDeviceProfile().aspectRatio;
            if (mIsVerticalBarLayout) {
                lp.width = (int) Math.max(lp.width, lp.height * aspectRatio);
            } else {
                lp.height = (int) Math.max(lp.height, lp.width * aspectRatio);
            }
            setLayoutParams(lp);

            final LayoutParams clipViewLp = (LayoutParams) mClipIconView.getLayoutParams();
            final int clipViewOgHeight = clipViewLp.height;
            final int clipViewOgWidth = clipViewLp.width;
            clipViewLp.width = lp.width;
            clipViewLp.height = lp.height;
            mClipIconView.setLayoutParams(clipViewLp);

            if (mBadge != null) {
                mBadge.setBounds(0, 0, clipViewOgWidth, clipViewOgHeight);
            }
        }
        invalidate();
    }

    /**
     * Checks if the icon result is loaded. If true, we set the icon immediately. Else, we add a
     * callback to set the icon once the icon result is loaded.
     */
    private void checkIconResult(View originalView) {
        CancellationSignal cancellationSignal = new CancellationSignal();

        if (mIconLoadResult == null) {
            Log.w(TAG, "No icon load result found in checkIconResult");
            return;
        }

        synchronized (mIconLoadResult) {
            if (mIconLoadResult.isIconLoaded) {
                setIcon(mIconLoadResult.drawable, mIconLoadResult.badge,
                        mIconLoadResult.iconOffset);
                setIconAndDotVisible(originalView, false);
            } else {
                mIconLoadResult.onIconLoaded = () -> {
                    if (cancellationSignal.isCanceled()) {
                        return;
                    }

                    setIcon(mIconLoadResult.drawable, mIconLoadResult.badge,
                            mIconLoadResult.iconOffset);

                    setVisibility(VISIBLE);
                    setIconAndDotVisible(originalView, false);
                };
                mLoadIconSignal = cancellationSignal;
            }
        }
    }

    @WorkerThread
    @SuppressWarnings("WrongThread")
    private static int getOffsetForIconBounds(Launcher l, Drawable drawable, RectF position) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || !(drawable instanceof AdaptiveIconDrawable)
                || (drawable instanceof FolderAdaptiveIcon)) {
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
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (mBadge != null) {
            mBadge.draw(canvas);
        }
    }

    /**
     * Sets a runnable that is called after a call to {@link #fastFinish()}.
     */
    public void setFastFinishRunnable(Runnable runnable) {
        mFastFinishRunnable = runnable;
    }

    public void fastFinish() {
        if (mFastFinishRunnable != null) {
            mFastFinishRunnable.run();
            mFastFinishRunnable = null;
        }
        if (mLoadIconSignal != null) {
            mLoadIconSignal.cancel();
            mLoadIconSignal = null;
        }
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
        if (!mIsOpening) {
            // When closing an app, we want the item on the workspace to be invisible immediately
            setIconAndDotVisible(mOriginalIcon, false);
        }
    }

    @Override
    public void onAnimationCancel(Animator animator) {}

    @Override
    public void onAnimationRepeat(Animator animator) {}

    @Override
    public void onGlobalLayout() {
        if (mOriginalIcon.isAttachedToWindow() && mPositionOut != null) {
            getLocationBoundsForView(mLauncher, mOriginalIcon, mIsOpening,
                    sTmpRectF);
            if (!sTmpRectF.equals(mPositionOut)) {
                updatePosition(sTmpRectF, (InsettableFrameLayout.LayoutParams) getLayoutParams());
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
        MODEL_EXECUTOR.getHandler().postAtFrontOfQueue(() -> {
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

        // We need to add it to the overlay, but keep it invisible until animation starts..
        view.setVisibility(INVISIBLE);
        parent.addView(view);
        dragLayer.addView(view.mListenerView);
        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.PAUSE_NOT_DETECTED, "getFloatingIconView. listenerView "
                    + "added to dragLayer. listenerView=" + view.mListenerView + ", fiv=" + view,
                    new Exception());
        }
        view.mListenerView.setListener(view::fastFinish);

        view.mEndRunnable = () -> {
            view.mEndRunnable = null;

            if (hideOriginal) {
                if (isOpening) {
                    setIconAndDotVisible(originalView, true);
                    view.finish(dragLayer);
                } else {
                    view.mFadeAnimatorSet = view.createFadeAnimation(originalView, dragLayer);
                    view.mFadeAnimatorSet.start();
                }
            } else {
                view.finish(dragLayer);
            }
        };

        // Must be called after matchPositionOf so that we know what size to load.
        // Must be called after the fastFinish listener and end runnable is created so that
        // the icon is not left in a hidden state.
        if (shouldLoadIcon) {
            view.checkIconResult(originalView);
        }

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

        if (originalView instanceof IconLabelDotView) {
            fade.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    setIconAndDotVisible(originalView, true);
                }
            });
        }

        if (originalView instanceof BubbleTextView) {
            BubbleTextView btv = (BubbleTextView) originalView;
            fade.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    btv.setIconVisible(true);
                    btv.setForceHideDot(true);
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
        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.PAUSE_NOT_DETECTED, "listenerView removed from dragLayer. "
                    + "listenerView=" + mListenerView + ", fiv=" + this, new Exception());
        }
        recycle();
        mLauncher.getViewCache().recycleView(R.layout.floating_icon_view, this);
    }

    private void recycle() {
        setTranslationX(0);
        setTranslationY(0);
        setScaleX(1);
        setScaleY(1);
        setAlpha(1);
        if (mLoadIconSignal != null) {
            mLoadIconSignal.cancel();
        }
        mLoadIconSignal = null;
        mEndRunnable = null;
        mFinalDrawableBounds.setEmpty();
        if (mFadeAnimatorSet != null) {
            mFadeAnimatorSet.cancel();
        }
        mPositionOut = null;
        mFadeAnimatorSet = null;
        mListenerView.setListener(null);
        mOriginalIcon = null;
        mOnTargetChangeRunnable = null;
        mBadge = null;
        sTmpObjArray[0] = null;
        mIconLoadResult = null;
        mClipIconView.recycle();
        mFastFinishRunnable = null;
    }

    private static class IconLoadResult {
        final ItemInfo itemInfo;
        Drawable drawable;
        Drawable badge;
        int iconOffset;
        Runnable onIconLoaded;
        boolean isIconLoaded;

        IconLoadResult(ItemInfo itemInfo) {
            this.itemInfo = itemInfo;
        }
    }
}
