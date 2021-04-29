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

import static com.android.launcher3.icons.GraphicsUtils.setColorAlphaBound;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.Toast;

import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.popup.PopupDataProvider;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.TestProtocol;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.util.SystemUiController;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.AbstractSlideInView;

/**
 * Base class for various widgets popup
 */
public abstract class BaseWidgetSheet extends AbstractSlideInView
        implements OnClickListener, OnLongClickListener, DragSource,
        PopupDataProvider.PopupDataChangeListener {


    /* Touch handling related member variables. */
    private Toast mWidgetInstructionToast;

    public BaseWidgetSheet(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    protected int getScrimColor(Context context) {
        int alpha = context.getResources().getInteger(R.integer.extracted_color_gradient_alpha);
        return setColorAlphaBound(context.getColor(R.color.wallpaper_popup_scrim), alpha);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mLauncher.getPopupDataProvider().setChangeListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mLauncher.getPopupDataProvider().setChangeListener(null);
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
        if (!ItemLongClickListener.canStartDrag(mLauncher)) return false;

        if (v instanceof WidgetCell) {
            return beginDraggingWidget((WidgetCell) v);
        } else if (v.getParent() instanceof WidgetCell) {
            return beginDraggingWidget((WidgetCell) v.getParent());
        }
        return true;
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
        dragHelper.setRemoteViewsPreview(v.getRemoteViewsPreview());
        dragHelper.setAppWidgetHostViewPreview(v.getAppWidgetHostViewPreview());

        if (image.getDrawable() != null) {
            int[] loc = new int[2];
            getPopupContainer().getLocationInDragLayer(image, loc);

            dragHelper.startDrag(image.getBitmapBounds(), image.getDrawable().getIntrinsicWidth(),
                    image.getWidth(), new Point(loc[0], loc[1]), this, new DragOptions());
        } else {
            View preview = v.getAppWidgetHostViewPreview();
            int[] loc = new int[2];
            getPopupContainer().getLocationInDragLayer(preview, loc);

            Rect r = new Rect(0, 0, preview.getWidth(), preview.getHeight());
            dragHelper.startDrag(r, preview.getMeasuredWidth(), preview.getMeasuredWidth(),
                    new Point(loc[0], loc[1]), this, new DragOptions());
        }
        close(true);
        return true;
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
        return mLauncher.getSystemUiController();
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
}
