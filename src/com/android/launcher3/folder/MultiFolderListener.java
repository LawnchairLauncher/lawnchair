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

package com.android.launcher3.folder;

import com.android.launcher3.FolderInfo.FolderListener;
import com.android.launcher3.ShortcutInfo;

/**
 * An implementation of {@link FolderListener} which passes the events to 2 children.
 */
public class MultiFolderListener implements FolderListener {

    private final FolderListener mListener1;
    private final FolderListener mListener2;

    public MultiFolderListener(FolderListener listener1, FolderListener listener2) {
        mListener1 = listener1;
        mListener2 = listener2;
    }

    @Override
    public void onAdd(ShortcutInfo item) {
        mListener1.onAdd(item);
        mListener2.onAdd(item);
    }

    @Override
    public void onRemove(ShortcutInfo item) {
        mListener1.onRemove(item);
        mListener2.onRemove(item);
    }

    @Override
    public void onItemsChanged(boolean animate) {
        mListener1.onItemsChanged(animate);
        mListener2.onItemsChanged(animate);
    }
}
