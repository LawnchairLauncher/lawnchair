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
import com.android.launcher3.util.ApiWrapper;
import com.android.launcher3.util.ShortcutUtil;
import com.android.launcher3.util.TouchController;
import com.android.launcher3.views.BaseDragLayer;

import java.util.ArrayList;
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
        super.recreateControllers();
        TouchController statusBarController =
            ApiWrapper.INSTANCE.get(getContext())
                .createStatusBarTouchController(mContainer, () -> true);

        if (statusBarController != null) {
            mControllers = new TouchController[]{
                new CloseAllAppsTouchController(),
                mContainer.getDragController(),
                statusBarController
            };
        } else {
            mControllers = new TouchController[]{
                new CloseAllAppsTouchController(),
                mContainer.getDragController()
            };
        }
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
        mPinnedAppsAdapter = new PinnedAppsAdapter(mContainer, mAppsView.getAppsStore(),
                this::onIconLongClicked);
        mWorkspace.setAdapter(mPinnedAppsAdapter);
        mWorkspace.setNumColumns(mContainer.getDeviceProfile().inv.numColumns);
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

        DeviceProfile grid = mContainer.getDeviceProfile();
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child == mAppsView) {
                int horizontalPadding = (2 * grid.desiredWorkspaceHorizontalMarginPx)
                        + grid.cellLayoutPaddingPx.left + grid.cellLayoutPaddingPx.right;
                int verticalPadding =
                        grid.cellLayoutPaddingPx.top + grid.cellLayoutPaddingPx.bottom;

                int maxWidth =
                        grid.getAllAppsProfile().getCellWidthPx() * grid.numShownAllAppsColumns
                                + horizontalPadding;
                int appsWidth = Math.min(width - getPaddingLeft() - getPaddingRight(), maxWidth);

                int maxHeight =
                        grid.getAllAppsProfile().getCellHeightPx() * grid.numShownAllAppsColumns
                                + verticalPadding;
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
            if (!mContainer.isAppDrawerShown()) {
                return false;
            }

            if (AbstractFloatingView.getTopOpenView(mContainer) != null) {
                return false;
            }

            if (ev.getAction() == MotionEvent.ACTION_DOWN
                    && !isEventOverView(mContainer.getAppsView(), ev)) {
                mContainer.showAppDrawer(false);
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
        if (PopupContainerWithArrow.getOpen(mContainer) != null) {
            // There is already an items container open, so don't open this one.
            v.clearFocus();
            return false;
        }
        ItemInfo item = (ItemInfo) v.getTag();
        if (!ShortcutUtil.supportsShortcuts(item)) {
            return false;
        }
        PopupDataProvider popupDataProvider = mContainer.getPopupDataProvider();
        if (popupDataProvider == null) {
            return false;
        }

        // order of this list will reflect in the popup
        List<SystemShortcut> systemShortcuts = new ArrayList<>();
        systemShortcuts.add(APP_INFO.getShortcut(mContainer, item, v));
        // Hide redundant pin shortcut for app drawer icons if drag-n-drop is enabled.
        if (!FeatureFlags.SECONDARY_DRAG_N_DROP_TO_PIN.get() || !mContainer.isAppDrawerShown()) {
            systemShortcuts.add(mPinnedAppsAdapter.getSystemShortcut(item, v));
        }
        int deepShortcutCount = popupDataProvider.getShortcutCountForItem(item);
        final PopupContainerWithArrow<SecondaryDisplayLauncher> container;
        container = (PopupContainerWithArrow) mContainer.getLayoutInflater().inflate(
                R.layout.popup_container, mContainer.getDragLayer(), false);
        container.populateAndShowRows((BubbleTextView) v, deepShortcutCount,
                systemShortcuts);
        container.requestFocus();

        if (!FeatureFlags.SECONDARY_DRAG_N_DROP_TO_PIN.get() || !mContainer.isAppDrawerShown()) {
            return true;
        }

        DragOptions options = new DragOptions();
        DeviceProfile grid = mContainer.getDeviceProfile();
        options.intrinsicIconScaleFactor = (float) grid.getAllAppsProfile().getIconSizePx()
                / grid.iconSizePx;
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
                                mContainer.beginDragShared(v, mContainer.getAppsView(), options));
                    }
                }

                @Override
                public void onPreDragEnd(DropTarget.DragObject dragObject, boolean dragStarted) {
                    mDragView = null;
                }
            };
        }
        mContainer.beginDragShared(v, mContainer.getAppsView(), options);
        return true;
    }
}