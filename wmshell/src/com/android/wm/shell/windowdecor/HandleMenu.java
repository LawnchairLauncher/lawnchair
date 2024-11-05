/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.windowdecor;

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_UP;

import static com.android.wm.shell.common.split.SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT;
import static com.android.wm.shell.common.split.SplitScreenConstants.SPLIT_POSITION_TOP_OR_LEFT;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.SurfaceControl;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.window.SurfaceSyncGroup;

import androidx.annotation.VisibleForTesting;

import com.android.window.flags.Flags;
import com.android.wm.shell.R;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.splitscreen.SplitScreenController;
import com.android.wm.shell.windowdecor.additionalviewcontainer.AdditionalSystemViewContainer;
import com.android.wm.shell.windowdecor.additionalviewcontainer.AdditionalViewContainer;

/**
 * Handle menu opened when the appropriate button is clicked on.
 *
 * Displays up to 3 pills that show the following:
 * App Info: App name, app icon, and collapse button to close the menu.
 * Windowing Options(Proto 2 only): Buttons to change windowing modes.
 * Additional Options: Miscellaneous functions including screenshot and closing task.
 */
class HandleMenu {
    private static final String TAG = "HandleMenu";
    private static final boolean SHOULD_SHOW_MORE_ACTIONS_PILL = false;
    private final Context mContext;
    private final DesktopModeWindowDecoration mParentDecor;
    @VisibleForTesting
    AdditionalViewContainer mHandleMenuViewContainer;
    // Position of the handle menu used for laying out the handle view.
    @VisibleForTesting
    final PointF mHandleMenuPosition = new PointF();
    // With the introduction of {@link AdditionalSystemViewContainer}, {@link mHandleMenuPosition}
    // may be in a different coordinate space than the input coordinates. Therefore, we still care
    // about the menu's coordinates relative to the display as a whole, so we need to maintain
    // those as well.
    final Point mGlobalMenuPosition = new Point();
    private final boolean mShouldShowWindowingPill;
    private final Bitmap mAppIconBitmap;
    private final CharSequence mAppName;
    private final View.OnClickListener mOnClickListener;
    private final View.OnTouchListener mOnTouchListener;
    private final RunningTaskInfo mTaskInfo;
    private final DisplayController mDisplayController;
    private final SplitScreenController mSplitScreenController;
    private final int mLayoutResId;
    private int mMarginMenuTop;
    private int mMarginMenuStart;
    private int mMenuHeight;
    private int mMenuWidth;
    private final int mCaptionHeight;
    private HandleMenuAnimator mHandleMenuAnimator;


    HandleMenu(DesktopModeWindowDecoration parentDecor, int layoutResId,
            View.OnClickListener onClickListener, View.OnTouchListener onTouchListener,
            Bitmap appIcon, CharSequence appName, DisplayController displayController,
            SplitScreenController splitScreenController, boolean shouldShowWindowingPill,
            int captionHeight) {
        mParentDecor = parentDecor;
        mContext = mParentDecor.mDecorWindowContext;
        mTaskInfo = mParentDecor.mTaskInfo;
        mDisplayController = displayController;
        mSplitScreenController = splitScreenController;
        mLayoutResId = layoutResId;
        mOnClickListener = onClickListener;
        mOnTouchListener = onTouchListener;
        mAppIconBitmap = appIcon;
        mAppName = appName;
        mShouldShowWindowingPill = shouldShowWindowingPill;
        mCaptionHeight = captionHeight;
        loadHandleMenuDimensions();
        updateHandleMenuPillPositions();
    }

    void show() {
        final SurfaceSyncGroup ssg = new SurfaceSyncGroup(TAG);
        SurfaceControl.Transaction t = new SurfaceControl.Transaction();

        createHandleMenuViewContainer(t, ssg);
        ssg.addTransaction(t);
        ssg.markSyncReady();
        setupHandleMenu();
        animateHandleMenu();
    }

