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

import static com.android.launcher3.config.FeatureFlags.ENABLE_MATERIAL_U_POPUP;
import static com.android.launcher3.popup.SystemShortcut.APP_INFO;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.GridView;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DropTarget;
import com.android.launcher3.R;
import com.android.launcher3.allapps.ActivityAllAppsContainerView;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.popup.PopupDataProvider;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.util.ShortcutUtil;
import com.android.launcher3.util.TouchController;
import com.android.launcher3.views.BaseDragLayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * DragLayer for Secondary launcher
 */
public class SecondaryDragLayer extends BaseDragLayer<SecondaryDisplayLauncher> {

    private View mAllAppsButton;
    private ActivityAllAppsContainerView<SecondaryDisplayLauncher> mAppsView;

    private GridView mWorkspace;
    private PinnedAppsAdapter mPinnedAppsAdapter;

    public SecondaryDragLayer(Context context, AttributeSet attrs) {
        super(context, attrs, 1 /* alphaChannelCount */);
        recreateControllers();
    }

    @Override
    public void recreateControllers() {
        mControllers = new TouchController[]{new CloseAllAppsTouchController(),
                mActivity.getDragController()};
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mAllAppsButton = findViewById(R.id.all_apps_button);

        mAppsView = findViewById(R.id.apps_view);
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
                int horizontalPadding = (2 * grid.desiredWorkspaceHorizontalMarginPx)
                        + grid.cellLayoutPaddingPx.left + grid.cellLayoutPaddingPx.right;
                int verticalPadding =
                        grid.cellLayoutPaddingPx.top + grid.cellLayoutPaddingPx.bottom;

                int maxWidth =
                        grid.allAppsCellWidthPx * grid.numShownAllAppsColumns + horizontalPadding;
                int appsWidth = Math.min(width - getPaddingLeft() - getPaddingRight(), maxWidth);

                int maxHeight =
                        grid.allAppsCellHeightPx * grid.numShownAllAppsColumns + verticalPadding;
                int appsHeight = Math.min(height - getPaddingTop() - getPaddingBottom(), maxHeight);

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

    public PinnedAppsAdapter getPinnedAppsAdapter() {
        return mPinnedAppsAdapter;
    }

    boolean onIconLongClicked(View v) {
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
        PopupDataProvider popupDataProvider = mActivity.getPopupDataProvider();
        if (popupDataProvider == null) {
            return false;
        }

        // order of this list will reflect in the popup
        List<SystemShortcut> systemShortcuts = new ArrayList<>();
        systemShortcuts.add(APP_INFO.getShortcut(mActivity, item, v));
        // Hide redundant pin shortcut for app drawer icons if drag-n-drop is enabled.
        if (!FeatureFlags.SECONDARY_DRAG_N_DROP_TO_PIN.get() || !mActivity.isAppDrawerShown()) {
            systemShortcuts.add(mPinnedAppsAdapter.getSystemShortcut(item, v));
        }
        int deepShortcutCount = popupDataProvider.getShortcutCountForItem(item);
        final PopupContainerWithArrow<SecondaryDisplayLauncher> container;
        if (ENABLE_MATERIAL_U_POPUP.get()) {
            container = (PopupContainerWithArrow) mActivity.getLayoutInflater().inflate(
                    R.layout.popup_container_material_u, mActivity.getDragLayer(), false);
            container.populateAndShowRowsMaterialU((BubbleTextView) v, deepShortcutCount,
                    systemShortcuts);
        } else {
            container = (PopupContainerWithArrow) mActivity.getLayoutInflater().inflate(
                    R.layout.popup_container, mActivity.getDragLayer(), false);
            container.populateAndShow(
                    (BubbleTextView) v,
                    deepShortcutCount,
                    Collections.emptyList(),
                    systemShortcuts);
        }
        container.requestFocus();

        if (!FeatureFlags.SECONDARY_DRAG_N_DROP_TO_PIN.get() || !mActivity.isAppDrawerShown()) {
            return true;
        }

        DragOptions options = new DragOptions();
        DeviceProfile grid = mActivity.getDeviceProfile();
        options.intrinsicIconScaleFactor = (float) grid.allAppsIconSizePx / grid.iconSizePx;
        options.preDragCondition = container.createPreDragCondition(false);
        if (options.preDragCondition == null) {
            options.preDragCondition = new DragOptions.PreDragCondition() {
                private DragView<SecondaryDisplayLauncher> mDragView;

                @Override
                public boolean shouldStartDrag(double distanceDragged) {
                    return mDragView != null && mDragView.isScaleAnimationFinished();
                }

                @Override
                public void onPreDragStart(DropTarget.DragObject dragObject) {
                    mDragView = dragObject.dragView;
                    if (!shouldStartDrag(0)) {
                        mDragView.setOnScaleAnimEndCallback(() ->
                                mActivity.beginDragShared(v, mActivity.getAppsView(), options));
                    }
                }

                @Override
                public void onPreDragEnd(DropTarget.DragObject dragObject, boolean dragStarted) {
                    mDragView = null;
                }
            };
        }
        mActivity.beginDragShared(v, mActivity.getAppsView(), options);
        return true;
    }
}
