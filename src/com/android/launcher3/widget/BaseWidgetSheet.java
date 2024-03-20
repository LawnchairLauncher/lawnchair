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
package com.android.launcher3.widget;

import static com.android.app.animation.Interpolators.EMPHASIZED;
import static com.android.launcher3.Flags.enableCategorizedWidgetSuggestions;
import static com.android.launcher3.Flags.enableUnfoldedTwoPanePicker;
import static com.android.launcher3.Flags.enableWidgetTapToAdd;
import static com.android.launcher3.LauncherPrefs.WIDGETS_EDUCATION_TIP_SEEN;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.WindowInsets;
import android.view.animation.Interpolator;

import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.core.view.ViewCompat;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener;
import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.PendingAddItemInfo;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.popup.PopupDataProvider;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.util.SystemUiController;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.window.WindowManagerProxy;
import com.android.launcher3.views.AbstractSlideInView;
import com.android.launcher3.views.ArrowTipView;

/**
 * Base class for various widgets popup
 */
public abstract class BaseWidgetSheet extends AbstractSlideInView<BaseActivity>
        implements OnClickListener, OnLongClickListener,
        PopupDataProvider.PopupDataChangeListener, Insettable, OnDeviceProfileChangeListener {
    /** The default number of cells that can fit horizontally in a widget sheet. */
    public static final int DEFAULT_MAX_HORIZONTAL_SPANS = 4;

    protected final Rect mInsets = new Rect();

    @Px
    protected int mContentHorizontalMargin;
    @Px
    protected int mWidgetCellHorizontalPadding;

    protected int mNavBarScrimHeight;
    private final Paint mNavBarScrimPaint;

    private boolean mDisableNavBarScrim = false;

    @Nullable private WidgetCell mWidgetCellWithAddButton = null;

    public BaseWidgetSheet(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContentHorizontalMargin = getWidgetListHorizontalMargin();
        mWidgetCellHorizontalPadding = getResources().getDimensionPixelSize(
                R.dimen.widget_cell_horizontal_padding);
        mNavBarScrimPaint = new Paint();
        mNavBarScrimPaint.setColor(Themes.getNavBarScrimColor(mActivityContext));
    }

    /**
     * Returns the margins to be applied to the left and right of the widget apps list.
     */
    protected int getWidgetListHorizontalMargin() {
        return getResources().getDimensionPixelSize(
                R.dimen.widget_list_horizontal_margin);
    }

    protected int getScrimColor(Context context) {
        return context.getResources().getColor(R.color.widgets_picker_scrim);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        WindowInsets windowInsets = WindowManagerProxy.INSTANCE.get(getContext())
                .normalizeWindowInsets(getContext(), getRootWindowInsets(), new Rect());
        mNavBarScrimHeight = getNavBarScrimHeight(windowInsets);
        mActivityContext.getPopupDataProvider().setChangeListener(this);
        mActivityContext.addOnDeviceProfileChangeListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mActivityContext.getPopupDataProvider().setChangeListener(null);
        mActivityContext.removeOnDeviceProfileChangeListener(this);
    }

    @Override
    public void onDeviceProfileChanged(DeviceProfile dp) {
        int navBarScrimColor = Themes.getNavBarScrimColor(mActivityContext);
        if (mNavBarScrimPaint.getColor() != navBarScrimColor) {
            mNavBarScrimPaint.setColor(navBarScrimColor);
            invalidate();
        }
        setupNavBarColor();
    }

    @Override
    public final void onClick(View v) {
        WidgetCell wc;
        if (v instanceof WidgetCell view) {
            wc = view;
        }  else if (v.getParent() instanceof WidgetCell parent) {
            wc = parent;
        } else {
            return;
        }

        if (enableWidgetTapToAdd()) {
            if (mWidgetCellWithAddButton != null) {
                // If there is a add button currently showing, hide it.
                mWidgetCellWithAddButton.hideAddButton(/* animate= */ true);
            }

            if (mWidgetCellWithAddButton != wc) {
                // If click is on a cell not showing an add button, show it now.
                final PendingAddItemInfo info = (PendingAddItemInfo) wc.getTag();
                if (mActivityContext instanceof Launcher) {
                    wc.showAddButton((view) -> addWidget(info));
                } else {
                    wc.showAddButton((view) -> mActivityContext.getItemOnClickListener()
                            .onClick(wc));
                }
            }

            mWidgetCellWithAddButton = mWidgetCellWithAddButton != wc ? wc : null;
        } else {
            mActivityContext.getItemOnClickListener().onClick(wc);
        }
    }

    /**
     * Click handler for tap to add button.
     */
    public void addWidget(PendingAddItemInfo info) {
        handleClose(true);
        Launcher.getLauncher(mActivityContext).getAccessibilityDelegate()
                .addToWorkspace(info, /*accessibility=*/ false, /*finishCallback=*/ null);
        mActivityContext.getStatsLogManager().logger().withItemInfo(info).log(
                StatsLogManager.LauncherEvent.LAUNCHER_WIDGET_ADD_BUTTON_TAP);
    }

    @Override
    public boolean onLongClick(View v) {
        TestLogging.recordEvent(TestProtocol.SEQUENCE_MAIN, "Widgets.onLongClick");
        v.cancelLongPress();

        boolean result;
        if (v instanceof WidgetCell) {
            result = mActivityContext.getAllAppsItemLongClickListener().onLongClick(v);
        } else if (v.getParent() instanceof WidgetCell wc) {
            result = mActivityContext.getAllAppsItemLongClickListener().onLongClick(wc);
        } else {
            return true;
        }
        if (result) {
            close(true);
        }
        return result;
    }

    @Override
    public void setInsets(Rect insets) {
        mInsets.set(insets);
        @Px int contentHorizontalMargin = getWidgetListHorizontalMargin();
        if (contentHorizontalMargin != mContentHorizontalMargin) {
            onContentHorizontalMarginChanged(contentHorizontalMargin);
            mContentHorizontalMargin = contentHorizontalMargin;
        }
    }

    /** Enables or disables the sheet's nav bar scrim. */
    public void disableNavBarScrim(boolean disable) {
        mDisableNavBarScrim = disable;
    }

    private int getNavBarScrimHeight(WindowInsets insets) {
        if (mDisableNavBarScrim) {
            return 0;
        } else {
            return insets.getTappableElementInsets().bottom;
        }
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        mNavBarScrimHeight = getNavBarScrimHeight(insets);
        return super.onApplyWindowInsets(insets);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        if (mNavBarScrimHeight > 0) {
            canvas.drawRect(0, getHeight() - mNavBarScrimHeight, getWidth(), getHeight(),
                    mNavBarScrimPaint);
        }
    }

    /** Called when the horizontal margin of the content view has changed. */
    protected abstract void onContentHorizontalMarginChanged(int contentHorizontalMarginInPx);

    /**
     * Measures the dimension of this view and its children by taking system insets, navigation bar,
     * status bar, into account.
     */
    protected void doMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        DeviceProfile deviceProfile = mActivityContext.getDeviceProfile();
        int widthUsed;
        if (deviceProfile.isTablet) {
            widthUsed = Math.max(2 * getTabletHorizontalMargin(deviceProfile),
                    2 * (mInsets.left + mInsets.right));
        } else if (mInsets.bottom > 0) {
            widthUsed = mInsets.left + mInsets.right;
        } else {
            Rect padding = deviceProfile.workspacePadding;
            widthUsed = Math.max(padding.left + padding.right,
                    2 * (mInsets.left + mInsets.right));
        }

        measureChildWithMargins(mContent, widthMeasureSpec,
                widthUsed, heightMeasureSpec, deviceProfile.bottomSheetTopPadding);
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
                MeasureSpec.getSize(heightMeasureSpec));
    }

    private int getTabletHorizontalMargin(DeviceProfile deviceProfile) {
        // All bottom-sheets showing widgets will be full-width across all devices.
        if (enableCategorizedWidgetSuggestions()) {
            return 0;
        }
        if (deviceProfile.isLandscape && !deviceProfile.isTwoPanels) {
            return getResources().getDimensionPixelSize(
                    R.dimen.widget_picker_landscape_tablet_left_right_margin);
        }
        if (deviceProfile.isTwoPanels && enableUnfoldedTwoPanePicker()) {
            return getResources().getDimensionPixelSize(
                    R.dimen.widget_picker_two_panels_left_right_margin);
        }
        return deviceProfile.allAppsLeftRightMargin;
    }

    @Override
    protected Interpolator getIdleInterpolator() {
        return mActivityContext.getDeviceProfile().isTablet
                ? EMPHASIZED : super.getIdleInterpolator();
    }

    protected void onCloseComplete() {
        super.onCloseComplete();
        clearNavBarColor();
    }

    protected void clearNavBarColor() {
        getSystemUiController().updateUiState(
                SystemUiController.UI_STATE_WIDGET_BOTTOM_SHEET, 0);
    }

    protected void setupNavBarColor() {
        boolean isNavBarDark = Themes.getAttrBoolean(getContext(), R.attr.isMainColorDark);

        // In light mode, landscape reverses navbar background color.
        boolean isPhoneLandscape =
                !mActivityContext.getDeviceProfile().isTablet && mInsets.bottom == 0;
        if (!isNavBarDark && isPhoneLandscape) {
            isNavBarDark = true;
        }

        getSystemUiController().updateUiState(SystemUiController.UI_STATE_WIDGET_BOTTOM_SHEET,
                isNavBarDark ? SystemUiController.FLAG_DARK_NAV
                        : SystemUiController.FLAG_LIGHT_NAV);
    }

    protected SystemUiController getSystemUiController() {
        return mActivityContext.getSystemUiController();
    }

    /** Shows education tip on top center of {@code view} if view is laid out. */
    @Nullable
    protected ArrowTipView showEducationTipOnViewIfPossible(@Nullable View view) {
        if (view == null || !ViewCompat.isLaidOut(view)) {
            return null;
        }
        int[] coords = new int[2];
        view.getLocationOnScreen(coords);
        ArrowTipView arrowTipView =
                new ArrowTipView(mActivityContext,  /* isPointingUp= */ false).showAtLocation(
                        getContext().getString(R.string.long_press_widget_to_add),
                        /* arrowXCoord= */coords[0] + view.getWidth() / 2,
                        /* yCoord= */coords[1]);
        if (arrowTipView != null) {
            LauncherPrefs.get(getContext()).put(WIDGETS_EDUCATION_TIP_SEEN, true);
        }
        return arrowTipView;
    }

    /** Returns {@code true} if tip has previously been shown on any of {@link BaseWidgetSheet}. */
    protected boolean hasSeenEducationTip() {
        return LauncherPrefs.get(getContext()).get(WIDGETS_EDUCATION_TIP_SEEN)
                || Utilities.isRunningInTestHarness();
    }

    @Override
    protected void setTranslationShift(float translationShift) {
        super.setTranslationShift(translationShift);
        if (mActivityContext instanceof Launcher ls) {
            ls.onWidgetsTransition(1 - translationShift);
        }
    }
}
