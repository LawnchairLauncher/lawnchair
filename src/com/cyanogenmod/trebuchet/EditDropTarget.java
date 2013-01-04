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

package com.cyanogenmod.trebuchet;

import android.content.ComponentName;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.TransitionDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.cyanogenmod.trebuchet.R;

public class EditDropTarget extends ButtonDropTarget {

    private ColorStateList mOriginalTextColor;
    private TransitionDrawable mDrawable;

    public EditDropTarget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EditDropTarget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mOriginalTextColor = getTextColors();

        // Get the hover color
        Resources r = getResources();
        mHoverColor = r.getColor(R.color.edit_target_hover_tint);
        mDrawable = (TransitionDrawable) getCurrentDrawable();
        mDrawable.setCrossFadeEnabled(true);

        // Remove the text in landscape
        int orientation = getResources().getConfiguration().orientation;
        boolean transposeLayout = getResources().getBoolean(R.bool.hotseat_transpose_layout_with_orientation);
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (transposeLayout) {
                setText("");
            }
        }
    }

    private boolean isDragSourceWorkspaceOrFolder(DragSource source) {
        return (source instanceof Workspace) || (source instanceof Folder);
    }
    private boolean isWorkspaceOrFolderApplication(DragSource source, Object info) {
        return isDragSourceWorkspaceOrFolder(source) && (info instanceof ShortcutInfo);
    }

    @Override
    public boolean acceptDrop(DragObject d) {
        // acceptDrop is called just before onDrop. We do the work here, rather than
        // in onDrop, because it allows us to reject the drop (by returning false)
        // so that the object being dragged isn't removed from the drag source.
        if (d.dragInfo instanceof ShortcutInfo) {
            mLauncher.updateShortcut((ShortcutInfo) d.dragInfo);
        } else if (d.dragInfo instanceof Folder) {
            //
        }

        // There is no post-drop animation, so clean up the DragView now
        d.deferDragViewCleanupPostAnimation = false;
        return false;
    }

    @Override
    public void onDragStart(DragSource source, Object info, int dragAction) {
        boolean isVisible = true;

        // Hide this button unless we are dragging something from AllApps
        if (!isWorkspaceOrFolderApplication(source, info)) {
            isVisible = false;
        }

        mActive = isVisible;
        mDrawable.resetTransition();
        setTextColor(mOriginalTextColor);
        ((ViewGroup) getParent()).setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDragEnd() {
        super.onDragEnd();
        mActive = false;
    }

    public void onDragEnter(DragObject d) {
        super.onDragEnter(d);

        mDrawable.startTransition(mTransitionDuration);
        setTextColor(mHoverColor);
    }

    public void onDragExit(DragObject d) {
        super.onDragExit(d);

        if (!d.dragComplete) {
            mDrawable.resetTransition();
            setTextColor(mOriginalTextColor);
        }
    }
}