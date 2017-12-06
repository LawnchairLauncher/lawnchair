/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.annotation.IntDef;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action;
import com.android.launcher3.util.TouchController;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Base class for a View which shows a floating UI on top of the launcher UI.
 */
public abstract class AbstractFloatingView extends LinearLayout implements TouchController {

    @IntDef(flag = true, value = {
            TYPE_FOLDER,
            TYPE_ACTION_POPUP,
            TYPE_WIDGETS_BOTTOM_SHEET,
            TYPE_WIDGET_RESIZE_FRAME,
            TYPE_WIDGETS_FULL_SHEET,
            TYPE_QUICKSTEP_PREVIEW,
            TYPE_ON_BOARD_POPUP
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface FloatingViewType {}
    public static final int TYPE_FOLDER = 1 << 0;
    public static final int TYPE_ACTION_POPUP = 1 << 1;
    public static final int TYPE_WIDGETS_BOTTOM_SHEET = 1 << 2;
    public static final int TYPE_WIDGET_RESIZE_FRAME = 1 << 3;
    public static final int TYPE_WIDGETS_FULL_SHEET = 1 << 4;
    public static final int TYPE_QUICKSTEP_PREVIEW = 1 << 5;
    public static final int TYPE_ON_BOARD_POPUP = 1 << 6;

    public static final int TYPE_ALL = TYPE_FOLDER | TYPE_ACTION_POPUP
            | TYPE_WIDGETS_BOTTOM_SHEET | TYPE_WIDGET_RESIZE_FRAME | TYPE_WIDGETS_FULL_SHEET
            | TYPE_QUICKSTEP_PREVIEW | TYPE_ON_BOARD_POPUP;

    // Type of popups which should be kept open during launcher rebind
    public static final int TYPE_REBIND_SAFE = TYPE_WIDGETS_FULL_SHEET
            | TYPE_QUICKSTEP_PREVIEW | TYPE_ON_BOARD_POPUP;

    protected boolean mIsOpen;

    public AbstractFloatingView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AbstractFloatingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * We need to handle touch events to prevent them from falling through to the workspace below.
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return true;
    }

    public final void close(boolean animate) {
        animate &= !Utilities.isPowerSaverOn(getContext());
        handleClose(animate);
        Launcher.getLauncher(getContext()).getUserEventDispatcher().resetElapsedContainerMillis();
    }

    protected abstract void handleClose(boolean animate);

    public abstract void logActionCommand(int command);

    public final boolean isOpen() {
        return mIsOpen;
    }

    protected void onWidgetsBound() {
    }

    protected abstract boolean isOfType(@FloatingViewType int type);

    public void onBackPressed() {
        logActionCommand(Action.Command.BACK);
        close(true);
    }

    @Override
    public boolean onControllerTouchEvent(MotionEvent ev) {
        return false;
    }

    protected static <T extends AbstractFloatingView> T getOpenView(
            Launcher launcher, @FloatingViewType int type) {
        DragLayer dragLayer = launcher.getDragLayer();
        // Iterate in reverse order. AbstractFloatingView is added later to the dragLayer,
        // and will be one of the last views.
        for (int i = dragLayer.getChildCount() - 1; i >= 0; i--) {
            View child = dragLayer.getChildAt(i);
            if (child instanceof AbstractFloatingView) {
                AbstractFloatingView view = (AbstractFloatingView) child;
                if (view.isOfType(type) && view.isOpen()) {
                    return (T) view;
                }
            }
        }
        return null;
    }

    public static void closeOpenContainer(Launcher launcher, @FloatingViewType int type) {
        AbstractFloatingView view = getOpenView(launcher, type);
        if (view != null) {
            view.close(true);
        }
    }

    public static void closeOpenViews(Launcher launcher, boolean animate,
            @FloatingViewType int type) {
        DragLayer dragLayer = launcher.getDragLayer();
        // Iterate in reverse order. AbstractFloatingView is added later to the dragLayer,
        // and will be one of the last views.
        for (int i = dragLayer.getChildCount() - 1; i >= 0; i--) {
            View child = dragLayer.getChildAt(i);
            if (child instanceof AbstractFloatingView) {
                AbstractFloatingView abs = (AbstractFloatingView) child;
                if (abs.isOfType(type)) {
                    abs.close(animate);
                }
            }
        }
    }

    public static void closeAllOpenViews(Launcher launcher, boolean animate) {
        closeOpenViews(launcher, animate, TYPE_ALL);
    }

    public static void closeAllOpenViews(Launcher launcher) {
        closeAllOpenViews(launcher, true);
    }

    public static AbstractFloatingView getTopOpenView(Launcher launcher) {
        return getOpenView(launcher, TYPE_ALL);
    }
}
