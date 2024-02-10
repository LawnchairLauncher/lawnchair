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

package com.android.launcher3.accessibility;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.OnHierarchyChangeListener;

import androidx.annotation.Nullable;

import com.android.launcher3.CellLayout;
import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.Launcher;
import com.android.launcher3.dragndrop.DragController.DragListener;
import com.android.launcher3.dragndrop.DragOptions;

import java.util.function.Function;

/**
 * Utility listener to enable/disable accessibility drag flags for a ViewGroup
 * containing CellLayouts
 */
public class AccessibleDragListenerAdapter implements DragListener, OnHierarchyChangeListener {

    private final ViewGroup mViewGroup;
    private final Function<CellLayout, DragAndDropAccessibilityDelegate> mDelegateFactory;

    /**
     * @param parent the viewgroup containing all the children
     * @param delegateFactory function to create no delegates
     */
    public AccessibleDragListenerAdapter(ViewGroup parent,
            Function<CellLayout, DragAndDropAccessibilityDelegate> delegateFactory) {
        mViewGroup = parent;
        mDelegateFactory = delegateFactory;
    }

    @Override
    public void onDragStart(DragObject dragObject, DragOptions options) {
        mViewGroup.setOnHierarchyChangeListener(this);
        enableAccessibleDrag(true, dragObject);
    }

    @Override
    public void onDragEnd() {
        mViewGroup.setOnHierarchyChangeListener(null);
        enableAccessibleDrag(false, null);
        Launcher.getLauncher(mViewGroup.getContext()).getDragController().removeDragListener(this);
    }


    @Override
    public void onChildViewAdded(View parent, View child) {
        if (parent == mViewGroup) {
            setEnableForLayout((CellLayout) child, true);
        }
    }

    @Override
    public void onChildViewRemoved(View parent, View child) {
        if (parent == mViewGroup) {
            setEnableForLayout((CellLayout) child, false);
        }
    }

    protected void enableAccessibleDrag(boolean enable, @Nullable DragObject dragObject) {
        for (int i = 0; i < mViewGroup.getChildCount(); i++) {
            setEnableForLayout((CellLayout) mViewGroup.getChildAt(i), enable);
        }
    }

    protected final void setEnableForLayout(CellLayout layout, boolean enable) {
        layout.setDragAndDropAccessibilityDelegate(enable ? mDelegateFactory.apply(layout) : null);
    }
}
