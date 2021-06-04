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

import static com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY;
import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_X;
import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_Y;
import static com.android.launcher3.anim.Interpolators.LINEAR;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Insettable;
import com.android.launcher3.R;
import com.android.launcher3.anim.PropertySetter;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.uioverrides.ApiWrapper;
import com.android.launcher3.views.ActivityContext;

/**
 * Hosts the Taskbar content such as Hotseat and Recent Apps. Drawn on top of other apps.
 */
public class TaskbarView extends FrameLayout implements FolderIcon.FolderIconParent, Insettable {

    private final int[] mTempOutLocation = new int[2];

    private final Rect mIconLayoutBounds = new Rect();
    private final int mIconTouchSize;
    private final int mItemMarginLeftRight;
    private final int mItemPadding;

    private final TaskbarActivityContext mActivityContext;

    // Initialized in init.
    private View.OnClickListener mIconClickListener;
    private View.OnLongClickListener mIconLongClickListener;

    // Prevents dispatching touches to children if true
    private boolean mTouchEnabled = true;

    // Only non-null when the corresponding Folder is open.
    private @Nullable FolderIcon mLeaveBehindFolderIcon;

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

        int actualMargin = resources.getDimensionPixelSize(R.dimen.taskbar_icon_spacing);
        int actualIconSize = mActivityContext.getDeviceProfile().iconSizePx;

        // We layout the icons to be of mIconTouchSize in width and height
        mItemMarginLeftRight = actualMargin - (mIconTouchSize - actualIconSize) / 2;
        mItemPadding = (mIconTouchSize - actualIconSize) / 2;
    }

    protected void init(OnClickListener clickListener, OnLongClickListener longClickListener) {
        mIconClickListener = clickListener;
        mIconLongClickListener = longClickListener;

        int numHotseatIcons = mActivityContext.getDeviceProfile().numShownHotseatIcons;
        updateHotseatItems(new ItemInfo[numHotseatIcons]);
    }

    /**
     * Aligns the icons in the taskbar to that of Launcher.
     */
    public void alignIconsWithLauncher(DeviceProfile launcherDp, PropertySetter setter) {
        Rect hotseatPadding = launcherDp.getHotseatLayoutPadding(getContext());
        float scaleUp = ((float) launcherDp.iconSizePx)
                / mActivityContext.getDeviceProfile().iconSizePx;
        int hotseatCellSize =
                (launcherDp.availableWidthPx - hotseatPadding.left - hotseatPadding.right)
                        / launcherDp.numShownHotseatIcons;

        int offsetY = launcherDp.getTaskbarOffsetY();
        setter.setFloat(this, VIEW_TRANSLATE_Y, -offsetY, LINEAR);
        mActivityContext.setTaskbarWindowHeight(
                mActivityContext.getDeviceProfile().taskbarSize + offsetY);

        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != VISIBLE) {
                continue;
            }
            setter.setFloat(child, SCALE_PROPERTY, scaleUp, LINEAR);

            float childCenter = (child.getLeft() + child.getRight()) / 2;
            float hotseatIconCenter = hotseatPadding.left + hotseatCellSize * (i)
                    + hotseatCellSize / 2;
            setter.setFloat(child, VIEW_TRANSLATE_X, hotseatIconCenter - childCenter, LINEAR);
        }
    }

    /**
     * Aligns the icons in the taskbar to that of Launcher.
     * @return a callback to be executed at the end of the setter
     */
    public Runnable resetIconPosition(PropertySetter setter) {
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            setter.setFloat(child, SCALE_PROPERTY, 1, LINEAR);
            setter.setFloat(child, VIEW_TRANSLATE_X, 0, LINEAR);
        }
        setter.setFloat(this, VIEW_TRANSLATE_Y, 0, LINEAR);
        return () -> mActivityContext.setTaskbarWindowHeight(
                mActivityContext.getDeviceProfile().taskbarSize);
    }

    /**
     * Inflates/binds the Hotseat views to show in the Taskbar given their ItemInfos.
     */
    protected void updateHotseatItems(ItemInfo[] hotseatItemInfos) {
        for (int i = 0; i < hotseatItemInfos.length; i++) {
            ItemInfo hotseatItemInfo = hotseatItemInfos[i];
            View hotseatView = getChildAt(i);

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
                removeView(hotseatView);
                if (isFolder) {
                    FolderInfo folderInfo = (FolderInfo) hotseatItemInfo;
                    FolderIcon folderIcon = FolderIcon.inflateFolderAndIcon(expectedLayoutResId,
                            mActivityContext, this, folderInfo);
                    folderIcon.setTextVisible(false);
                    hotseatView = folderIcon;
                } else {
                    hotseatView = inflate(expectedLayoutResId);
                }
                LayoutParams lp = new LayoutParams(mIconTouchSize, mIconTouchSize);
                hotseatView.setPadding(mItemPadding, mItemPadding, mItemPadding, mItemPadding);
                addView(hotseatView, i, lp);
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
            hotseatView.setVisibility(hotseatView.getTag() != null ? VISIBLE : INVISIBLE);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int count = getChildCount();
        // Find total visible children
        int visibleChildren = 0;
        for (int i = 0; i < count; i++) {
            if (getChildAt(i).getVisibility() == VISIBLE) {
                visibleChildren++;
            }
        }

        int spaceNeeded = visibleChildren * (mItemMarginLeftRight * 2 + mIconTouchSize);
        int iconStart = (right - left - spaceNeeded) / 2;
        int startOffset = ApiWrapper.getHotseatStartOffset(getContext());
        if (startOffset > iconStart) {
            int diff = startOffset - iconStart;
            iconStart = isLayoutRtl() ? (iconStart - diff) : iconStart + diff;
        }
        // Layout the children
        mIconLayoutBounds.left = iconStart;
        mIconLayoutBounds.top = (bottom - top - mIconTouchSize) / 2;
        mIconLayoutBounds.bottom = mIconLayoutBounds.top + mIconTouchSize;
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == VISIBLE) {
                iconStart += mItemMarginLeftRight;
                int iconEnd = iconStart + mIconTouchSize;
                child.layout(iconStart, mIconLayoutBounds.top, iconEnd, mIconLayoutBounds.bottom);
                iconStart = iconEnd + mItemMarginLeftRight;
            }
        }
        mIconLayoutBounds.right = iconStart;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (!mTouchEnabled) {
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    public void setTouchesEnabled(boolean touchEnabled) {
        this.mTouchEnabled = touchEnabled;
    }

    /**
     * Returns whether the given MotionEvent, *in screen coorindates*, is within any Taskbar item's
     * touch bounds.
     */
    public boolean isEventOverAnyItem(MotionEvent ev) {
        getLocationOnScreen(mTempOutLocation);
        int xInOurCoordinates = (int) ev.getX() - mTempOutLocation[0];
        int yInOurCoorindates = (int) ev.getY() - mTempOutLocation[1];
        return isShown() && mIconLayoutBounds.contains(xInOurCoordinates, yInOurCoorindates);
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

    public boolean areIconsVisible() {
        // Consider the overall visibility
        return getVisibility() == VISIBLE;
    }
}
