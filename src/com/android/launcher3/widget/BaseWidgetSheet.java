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
import static com.android.launcher3.config.FeatureFlags.LARGE_SCREEN_WIDGET_PICKER;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.WindowInsets;
import android.view.animation.Interpolator;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.core.view.ViewCompat;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.popup.PopupDataProvider;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.util.SystemUiController;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.window.WindowManagerProxy;
import com.android.launcher3.views.AbstractSlideInView;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.ArrowTipView;

/**
 * Base class for various widgets popup
 */
public abstract class BaseWidgetSheet extends AbstractSlideInView<Launcher>
        implements OnClickListener, OnLongClickListener, DragSource,
        PopupDataProvider.PopupDataChangeListener, Insettable, OnDeviceProfileChangeListener {
    /** The default number of cells that can fit horizontally in a widget sheet. */
    public static final int DEFAULT_MAX_HORIZONTAL_SPANS = 4;

    protected static final String KEY_WIDGETS_EDUCATION_TIP_SEEN =
            "launcher.widgets_education_tip_seen";
    protected final Rect mInsets = new Rect();

    /* Touch handling related member variables. */
    private Toast mWidgetInstructionToast;

    @Px protected int mContentHorizontalMargin;
    @Px protected int mWidgetCellHorizontalPadding;

    protected int mNavBarScrimHeight;
    private final Paint mNavBarScrimPaint;

    public BaseWidgetSheet(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContentHorizontalMargin = getResources().getDimensionPixelSize(
                R.dimen.widget_list_horizontal_margin);
        mWidgetCellHorizontalPadding = getResources().getDimensionPixelSize(
                R.dimen.widget_cell_horizontal_padding);
        mNavBarScrimPaint = new Paint();
        mNavBarScrimPaint.setColor(Themes.getNavBarScrimColor(mActivityContext));
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
    }

    @Override
    public final void onClick(View v) {
        Object tag = null;
        if (v instanceof WidgetCell) {
            tag = v.getTag();
        } else if (v.getParent() instanceof WidgetCell) {
            tag = ((WidgetCell) v.getParent()).getTag();
        }
        if (tag instanceof PendingAddShortcutInfo) {
            mWidgetInstructionToast = showShortcutToast(getContext(), mWidgetInstructionToast);
        } else {
            mWidgetInstructionToast = showWidgetToast(getContext(), mWidgetInstructionToast);
        }

    }

    @Override
    public boolean onLongClick(View v) {
        TestLogging.recordEvent(TestProtocol.SEQUENCE_MAIN, "Widgets.onLongClick");
        v.cancelLongPress();
        if (!ItemLongClickListener.canStartDrag(mActivityContext)) return false;

        if (v instanceof WidgetCell) {
            return beginDraggingWidget((WidgetCell) v);
        } else if (v.getParent() instanceof WidgetCell) {
            return beginDraggingWidget((WidgetCell) v.getParent());
        }
        return true;
    }

    @Override
    public void setInsets(Rect insets) {
        mInsets.set(insets);
        @Px int contentHorizontalMargin = getResources().getDimensionPixelSize(
                R.dimen.widget_list_horizontal_margin);
        if (contentHorizontalMargin != mContentHorizontalMargin) {
            onContentHorizontalMarginChanged(contentHorizontalMargin);
            mContentHorizontalMargin = contentHorizontalMargin;
        }
    }

    private int getNavBarScrimHeight(WindowInsets insets) {
        if (Utilities.ATLEAST_Q) {
            return insets.getTappableElementInsets().bottom;
        } else {
            return insets.getStableInsetBottom();
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
            int margin = deviceProfile.allAppsLeftRightMargin;
            if (deviceProfile.isLandscape
                    && LARGE_SCREEN_WIDGET_PICKER.get()
                    && !deviceProfile.isTwoPanels) {
                margin = getResources().getDimensionPixelSize(
                        R.dimen.widget_picker_landscape_tablet_left_right_margin);
            }
            widthUsed = Math.max(2 * margin, 2 * (mInsets.left + mInsets.right));
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

    private boolean beginDraggingWidget(WidgetCell v) {
        // Get the widget preview as the drag representation
        WidgetImageView image = v.getWidgetView();

        // If the ImageView doesn't have a drawable yet, the widget preview hasn't been loaded and
        // we abort the drag.
        if (image.getDrawable() == null && v.getAppWidgetHostViewPreview() == null) {
            return false;
        }

        PendingItemDragHelper dragHelper = new PendingItemDragHelper(v);
        // RemoteViews are being rendered in AppWidgetHostView in WidgetCell. And thus, the scale of
        // RemoteViews is equivalent to the AppWidgetHostView scale.
        dragHelper.setRemoteViewsPreview(v.getRemoteViewsPreview(), v.getAppWidgetHostViewScale());
        dragHelper.setAppWidgetHostViewPreview(v.getAppWidgetHostViewPreview());

        if (image.getDrawable() != null) {
            int[] loc = new int[2];
            getPopupContainer().getLocationInDragLayer(image, loc);

            dragHelper.startDrag(image.getBitmapBounds(), image.getDrawable().getIntrinsicWidth(),
                    image.getWidth(), new Point(loc[0], loc[1]), this, new DragOptions());
        } else {
            NavigableAppWidgetHostView preview = v.getAppWidgetHostViewPreview();
            int[] loc = new int[2];
            getPopupContainer().getLocationInDragLayer(preview, loc);
            Rect r = new Rect();
            preview.getWorkspaceVisualDragBounds(r);
            dragHelper.startDrag(r, preview.getMeasuredWidth(), preview.getMeasuredWidth(),
                    new Point(loc[0], loc[1]), this, new DragOptions());
        }
        close(true);
        return true;
    }

    @Override
    protected Interpolator getIdleInterpolator() {
        return mActivityContext.getDeviceProfile().isTablet
                ? EMPHASIZED : super.getIdleInterpolator();
    }

    //
    // Drag related handling methods that implement {@link DragSource} interface.
    //

    @Override
    public void onDropCompleted(View target, DragObject d, boolean success) { }


    protected void onCloseComplete() {
        super.onCloseComplete();
        clearNavBarColor();
    }

    protected void clearNavBarColor() {
        getSystemUiController().updateUiState(
                SystemUiController.UI_STATE_WIDGET_BOTTOM_SHEET, 0);
    }

    protected void setupNavBarColor() {
        boolean isSheetDark = Themes.getAttrBoolean(getContext(), R.attr.isMainColorDark);
        getSystemUiController().updateUiState(
                SystemUiController.UI_STATE_WIDGET_BOTTOM_SHEET,
                isSheetDark ? SystemUiController.FLAG_DARK_NAV : SystemUiController.FLAG_LIGHT_NAV);
    }

    protected SystemUiController getSystemUiController() {
        return mActivityContext.getSystemUiController();
    }

    /**
     * Show Widget tap toast prompting user to drag instead
     */
    public static Toast showWidgetToast(Context context, Toast toast) {
        // Let the user know that they have to long press to add a widget
        if (toast != null) {
            toast.cancel();
        }

        CharSequence msg = Utilities.wrapForTts(
                context.getText(R.string.long_press_widget_to_add),
                context.getString(R.string.long_accessible_way_to_add));
        toast = Toast.makeText(context, msg, Toast.LENGTH_SHORT);
        toast.show();
        return toast;
    }

    /**
     * Show shortcut tap toast prompting user to drag instead.
     */
    private static Toast showShortcutToast(Context context, Toast toast) {
        // Let the user know that they have to long press to add a widget
        if (toast != null) {
            toast.cancel();
        }

        CharSequence msg = Utilities.wrapForTts(
                context.getText(R.string.long_press_shortcut_to_add),
                context.getString(R.string.long_accessible_way_to_add_shortcut));
        toast = Toast.makeText(context, msg, Toast.LENGTH_SHORT);
        toast.show();
        return toast;
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
            mActivityContext.getSharedPrefs().edit()
                    .putBoolean(KEY_WIDGETS_EDUCATION_TIP_SEEN, true).apply();
        }
        return arrowTipView;
    }

    /** Returns {@code true} if tip has previously been shown on any of {@link BaseWidgetSheet}. */
    protected boolean hasSeenEducationTip() {
        return mActivityContext.getSharedPrefs().getBoolean(KEY_WIDGETS_EDUCATION_TIP_SEEN, false)
                || Utilities.isRunningInTestHarness();
    }

    @Override
    protected void setTranslationShift(float translationShift) {
        super.setTranslationShift(translationShift);
        Launcher launcher = ActivityContext.lookupContext(getContext());
        launcher.onWidgetsTransition(1 - translationShift);
    }
}
