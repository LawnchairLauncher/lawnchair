/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.graphics.drawable.Drawable;

public class LiveFolderIcon extends FolderIcon {
    public LiveFolderIcon(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LiveFolderIcon(Context context) {
        super(context);
    }

    static LiveFolderIcon fromXml(int resId, Launcher launcher, ViewGroup group,
            LiveFolderInfo folderInfo) {

        LiveFolderIcon icon = (LiveFolderIcon)
                LayoutInflater.from(launcher).inflate(resId, group, false);

        final Resources resources = launcher.getResources();
        Drawable d = folderInfo.icon;
        if (d == null) {
            d = Utilities.createIconThumbnail(
                    resources.getDrawable(R.drawable.ic_launcher_folder), launcher);
            folderInfo.filtered = true;
        }
        icon.setCompoundDrawablesWithIntrinsicBounds(null, d, null, null);
        icon.setText(folderInfo.title);
        icon.setTag(folderInfo);
        icon.setOnClickListener(launcher);
        
        return icon;
    }

    @Override
    public boolean acceptDrop(DragSource source, int x, int y, int xOffset, int yOffset, Object dragInfo) {
        return false;
    }

    @Override
    public void onDrop(DragSource source, int x, int y, int xOffset, int yOffset, Object dragInfo) {
    }

    @Override
    public void onDragEnter(DragSource source, int x, int y, int xOffset, int yOffset, Object dragInfo) {
    }

    @Override
    public void onDragOver(DragSource source, int x, int y, int xOffset, int yOffset, Object dragInfo) {
    }

    @Override
    public void onDragExit(DragSource source, int x, int y, int xOffset, int yOffset, Object dragInfo) {
    }
}