    private void createHandleMenuViewContainer(SurfaceControl.Transaction t,
            SurfaceSyncGroup ssg) {
        final int x = (int) mHandleMenuPosition.x;
        final int y = (int) mHandleMenuPosition.y;
        if (!mTaskInfo.isFreeform() && Flags.enableAdditionalWindowsAboveStatusBar()) {
            mHandleMenuViewContainer = new AdditionalSystemViewContainer(mContext,
                    R.layout.desktop_mode_window_decor_handle_menu, mTaskInfo.taskId,
                    x, y, mMenuWidth, mMenuHeight);
        } else {
            mHandleMenuViewContainer = mParentDecor.addWindow(
                    R.layout.desktop_mode_window_decor_handle_menu, "Handle Menu",
                    t, ssg, x, y, mMenuWidth, mMenuHeight);
        }
        final View handleMenuView = mHandleMenuViewContainer.getView();
        mHandleMenuAnimator = new HandleMenuAnimator(handleMenuView, mMenuWidth, mCaptionHeight);
    }

    /**
     * Animates the appearance of the handle menu and its three pills.
     */
    private void animateHandleMenu() {
        if (mTaskInfo.getWindowingMode() == WINDOWING_MODE_FULLSCREEN
                || mTaskInfo.getWindowingMode() == WINDOWING_MODE_MULTI_WINDOW) {
            mHandleMenuAnimator.animateCaptionHandleExpandToOpen();
        } else {
            mHandleMenuAnimator.animateOpen();
        }
    }

    /**
     * Set up all three pills of the handle menu: app info pill, windowing pill, & more actions
     * pill.
     */
    private void setupHandleMenu() {
        final View handleMenu = mHandleMenuViewContainer.getView();
        handleMenu.setOnTouchListener(mOnTouchListener);
        setupAppInfoPill(handleMenu);
        if (mShouldShowWindowingPill) {
            setupWindowingPill(handleMenu);
        }
        setupMoreActionsPill(handleMenu);
    }

    /**
     * Set up interactive elements of handle menu's app info pill.
     */
    private void setupAppInfoPill(View handleMenu) {
        final HandleMenuImageButton collapseBtn =
                handleMenu.findViewById(R.id.collapse_menu_button);
        final ImageView appIcon = handleMenu.findViewById(R.id.application_icon);
        final TextView appName = handleMenu.findViewById(R.id.application_name);
        collapseBtn.setOnClickListener(mOnClickListener);
        collapseBtn.setTaskInfo(mTaskInfo);
        appIcon.setImageBitmap(mAppIconBitmap);
        appName.setText(mAppName);
    }

    /**
     * Set up interactive elements and color of handle menu's windowing pill.
     */
    private void setupWindowingPill(View handleMenu) {
        final ImageButton fullscreenBtn = handleMenu.findViewById(
                R.id.fullscreen_button);
        final ImageButton splitscreenBtn = handleMenu.findViewById(
                R.id.split_screen_button);
        final ImageButton floatingBtn = handleMenu.findViewById(R.id.floating_button);
        // TODO: Remove once implemented.
        floatingBtn.setVisibility(View.GONE);

        final ImageButton desktopBtn = handleMenu.findViewById(R.id.desktop_button);
        fullscreenBtn.setOnClickListener(mOnClickListener);
        splitscreenBtn.setOnClickListener(mOnClickListener);
        floatingBtn.setOnClickListener(mOnClickListener);
        desktopBtn.setOnClickListener(mOnClickListener);
        // The button corresponding to the windowing mode that the task is currently in uses a
        // different color than the others.
        final ColorStateList[] iconColors = getWindowingIconColor();
        final ColorStateList inActiveColorStateList = iconColors[0];
        final ColorStateList activeColorStateList = iconColors[1];
        final int windowingMode = mTaskInfo.getWindowingMode();
        fullscreenBtn.setImageTintList(windowingMode == WINDOWING_MODE_FULLSCREEN
                ? activeColorStateList : inActiveColorStateList);
        splitscreenBtn.setImageTintList(windowingMode == WINDOWING_MODE_MULTI_WINDOW
                ? activeColorStateList : inActiveColorStateList);
        floatingBtn.setImageTintList(windowingMode == WINDOWING_MODE_PINNED
                ? activeColorStateList : inActiveColorStateList);
        desktopBtn.setImageTintList(windowingMode == WINDOWING_MODE_FREEFORM
                ? activeColorStateList : inActiveColorStateList);
    }

