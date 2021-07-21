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

import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.makeMeasureSpec;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.Insettable;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.views.ActivityContext;

/**
 * Hosts the Taskbar content such as Hotseat and Recent Apps. Drawn on top of other apps.
 */
public class TaskbarView extends LinearLayout implements FolderIcon.FolderIconParent, Insettable {

    private final int mIconTouchSize;
    private final boolean mIsRtl;
    private final int mTouchSlop;
    private final RectF mTempDelegateBounds = new RectF();
    private final RectF mDelegateSlopBounds = new RectF();
    private final int[] mTempOutLocation = new int[2];

    private final int mItemMarginLeftRight;

    private final TaskbarActivityContext mActivityContext;

    // Initialized in TaskbarController constructor.
    private View.OnClickListener mIconClickListener;
    private View.OnLongClickListener mIconLongClickListener;

    LinearLayout mSystemButtonContainer;
    LinearLayout mHotseatIconsContainer;

    // Delegate touches to the closest view if within mIconTouchSize.
    private boolean mDelegateTargeted;
    private View mDelegateView;
    // Prevents dispatching touches to children if true
    private boolean mTouchEnabled = true;

    private boolean mIsDraggingItem;
    // Only non-null when the corresponding Folder is open.
    private @Nullable FolderIcon mLeaveBehindFolderIcon;

    /** Provider of buttons added to taskbar in 3 button nav */
    private ButtonProvider mButtonProvider;

    private boolean mDisableRelayout;
    private boolean mAreHolesAllowed;

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
        mActivityContext = ActivityContext.lookupContext(context);

        Resources resources = getResources();
        mIconTouchSize = resources.getDimensionPixelSize(R.dimen.taskbar_icon_touch_size);
        mItemMarginLeftRight = resources.getDimensionPixelSize(R.dimen.taskbar_icon_spacing);

