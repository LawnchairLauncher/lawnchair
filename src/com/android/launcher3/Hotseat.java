/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.launcher3;

import static com.android.launcher3.LauncherState.ALL_APPS;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.logging.UserEventDispatcher;
import com.android.launcher3.logging.UserEventDispatcher.LogContainerProvider;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.launcher3.userevent.nano.LauncherLogProto.ControlType;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;

public class Hotseat extends FrameLayout implements LogContainerProvider, Insettable {

    private final Launcher mLauncher;
    private CellLayout mContent;

    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mHasVerticalHotseat;

    public Hotseat(Context context) {
        this(context, null);
    }

    public Hotseat(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Hotseat(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mLauncher = Launcher.getLauncher(context);
    }

    public CellLayout getLayout() {
        return mContent;
    }

    /**
     * Registers the specified listener on the cell layout of the hotseat.
     */
    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        mContent.setOnLongClickListener(l);
    }

    /* Get the orientation invariant order of the item in the hotseat for persistence. */
    int getOrderInHotseat(int x, int y) {
        return mHasVerticalHotseat ? (mContent.getCountY() - y - 1) : x;
    }

    /* Get the orientation specific coordinates given an invariant order in the hotseat. */
    int getCellXFromOrder(int rank) {
        return mHasVerticalHotseat ? 0 : rank;
    }

    int getCellYFromOrder(int rank) {
        return mHasVerticalHotseat ? (mContent.getCountY() - (rank + 1)) : 0;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContent = findViewById(R.id.layout);

        resetLayout();
    }

    void resetLayout() {
        mContent.removeAllViewsInLayout();

        if (!FeatureFlags.NO_ALL_APPS_ICON) {
            // Add the Apps button
            Context context = getContext();
            DeviceProfile grid = mLauncher.getDeviceProfile();
            int allAppsButtonRank = grid.inv.getAllAppsButtonRank();

            LayoutInflater inflater = LayoutInflater.from(context);
            TextView allAppsButton = (TextView)
                    inflater.inflate(R.layout.all_apps_button, mContent, false);
            Drawable d = context.getResources().getDrawable(R.drawable.all_apps_button_icon);
            d.setBounds(0, 0, grid.iconSizePx, grid.iconSizePx);

            int scaleDownPx = getResources().getDimensionPixelSize(R.dimen.all_apps_button_scale_down);
            Rect bounds = d.getBounds();
            d.setBounds(bounds.left, bounds.top + scaleDownPx / 2, bounds.right - scaleDownPx,
                    bounds.bottom - scaleDownPx / 2);
            allAppsButton.setCompoundDrawables(null, d, null, null);

            allAppsButton.setContentDescription(context.getString(R.string.all_apps_button_label));
            allAppsButton.setOnKeyListener(new HotseatIconKeyEventListener());
            if (mLauncher != null) {
                allAppsButton.setOnClickListener((v) -> {
                    if (!mLauncher.isInState(ALL_APPS)) {
                        mLauncher.getUserEventDispatcher().logActionOnControl(Action.Touch.TAP,
                                ControlType.ALL_APPS_BUTTON);
                        mLauncher.getStateManager().goToState(ALL_APPS);
                    }
                });
                allAppsButton.setOnFocusChangeListener(mLauncher.mFocusHandler);
            }

            // Note: We do this to ensure that the hotseat is always laid out in the orientation of
            // the hotseat in order regardless of which orientation they were added
            int x = getCellXFromOrder(allAppsButtonRank);
            int y = getCellYFromOrder(allAppsButtonRank);
            CellLayout.LayoutParams lp = new CellLayout.LayoutParams(x, y, 1, 1);
            lp.canReorder = false;
            mContent.addViewToCellLayout(allAppsButton, -1, allAppsButton.getId(), lp, true);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // We don't want any clicks to go through to the hotseat unless the workspace is in
        // the normal state or an accessible drag is in progress.
        return !mLauncher.getWorkspace().workspaceIconsCanBeDragged() &&
                !mLauncher.getAccessibilityDelegate().isInAccessibleDrag();
    }

    @Override
    public void fillInLogContainerData(View v, ItemInfo info, Target target, Target targetParent) {
        target.gridX = info.cellX;
        target.gridY = info.cellY;
        targetParent.containerType = ContainerType.HOTSEAT;
    }

    @Override
    public void setInsets(Rect insets) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        DeviceProfile grid = mLauncher.getDeviceProfile();
        mHasVerticalHotseat = mLauncher.getDeviceProfile().isVerticalBarLayout();

        if (mHasVerticalHotseat) {
            mContent.setGridSize(1, grid.inv.numHotseatIcons);

            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            if (grid.isSeascape()) {
                lp.gravity = Gravity.LEFT;
                lp.width = grid.hotseatBarSizePx + insets.left + grid.hotseatBarSidePaddingPx;
                getLayout().setPadding(
                        insets.left, insets.top, grid.hotseatBarSidePaddingPx, insets.bottom);

            } else {
                lp.gravity = Gravity.RIGHT;
                lp.width = grid.hotseatBarSizePx + insets.right + grid.hotseatBarSidePaddingPx;
                getLayout().setPadding(
                        grid.hotseatBarSidePaddingPx, insets.top, insets.right, insets.bottom);
            }
        } else {
            mContent.setGridSize(grid.inv.numHotseatIcons, 1);

            lp.gravity = Gravity.BOTTOM;
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = grid.hotseatBarSizePx + insets.bottom;

            // We want the edges of the hotseat to line up with the edges of the workspace, but the
            // icons in the hotseat are a different size, and so don't line up perfectly. To account for
            // this, we pad the left and right of the hotseat with half of the difference of a workspace
            // cell vs a hotseat cell.
            float workspaceCellWidth = (float) grid.widthPx / grid.inv.numColumns;
            float hotseatCellWidth = (float) grid.widthPx / grid.inv.numHotseatIcons;
            int hotseatAdjustment = Math.round((workspaceCellWidth - hotseatCellWidth) / 2);
            Rect workspacePadding = grid.workspacePadding;

            getLayout().setPadding(
                    hotseatAdjustment + workspacePadding.left + grid.cellLayoutPaddingLeftRightPx,
                    grid.hotseatBarTopPaddingPx,
                    hotseatAdjustment + workspacePadding.right + grid.cellLayoutPaddingLeftRightPx,
                    grid.hotseatBarBottomPaddingPx + insets.bottom + grid.cellLayoutBottomPaddingPx);
        }
        setLayoutParams(lp);
        InsettableFrameLayout.dispatchInsets(this, insets);
    }
}
