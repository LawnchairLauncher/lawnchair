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
package com.android.launcher3.taskbar;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.R;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.systemui.shared.recents.model.Task;

/**
 * Hosts the Taskbar content such as Hotseat and Recent Apps. Drawn on top of other apps.
 */
public class TaskbarView extends LinearLayout {

    private final ColorDrawable mBackgroundDrawable;
    private final int mItemMarginLeftRight;
    private final int mIconTouchSize;
    private final int mTouchSlop;
    private final RectF mTempDelegateBounds = new RectF();
    private final RectF mDelegateSlopBounds = new RectF();
    private final int[] mTempOutLocation = new int[2];

    // Initialized in init().
    private int mHotseatStartIndex;
    private int mHotseatEndIndex;
    private View mHotseatRecentsDivider;
    private int mRecentsStartIndex;
    private int mRecentsEndIndex;

    private TaskbarController.TaskbarViewCallbacks mControllerCallbacks;

    // Delegate touches to the closest view if within mIconTouchSize.
    private boolean mDelegateTargeted;
    private View mDelegateView;

    private boolean mIsDraggingItem;

    public TaskbarView(@NonNull Context context) {
        this(context, null);
    }

    public TaskbarView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskbarView(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public TaskbarView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        Resources resources = getResources();
        mBackgroundDrawable = (ColorDrawable) getBackground();
        mItemMarginLeftRight = resources.getDimensionPixelSize(R.dimen.taskbar_icon_spacing);
        mIconTouchSize = resources.getDimensionPixelSize(R.dimen.taskbar_icon_touch_size);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    protected void setCallbacks(TaskbarController.TaskbarViewCallbacks taskbarViewCallbacks) {
        mControllerCallbacks = taskbarViewCallbacks;
    }

    protected void init(int numHotseatIcons, int numRecentIcons) {
        mHotseatStartIndex = 0;
        mHotseatEndIndex = mHotseatStartIndex + numHotseatIcons - 1;
        updateHotseatItems(new ItemInfo[numHotseatIcons]);

        int dividerIndex = mHotseatEndIndex + 1;
        mHotseatRecentsDivider = addDivider(dividerIndex);

        mRecentsStartIndex = dividerIndex + 1;
        mRecentsEndIndex = mRecentsStartIndex + numRecentIcons - 1;
        updateRecentTasks(new Task[numRecentIcons]);
    }

    protected void cleanup() {
        removeAllViews();
    }

    /**
     * Sets the alpha of the background color behind all the Taskbar contents.
     * @param alpha 0 is fully transparent, 1 is fully opaque.
     */
    public void setBackgroundAlpha(float alpha) {
        mBackgroundDrawable.setAlpha((int) (alpha * 255));
    }

    /**
     * Inflates/binds the Hotseat views to show in the Taskbar given their ItemInfos.
     */
    protected void updateHotseatItems(ItemInfo[] hotseatItemInfos) {
        for (int i = 0; i < hotseatItemInfos.length; i++) {
            ItemInfo hotseatItemInfo = hotseatItemInfos[i];
            int hotseatIndex = mHotseatStartIndex + i;
            View hotseatView = getChildAt(hotseatIndex);

            // Replace any Hotseat views with the appropriate type if it's not already that type.
            final int expectedLayoutResId;
            if (hotseatItemInfo != null && hotseatItemInfo.isPredictedItem()) {
                expectedLayoutResId = R.layout.taskbar_predicted_app_icon;
            } else {
                expectedLayoutResId = R.layout.taskbar_app_icon;
            }
            if (hotseatView == null || hotseatView.getSourceLayoutResId() != expectedLayoutResId) {
                removeView(hotseatView);
                BubbleTextView btv = (BubbleTextView) inflate(expectedLayoutResId);
                LayoutParams lp = new LayoutParams(btv.getIconSize(), btv.getIconSize());
                lp.setMargins(mItemMarginLeftRight, 0, mItemMarginLeftRight, 0);
                hotseatView = btv;
                addView(hotseatView, hotseatIndex, lp);
            }

            // Apply the Hotseat ItemInfos, or hide the view if there is none for a given index.
            if (hotseatView instanceof BubbleTextView
                    && hotseatItemInfo instanceof WorkspaceItemInfo) {
                ((BubbleTextView) hotseatView).applyFromWorkspaceItem(
                        (WorkspaceItemInfo) hotseatItemInfo);
                hotseatView.setVisibility(VISIBLE);
                hotseatView.setOnClickListener(mControllerCallbacks.getItemOnClickListener());
                hotseatView.setOnLongClickListener(
                        mControllerCallbacks.getItemOnLongClickListener());
            } else {
                hotseatView.setVisibility(GONE);
                hotseatView.setOnClickListener(null);
                hotseatView.setOnLongClickListener(null);
            }
        }

        updateHotseatRecentsDividerVisibility();
    }

    private View addDivider(int dividerIndex) {
        View divider = inflate(R.layout.taskbar_divider);
        addView(divider, dividerIndex);
        return divider;
    }

    /**
     * Inflates/binds the Recents items to show in the Taskbar given their Tasks.
     */
    protected void updateRecentTasks(Task[] tasks) {
        for (int i = 0; i < tasks.length; i++) {
            Task task = tasks[i];
            int recentsIndex = mRecentsStartIndex + i;
            View recentsView = getChildAt(recentsIndex);

            // Inflate empty icon Views.
            if (recentsView == null) {
                BubbleTextView btv = (BubbleTextView) inflate(R.layout.taskbar_app_icon);
                LayoutParams lp = new LayoutParams(btv.getIconSize(), btv.getIconSize());
                lp.setMargins(mItemMarginLeftRight, 0, mItemMarginLeftRight, 0);
                recentsView = btv;
                addView(recentsView, recentsIndex, lp);
            }

            // Apply the Task, or hide the view if there is none for a given index.
            if (recentsView instanceof BubbleTextView && task != null) {
                applyTaskToBubbleTextView((BubbleTextView) recentsView, task);
                recentsView.setVisibility(VISIBLE);
                recentsView.setOnClickListener(mControllerCallbacks.getItemOnClickListener());
                recentsView.setOnLongClickListener(
                        mControllerCallbacks.getItemOnLongClickListener());
            } else {
                recentsView.setVisibility(GONE);
                recentsView.setOnClickListener(null);
                recentsView.setOnLongClickListener(null);
            }
        }

        updateHotseatRecentsDividerVisibility();
    }

    private void applyTaskToBubbleTextView(BubbleTextView btv, Task task) {
        if (task.icon != null) {
            Drawable icon = task.icon.getConstantState().newDrawable().mutate();
            btv.applyIconAndLabel(icon, task.titleDescription);
        }
        btv.setTag(task);
    }

    protected void updateRecentTaskAtIndex(int taskIndex, Task task) {
        View taskView = getChildAt(mRecentsStartIndex + taskIndex);
        if (taskView instanceof BubbleTextView) {
            applyTaskToBubbleTextView((BubbleTextView) taskView, task);
        }
    }

    /**
     * Make the divider VISIBLE between the Hotseat and Recents if there is at least one icon in
     * each, otherwise make it GONE.
     */
    private void updateHotseatRecentsDividerVisibility() {
        if (mHotseatRecentsDivider == null) {
            return;
        }

        boolean hasAtLeastOneHotseatItem = false;
        for (int i = mHotseatStartIndex; i <= mHotseatEndIndex; i++) {
            if (getChildAt(i).getVisibility() != GONE) {
                hasAtLeastOneHotseatItem = true;
                break;
            }
        }

        boolean hasAtLeastOneRecentItem = false;
        for (int i = mRecentsStartIndex; i <= mRecentsEndIndex; i++) {
            if (getChildAt(i).getVisibility() != GONE) {
                hasAtLeastOneRecentItem = true;
                break;
            }
        }

        mHotseatRecentsDivider.setVisibility(hasAtLeastOneHotseatItem && hasAtLeastOneRecentItem
                ? VISIBLE : GONE);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean handled = delegateTouchIfNecessary(event);
        return super.onTouchEvent(event) || handled;
    }

    /**
     * User touched the Taskbar background. Determine whether the touch is close enough to a view
     * that we should forward the touches to it.
     * @return Whether a delegate view was chosen and it handled the touch event.
     */
    private boolean delegateTouchIfNecessary(MotionEvent event) {
        final float x = event.getX();
        final float y = event.getY();
        if (mDelegateView == null && event.getAction() == MotionEvent.ACTION_DOWN) {
            View delegateView = findDelegateView(x, y);
            if (delegateView != null) {
                mDelegateTargeted = true;
                mDelegateView = delegateView;
                mDelegateSlopBounds.set(mTempDelegateBounds);
                mDelegateSlopBounds.inset(-mTouchSlop, -mTouchSlop);
            }
        }

        boolean sendToDelegate = mDelegateTargeted;
        boolean inBounds = true;
        switch (event.getAction()) {
            case MotionEvent.ACTION_MOVE:
                inBounds = mDelegateSlopBounds.contains(x, y);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mDelegateTargeted = false;
                break;
        }

        boolean handled = false;
        if (sendToDelegate) {
            if (inBounds) {
                // Offset event coordinates to be inside the target view
                event.setLocation(mDelegateView.getWidth() / 2f, mDelegateView.getHeight() / 2f);
            } else {
                // Offset event coordinates to be outside the target view (in case it does
                // something like tracking pressed state)
                event.setLocation(-mTouchSlop * 2, -mTouchSlop * 2);
            }
            handled = mDelegateView.dispatchTouchEvent(event);
            // Cleanup if this was the last event to send to the delegate.
            if (!mDelegateTargeted) {
                mDelegateView = null;
            }
        }
        return handled;
    }

    /**
     * Return an item whose touch bounds contain the given coordinates,
     * or null if no such item exists.
     *
     * Also sets {@link #mTempDelegateBounds} to be the touch bounds of the chosen delegate view.
     */
    private @Nullable View findDelegateView(float x, float y) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (!child.isShown() || !child.isClickable()) {
                continue;
            }
            int childCenterX = child.getLeft() + child.getWidth() / 2;
            int childCenterY = child.getTop() + child.getHeight() / 2;
            mTempDelegateBounds.set(
                    childCenterX - mIconTouchSize / 2f,
                    childCenterY - mIconTouchSize / 2f,
                    childCenterX + mIconTouchSize / 2f,
                    childCenterY + mIconTouchSize / 2f);
            if (mTempDelegateBounds.contains(x, y)) {
                return child;
            }
        }
        return null;
    }

    /**
     * Returns whether the given MotionEvent, *in screen coorindates*, is within any Taskbar item's
     * touch bounds.
     */
    public boolean isEventOverAnyItem(MotionEvent ev) {
        getLocationOnScreen(mTempOutLocation);
        float xInOurCoordinates = ev.getX() - mTempOutLocation[0];
        float yInOurCoorindates = ev.getY() - mTempOutLocation[1];
        return findDelegateView(xInOurCoordinates, yInOurCoorindates) != null;
    }

    @Override
    public boolean onDragEvent(DragEvent event) {
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                mIsDraggingItem = true;
                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                mIsDraggingItem = false;
                break;
        }
        return super.onDragEvent(event);
    }

    public boolean isDraggingItem() {
        return mIsDraggingItem;
    }

    private View inflate(@LayoutRes int layoutResId) {
        return LayoutInflater.from(getContext()).inflate(layoutResId, this, false);
    }
}
