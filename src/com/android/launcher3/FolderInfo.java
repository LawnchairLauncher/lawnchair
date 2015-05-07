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

import java.util.ArrayList;
import java.util.Arrays;

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
    boolean opened;

    public int options;

    /**
     * The apps and shortcuts
     */
    public ArrayList<ShortcutInfo> contents = new ArrayList<ShortcutInfo>();

    ArrayList<FolderListener> listeners = new ArrayList<FolderListener>();

    public FolderInfo() {
        itemType = LauncherSettings.Favorites.ITEM_TYPE_FOLDER;
        user = UserHandleCompat.myUserHandle();
    }

    /**
     * Add an app or shortcut
     *
     * @param item
     */
    public void add(ShortcutInfo item) {
        contents.add(item);
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).onAdd(item);
        }
        itemsChanged();
    }

    /**
     * Remove an app or shortcut. Does not change the DB.
     *
     * @param item
     */
    public void remove(ShortcutInfo item) {
        contents.remove(item);
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).onRemove(item);
        }
        itemsChanged();
    }

    public void setTitle(CharSequence title) {
        this.title = title;
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).onTitleChanged(title);
        }
    }

    @Override
    void onAddToDatabase(Context context, ContentValues values) {
        super.onAddToDatabase(context, values);
        values.put(LauncherSettings.Favorites.TITLE, title.toString());
        values.put(LauncherSettings.Favorites.OPTIONS, options);

    }

    void addListener(FolderListener listener) {
        listeners.add(listener);
    }

    void removeListener(FolderListener listener) {
        if (listeners.contains(listener)) {
            listeners.remove(listener);
        }
    }

    void itemsChanged() {
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).onItemsChanged();
        }
    }

    @Override
    void unbind() {
        super.unbind();
        listeners.clear();
    }

    interface FolderListener {
        public void onAdd(ShortcutInfo item);
        public void onRemove(ShortcutInfo item);
        public void onTitleChanged(CharSequence title);
        public void onItemsChanged();
    }

    @Override
    public String toString() {
        return "FolderInfo(id=" + this.id + " type=" + this.itemType
                + " container=" + this.container + " screen=" + screenId
                + " cellX=" + cellX + " cellY=" + cellY + " spanX=" + spanX
                + " spanY=" + spanY + " dropPos=" + Arrays.toString(dropPos) + ")";
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
