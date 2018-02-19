/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.TextView;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DropTarget;
import com.android.launcher3.Insettable;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.touch.SwipeDetector;
import com.android.launcher3.anim.PropertyListBuilder;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.graphics.GradientView;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.SystemUiController;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.TouchController;

import java.util.List;

/**
 * Bottom sheet for the "Widgets" system shortcut in the long-press popup.
 */
public class WidgetsBottomSheet extends AbstractFloatingView implements Insettable, TouchController,
        SwipeDetector.Listener, View.OnClickListener, View.OnLongClickListener,
        DragController.DragListener {

    private int mTranslationYOpen;
    private int mTranslationYClosed;
    private float mTranslationYRange;

    private Launcher mLauncher;
    private ItemInfo mOriginalItemInfo;
    private ObjectAnimator mOpenCloseAnimator;
    private Interpolator mFastOutSlowInInterpolator;
    private SwipeDetector.ScrollInterpolator mScrollInterpolator;
    private Rect mInsets;
    private SwipeDetector mSwipeDetector;
    private GradientView mGradientBackground;

    public WidgetsBottomSheet(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WidgetsBottomSheet(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);
        mLauncher = Launcher.getLauncher(context);
        mOpenCloseAnimator = LauncherAnimUtils.ofPropertyValuesHolder(this);
        mFastOutSlowInInterpolator =
                AnimationUtils.loadInterpolator(context, android.R.interpolator.fast_out_slow_in);
        mScrollInterpolator = new SwipeDetector.ScrollInterpolator();
        mInsets = new Rect();
        mSwipeDetector = new SwipeDetector(context, this, SwipeDetector.VERTICAL);
        mGradientBackground = (GradientView) mLauncher.getLayoutInflater().inflate(
                R.layout.gradient_bg, mLauncher.getDragLayer(), false);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mTranslationYOpen = 0;
        mTranslationYClosed = getMeasuredHeight();
        mTranslationYRange = mTranslationYClosed - mTranslationYOpen;
    }

    public void populateAndShow(ItemInfo itemInfo) {
        mOriginalItemInfo = itemInfo;
        ((TextView) findViewById(R.id.title)).setText(getContext().getString(
                R.string.widgets_bottom_sheet_title, mOriginalItemInfo.title));

        onWidgetsBound();

        mLauncher.getDragLayer().addView(mGradientBackground);
        mGradientBackground.setVisibility(VISIBLE);
        mLauncher.getDragLayer().addView(this);
        measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        setTranslationY(mTranslationYClosed);
        mIsOpen = false;
        open(true);
    }

    @Override
    protected void onWidgetsBound() {
        List<WidgetItem> widgets = mLauncher.getWidgetsForPackageUser(new PackageUserKey(
                mOriginalItemInfo.getTargetComponent().getPackageName(), mOriginalItemInfo.user));

        ViewGroup widgetRow = (ViewGroup) findViewById(R.id.widgets);
        ViewGroup widgetCells = (ViewGroup) widgetRow.findViewById(R.id.widgets_cell_list);

        widgetCells.removeAllViews();

        for (int i = 0; i < widgets.size(); i++) {
            WidgetCell widget = addItemCell(widgetCells);
            widget.applyFromCellItem(widgets.get(i), LauncherAppState.getInstance(mLauncher)
                    .getWidgetCache());
            widget.ensurePreview();
            widget.setVisibility(View.VISIBLE);
            if (i < widgets.size() - 1) {
                addDivider(widgetCells);
            }
        }

        if (widgets.size() == 1) {
            // If there is only one widget, we want to center it instead of left-align.
            WidgetsBottomSheet.LayoutParams params = (WidgetsBottomSheet.LayoutParams)
                    widgetRow.getLayoutParams();
            params.gravity = Gravity.CENTER_HORIZONTAL;
        } else {
            // Otherwise, add an empty view to the start as padding (but still scroll edge to edge).
            View leftPaddingView = LayoutInflater.from(getContext()).inflate(
                    R.layout.widget_list_divider, widgetRow, false);
            leftPaddingView.getLayoutParams().width = Utilities.pxFromDp(
                    16, getResources().getDisplayMetrics());
            widgetCells.addView(leftPaddingView, 0);
        }
    }

    private void addDivider(ViewGroup parent) {
        LayoutInflater.from(getContext()).inflate(R.layout.widget_list_divider, parent, true);
    }

    private WidgetCell addItemCell(ViewGroup parent) {
        WidgetCell widget = (WidgetCell) LayoutInflater.from(getContext()).inflate(
                R.layout.widget_cell, parent, false);

        widget.setOnClickListener(this);
        widget.setOnLongClickListener(this);
        widget.setAnimatePreview(false);

        parent.addView(widget);
        return widget;
    }

    @Override
    public void onClick(View view) {
        mLauncher.getWidgetsView().handleClick();
    }

    @Override
    public boolean onLongClick(View view) {
        mLauncher.getDragController().addDragListener(this);
        return mLauncher.getWidgetsView().handleLongClick(view);
    }

    private void open(boolean animate) {
        if (mIsOpen || mOpenCloseAnimator.isRunning()) {
            return;
        }
        mIsOpen = true;
        boolean isSheetDark = Themes.getAttrBoolean(mLauncher, R.attr.isMainColorDark);
        mLauncher.getSystemUiController().updateUiState(
                SystemUiController.UI_STATE_WIDGET_BOTTOM_SHEET,
                isSheetDark ? SystemUiController.FLAG_DARK_NAV : SystemUiController.FLAG_LIGHT_NAV);
        if (animate) {
            mOpenCloseAnimator.setValues(new PropertyListBuilder()
                    .translationY(mTranslationYOpen).build());
            mOpenCloseAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mSwipeDetector.finishedScrolling();
                }
            });
            mOpenCloseAnimator.setInterpolator(mFastOutSlowInInterpolator);
            mOpenCloseAnimator.start();
        } else {
            setTranslationY(mTranslationYOpen);
        }
    }

    @Override
    protected void handleClose(boolean animate) {
        if (!mIsOpen || mOpenCloseAnimator.isRunning()) {
            return;
        }
        if (animate) {
            mOpenCloseAnimator.setValues(new PropertyListBuilder()
                    .translationY(mTranslationYClosed).build());
            mOpenCloseAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mSwipeDetector.finishedScrolling();
                    onCloseComplete();
                }
            });
            mOpenCloseAnimator.setInterpolator(mSwipeDetector.isIdleState()
                    ? mFastOutSlowInInterpolator : mScrollInterpolator);
            mOpenCloseAnimator.start();
        } else {
            setTranslationY(mTranslationYClosed);
            onCloseComplete();
        }
    }

    private void onCloseComplete() {
        mIsOpen = false;
        mLauncher.getDragLayer().removeView(mGradientBackground);
        mLauncher.getDragLayer().removeView(WidgetsBottomSheet.this);
        mLauncher.getSystemUiController().updateUiState(
                SystemUiController.UI_STATE_WIDGET_BOTTOM_SHEET, 0);
    }

    @Override
    protected boolean isOfType(@FloatingViewType int type) {
        return (type & TYPE_WIDGETS_BOTTOM_SHEET) != 0;
    }

    @Override
    public int getLogContainerType() {
        return LauncherLogProto.ContainerType.WIDGETS; // TODO: be more specific
    }

    /**
     * Returns a {@link WidgetsBottomSheet} which is already open or null
     */
    public static WidgetsBottomSheet getOpen(Launcher launcher) {
        return getOpenView(launcher, TYPE_WIDGETS_BOTTOM_SHEET);
    }

    @Override
    public void setInsets(Rect insets) {
        // Extend behind left, right, and bottom insets.
        int leftInset = insets.left - mInsets.left;
        int rightInset = insets.right - mInsets.right;
        int bottomInset = insets.bottom - mInsets.bottom;
        mInsets.set(insets);

        if (!Utilities.ATLEAST_OREO && !mLauncher.getDeviceProfile().isVerticalBarLayout()) {
            View navBarBg = findViewById(R.id.nav_bar_bg);
            ViewGroup.LayoutParams navBarBgLp = navBarBg.getLayoutParams();
            navBarBgLp.height = bottomInset;
            navBarBg.setLayoutParams(navBarBgLp);
            bottomInset = 0;
        }

        setPadding(getPaddingLeft() + leftInset, getPaddingTop(),
                getPaddingRight() + rightInset, getPaddingBottom() + bottomInset);
    }

    /* SwipeDetector.Listener */

    @Override
    public void onDragStart(boolean start) {
    }

    @Override
    public boolean onDrag(float displacement, float velocity) {
        setTranslationY(Utilities.boundToRange(displacement, mTranslationYOpen,
                mTranslationYClosed));
        return true;
    }

    @Override
    public void setTranslationY(float translationY) {
        super.setTranslationY(translationY);
        if (mGradientBackground == null) return;
        float p = (mTranslationYClosed - translationY) / mTranslationYRange;
        boolean showScrim = p <= 0;
        mGradientBackground.setProgress(p, showScrim);
    }

    @Override
    public void onDragEnd(float velocity, boolean fling) {
        if ((fling && velocity > 0) || getTranslationY() > (mTranslationYRange) / 2) {
            mScrollInterpolator.setVelocityAtZero(velocity);
            mOpenCloseAnimator.setDuration(SwipeDetector.calculateDuration(velocity,
                    (mTranslationYClosed - getTranslationY()) / mTranslationYRange));
            close(true);
        } else {
            mIsOpen = false;
            mOpenCloseAnimator.setDuration(SwipeDetector.calculateDuration(velocity,
                    (getTranslationY() - mTranslationYOpen) / mTranslationYRange));
            open(true);
        }
    }

    @Override
    public boolean onControllerTouchEvent(MotionEvent ev) {
        return mSwipeDetector.onTouchEvent(ev);
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        int directionsToDetectScroll = mSwipeDetector.isIdleState() ?
                SwipeDetector.DIRECTION_NEGATIVE : 0;
        mSwipeDetector.setDetectableScrollConditions(
                directionsToDetectScroll, false);
        mSwipeDetector.onTouchEvent(ev);
        return mSwipeDetector.isDraggingOrSettling();
    }

    /* DragListener */

    @Override
    public void onDragStart(DropTarget.DragObject dragObject, DragOptions options) {
        // A widget or custom shortcut was dragged.
        close(true);
    }

    @Override
    public void onDragEnd() {
    }
}
