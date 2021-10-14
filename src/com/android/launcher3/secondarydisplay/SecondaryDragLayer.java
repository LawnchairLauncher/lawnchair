/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.launcher3.secondarydisplay;

import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.makeMeasureSpec;

import static com.android.launcher3.popup.SystemShortcut.APP_INFO;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.GridView;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.allapps.AllAppsContainerView;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.util.ShortcutUtil;
import com.android.launcher3.util.TouchController;
import com.android.launcher3.views.BaseDragLayer;

import java.util.Arrays;
import java.util.Collections;

/**
 * DragLayer for Secondary launcher
 */
public class SecondaryDragLayer extends BaseDragLayer<SecondaryDisplayLauncher> {

    private View mAllAppsButton;
    private AllAppsContainerView mAppsView;

    private GridView mWorkspace;
    private PinnedAppsAdapter mPinnedAppsAdapter;

    public SecondaryDragLayer(Context context, AttributeSet attrs) {
        super(context, attrs, 1 /* alphaChannelCount */);
        recreateControllers();
    }

    @Override
    public void recreateControllers() {
        mControllers = new TouchController[] {new CloseAllAppsTouchController()};
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mAllAppsButton = findViewById(R.id.all_apps_button);

        mAppsView = findViewById(R.id.apps_view);
        mAppsView.setOnIconLongClickListener(this::onIconLongClicked);

        // Setup workspace
        mWorkspace = findViewById(R.id.workspace_grid);
        mPinnedAppsAdapter = new PinnedAppsAdapter(mActivity, mAppsView.getAppsStore(),
                this::onIconLongClicked);
        mWorkspace.setAdapter(mPinnedAppsAdapter);
        mWorkspace.setNumColumns(mActivity.getDeviceProfile().inv.numColumns);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mPinnedAppsAdapter.init();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mPinnedAppsAdapter.destroy();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);

        DeviceProfile grid = mActivity.getDeviceProfile();
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child == mAppsView) {
                int padding = 2 * (grid.desiredWorkspaceLeftRightMarginPx
                        + grid.cellLayoutPaddingLeftRightPx);

                int maxWidth = grid.allAppsCellWidthPx * grid.numShownAllAppsColumns + padding;
                int appsWidth = Math.min(width, maxWidth);

                int maxHeight = grid.allAppsCellHeightPx * grid.numShownAllAppsColumns + padding;
                int appsHeight = Math.min(height, maxHeight);

                mAppsView.measure(
                        makeMeasureSpec(appsWidth, EXACTLY), makeMeasureSpec(appsHeight, EXACTLY));

            } else if (child == mAllAppsButton) {
                int appsButtonSpec = makeMeasureSpec(grid.iconSizePx, EXACTLY);
                mAllAppsButton.measure(appsButtonSpec, appsButtonSpec);

            } else if (child == mWorkspace) {
                measureChildWithMargins(mWorkspace, widthMeasureSpec, 0, heightMeasureSpec,
                        grid.iconSizePx + grid.edgeMarginPx);

            } else {
                measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
            }
        }
    }

    private class CloseAllAppsTouchController implements TouchController {

        @Override
        public boolean onControllerTouchEvent(MotionEvent ev) {
            return false;
        }

        @Override
        public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
            if (!mActivity.isAppDrawerShown()) {
                return false;
            }

            if (AbstractFloatingView.getTopOpenView(mActivity) != null) {
                return false;
            }

            if (ev.getAction() == MotionEvent.ACTION_DOWN
                    && !isEventOverView(mActivity.getAppsView(), ev)) {
                mActivity.showAppDrawer(false);
                return true;
            }
            return false;
        }
    }

    private boolean onIconLongClicked(View v) {
        if (!(v instanceof BubbleTextView)) {
            return false;
        }
        if (PopupContainerWithArrow.getOpen(mActivity) != null) {
            // There is already an items container open, so don't open this one.
            v.clearFocus();
            return false;
        }
        ItemInfo item = (ItemInfo) v.getTag();
        if (!ShortcutUtil.supportsShortcuts(item)) {
            return false;
        }
        final PopupContainerWithArrow container =
                (PopupContainerWithArrow) mActivity.getLayoutInflater().inflate(
                        R.layout.popup_container, mActivity.getDragLayer(), false);

        container.populateAndShow((BubbleTextView) v,
                mActivity.getPopupDataProvider().getShortcutCountForItem(item),
                Collections.emptyList(),
                Arrays.asList(mPinnedAppsAdapter.getSystemShortcut(item),
                        APP_INFO.getShortcut(mActivity, item)));
        v.getParent().requestDisallowInterceptTouchEvent(true);
        return true;
    }
}
