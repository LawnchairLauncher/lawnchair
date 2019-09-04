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
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.IntProperty;
import android.util.Pair;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.launcher3.Insettable;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.ResourceUtils;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.util.PackageUserKey;

import java.util.List;

/**
 * Bottom sheet for the "Widgets" system shortcut in the long-press popup.
 */
public class WidgetsBottomSheet extends BaseWidgetSheet implements Insettable {

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
    private ItemInfo mOriginalItemInfo;
    private Rect mInsets;

    public WidgetsBottomSheet(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WidgetsBottomSheet(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);
        mInsets = new Rect();
        mContent = this;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        setTranslationShift(mTranslationShift);
    }

    public void populateAndShow(ItemInfo itemInfo) {
        mOriginalItemInfo = itemInfo;
        ((TextView) findViewById(R.id.title)).setText(getContext().getString(
                R.string.widgets_bottom_sheet_title, mOriginalItemInfo.title));

        onWidgetsBound();
        attachToContainer();
        mIsOpen = false;
        animateOpen();
    }

    @Override
    public void onWidgetsBound() {
        List<WidgetItem> widgets = mLauncher.getPopupDataProvider().getWidgetsForPackageUser(
                new PackageUserKey(
                        mOriginalItemInfo.getTargetComponent().getPackageName(),
                        mOriginalItemInfo.user));

        ViewGroup widgetRow = findViewById(R.id.widgets);
        ViewGroup widgetCells = widgetRow.findViewById(R.id.widgets_cell_list);

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
            leftPaddingView.getLayoutParams().width = ResourceUtils.pxFromDp(
                    16, getResources().getDisplayMetrics());
            widgetCells.addView(leftPaddingView, 0);
        }
    }

    private void addDivider(ViewGroup parent) {
        LayoutInflater.from(getContext()).inflate(R.layout.widget_list_divider, parent, true);
    }

    protected WidgetCell addItemCell(ViewGroup parent) {
        WidgetCell widget = (WidgetCell) LayoutInflater.from(getContext()).inflate(
                R.layout.widget_cell, parent, false);

        widget.setOnClickListener(this);
        widget.setOnLongClickListener(this);
        widget.setAnimatePreview(false);

        parent.addView(widget);
        return widget;
    }

    private void animateOpen() {
        if (mIsOpen || mOpenCloseAnimator.isRunning()) {
            return;
        }
        mIsOpen = true;
        setupNavBarColor();
        mOpenCloseAnimator.setValues(
                PropertyValuesHolder.ofFloat(TRANSLATION_SHIFT, TRANSLATION_SHIFT_OPENED));
        mOpenCloseAnimator.setInterpolator(Interpolators.FAST_OUT_SLOW_IN);
        mOpenCloseAnimator.start();
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
        // Extend behind left, right, and bottom insets.
        int leftInset = insets.left - mInsets.left;
        int rightInset = insets.right - mInsets.right;
        int bottomInset = insets.bottom - mInsets.bottom;
        mInsets.set(insets);
        setPadding(leftInset, getPaddingTop(), rightInset, bottomInset);
    }

    @Override
    protected int getElementsRowCount() {
        return 1;
    }

    @Override
    protected Pair<View, String> getAccessibilityTarget() {
        return Pair.create(findViewById(R.id.title),  getContext().getString(
                mIsOpen ? R.string.widgets_list : R.string.widgets_list_closed));
    }

    @Nullable
    @Override
    public Animator createHintCloseAnim(float distanceToMove) {
        return ObjectAnimator.ofInt(this, PADDING_BOTTOM, (int) (distanceToMove + mInsets.bottom));
    }
}
