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
package com.android.quickstep.views;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Size;
import android.view.GhostView;
import android.view.RemoteAnimationTarget;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.FloatingView;
import com.android.launcher3.views.ListenerView;
import com.android.launcher3.widget.LauncherAppWidgetHostView;
import com.android.launcher3.widget.RoundedCornerEnforcement;

/** A view that mimics an App Widget through a launch animation. */
@TargetApi(Build.VERSION_CODES.S)
public class FloatingWidgetView extends FrameLayout implements AnimatorListener,
        OnGlobalLayoutListener, FloatingView {
    private static final Matrix sTmpMatrix = new Matrix();

    private final QuickstepLauncher mLauncher;
    private final ListenerView mListenerView;
    private final FloatingWidgetBackgroundView mBackgroundView;
    private final RectF mBackgroundOffset = new RectF();

    private LauncherAppWidgetHostView mAppWidgetView;
    private View mAppWidgetBackgroundView;
    private RectF mBackgroundPosition;
    @Nullable
    private GhostView mForegroundOverlayView;

    @Nullable
    private Runnable mEndRunnable;
    @Nullable
    private Runnable mFastFinishRunnable;
    @Nullable
    private Runnable mOnTargetChangeRunnable;
    private boolean mAppTargetIsTranslucent;

    private float mIconOffsetY;

    public FloatingWidgetView(Context context) {
        this(context, null);
    }

    public FloatingWidgetView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FloatingWidgetView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mLauncher = QuickstepLauncher.getLauncher(context);
        mListenerView = new ListenerView(context, attrs);
        mBackgroundView = new FloatingWidgetBackgroundView(context, attrs, defStyleAttr);
        addView(mBackgroundView);
        setWillNotDraw(false);
    }

    @Override
    public void onAnimationEnd(Animator animator) {
        Runnable endRunnable = mEndRunnable;
        mEndRunnable = null;
        if (endRunnable != null) {
            endRunnable.run();
        }
    }

    @Override
    public void onAnimationStart(Animator animator) {
    }

    @Override
    public void onAnimationCancel(Animator animator) {
    }

    @Override
    public void onAnimationRepeat(Animator animator) {
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

    @Override
    public void onGlobalLayout() {
        if (isUninitialized()) return;
        positionViews();
        if (mOnTargetChangeRunnable != null) {
            mOnTargetChangeRunnable.run();
        }
    }

    /** Sets a runnable that is called on global layout change. */
    public void setOnTargetChangeListener(Runnable onTargetChangeListener) {
        mOnTargetChangeRunnable = onTargetChangeListener;
    }

    /** Sets a runnable that is called after a call to {@link #fastFinish()}. */
    public void setFastFinishRunnable(Runnable runnable) {
        mFastFinishRunnable = runnable;
    }

    /** Callback at the end or early exit of the animation. */
    @Override
    public void fastFinish() {
        if (isUninitialized()) return;
        Runnable fastFinishRunnable = mFastFinishRunnable;
        if (fastFinishRunnable != null) {
            fastFinishRunnable.run();
        }
        Runnable endRunnable = mEndRunnable;
        mEndRunnable = null;
        if (endRunnable != null) {
            endRunnable.run();
        }
    }

    private void init(DragLayer dragLayer, LauncherAppWidgetHostView originalView,
            RectF widgetBackgroundPosition, Size windowSize, float windowCornerRadius,
            boolean appTargetIsTranslucent, int fallbackBackgroundColor) {
        mAppWidgetView = originalView;
        // Deferrals must begin before GhostView is created. See b/190818220
        mAppWidgetView.beginDeferringUpdates();
        mBackgroundPosition = widgetBackgroundPosition;
        mAppTargetIsTranslucent = appTargetIsTranslucent;
        mEndRunnable = () -> finish(dragLayer);

        mAppWidgetBackgroundView = RoundedCornerEnforcement.findBackground(mAppWidgetView);
        if (mAppWidgetBackgroundView == null) {
            mAppWidgetBackgroundView = mAppWidgetView;
        }

        getRelativePosition(mAppWidgetBackgroundView, dragLayer, mBackgroundPosition);
        getRelativePosition(mAppWidgetBackgroundView, mAppWidgetView, mBackgroundOffset);
        if (!mAppTargetIsTranslucent) {
            mBackgroundView.init(mAppWidgetView, mAppWidgetBackgroundView, windowCornerRadius,
                    fallbackBackgroundColor);
            // Layout call before GhostView creation so that the overlaid view isn't clipped
            layout(0, 0, windowSize.getWidth(), windowSize.getHeight());
            mForegroundOverlayView = GhostView.addGhost(mAppWidgetView, this);
            positionViews();
        }

        mListenerView.setListener(this::fastFinish);
        dragLayer.addView(mListenerView);
    }

    /**
     * Updates the position and opacity of the floating widget's components.
     *
     * @param backgroundPosition      the new position of the widget's background relative to the
     *                                {@link FloatingWidgetView}'s parent
     * @param floatingWidgetAlpha     the overall opacity of the {@link FloatingWidgetView}
     * @param foregroundAlpha         the opacity of the foreground layer
     * @param fallbackBackgroundAlpha the opacity of the fallback background used when the App
     *                                Widget doesn't have a background
     * @param cornerRadiusProgress    progress of the corner radius animation, where 0 is the
     *                                original radius and 1 is the window radius
     */
    public void update(RectF backgroundPosition, float floatingWidgetAlpha, float foregroundAlpha,
            float fallbackBackgroundAlpha, float cornerRadiusProgress) {
        if (isUninitialized() || mAppTargetIsTranslucent) return;
        setAlpha(floatingWidgetAlpha);
        mBackgroundView.update(cornerRadiusProgress, fallbackBackgroundAlpha);
        mAppWidgetView.setAlpha(foregroundAlpha);
        mBackgroundPosition = backgroundPosition;
        positionViews();
    }

    @Override
    public void setPositionOffsetY(float y) {
        mIconOffsetY = y;
        onGlobalLayout();
    }

    /** Sets the layout parameters of the floating view and its background view child. */
    private void positionViews() {
        LayoutParams layoutParams = (LayoutParams) getLayoutParams();
        layoutParams.setMargins(0, 0, 0, 0);
        setLayoutParams(layoutParams);

        // FloatingWidgetView layout is forced LTR
        mBackgroundView.setTranslationX(mBackgroundPosition.left);
        mBackgroundView.setTranslationY(mBackgroundPosition.top + mIconOffsetY);
        LayoutParams backgroundParams = (LayoutParams) mBackgroundView.getLayoutParams();
        backgroundParams.leftMargin = 0;
        backgroundParams.topMargin = 0;
        backgroundParams.width = (int) mBackgroundPosition.width();
        backgroundParams.height = (int) mBackgroundPosition.height();
        mBackgroundView.setLayoutParams(backgroundParams);

        if (mForegroundOverlayView != null) {
            sTmpMatrix.reset();
            float foregroundScale =
                    mBackgroundPosition.width() / mAppWidgetBackgroundView.getWidth();
            sTmpMatrix.setTranslate(-mBackgroundOffset.left - mAppWidgetView.getLeft(),
                    -mBackgroundOffset.top - mAppWidgetView.getTop());
            sTmpMatrix.postScale(foregroundScale, foregroundScale);
            sTmpMatrix.postTranslate(mBackgroundPosition.left, mBackgroundPosition.top
                    + mIconOffsetY);
            mForegroundOverlayView.setMatrix(sTmpMatrix);
        }
    }

    private void finish(DragLayer dragLayer) {
        mAppWidgetView.setAlpha(1f);
        GhostView.removeGhost(mAppWidgetView);
        ((ViewGroup) dragLayer.getParent()).removeView(this);
        dragLayer.removeView(mListenerView);
        mBackgroundView.finish();
        // Removing GhostView must occur before ending deferrals. See b/190818220
        mAppWidgetView.endDeferringUpdates();
        recycle();
        mLauncher.getViewCache().recycleView(R.layout.floating_widget_view, this);
    }

    public float getInitialCornerRadius() {
        return mBackgroundView.getMaximumRadius();
    }

    private boolean isUninitialized() {
        return mForegroundOverlayView == null;
    }

    private void recycle() {
        mIconOffsetY = 0;
        mEndRunnable = null;
        mFastFinishRunnable = null;
        mOnTargetChangeRunnable = null;
        mBackgroundPosition = null;
        mListenerView.setListener(null);
        mAppWidgetView = null;
        mForegroundOverlayView = null;
        mAppWidgetBackgroundView = null;
        mBackgroundView.recycle();
    }

    /**
     * Configures and returns a an instance of {@link FloatingWidgetView} matching the appearance of
     * {@param originalView}.
     *
     * @param widgetBackgroundPosition a {@link RectF} that will be updated with the widget's
     *                                 background bounds
     * @param windowSize               the size of the window when launched
     * @param windowCornerRadius       the corner radius of the window
     */
    public static FloatingWidgetView getFloatingWidgetView(QuickstepLauncher launcher,
            LauncherAppWidgetHostView originalView, RectF widgetBackgroundPosition,
            Size windowSize, float windowCornerRadius, boolean appTargetsAreTranslucent,
            int fallbackBackgroundColor) {
        final DragLayer dragLayer = launcher.getDragLayer();
        ViewGroup parent = (ViewGroup) dragLayer.getParent();
        FloatingWidgetView floatingView =
                launcher.getViewCache().getView(R.layout.floating_widget_view, launcher, parent);
        floatingView.recycle();

        floatingView.init(dragLayer, originalView, widgetBackgroundPosition, windowSize,
                windowCornerRadius, appTargetsAreTranslucent, fallbackBackgroundColor);
        parent.addView(floatingView);
        return floatingView;
    }

    /**
     * Extract a background color from a target's task description, or fall back to the given
     * context's theme background color.
     */
    public static int getDefaultBackgroundColor(
            Context context, RemoteAnimationTarget target) {
        return (target != null && target.taskInfo != null
                && target.taskInfo.taskDescription != null)
                ? target.taskInfo.taskDescription.getBackgroundColor()
                : Themes.getColorBackground(context);
    }

    private static void getRelativePosition(View descendant, View ancestor, RectF position) {
        float[] points = new float[]{0, 0, descendant.getWidth(), descendant.getHeight()};
        Utilities.getDescendantCoordRelativeToAncestor(descendant, ancestor, points,
                false /* includeRootScroll */, true /* ignoreTransform */);
        position.set(
                Math.min(points[0], points[2]),
                Math.min(points[1], points[3]),
                Math.max(points[0], points[2]),
                Math.max(points[1], points[3]));
    }
}
