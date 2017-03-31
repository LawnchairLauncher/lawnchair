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
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
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
import com.android.launcher3.allapps.VerticalPullDetector;
import com.android.launcher3.anim.PropertyListBuilder;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.TouchController;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static android.R.attr.bottom;

/**
 * Bottom sheet for the "Widgets & more" long-press option.
 */
public class WidgetsAndMore extends AbstractFloatingView implements Insettable, TouchController,
        VerticalPullDetector.Listener, View.OnClickListener, View.OnLongClickListener,
        DragController.DragListener {

    private int mTranslationYOpen;
    private int mTranslationYClosed;
    private float mTranslationYRange;

    private Launcher mLauncher;
    private ItemInfo mOriginalItemInfo;
    private ObjectAnimator mOpenCloseAnimator;
    private Interpolator mFastOutSlowInInterpolator;
    private VerticalPullDetector.ScrollInterpolator mScrollInterpolator;
    private Rect mInsets;
    private boolean mWasNavBarLight;
    private VerticalPullDetector mVerticalPullDetector;

    public WidgetsAndMore(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WidgetsAndMore(Context context, AttributeSet attrs, int defStyleAttr) {
        super(new ContextThemeWrapper(context, R.style.WidgetContainerTheme), attrs, defStyleAttr);
        setWillNotDraw(false);
        mLauncher = Launcher.getLauncher(context);
        mOpenCloseAnimator = LauncherAnimUtils.ofPropertyValuesHolder(this);
        mFastOutSlowInInterpolator = new FastOutSlowInInterpolator();
        mScrollInterpolator = new VerticalPullDetector.ScrollInterpolator();
        mInsets = new Rect();
        mVerticalPullDetector = new VerticalPullDetector(context);
        mVerticalPullDetector.setListener(this);
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
        ((TextView) findViewById(R.id.title)).setText(mOriginalItemInfo.title);

        onWidgetsBound();

        mWasNavBarLight = (mLauncher.getWindow().getDecorView().getSystemUiVisibility()
                & View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR) != 0;
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
        List<WidgetItem> shortcuts = new ArrayList<>();
        // Transfer configurable widgets to shortcuts
        Iterator<WidgetItem> widgetsIter = widgets.iterator();
        WidgetItem nextWidget;
        while (widgetsIter.hasNext()) {
            nextWidget = widgetsIter.next();
            if (nextWidget.activityInfo != null) {
                shortcuts.add(nextWidget);
                widgetsIter.remove();
            }
        }

        ViewGroup widgetRow = (ViewGroup) findViewById(R.id.widgets);
        ViewGroup widgetCells = (ViewGroup) widgetRow.findViewById(R.id.widgets_cell_list);

        ViewGroup shortcutRow = (ViewGroup) findViewById(R.id.shortcuts);
        ViewGroup shortcutCells = (ViewGroup) shortcutRow.findViewById(R.id.widgets_cell_list);

        widgetCells.removeAllViews();
        shortcutCells.removeAllViews();

        for (int i = 0; i < widgets.size(); i++) {
            addItemCell(widgetCells);
            if (i < widgets.size() - 1) {
                addDivider(widgetCells);
            }
        }
        for (int i = 0; i < shortcuts.size(); i++) {
            addItemCell(shortcutCells);
            if (i < shortcuts.size() - 1) {
                addDivider(shortcutCells);
            }
        }

        // Bind the views in the horizontal tray regions.
        if (widgetCells.getChildCount() > 0) {
            for (int i = 0; i < widgets.size(); i++) {
                WidgetCell widget = (WidgetCell) widgetCells.getChildAt(i*2); // skip dividers
                widget.applyFromCellItem(widgets.get(i), LauncherAppState.getInstance(mLauncher)
                        .getWidgetCache());
                widget.ensurePreview();
                widget.setVisibility(View.VISIBLE);
            }
        } else {
            removeView(findViewById(R.id.widgets_header));
        }
        if (shortcutCells.getChildCount() > 0) {
            for (int i = 0; i < shortcuts.size(); i++) {
                WidgetCell shortcut = (WidgetCell) shortcutCells.getChildAt(i*2); // skip dividers
                shortcut.applyFromCellItem(shortcuts.get(i), LauncherAppState.getInstance(mLauncher)
                        .getWidgetCache());
                shortcut.ensurePreview();
                shortcut.setVisibility(View.VISIBLE);
            }
        } else {
            removeView(findViewById(R.id.shortcuts_header));
        }
    }

    private void addDivider(ViewGroup parent) {
        LayoutInflater.from(getContext()).inflate(R.layout.widget_list_divider, parent, true);
    }

    private void addItemCell(ViewGroup parent) {
        WidgetCell widget = (WidgetCell) LayoutInflater.from(getContext()).inflate(
                R.layout.widget_cell, parent, false);

        widget.setOnClickListener(this);
        widget.setOnLongClickListener(this);
        widget.setAnimatePreview(false);

        parent.addView(widget);
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
        setLightNavBar(true);
        if (animate) {
            mOpenCloseAnimator.setValues(new PropertyListBuilder()
                    .translationY(mTranslationYOpen).build());
            mOpenCloseAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mVerticalPullDetector.finishedScrolling();
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
                    mIsOpen = false;
                    mVerticalPullDetector.finishedScrolling();
                    ((ViewGroup) getParent()).removeView(WidgetsAndMore.this);
                    setLightNavBar(mWasNavBarLight);
                }
            });
            mOpenCloseAnimator.setInterpolator(mVerticalPullDetector.isIdleState()
                    ? mFastOutSlowInInterpolator : mScrollInterpolator);
            mOpenCloseAnimator.start();
        } else {
            setTranslationY(mTranslationYClosed);
            setLightNavBar(mWasNavBarLight);
            mIsOpen = false;
        }
    }

    private void setLightNavBar(boolean lightNavBar) {
        mLauncher.activateLightSystemBars(lightNavBar, false /* statusBar */, true /* navBar */);
    }

    @Override
    protected boolean isOfType(@FloatingViewType int type) {
        return (type & TYPE_WIDGETS_AND_MORE) != 0;
    }

    @Override
    public int getLogContainerType() {
        return LauncherLogProto.ContainerType.WIDGETS; // TODO: be more specific
    }

    /**
     * Returns a WidgetsAndMore which is already open or null
     */
    public static WidgetsAndMore getOpen(Launcher launcher) {
        return getOpenView(launcher, TYPE_WIDGETS_AND_MORE);
    }

    @Override
    public void setInsets(Rect insets) {
        // Extend behind left, right, and bottom insets.
        int leftInset = insets.left - mInsets.left;
        int rightInset = insets.right - mInsets.right;
        int bottomInset = insets.bottom - mInsets.bottom;
        mInsets.set(insets);
        setPadding(getPaddingLeft() + leftInset, getPaddingTop(),
                getPaddingRight() + rightInset, getPaddingBottom() + bottomInset);
    }

    /* VerticalPullDetector.Listener */

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
    public void onDragEnd(float velocity, boolean fling) {
        if ((fling && velocity > 0) || getTranslationY() > (mTranslationYRange) / 2) {
            mScrollInterpolator.setVelocityAtZero(velocity);
            mOpenCloseAnimator.setDuration(mVerticalPullDetector.calculateDuration(velocity,
                    (mTranslationYClosed - getTranslationY()) / mTranslationYRange));
            close(true);
        } else {
            mIsOpen = false;
            mOpenCloseAnimator.setDuration(mVerticalPullDetector.calculateDuration(velocity,
                    (getTranslationY() - mTranslationYOpen) / mTranslationYRange));
            open(true);
        }
    }

    @Override
    public boolean onControllerTouchEvent(MotionEvent ev) {
        return mVerticalPullDetector.onTouchEvent(ev);
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        int directionsToDetectScroll = mVerticalPullDetector.isIdleState() ?
                VerticalPullDetector.DIRECTION_DOWN : 0;
        mVerticalPullDetector.setDetectableScrollConditions(
                directionsToDetectScroll, false);
        mVerticalPullDetector.onTouchEvent(ev);
        return mVerticalPullDetector.isDraggingOrSettling();
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
