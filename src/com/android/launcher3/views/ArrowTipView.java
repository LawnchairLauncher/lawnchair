/*
 * Copyright (C) 2008 The Android Open Source Project
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
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.ShapeDrawable;
import android.os.Handler;
import android.util.IntProperty;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.Px;

import com.android.app.animation.Interpolators;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.graphics.TriangleShape;

/**
 * A base class for arrow tip view in launcher.
 */
public class ArrowTipView extends AbstractFloatingView {

    private static final String TAG = ArrowTipView.class.getSimpleName();
    private static final long AUTO_CLOSE_TIMEOUT_MILLIS = 10 * 1000;
    private static final long SHOW_DELAY_MS = 200;
    private static final long SHOW_DURATION_MS = 300;
    private static final long HIDE_DURATION_MS = 100;

    public static final IntProperty<ArrowTipView> TEXT_ALPHA =
            new IntProperty<>("textAlpha") {
                @Override
                public void setValue(ArrowTipView view, int v) {
                    view.setTextAlpha(v);
                }

                @Override
                public Integer get(ArrowTipView view) {
                    return view.getTextAlpha();
                }
            };

    private final ActivityContext mActivityContext;
    private final Handler mHandler = new Handler();
    private boolean mIsPointingUp;
    private Runnable mOnClosed;
    private View mArrowView;
    private final int mArrowWidth;
    private final int mArrowMinOffset;
    private final int mArrowViewPaintColor;

    private AnimatorSet mOpenAnimator = new AnimatorSet();
    private AnimatorSet mCloseAnimator = new AnimatorSet();

    private int mTextAlpha;

    public ArrowTipView(Context context) {
        this(context, false);
    }

    public ArrowTipView(Context context, boolean isPointingUp) {
        this(context, isPointingUp, R.layout.arrow_toast);
    }

