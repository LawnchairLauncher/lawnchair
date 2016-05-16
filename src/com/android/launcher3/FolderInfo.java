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

package com.android.launcher3;

import android.content.ContentValues;
import android.content.Context;

import com.android.launcher3.compat.UserHandleCompat;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * Represents a folder containing shortcuts or apps.
 */
public class FolderInfo extends ItemInfo {

    public static final int NO_FLAGS = 0x00000000;

    /**
     * The folder is locked in sorted mode
     */
    public static final int FLAG_ITEMS_SORTED = 0x00000001;

    /**
     * It is a work folder
     */
    public static final int FLAG_WORK_FOLDER = 0x00000002;

    /**
     * The multi-page animation has run for this folder
     */
    public static final int FLAG_MULTI_PAGE_ANIMATION = 0x00000004;

    /**
     * Whether this folder has been opened
     */
    public boolean opened;

    public int options;

    /**
     * The apps and shortcuts
     */
    public ArrayList<ShortcutInfo> contents = new ArrayList<ShortcutInfo>();

    /**
     * A collection of listeners for folder info changes. Since this listeners are implemented by
     * the UI objects, using a WeakReference prevents context leaks.
     */
    private  WeakReference<FolderListener> mListener;

    public FolderInfo() {
        itemType = LauncherSettings.Favorites.ITEM_TYPE_FOLDER;
        user = UserHandleCompat.myUserHandle();
    }

    /**
     * Add an app or shortcut
     *
     * @param item
     */
    public void add(ShortcutInfo item, boolean animate) {
        contents.add(item);
        FolderListener listener = mListener == null ? null : mListener.get();
        if (listener != null) {
            listener.onAdd(item);
        }
        itemsChanged(animate);
    }

    /**
     * Remove an app or shortcut. Does not change the DB.
     *
     * @param item
     */
    public void remove(ShortcutInfo item, boolean animate) {
        contents.remove(item);
        FolderListener listener = mListener == null ? null : mListener.get();
        if (listener != null) {
            listener.onRemove(item);
        }
        itemsChanged(animate);
    }

    @Override
    void onAddToDatabase(Context context, ContentValues values) {
        super.onAddToDatabase(context, values);
        values.put(LauncherSettings.Favorites.TITLE, title.toString());
        values.put(LauncherSettings.Favorites.OPTIONS, options);

    }

    /**
     * Registers a listener for info change events.
     */
    public void setListener(FolderListener listener) {
        mListener = new WeakReference<>(listener);
    }

    public void itemsChanged(boolean animate) {
        FolderListener listener = mListener == null ? null : mListener.get();
        if (listener != null) {
            listener.onItemsChanged(animate);
        }
    }

    @Override
    void unbind() {
        super.unbind();
        mListener = null;
    }

    public interface FolderListener {
        void onAdd(ShortcutInfo item);
        void onRemove(ShortcutInfo item);
        void onItemsChanged(boolean animate);
    }

    @Override
    public String toString() {
        return "FolderInfo(id=" + this.id + " type=" + this.itemType
                + " container=" + this.container + " screen=" + screenId
                + " cellX=" + cellX + " cellY=" + cellY + " spanX=" + spanX
                + " spanY=" + spanY + ")";
    }

    public boolean hasOption(int optionFlag) {
        return (options & optionFlag) != 0;
    }

    /**
     * @param option flag to set or clear
     * @param isEnabled whether to set or clear the flag
     * @param context if not null, save changes to the db.
     */
    public void setOption(int option, boolean isEnabled, Context context) {
        int oldOptions = options;
        if (isEnabled) {
            options |= option;
        } else {
            options &= ~option;
        }
        if (context != null && oldOptions != options) {
            LauncherModel.updateItemInDatabase(context, this);
        }
    }
}
