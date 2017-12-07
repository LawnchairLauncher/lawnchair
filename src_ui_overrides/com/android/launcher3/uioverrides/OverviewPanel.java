/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.uioverrides;

import static com.android.launcher3.WorkspaceStateTransitionAnimation.NO_ANIM_PROPERTY_SETTER;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherStateManager;
import com.android.launcher3.LauncherStateManager.AnimationConfig;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.WorkspaceStateTransitionAnimation.AnimatedPropertySetter;
import com.android.launcher3.WorkspaceStateTransitionAnimation.PropertySetter;
import com.android.launcher3.anim.AnimationLayerSet;
import com.android.launcher3.anim.AnimatorSetBuilder;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action;
import com.android.launcher3.userevent.nano.LauncherLogProto.ControlType;
import com.android.launcher3.widget.WidgetsFullSheet;

public class OverviewPanel extends LinearLayout implements Insettable, View.OnClickListener,
        View.OnLongClickListener, LauncherStateManager.StateHandler {

    // Out of 100, the percent of space the overview bar should try and take vertically.
    private static final float OVERVIEW_ICON_ZONE_RATIO = 0.22f;

    private final Launcher mLauncher;

    public OverviewPanel(Context context) {
        this(context, null);
    }

    public OverviewPanel(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public OverviewPanel(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mLauncher = Launcher.getLauncher(context);
        setAlpha(0);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        int visibleChildCount = 3;
        // Attach buttons.
        attachListeners(findViewById(R.id.wallpaper_button));
        attachListeners(findViewById(R.id.widget_button));

        View settingsButton = findViewById(R.id.settings_button);
        if (mLauncher.hasSettings()) {
            attachListeners(settingsButton);
        } else {
            settingsButton.setVisibility(GONE);
            visibleChildCount--;
        }

        // Init UI
        Resources res = getResources();
        int itemWidthPx =
                res.getDimensionPixelSize(R.dimen.dynamic_grid_overview_bar_item_width);
        int spacerWidthPx =
                res.getDimensionPixelSize(R.dimen.dynamic_grid_overview_bar_spacer_width);

        int totalItemWidth = visibleChildCount * itemWidthPx;
        int maxWidth = totalItemWidth + (visibleChildCount - 1) * spacerWidthPx;

        getLayoutParams().width = Math.min(mLauncher.getDeviceProfile().availableWidthPx, maxWidth);
        getLayoutParams().height = getButtonBarHeight(mLauncher);
    }

    private void attachListeners(View view) {
        view.setOnClickListener(this);
        view.setOnLongClickListener(this);
    }

    @Override
    public void setInsets(Rect insets) {
        ((FrameLayout.LayoutParams) getLayoutParams()).bottomMargin = insets.bottom;
    }

    @Override
    public void onClick(View view) {
        handleViewClick(view, Action.Touch.TAP);
    }

    @Override
    public boolean onLongClick(View view) {
        return handleViewClick(view, Action.Touch.LONGPRESS);
    }

    private boolean handleViewClick(View view, int action) {
        if (mLauncher.getWorkspace().isSwitchingState()) {
            return false;
        }

        final int controlType;
        if (view.getId() == R.id.wallpaper_button) {
            mLauncher.onClickWallpaperPicker(view);
            controlType = ControlType.WALLPAPER_BUTTON;
        } else if (view.getId() == R.id.widget_button) {
            onClickAddWidgetButton();
            controlType = ControlType.WIDGETS_BUTTON;
        } else if (view.getId() == R.id.settings_button) {
            onClickSettingsButton(view);
            controlType = ControlType.SETTINGS_BUTTON;
        } else {
            return false;
        }

        mLauncher.getUserEventDispatcher().logActionOnControl(action, controlType);
        return true;
    }

    /**
     * Event handler for the (Add) Widgets button that appears after a long press
     * on the home screen.
     */
    public void onClickAddWidgetButton() {
        if (getContext().getPackageManager().isSafeMode()) {
            Toast.makeText(mLauncher, R.string.safemode_widget_error, Toast.LENGTH_SHORT).show();
        } else {
            WidgetsFullSheet.show(mLauncher, true /* animated */);
        }
    }

    /**
     * Event handler for a click on the settings button that appears after a long press
     * on the home screen.
     */
    public void onClickSettingsButton(View v) {
        Intent intent = new Intent(Intent.ACTION_APPLICATION_PREFERENCES)
                .setPackage(getContext().getPackageName());
        intent.setSourceBounds(mLauncher.getViewBounds(v));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(intent, mLauncher.getActivityLaunchOptions(v));
    }

    @Override
    public void setState(LauncherState state) {
        setState(state, NO_ANIM_PROPERTY_SETTER);
    }

    @Override
    public void setStateWithAnimation(LauncherState toState, AnimationLayerSet layerViews,
            AnimatorSetBuilder builder, AnimationConfig config) {
        setState(toState, new AnimatedPropertySetter(config.duration, layerViews, builder));
    }

    private void setState(LauncherState state, PropertySetter setter) {
        boolean isOverview = state == LauncherState.OVERVIEW;
        float finalHotseatAlpha = isOverview ? 0 : 1;

        setter.setViewAlpha(null, this, isOverview ? 1 : 0);
        setter.setViewAlpha(
                mLauncher.getWorkspace().createHotseatAlphaAnimator(finalHotseatAlpha),
                mLauncher.getHotseat(), finalHotseatAlpha);
    }

    public static int getButtonBarHeight(Launcher launcher) {
        int zoneHeight = (int) (OVERVIEW_ICON_ZONE_RATIO *
                launcher.getDeviceProfile().availableHeightPx);
        Resources res = launcher.getResources();
        int overviewModeMinIconZoneHeightPx =
                res.getDimensionPixelSize(R.dimen.dynamic_grid_overview_min_icon_zone_height);
        int overviewModeMaxIconZoneHeightPx =
                res.getDimensionPixelSize(R.dimen.dynamic_grid_overview_max_icon_zone_height);
        return Utilities.boundToRange(zoneHeight,
                overviewModeMinIconZoneHeightPx,
                overviewModeMaxIconZoneHeightPx);
    }
}
