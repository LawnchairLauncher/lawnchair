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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Base class for a View which shows a floating UI on top of the launcher UI.
 */
public abstract class AbstractFloatingView extends LinearLayout {

    @IntDef(flag = true, value = {
            TYPE_FOLDER,
            TYPE_POPUP_CONTAINER_WITH_ARROW,
            TYPE_WIDGETS_BOTTOM_SHEET
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface FloatingViewType {}
    public static final int TYPE_FOLDER = 1 << 0;
    public static final int TYPE_POPUP_CONTAINER_WITH_ARROW = 1 << 1;
    public static final int TYPE_WIDGETS_BOTTOM_SHEET = 1 << 2;

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

    /**
     * If the view is current handling keyboard, return the active target, null otherwise
     */
    public ExtendedEditText getActiveTextView() {
        return null;
    }


    /**
     * Any additional view (outside of this container) where touch should be allowed while this
     * view is visible.
     */
    public View getExtendedTouchView() {
        return null;
    }

    public final boolean isOpen() {
        return mIsOpen;
    }

    protected void onWidgetsBound() {
    }

    protected abstract boolean isOfType(@FloatingViewType int type);

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

    public static void closeAllOpenViews(Launcher launcher, boolean animate) {
        DragLayer dragLayer = launcher.getDragLayer();
        // Iterate in reverse order. AbstractFloatingView is added later to the dragLayer,
        // and will be one of the last views.
        for (int i = dragLayer.getChildCount() - 1; i >= 0; i--) {
            View child = dragLayer.getChildAt(i);
            if (child instanceof AbstractFloatingView) {
                ((AbstractFloatingView) child).close(animate);
            }
        }
    }

    public static void closeAllOpenViews(Launcher launcher) {
        closeAllOpenViews(launcher, true);
    }

    public static AbstractFloatingView getTopOpenView(Launcher launcher) {
        return getOpenView(launcher, TYPE_FOLDER | TYPE_POPUP_CONTAINER_WITH_ARROW
                | TYPE_WIDGETS_BOTTOM_SHEET);
    }

    public abstract int getLogContainerType();
}
