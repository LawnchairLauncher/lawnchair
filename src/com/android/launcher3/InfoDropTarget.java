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

import android.content.ComponentName;
import android.content.Context;
import android.util.AttributeSet;

import com.android.launcher3.compat.UserHandleCompat;

public class InfoDropTarget extends ButtonDropTarget {

    public InfoDropTarget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public InfoDropTarget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        // Get the hover color
        mHoverColor = getResources().getColor(R.color.info_target_hover_tint);

        setDrawable(R.drawable.ic_info_launcher);
    }

    public static void startDetailsActivityForInfo(Object info, Launcher launcher) {
        ComponentName componentName = null;
        if (info instanceof AppInfo) {
            componentName = ((AppInfo) info).componentName;
        } else if (info instanceof ShortcutInfo) {
            componentName = ((ShortcutInfo) info).intent.getComponent();
        } else if (info instanceof PendingAddItemInfo) {
            componentName = ((PendingAddItemInfo) info).componentName;
        }
        final UserHandleCompat user;
        if (info instanceof ItemInfo) {
            user = ((ItemInfo) info).user;
        } else {
            user = UserHandleCompat.myUserHandle();
        }

        if (componentName != null) {
            launcher.startApplicationDetailsActivity(componentName, user);
        }
    }

    @Override
    protected boolean supportsDrop(DragSource source, Object info) {
        return source.supportsAppInfoDropTarget() && supportsDrop(getContext(), info);
    }

    public static boolean supportsDrop(Context context, Object info) {
        return info instanceof AppInfo || info instanceof PendingAddItemInfo;
    }

    @Override
    void completeDrop(DragObject d) {
        startDetailsActivityForInfo(d.dragInfo, mLauncher);
    }
}