    /**
     * Set up interactive elements & height of handle menu's more actions pill
     */
    private void setupMoreActionsPill(View handleMenu) {
        if (!SHOULD_SHOW_MORE_ACTIONS_PILL) {
            handleMenu.findViewById(R.id.more_actions_pill).setVisibility(View.GONE);
        }
    }

    /**
     * Returns array of windowing icon color based on current UI theme. First element of the
     * array is for inactive icons and the second is for active icons.
     */
    private ColorStateList[] getWindowingIconColor() {
        final int mode = mContext.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        final boolean isNightMode = (mode == Configuration.UI_MODE_NIGHT_YES);
        final TypedArray typedArray = mContext.obtainStyledAttributes(new int[]{
                com.android.internal.R.attr.materialColorOnSurface,
                com.android.internal.R.attr.materialColorPrimary});
        final int inActiveColor = typedArray.getColor(0, isNightMode ? Color.WHITE : Color.BLACK);
        final int activeColor = typedArray.getColor(1, isNightMode ? Color.WHITE : Color.BLACK);
        typedArray.recycle();
        return new ColorStateList[]{ColorStateList.valueOf(inActiveColor),
                ColorStateList.valueOf(activeColor)};
    }

    /**
     * Updates handle menu's position variables to reflect its next position.
     */
    private void updateHandleMenuPillPositions() {
        int menuX;
        final int menuY;
        final Rect taskBounds = mTaskInfo.getConfiguration().windowConfiguration.getBounds();
        updateGlobalMenuPosition(taskBounds);
        if (mLayoutResId == R.layout.desktop_mode_app_header) {
            // Align the handle menu to the left side of the caption.
            menuX = mMarginMenuStart;
            menuY = mMarginMenuTop;
        } else {
            if (Flags.enableAdditionalWindowsAboveStatusBar()) {
                // In a focused decor, we use global coordinates for handle menu. Therefore we
                // need to account for other factors like split stage and menu/handle width to
                // center the menu.
                final DisplayLayout layout = mDisplayController
                        .getDisplayLayout(mTaskInfo.displayId);
                menuX = mGlobalMenuPosition.x + ((mMenuWidth - layout.width()) / 2);
                menuY = mGlobalMenuPosition.y + ((mMenuHeight - layout.height()) / 2);
            } else {
                menuX = (taskBounds.width() / 2) - (mMenuWidth / 2);
                menuY = mMarginMenuTop;
            }
        }
        // Handle Menu position setup.
        mHandleMenuPosition.set(menuX, menuY);
    }

    private void updateGlobalMenuPosition(Rect taskBounds) {
        if (mTaskInfo.isFreeform()) {
            mGlobalMenuPosition.set(taskBounds.left + mMarginMenuStart,
                    taskBounds.top + mMarginMenuTop);
        } else if (mTaskInfo.getWindowingMode() == WINDOWING_MODE_FULLSCREEN) {
            mGlobalMenuPosition.set(
                    (taskBounds.width() / 2) - (mMenuWidth / 2) + mMarginMenuStart,
                    mMarginMenuTop
            );
        } else if (mTaskInfo.getWindowingMode() == WINDOWING_MODE_MULTI_WINDOW) {
            final int splitPosition = mSplitScreenController.getSplitPosition(mTaskInfo.taskId);
            final Rect leftOrTopStageBounds = new Rect();
            final Rect rightOrBottomStageBounds = new Rect();
            mSplitScreenController.getStageBounds(leftOrTopStageBounds,
                    rightOrBottomStageBounds);
            // TODO(b/343561161): This needs to be calculated differently if the task is in
            //  top/bottom split.
            if (splitPosition == SPLIT_POSITION_BOTTOM_OR_RIGHT) {
                mGlobalMenuPosition.set(leftOrTopStageBounds.width()
                                + (rightOrBottomStageBounds.width() / 2)
                                - (mMenuWidth / 2) + mMarginMenuStart,
                        mMarginMenuTop);
            } else if (splitPosition == SPLIT_POSITION_TOP_OR_LEFT) {
                mGlobalMenuPosition.set((leftOrTopStageBounds.width() / 2)
                                - (mMenuWidth / 2) + mMarginMenuStart,
                        mMarginMenuTop);
            }
        }
    }

