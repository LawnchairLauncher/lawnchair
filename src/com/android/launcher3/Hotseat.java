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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.support.v4.graphics.ColorUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dynamicui.ExtractedColors;
import com.android.launcher3.logging.UserEventDispatcher;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;
import com.android.launcher3.util.Themes;

public class Hotseat extends FrameLayout
        implements UserEventDispatcher.LogContainerProvider {

    private CellLayout mContent;

    private Launcher mLauncher;

    @ViewDebug.ExportedProperty(category = "launcher")
    private final boolean mHasVerticalHotseat;

    @ViewDebug.ExportedProperty(category = "launcher")
    private int mBackgroundColor;
    @ViewDebug.ExportedProperty(category = "launcher")
    private ColorDrawable mBackground;
    private ValueAnimator mBackgroundColorAnimator;

    public Hotseat(Context context) {
        this(context, null);
    }

    public Hotseat(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Hotseat(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mLauncher = Launcher.getLauncher(context);
        mHasVerticalHotseat = mLauncher.getDeviceProfile().isVerticalBarLayout();
        mBackgroundColor = ColorUtils.setAlphaComponent(
                Themes.getAttrColor(context, android.R.attr.colorPrimary), 0);
        mBackground = new ColorDrawable(mBackgroundColor);
        if (!FeatureFlags.LAUNCHER3_GRADIENT_ALL_APPS) {
            setBackground(mBackground);
        }
    }

    public CellLayout getLayout() {
        return mContent;
    }

    /**
     * Returns whether there are other icons than the all apps button in the hotseat.
     */
    public boolean hasIcons() {
        return mContent.getShortcutsAndWidgets().getChildCount() > 1;
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
        DeviceProfile grid = mLauncher.getDeviceProfile();
        mContent = (CellLayout) findViewById(R.id.layout);
        if (grid.isVerticalBarLayout()) {
            mContent.setGridSize(1, grid.inv.numHotseatIcons);
        } else {
            mContent.setGridSize(grid.inv.numHotseatIcons, 1);
        }

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
                mLauncher.setAllAppsButton(allAppsButton);
                allAppsButton.setOnClickListener(mLauncher);
                allAppsButton.setOnLongClickListener(mLauncher);
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

    public void updateColor(ExtractedColors extractedColors, boolean animate) {
        if (FeatureFlags.LAUNCHER3_GRADIENT_ALL_APPS) {
            // not hotseat visible
            return;
        }
        if (!mHasVerticalHotseat) {
            int color = extractedColors.getColor(ExtractedColors.HOTSEAT_INDEX);
            if (mBackgroundColorAnimator != null) {
                mBackgroundColorAnimator.cancel();
            }
            if (!animate) {
                setBackgroundColor(color);
            } else {
                mBackgroundColorAnimator = ValueAnimator.ofInt(mBackgroundColor, color);
                mBackgroundColorAnimator.setEvaluator(new ArgbEvaluator());
                mBackgroundColorAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        mBackground.setColor((Integer) animation.getAnimatedValue());
                    }
                });
                mBackgroundColorAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mBackgroundColorAnimator = null;
                    }
                });
                mBackgroundColorAnimator.start();
            }
            mBackgroundColor = color;
        }
    }

    public void setBackgroundTransparent(boolean enable) {
        if (enable) {
            mBackground.setAlpha(0);
        } else {
            mBackground.setAlpha(255);
        }
    }

    public int getBackgroundDrawableColor() {
        return mBackgroundColor;
    }
}
