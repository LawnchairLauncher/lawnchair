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

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_BOTTOM_WIDGETS_TRAY;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.IntProperty;
import android.util.Pair;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.Px;

import com.android.launcher3.R;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.widget.util.WidgetsTableUtils;

import java.util.List;

/**
 * Bottom sheet for the "Widgets" system shortcut in the long-press popup.
 */
public class WidgetsBottomSheet extends BaseWidgetSheet {
    private static final String TAG = "WidgetsBottomSheet";

    private static final IntProperty<View> PADDING_BOTTOM =
            new IntProperty<View>("paddingBottom") {
                @Override
                public void setValue(View view, int paddingBottom) {
                    view.setPadding(view.getPaddingLeft(), view.getPaddingTop(),
                            view.getPaddingRight(), paddingBottom);
                }

                @Override
                public Integer get(View view) {
                    return view.getPaddingBottom();
                }
            };

    private static final int DEFAULT_CLOSE_DURATION = 200;
    private static final long EDUCATION_TIP_DELAY_MS = 300;

    private ItemInfo mOriginalItemInfo;
    @Px private int mMaxHorizontalSpan;

    private final OnLayoutChangeListener mLayoutChangeListenerToShowTips =
            new OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom,
                        int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    if (hasSeenEducationTip()) {
                        removeOnLayoutChangeListener(this);
                        return;
                    }
                    // Widgets are loaded asynchronously, We are adding a delay because we only want
                    // to show the tip when the widget preview has finished loading and rendering in
                    // this view.
                    removeCallbacks(mShowEducationTipTask);
                    postDelayed(mShowEducationTipTask, EDUCATION_TIP_DELAY_MS);
                }
            };

    private final Runnable mShowEducationTipTask = () -> {
        if (hasSeenEducationTip()) {
            removeOnLayoutChangeListener(mLayoutChangeListenerToShowTips);
            return;
        }
        View viewForTip = ((ViewGroup) ((TableLayout) findViewById(R.id.widgets_table))
                                    .getChildAt(0)).getChildAt(0);
        if (showEducationTipOnViewIfPossible(viewForTip) != null) {
            removeOnLayoutChangeListener(mLayoutChangeListenerToShowTips);
        }
    };

    public WidgetsBottomSheet(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WidgetsBottomSheet(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);
        if (!hasSeenEducationTip()) {
            addOnLayoutChangeListener(mLayoutChangeListenerToShowTips);
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContent = findViewById(R.id.widgets_bottom_sheet);
        setContentBackgroundWithParent(
                getContext().getDrawable(R.drawable.bg_rounded_corner_bottom_sheet), mContent);
        View scrollView = findViewById(R.id.widgets_table_scroll_view);
        scrollView.setOutlineProvider(mViewOutlineProvider);
        scrollView.setClipToOutline(true);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        doMeasure(widthMeasureSpec, heightMeasureSpec);
        if (updateMaxSpansPerRow()) {
            doMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    /** Returns {@code true} if the max spans have been updated. */
    private boolean updateMaxSpansPerRow() {
        if (getMeasuredWidth() == 0) return false;

        @Px int maxHorizontalSpan = mContent.getMeasuredWidth() - (2 * mContentHorizontalMargin);
        if (mMaxHorizontalSpan != maxHorizontalSpan) {
            // Ensure the table layout is showing widgets in the right column after measure.
            mMaxHorizontalSpan = maxHorizontalSpan;
            onWidgetsBound();
            return true;
        }
        return false;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l;
        int height = b - t;

        // Content is laid out as center bottom aligned.
        int contentWidth = mContent.getMeasuredWidth();
        int contentLeft = (width - contentWidth - mInsets.left - mInsets.right) / 2 + mInsets.left;
        mContent.layout(contentLeft, height - mContent.getMeasuredHeight(),
                contentLeft + contentWidth, height);

        setTranslationShift(mTranslationShift);

        ScrollView widgetsTableScrollView = findViewById(R.id.widgets_table_scroll_view);
        TableLayout widgetsTable = findViewById(R.id.widgets_table);
        if (widgetsTable.getMeasuredHeight() > widgetsTableScrollView.getMeasuredHeight()) {
            findViewById(R.id.collapse_handle).setVisibility(VISIBLE);
        }
    }

    public void populateAndShow(ItemInfo itemInfo) {
        mOriginalItemInfo = itemInfo;
        ((TextView) findViewById(R.id.title)).setText(mOriginalItemInfo.title);

        onWidgetsBound();
        attachToContainer();
        mIsOpen = false;
        animateOpen();
    }

    @Override
    public void onWidgetsBound() {
        List<WidgetItem> widgets = mActivityContext.getPopupDataProvider().getWidgetsForPackageUser(
                new PackageUserKey(
                        mOriginalItemInfo.getTargetComponent().getPackageName(),
                        mOriginalItemInfo.user));

        TableLayout widgetsTable = findViewById(R.id.widgets_table);
        widgetsTable.removeAllViews();

        WidgetsTableUtils.groupWidgetItemsUsingRowPxWithReordering(widgets, mActivityContext,
                mActivityContext.getDeviceProfile(), mMaxHorizontalSpan,
                mWidgetCellHorizontalPadding)
                .forEach(row -> {
                    TableRow tableRow = new TableRow(getContext());
                    tableRow.setGravity(Gravity.TOP);
                    row.forEach(widgetItem -> {
                        WidgetCell widget = addItemCell(tableRow);
                        widget.applyFromCellItem(widgetItem);
                    });
                    widgetsTable.addView(tableRow);
                });
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mNoIntercept = false;
            ScrollView scrollView = findViewById(R.id.widgets_table_scroll_view);
            if (getPopupContainer().isEventOverView(scrollView, ev)
                    && scrollView.getScrollY() > 0) {
                mNoIntercept = true;
            }
        }
        return super.onControllerInterceptTouchEvent(ev);
    }

    protected WidgetCell addItemCell(ViewGroup parent) {
        WidgetCell widget = (WidgetCell) LayoutInflater.from(getContext())
                .inflate(R.layout.widget_cell, parent, false);

        View previewContainer = widget.findViewById(R.id.widget_preview_container);
        previewContainer.setOnClickListener(this);
        previewContainer.setOnLongClickListener(this);
        widget.setAnimatePreview(false);
        widget.setSourceContainer(CONTAINER_BOTTOM_WIDGETS_TRAY);

        parent.addView(widget);
        return widget;
    }

    private void animateOpen() {
        if (mIsOpen || mOpenCloseAnimation.getAnimationPlayer().isRunning()) {
            return;
        }
        mIsOpen = true;
        setupNavBarColor();
        setUpDefaultOpenAnimation().start();
    }

    @Override
    protected void handleClose(boolean animate) {
        handleClose(animate, DEFAULT_CLOSE_DURATION);
    }

    @Override
    protected boolean isOfType(@FloatingViewType int type) {
        return (type & TYPE_WIDGETS_BOTTOM_SHEET) != 0;
    }

    @Override
    public void setInsets(Rect insets) {
        super.setInsets(insets);
        int bottomPadding = Math.max(insets.bottom, mNavBarScrimHeight);

        View widgetsTable = findViewById(R.id.widgets_table);
        widgetsTable.setPadding(
                widgetsTable.getPaddingLeft(),
                widgetsTable.getPaddingTop(),
                widgetsTable.getPaddingRight(),
                bottomPadding);
        if (bottomPadding > 0) {
            setupNavBarColor();
        } else {
            clearNavBarColor();
        }
    }

    @Override
    protected void onContentHorizontalMarginChanged(int contentHorizontalMarginInPx) {
        ViewGroup.MarginLayoutParams layoutParams =
                ((ViewGroup.MarginLayoutParams) findViewById(R.id.widgets_table).getLayoutParams());
        layoutParams.setMarginStart(contentHorizontalMarginInPx);
        layoutParams.setMarginEnd(contentHorizontalMarginInPx);
    }

    @Override
    protected Pair<View, String> getAccessibilityTarget() {
        return Pair.create(findViewById(R.id.title),  getContext().getString(
                mIsOpen ? R.string.widgets_list : R.string.widgets_list_closed));
    }

    @Override
    public void addHintCloseAnim(
            float distanceToMove, Interpolator interpolator, PendingAnimation target) {
        target.setInt(this, PADDING_BOTTOM, (int) (distanceToMove + mInsets.bottom), interpolator);
    }
}