    /**
     * Update pill layout, in case task changes have caused positioning to change.
     */
    void relayout(SurfaceControl.Transaction t) {
        if (mHandleMenuViewContainer != null) {
            updateHandleMenuPillPositions();
            mHandleMenuViewContainer.setPosition(t, mHandleMenuPosition.x, mHandleMenuPosition.y);
        }
    }

    /**
     * Check a passed MotionEvent if a click or hover has occurred on any button on this caption
     * Note this should only be called when a regular onClick/onHover is not possible
     * (i.e. the button was clicked through status bar layer)
     *
     * @param ev the MotionEvent to compare against.
     */
    void checkMotionEvent(MotionEvent ev) {
        // If the menu view is above status bar, we can let the views handle input directly.
        if (isViewAboveStatusBar()) return;
        final View handleMenu = mHandleMenuViewContainer.getView();
        final HandleMenuImageButton collapse = handleMenu.findViewById(R.id.collapse_menu_button);
        final PointF inputPoint = translateInputToLocalSpace(ev);
        final boolean inputInCollapseButton = pointInView(collapse, inputPoint.x, inputPoint.y);
        final int action = ev.getActionMasked();
        collapse.setHovered(inputInCollapseButton && action != ACTION_UP);
        collapse.setPressed(inputInCollapseButton && action == ACTION_DOWN);
        if (action == ACTION_UP && inputInCollapseButton) {
            collapse.performClick();
        }
    }

    private boolean isViewAboveStatusBar() {
        return Flags.enableAdditionalWindowsAboveStatusBar()
                && !mTaskInfo.isFreeform();
    }

    // Translate the input point from display coordinates to the same space as the handle menu.
    private PointF translateInputToLocalSpace(MotionEvent ev) {
        return new PointF(ev.getX() - mHandleMenuPosition.x,
                ev.getY() - mHandleMenuPosition.y);
    }

    /**
     * A valid menu input is one of the following:
     * An input that happens in the menu views.
     * Any input before the views have been laid out.
     *
     * @param inputPoint the input to compare against.
     */
    boolean isValidMenuInput(PointF inputPoint) {
        if (!viewsLaidOut()) return true;
        if (!isViewAboveStatusBar()) {
            return pointInView(
                    mHandleMenuViewContainer.getView(),
                    inputPoint.x - mHandleMenuPosition.x,
                    inputPoint.y - mHandleMenuPosition.y);
        } else {
            // Handle menu exists in a different coordinate space when added to WindowManager.
            // Therefore we must compare the provided input coordinates to global menu coordinates.
            // This includes factoring for split stage as input coordinates are relative to split
            // stage position, not relative to the display as a whole.
            PointF inputRelativeToMenu = new PointF(
                    inputPoint.x - mGlobalMenuPosition.x,
                    inputPoint.y - mGlobalMenuPosition.y
            );
            if (mSplitScreenController.getSplitPosition(mTaskInfo.taskId)
                    == SPLIT_POSITION_BOTTOM_OR_RIGHT) {
                // TODO(b/343561161): This also needs to be calculated differently if
                //  the task is in top/bottom split.
                Rect leftStageBounds = new Rect();
                mSplitScreenController.getStageBounds(leftStageBounds, new Rect());
                inputRelativeToMenu.x += leftStageBounds.width();
            }
            return pointInView(
                    mHandleMenuViewContainer.getView(),
                    inputRelativeToMenu.x,
                    inputRelativeToMenu.y);
        }
    }

    private boolean pointInView(View v, float x, float y) {
        return v != null && v.getLeft() <= x && v.getRight() >= x
                && v.getTop() <= y && v.getBottom() >= y;
    }

