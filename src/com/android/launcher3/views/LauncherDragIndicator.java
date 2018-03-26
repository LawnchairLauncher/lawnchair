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
package com.android.launcher3.views;

import static com.android.launcher3.LauncherState.ALL_APPS;

import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action;
import com.android.launcher3.userevent.nano.LauncherLogProto.ControlType;

public class LauncherDragIndicator extends ImageView implements Insettable, OnClickListener {

    private static final int WALLPAPERS = R.string.wallpaper_button_text;
    private static final int WIDGETS = R.string.widget_button_text;
    private static final int SETTINGS = R.string.settings_button_text;

    protected final Launcher mLauncher;

    public LauncherDragIndicator(Context context) {
        this(context, null);
    }

    public LauncherDragIndicator(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LauncherDragIndicator(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mLauncher = Launcher.getLauncher(context);
        setOnClickListener(this);
    }

    @Override
    public void setInsets(Rect insets) {
        DeviceProfile grid = mLauncher.getDeviceProfile();
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();

        if (grid.isVerticalBarLayout()) {
            if (grid.isSeascape()) {
                lp.leftMargin = grid.hotseatBarSidePaddingPx;
                lp.rightMargin = insets.right;
                lp.gravity =  Gravity.RIGHT | Gravity.BOTTOM;
            } else {
                lp.leftMargin = insets.left;
                lp.rightMargin = grid.hotseatBarSidePaddingPx;
                lp.gravity = Gravity.LEFT | Gravity.BOTTOM;
            }
            lp.bottomMargin = grid.workspacePadding.bottom;
            setImageResource(R.drawable.all_apps_handle_landscape);
        } else {
            lp.leftMargin = lp.rightMargin = 0;
            lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
            lp.bottomMargin = getPortraitBottomMargin(grid, insets);
            setImageResource(R.drawable.ic_drag_indicator);
        }

        lp.width = lp.height = grid.pageIndicatorSizePx;
        setLayoutParams(lp);
    }

    protected int getPortraitBottomMargin(DeviceProfile grid, Rect insets) {
        return grid.hotseatBarSizePx + insets.bottom - grid.pageIndicatorSizePx;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        initCustomActions(info);
    }

    protected void initCustomActions(AccessibilityNodeInfo info) {
        Context context = getContext();
        if (Utilities.isWallpaperAllowed(context)) {
            info.addAction(new AccessibilityAction(WALLPAPERS, context.getText(WALLPAPERS)));
        }
        info.addAction(new AccessibilityAction(WIDGETS, context.getText(WIDGETS)));
        info.addAction(new AccessibilityAction(SETTINGS, context.getText(SETTINGS)));
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        Launcher launcher = Launcher.getLauncher(getContext());
        if (action == WALLPAPERS) {
            launcher.onClickWallpaperPicker(this);
            return true;
        } else if (action == WIDGETS) {
            return OptionsPopupView.onWidgetsClicked(launcher);
        } else if (action == SETTINGS) {
            OptionsPopupView.startSettings(launcher);
            return true;
        }
        return super.performAccessibilityAction(action, arguments);
    }

    @Override
    public void onClick(View view) {
        if (!mLauncher.isInState(ALL_APPS)) {
            mLauncher.getUserEventDispatcher().logActionOnControl(
                    Action.Touch.TAP, ControlType.ALL_APPS_BUTTON);
            mLauncher.getStateManager().goToState(ALL_APPS);
        }
    }
}