        mIsRtl = Utilities.isRtl(resources);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSystemButtonContainer = findViewById(R.id.system_button_layout);
        mHotseatIconsContainer = findViewById(R.id.hotseat_icons_layout);
    }

    protected void construct(OnClickListener clickListener, OnLongClickListener longClickListener,
                ButtonProvider buttonProvider) {
        mIconClickListener = clickListener;
        mIconLongClickListener = longClickListener;
        mButtonProvider = buttonProvider;

        if (mActivityContext.canShowNavButtons()) {
            createNavButtons();
        } else {
            mSystemButtonContainer.setVisibility(GONE);
        }

        int numHotseatIcons = mActivityContext.getDeviceProfile().numShownHotseatIcons;
        updateHotseatItems(new ItemInfo[numHotseatIcons]);
    }

    /**
     * Enables/disables empty icons in taskbar so that the layout matches with Launcher
     */
    public void setHolesAllowedInLayout(boolean areHolesAllowed) {
        if (mAreHolesAllowed != areHolesAllowed) {
            mAreHolesAllowed = areHolesAllowed;
            updateHotseatItemsVisibility();
            // TODO: Add animation
        }
    }

    private void setHolesAllowedInLayoutNoAnimation(boolean areHolesAllowed) {
        if (mAreHolesAllowed != areHolesAllowed) {
            mAreHolesAllowed = areHolesAllowed;
            updateHotseatItemsVisibility();
            onMeasure(makeMeasureSpec(getMeasuredWidth(), EXACTLY),
                    makeMeasureSpec(getMeasuredHeight(), EXACTLY));
            onLayout(false, getLeft(), getTop(), getRight(), getBottom());
        }
    }

    /**
     * Inflates/binds the Hotseat views to show in the Taskbar given their ItemInfos.
     */
    protected void updateHotseatItems(ItemInfo[] hotseatItemInfos) {
        for (int i = 0; i < hotseatItemInfos.length; i++) {
            ItemInfo hotseatItemInfo = hotseatItemInfos[
                    !mIsRtl ? i : hotseatItemInfos.length - i - 1];
            View hotseatView = mHotseatIconsContainer.getChildAt(i);

            // Replace any Hotseat views with the appropriate type if it's not already that type.
            final int expectedLayoutResId;
            boolean isFolder = false;
            boolean needsReinflate = false;
            if (hotseatItemInfo != null && hotseatItemInfo.isPredictedItem()) {
                expectedLayoutResId = R.layout.taskbar_predicted_app_icon;
            } else if (hotseatItemInfo instanceof FolderInfo) {
                expectedLayoutResId = R.layout.folder_icon;
                isFolder = true;
                // Unlike for BubbleTextView, we can't reapply a new FolderInfo after inflation, so
                // if the info changes we need to reinflate. This should only happen if a new folder
                // is dragged to the position that another folder previously existed.
                needsReinflate = hotseatView != null && hotseatView.getTag() != hotseatItemInfo;
            } else {
                expectedLayoutResId = R.layout.taskbar_app_icon;
            }
            if (hotseatView == null
                    || hotseatView.getSourceLayoutResId() != expectedLayoutResId
                    || needsReinflate) {
                mHotseatIconsContainer.removeView(hotseatView);
                if (isFolder) {
                    FolderInfo folderInfo = (FolderInfo) hotseatItemInfo;
                    FolderIcon folderIcon = FolderIcon.inflateFolderAndIcon(expectedLayoutResId,
                            mActivityContext, this, folderInfo);
                    folderIcon.setTextVisible(false);
                    hotseatView = folderIcon;
                } else {
                    hotseatView = inflate(expectedLayoutResId);
                }
                int iconSize = mActivityContext.getDeviceProfile().iconSizePx;
                LayoutParams lp = new LayoutParams(iconSize, iconSize);
                lp.setMargins(mItemMarginLeftRight, 0, mItemMarginLeftRight, 0);
                mHotseatIconsContainer.addView(hotseatView, i, lp);
            }

            // Apply the Hotseat ItemInfos, or hide the view if there is none for a given index.
            if (hotseatView instanceof BubbleTextView
                    && hotseatItemInfo instanceof WorkspaceItemInfo) {
                ((BubbleTextView) hotseatView).applyFromWorkspaceItem(
                        (WorkspaceItemInfo) hotseatItemInfo);
                hotseatView.setOnClickListener(mIconClickListener);
                hotseatView.setOnLongClickListener(mIconLongClickListener);
            } else if (isFolder) {
                hotseatView.setOnClickListener(mIconClickListener);
                hotseatView.setOnLongClickListener(mIconLongClickListener);
            } else {
                hotseatView.setOnClickListener(null);
                hotseatView.setOnLongClickListener(null);
                hotseatView.setTag(null);
            }
            updateHotseatItemVisibility(hotseatView);
        }
    }

    protected void updateHotseatItemsVisibility() {
        for (int i = mHotseatIconsContainer.getChildCount() - 1; i >= 0; i--) {
            updateHotseatItemVisibility(mHotseatIconsContainer.getChildAt(i));
        }
    }

    private void updateHotseatItemVisibility(View hotseatView) {
        hotseatView.setVisibility(
                hotseatView.getTag() != null ? VISIBLE : (mAreHolesAllowed ? INVISIBLE : GONE));
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (!mTouchEnabled) {
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean handled = delegateTouchIfNecessary(event);
        return super.onTouchEvent(event) || handled;
    }

    public void setTouchesEnabled(boolean touchEnabled) {
        this.mTouchEnabled = touchEnabled;
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

    /**
     * Add back/home/recents buttons into a single ViewGroup that will be inserted at
     * {@param navButtonStartIndex}
     */
    private void createNavButtons() {
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                mActivityContext.getDeviceProfile().iconSizePx,
                mActivityContext.getDeviceProfile().iconSizePx
        );
        buttonParams.gravity = Gravity.CENTER;

        mSystemButtonContainer.addView(mButtonProvider.getBack(), buttonParams);
        mSystemButtonContainer.addView(mButtonProvider.getHome(), buttonParams);
        mSystemButtonContainer.addView(mButtonProvider.getRecents(), buttonParams);
    }

    @Override
    public boolean onDragEvent(DragEvent event) {
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                mIsDraggingItem = true;
                AbstractFloatingView.closeAllOpenViews(mActivityContext);
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

    /**
     * @return The bounding box of where the hotseat elements are relative to this TaskbarView.
     */
    protected RectF getHotseatBounds() {
        RectF result;
        mDisableRelayout = true;
        boolean wereHolesAllowed = mAreHolesAllowed;
        setHolesAllowedInLayoutNoAnimation(true);
        result = new RectF(
                mHotseatIconsContainer.getLeft(),
                mHotseatIconsContainer.getTop(),
                mHotseatIconsContainer.getRight(),
                mHotseatIconsContainer.getBottom());
        setHolesAllowedInLayoutNoAnimation(wereHolesAllowed);
        mDisableRelayout = false;

        return result;
    }

    @Override
    public void requestLayout() {
        if (!mDisableRelayout) {
            super.requestLayout();
        }
    }

    // FolderIconParent implemented methods.

    @Override
    public void drawFolderLeaveBehindForIcon(FolderIcon child) {
        mLeaveBehindFolderIcon = child;
        invalidate();
    }

    @Override
    public void clearFolderLeaveBehind(FolderIcon child) {
        mLeaveBehindFolderIcon = null;
        invalidate();
    }

    // End FolderIconParent implemented methods.

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mLeaveBehindFolderIcon != null) {
            canvas.save();
            canvas.translate(mLeaveBehindFolderIcon.getLeft(), mLeaveBehindFolderIcon.getTop());
            mLeaveBehindFolderIcon.getFolderBackground().drawLeaveBehind(canvas);
            canvas.restore();
        }
    }

    private View inflate(@LayoutRes int layoutResId) {
        return mActivityContext.getLayoutInflater().inflate(layoutResId, this, false);
    }

    @Override
    public void setInsets(Rect insets) {
        // Ignore, we just implement Insettable to draw behind system insets.
    }

    public void setIconsVisibility(boolean isVisible) {
        mHotseatIconsContainer.setVisibility(isVisible ? VISIBLE : INVISIBLE);
    }

    public boolean areIconsVisible() {
        return mHotseatIconsContainer.getVisibility() == VISIBLE;
    }
}