    /**
     * Check if the views for handle menu can be seen.
     */
    private boolean viewsLaidOut() {
        return mHandleMenuViewContainer.getView().isLaidOut();
    }

    private void loadHandleMenuDimensions() {
        final Resources resources = mContext.getResources();
        mMenuWidth = loadDimensionPixelSize(resources,
                R.dimen.desktop_mode_handle_menu_width);
        mMenuHeight = getHandleMenuHeight(resources);
        mMarginMenuTop = loadDimensionPixelSize(resources,
                R.dimen.desktop_mode_handle_menu_margin_top);
        mMarginMenuStart = loadDimensionPixelSize(resources,
                R.dimen.desktop_mode_handle_menu_margin_start);
    }

    /**
     * Determines handle menu height based on if windowing pill should be shown.
     */
    private int getHandleMenuHeight(Resources resources) {
        int menuHeight = loadDimensionPixelSize(resources, R.dimen.desktop_mode_handle_menu_height);
        if (!mShouldShowWindowingPill) {
            menuHeight -= loadDimensionPixelSize(resources,
                    R.dimen.desktop_mode_handle_menu_windowing_pill_height);
        }
        if (!SHOULD_SHOW_MORE_ACTIONS_PILL) {
            menuHeight -= loadDimensionPixelSize(resources,
                    R.dimen.desktop_mode_handle_menu_more_actions_pill_height);
        }
        return menuHeight;
    }

    private int loadDimensionPixelSize(Resources resources, int resourceId) {
        if (resourceId == Resources.ID_NULL) {
            return 0;
        }
        return resources.getDimensionPixelSize(resourceId);
    }

    void close() {
        final Runnable after = () -> {
            mHandleMenuViewContainer.releaseView();
            mHandleMenuViewContainer = null;
        };
        if (mTaskInfo.getWindowingMode() == WINDOWING_MODE_FULLSCREEN
                || mTaskInfo.getWindowingMode() == WINDOWING_MODE_MULTI_WINDOW) {
            mHandleMenuAnimator.animateCollapseIntoHandleClose(after);
        } else {
            mHandleMenuAnimator.animateClose(after);
        }
    }

    static final class Builder {
        private final DesktopModeWindowDecoration mParent;
        private CharSequence mName;
        private Bitmap mAppIcon;
        private View.OnClickListener mOnClickListener;
        private View.OnTouchListener mOnTouchListener;
        private int mLayoutId;
        private boolean mShowWindowingPill;
        private int mCaptionHeight;
        private DisplayController mDisplayController;
        private SplitScreenController mSplitScreenController;

        Builder(@NonNull DesktopModeWindowDecoration parent) {
            mParent = parent;
        }

        Builder setAppName(@Nullable CharSequence name) {
            mName = name;
            return this;
        }

        Builder setAppIcon(@Nullable Bitmap appIcon) {
            mAppIcon = appIcon;
            return this;
        }

        Builder setOnClickListener(@Nullable View.OnClickListener onClickListener) {
            mOnClickListener = onClickListener;
            return this;
        }

        Builder setOnTouchListener(@Nullable View.OnTouchListener onTouchListener) {
            mOnTouchListener = onTouchListener;
            return this;
        }

        Builder setLayoutId(int layoutId) {
            mLayoutId = layoutId;
            return this;
        }

        Builder setWindowingButtonsVisible(boolean windowingButtonsVisible) {
            mShowWindowingPill = windowingButtonsVisible;
            return this;
        }

        Builder setCaptionHeight(int captionHeight) {
            mCaptionHeight = captionHeight;
            return this;
        }

        Builder setDisplayController(DisplayController displayController) {
            mDisplayController = displayController;
            return this;
        }

        Builder setSplitScreenController(SplitScreenController splitScreenController) {
            mSplitScreenController = splitScreenController;
            return this;
        }

        HandleMenu build() {
            return new HandleMenu(mParent, mLayoutId, mOnClickListener,
                    mOnTouchListener, mAppIcon, mName, mDisplayController, mSplitScreenController,
                    mShowWindowingPill, mCaptionHeight);
        }
    }
}