    public ArrowTipView(Context context, boolean isPointingUp, int layoutId) {
        super(context, null, 0);
        mActivityContext = ActivityContext.lookupContext(context);
        mIsPointingUp = isPointingUp;
        mArrowWidth = context.getResources().getDimensionPixelSize(
                R.dimen.arrow_toast_arrow_width);
        mArrowMinOffset = context.getResources().getDimensionPixelSize(
                R.dimen.dynamic_grid_cell_border_spacing);
        TypedArray ta = context.obtainStyledAttributes(R.styleable.ArrowTipView);
        // Set style to default to avoid inflation issues with missing attributes.
        if (!ta.hasValue(R.styleable.ArrowTipView_arrowTipBackground)
                || !ta.hasValue(R.styleable.ArrowTipView_arrowTipTextColor)) {
            context = new ContextThemeWrapper(context, R.style.ArrowTipStyle);
        }
        mArrowViewPaintColor = ta.getColor(R.styleable.ArrowTipView_arrowTipBackground,
                context.getColor(R.color.arrow_tip_view_bg));
        ta.recycle();
        init(context, layoutId);
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            close(true);
            if (mActivityContext.getDragLayer().isEventOverView(this, ev)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void handleClose(boolean animate) {
        if (mOpenAnimator.isStarted()) {
            mOpenAnimator.cancel();
        }
        if (mIsOpen) {
            if (animate) {
                mCloseAnimator.addListener(AnimatorListeners.forSuccessCallback(
                        () -> mActivityContext.getDragLayer().removeView(this)));
                mCloseAnimator.start();
            } else {
                mCloseAnimator.cancel();
                mActivityContext.getDragLayer().removeView(this);
            }
            if (mOnClosed != null) mOnClosed.run();
            mIsOpen = false;
        }
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_ON_BOARD_POPUP) != 0;
    }

    private void init(Context context, int layoutId) {
        inflate(context, layoutId, this);
        setOrientation(LinearLayout.VERTICAL);

        mArrowView = findViewById(R.id.arrow);
        updateArrowTipInView();
        setAlpha(0);

        // Create default open animator.
        mOpenAnimator.play(ObjectAnimator.ofFloat(this, ALPHA, 1f));
        mOpenAnimator.setStartDelay(SHOW_DELAY_MS);
        mOpenAnimator.setDuration(SHOW_DURATION_MS);
        mOpenAnimator.setInterpolator(Interpolators.DECELERATE);

        // Create default close animator.
        mCloseAnimator.play(ObjectAnimator.ofFloat(this, ALPHA, 0));
        mCloseAnimator.setStartDelay(0);
        mCloseAnimator.setDuration(HIDE_DURATION_MS);
        mCloseAnimator.setInterpolator(Interpolators.ACCELERATE);
        mCloseAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mActivityContext.getDragLayer().removeView(ArrowTipView.this);
            }
        });
    }

    /**
     * Show Tip with specified string and Y location
     */
    public ArrowTipView show(String text, int top) {
        return show(text, Gravity.CENTER_HORIZONTAL, 0, top);
    }

    /**
     * Show the ArrowTipView (tooltip) center, start, or end aligned.
     *
     * @param text             The text to be shown in the tooltip.
     * @param gravity          The gravity aligns the tooltip center, start, or end.
     * @param arrowMarginStart The margin from start to place arrow (ignored if center)
     * @param top              The Y coordinate of the bottom of tooltip.
     * @return The tooltip.
     */
    public ArrowTipView show(String text, int gravity, int arrowMarginStart, int top) {
        return show(text, gravity, arrowMarginStart, top, true);
    }

    /**
     * Show the ArrowTipView (tooltip) center, start, or end aligned.
     *
     * @param text The text to be shown in the tooltip.
     * @param gravity The gravity aligns the tooltip center, start, or end.
     * @param arrowMarginStart The margin from start to place arrow (ignored if center)
     * @param top  The Y coordinate of the bottom of tooltip.
     * @param shouldAutoClose If Tooltip should be auto close.
     * @return The tooltip.
     */
    public ArrowTipView show(
            String text, int gravity, int arrowMarginStart, int top, boolean shouldAutoClose) {
        ((TextView) findViewById(R.id.text)).setText(text);
        ViewGroup parent = mActivityContext.getDragLayer();
        parent.addView(this);

        DeviceProfile grid = mActivityContext.getDeviceProfile();

        DragLayer.LayoutParams params = (DragLayer.LayoutParams) getLayoutParams();
        params.gravity = gravity;
        params.leftMargin = mArrowMinOffset + grid.getInsets().left;
        params.rightMargin = mArrowMinOffset + grid.getInsets().right;
        params.width = LayoutParams.MATCH_PARENT;
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) mArrowView.getLayoutParams();

        lp.gravity = gravity;

        if (parent.getLayoutDirection() == LAYOUT_DIRECTION_RTL) {
            arrowMarginStart = parent.getMeasuredWidth() - arrowMarginStart;
        }
        if (gravity == Gravity.END) {
            lp.setMarginEnd(Math.max(mArrowMinOffset,
                    parent.getMeasuredWidth() - params.rightMargin - arrowMarginStart
                            - mArrowWidth / 2));
        } else if (gravity == Gravity.START) {
            lp.setMarginStart(Math.max(mArrowMinOffset,
                    arrowMarginStart - params.leftMargin - mArrowWidth / 2));
        }
        requestLayout();
        post(() -> setY(top - (mIsPointingUp ? 0 : getHeight())));

        mIsOpen = true;
        if (shouldAutoClose) {
            mHandler.postDelayed(() -> handleClose(true), AUTO_CLOSE_TIMEOUT_MILLIS);
        }

        mOpenAnimator.start();
        return this;
    }

    /**
     * Show the ArrowTipView (tooltip) custom aligned. The tooltip is vertically flipped if it
     * cannot fit on screen in the requested orientation.
     *
     * @param text The text to be shown in the tooltip.
     * @param arrowXCoord The X coordinate for the arrow on the tooltip. The arrow is usually in the
     *                    center of tooltip unless the tooltip goes beyond screen margin.
     * @param yCoord The Y coordinate of the pointed tip end of the tooltip.
     * @return The tool tip view. {@code null} if the tip can not be shown.
     */
    @Nullable public ArrowTipView showAtLocation(String text, @Px int arrowXCoord, @Px int yCoord) {
        return showAtLocation(
            text,
            arrowXCoord,
            /* yCoordDownPointingTip= */ yCoord,
            /* yCoordUpPointingTip= */ yCoord,
            /* shouldAutoClose= */ true);
    }

    /**
     * Show the ArrowTipView (tooltip) custom aligned. The tooltip is vertically flipped if it
     * cannot fit on screen in the requested orientation.
     *
     * @param text The text to be shown in the tooltip.
     * @param arrowXCoord The X coordinate for the arrow on the tooltip. The arrow is usually in the
     *                    center of tooltip unless the tooltip goes beyond screen margin.
     * @param yCoord The Y coordinate of the pointed tip end of the tooltip.
     * @param shouldAutoClose If Tooltip should be auto close.
     * @return The tool tip view. {@code null} if the tip can not be shown.
     */
    @Nullable public ArrowTipView showAtLocation(
            String text, @Px int arrowXCoord, @Px int yCoord, boolean shouldAutoClose) {
        return showAtLocation(
                text,
                arrowXCoord,
                /* yCoordDownPointingTip= */ yCoord,
                /* yCoordUpPointingTip= */ yCoord,
                /* shouldAutoClose= */ shouldAutoClose);
    }

    /**
     * Show the ArrowTipView (tooltip) custom aligned. The tooltip is vertically flipped if it
     * cannot fit on screen in the requested orientation.
     *
     * @param text The text to be shown in the tooltip.
     * @param arrowXCoord The X coordinate for the arrow on the tooltip. The arrow is usually in the
     *                    center of tooltip unless the tooltip goes beyond screen margin.
     * @param rect The coordinates of the view which requests the tooltip to be shown.
     * @param margin The margin between {@param rect} and the tooltip.
     * @return The tool tip view. {@code null} if the tip can not be shown.
     */
    @Nullable public ArrowTipView showAroundRect(
            String text, @Px int arrowXCoord, Rect rect, @Px int margin) {
        return showAtLocation(
                text,
                arrowXCoord,
                /* yCoordDownPointingTip= */ rect.top - margin,
                /* yCoordUpPointingTip= */ rect.bottom + margin,
                /* shouldAutoClose= */ true);
    }

    /**
     * Show the ArrowTipView (tooltip) custom aligned. The tooltip is vertically flipped if it
     * cannot fit on screen in the requested orientation.
     *
     * @param text The text to be shown in the tooltip.
     * @param arrowXCoord The X coordinate for the arrow on the tooltip. The arrow is usually in the
     *                    center of tooltip unless the tooltip goes beyond screen margin.
     * @param yCoordDownPointingTip The Y coordinate of the pointed tip end of the tooltip when the
     *                              tooltip is placed pointing downwards.
     * @param yCoordUpPointingTip The Y coordinate of the pointed tip end of the tooltip when the
     *                            tooltip is placed pointing upwards.
     * @param shouldAutoClose If Tooltip should be auto close.
     * @return The tool tip view. {@code null} if the tip can not be shown.
     */
    @Nullable private ArrowTipView showAtLocation(String text, @Px int arrowXCoord,
            @Px int yCoordDownPointingTip, @Px int yCoordUpPointingTip, boolean shouldAutoClose) {
        ViewGroup parent = mActivityContext.getDragLayer();
        @Px int parentViewWidth = parent.getWidth();
        @Px int parentViewHeight = parent.getHeight();
        @Px int maxTextViewWidth = getContext().getResources()
                .getDimensionPixelSize(R.dimen.widget_picker_education_tip_max_width);
        @Px int minViewMargin = getContext().getResources()
                .getDimensionPixelSize(R.dimen.widget_picker_education_tip_min_margin);
        if (parentViewWidth < maxTextViewWidth + 2 * minViewMargin) {
            Log.w(TAG, "Cannot display tip on a small screen of size: " + parentViewWidth);
            return null;
        }

        TextView textView = findViewById(R.id.text);
        textView.setText(text);
        textView.setMaxWidth(maxTextViewWidth);
        if (parent.indexOfChild(this) < 0) {
            parent.addView(this);
            requestLayout();
        }

        post(() -> {
            // Adjust the tooltip horizontally.
            float halfWidth = getWidth() / 2f;
            float xCoord;
            if (arrowXCoord - halfWidth < minViewMargin) {
                // If the tooltip is estimated to go beyond the left margin, place its start just at
                // the left margin.
                xCoord = minViewMargin;
            } else if (arrowXCoord + halfWidth > parentViewWidth - minViewMargin) {
                // If the tooltip is estimated to go beyond the right margin, place it such that its
                // end is just at the right margin.
                xCoord = parentViewWidth - minViewMargin - getWidth();
            } else {
                // Place the tooltip such that its center is at arrowXCoord.
                xCoord = arrowXCoord - halfWidth;
            }
            setX(xCoord);

            // Adjust the tooltip vertically.
            @Px int viewHeight = getHeight();
            if (mIsPointingUp
                    ? (yCoordUpPointingTip + viewHeight > parentViewHeight)
                    : (yCoordDownPointingTip - viewHeight < 0)) {
                // Flip the view if it exceeds the vertical bounds of screen.
                mIsPointingUp = !mIsPointingUp;
                updateArrowTipInView();
            }
            // Place the tooltip such that its top is at yCoordUpPointingTip if arrow is displayed
            // pointing upwards, otherwise place it such that its bottom is at
            // yCoordDownPointingTip.
            setY(mIsPointingUp ? yCoordUpPointingTip : yCoordDownPointingTip - viewHeight);

            // Adjust the arrow's relative position on tooltip to make sure the actual position of
            // arrow's pointed tip is always at arrowXCoord.
            mArrowView.setX(arrowXCoord - xCoord - mArrowView.getWidth() / 2f);
            requestLayout();
        });

        mIsOpen = true;
        if (shouldAutoClose) {
            mHandler.postDelayed(() -> handleClose(true), AUTO_CLOSE_TIMEOUT_MILLIS);
        }

        mOpenAnimator.start();
        return this;
    }

    private void updateArrowTipInView() {
        ViewGroup.LayoutParams arrowLp = mArrowView.getLayoutParams();
        ShapeDrawable arrowDrawable = new ShapeDrawable(TriangleShape.create(
                arrowLp.width, arrowLp.height, mIsPointingUp));
        Paint arrowPaint = arrowDrawable.getPaint();
        @Px int arrowTipRadius = getContext().getResources()
                .getDimensionPixelSize(R.dimen.arrow_toast_corner_radius);
        arrowPaint.setColor(mArrowViewPaintColor);
        arrowPaint.setPathEffect(new CornerPathEffect(arrowTipRadius));
        mArrowView.setBackground(arrowDrawable);
        // Add negative margin so that the rounded corners on base of arrow are not visible.
        removeView(mArrowView);
        if (mIsPointingUp) {
            addView(mArrowView, 0);
            ((ViewGroup.MarginLayoutParams) arrowLp).setMargins(0, 0, 0, -1 * arrowTipRadius);
        } else {
            addView(mArrowView, 1);
            ((ViewGroup.MarginLayoutParams) arrowLp).setMargins(0, -1 * arrowTipRadius, 0, 0);
        }
    }

    /**
     * Register a callback fired when toast is hidden
     */
    public ArrowTipView setOnClosedCallback(Runnable runnable) {
        mOnClosed = runnable;
        return this;
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        close(/* animate= */ false);
    }

    /**
     * Sets a custom animation to run on open of the ArrowTipView.
     */
    public void setCustomOpenAnimation(AnimatorSet animator) {
        mOpenAnimator = animator;
    }

    /**
     * Sets a custom animation to run on close of the ArrowTipView.
     */
    public void setCustomCloseAnimation(AnimatorSet animator) {
        mCloseAnimator = animator;
    }

    private void setTextAlpha(int textAlpha) {
        if (mTextAlpha != textAlpha) {
            mTextAlpha = textAlpha;
            TextView textView = findViewById(R.id.text);
            textView.setTextColor(textView.getTextColors().withAlpha(mTextAlpha));
        }
    }

    private int getTextAlpha() {
        return mTextAlpha;
    }
}
