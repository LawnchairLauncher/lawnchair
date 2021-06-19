/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.graphics.PointF;
import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Reorderable;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.views.ActivityContext;

import java.util.ArrayList;

/**
 * Extension of AppWidgetHostView with support for controlled keyboard navigation.
 */
public abstract class NavigableAppWidgetHostView extends AppWidgetHostView
        implements DraggableView, Reorderable {

    /**
     * The scaleX and scaleY value such that the widget fits within its cellspans, scaleX = scaleY.
     */
    private float mScaleToFit = 1f;

    /**
     * The translation values to center the widget within its cellspans.
     */
    private final PointF mTranslationForCentering = new PointF(0, 0);

    private final PointF mTranslationForReorderBounce = new PointF(0, 0);
    private final PointF mTranslationForReorderPreview = new PointF(0, 0);
    private float mScaleForReorderBounce = 1f;

    private final Rect mTempRect = new Rect();

    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mChildrenFocused;

    protected final BaseActivity mActivity;

    public NavigableAppWidgetHostView(Context context) {
        super(context);
        mActivity = ActivityContext.lookupContext(context);
    }

    @Override
    public int getDescendantFocusability() {
        return mChildrenFocused ? ViewGroup.FOCUS_BEFORE_DESCENDANTS
                : ViewGroup.FOCUS_BLOCK_DESCENDANTS;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (mChildrenFocused && event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE
                && event.getAction() == KeyEvent.ACTION_UP) {
            mChildrenFocused = false;
            requestFocus();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!mChildrenFocused && keyCode == KeyEvent.KEYCODE_ENTER) {
            event.startTracking();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (event.isTracking()) {
            if (!mChildrenFocused && keyCode == KeyEvent.KEYCODE_ENTER) {
                mChildrenFocused = true;
                ArrayList<View> focusableChildren = getFocusables(FOCUS_FORWARD);
                focusableChildren.remove(this);
                int childrenCount = focusableChildren.size();
                switch (childrenCount) {
                    case 0:
                        mChildrenFocused = false;
                        break;
                    case 1: {
                        if (shouldAllowDirectClick()) {
                            focusableChildren.get(0).performClick();
                            mChildrenFocused = false;
                            return true;
                        }
                        // continue;
                    }
                    default:
                        focusableChildren.get(0).requestFocus();
                        return true;
                }
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    /**
     * For a widget with only a single interactive element, return true if whole widget should act
     * as a single interactive element, and clicking 'enter' should activate the child element
     * directly. Otherwise clicking 'enter' will only move the focus inside the widget.
     */
    protected abstract boolean shouldAllowDirectClick();

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        if (gainFocus) {
            mChildrenFocused = false;
            dispatchChildFocus(false);
        }
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        dispatchChildFocus(mChildrenFocused && focused != null);
        if (focused != null) {
            focused.setFocusableInTouchMode(false);
        }
    }

    @Override
    public void clearChildFocus(View child) {
        super.clearChildFocus(child);
        dispatchChildFocus(false);
    }

    @Override
    public boolean dispatchUnhandledMove(View focused, int direction) {
        return mChildrenFocused;
    }

    private void dispatchChildFocus(boolean childIsFocused) {
        // The host view's background changes when selected, to indicate the focus is inside.
        setSelected(childIsFocused);
    }

    public View getView() {
        return this;
    }

    private void updateTranslation() {
        super.setTranslationX(mTranslationForReorderBounce.x + mTranslationForReorderPreview.x
                + mTranslationForCentering.x);
        super.setTranslationY(mTranslationForReorderBounce.y + mTranslationForReorderPreview.y
                + mTranslationForCentering.y);
    }

    public void setTranslationForCentering(float x, float y) {
        mTranslationForCentering.set(x, y);
        updateTranslation();
    }

    public void setReorderBounceOffset(float x, float y) {
        mTranslationForReorderBounce.set(x, y);
        updateTranslation();
    }

    public void getReorderBounceOffset(PointF offset) {
        offset.set(mTranslationForReorderBounce);
    }

    @Override
    public void setReorderPreviewOffset(float x, float y) {
        mTranslationForReorderPreview.set(x, y);
        updateTranslation();
    }

    @Override
    public void getReorderPreviewOffset(PointF offset) {
        offset.set(mTranslationForReorderPreview);
    }

    private void updateScale() {
        super.setScaleX(mScaleToFit * mScaleForReorderBounce);
        super.setScaleY(mScaleToFit * mScaleForReorderBounce);
    }

    public void setReorderBounceScale(float scale) {
        mScaleForReorderBounce = scale;
        updateScale();
    }

    public float getReorderBounceScale() {
        return mScaleForReorderBounce;
    }

    public void setScaleToFit(float scale) {
        mScaleToFit = scale;
        updateScale();
    }

    public float getScaleToFit() {
        return mScaleToFit;
    }

    @Override
    public int getViewType() {
        return DRAGGABLE_WIDGET;
    }

    @Override
    public void getWorkspaceVisualDragBounds(Rect bounds) {
        int width = (int) (getMeasuredWidth() * mScaleToFit);
        int height = (int) (getMeasuredHeight() * mScaleToFit);

        getWidgetInset(mActivity.getDeviceProfile(), mTempRect);
        bounds.set(mTempRect.left, mTempRect.top, width - mTempRect.right,
                height - mTempRect.bottom);
    }

    /**
     * Widgets have padding added by the system. We may choose to inset this padding if the grid
     * supports it.
     */
    public void getWidgetInset(DeviceProfile grid, Rect out) {
        if (!grid.shouldInsetWidgets()) {
            out.setEmpty();
            return;
        }
        AppWidgetProviderInfo info = getAppWidgetInfo();
        if (info == null) {
            out.set(grid.inv.defaultWidgetPadding);
        } else {
            AppWidgetHostView.getDefaultPaddingForWidget(getContext(), info.provider, out);
        }
    }
}
