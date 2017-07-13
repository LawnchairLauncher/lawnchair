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

import android.os.Process;

import com.android.launcher3.model.ModelWriter;
import com.android.launcher3.util.ContentWriter;

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

    public int options;

    /**
     * The apps and shortcuts
     */
    public ArrayList<ShortcutInfo> contents = new ArrayList<ShortcutInfo>();

    ArrayList<FolderListener> listeners = new ArrayList<FolderListener>();

    public FolderInfo() {
        itemType = LauncherSettings.Favorites.ITEM_TYPE_FOLDER;
        user = Process.myUserHandle();
    }

    /**
     * Add an app or shortcut
     *
     * @param item
     */
    public void add(ShortcutInfo item, boolean animate) {
        add(item, contents.size(), animate);
    }

    /**
     * Add an app or shortcut for a specified rank.
     */
    public void add(ShortcutInfo item, int rank, boolean animate) {
        rank = Utilities.boundToRange(rank, 0, contents.size());
        contents.add(rank, item);
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).onAdd(item, rank);
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
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).onRemove(item);
        }
        itemsChanged(animate);
    }

    public void setTitle(CharSequence title) {
        this.title = title;
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).onTitleChanged(title);
        }
    }

    @Override
    public void onAddToDatabase(ContentWriter writer) {
        super.onAddToDatabase(writer);
        writer.put(LauncherSettings.Favorites.TITLE, title)
                .put(LauncherSettings.Favorites.OPTIONS, options);

    }

    public void addListener(FolderListener listener) {
        listeners.add(listener);
    }

    public void removeListener(FolderListener listener) {
        listeners.remove(listener);
    }

    public void itemsChanged(boolean animate) {
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).onItemsChanged(animate);
        }
    }

    public void prepareAutoUpdate() {
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).prepareAutoUpdate();
        }
    }

    public interface FolderListener {
        public void onAdd(ShortcutInfo item, int rank);
        public void onRemove(ShortcutInfo item);
        public void onTitleChanged(CharSequence title);
        public void onItemsChanged(boolean animate);
        public void prepareAutoUpdate();
    }

    public boolean hasOption(int optionFlag) {
        return (options & optionFlag) != 0;
    }

    /**
     * @param option flag to set or clear
     * @param isEnabled whether to set or clear the flag
     * @param writer if not null, save changes to the db.
     */
    public void setOption(int option, boolean isEnabled, ModelWriter writer) {
        int oldOptions = options;
        if (isEnabled) {
            options |= option;
        } else {
            options &= ~option;
        }
        if (writer != null && oldOptions != options) {
            writer.updateItemInDatabase(this);
        }
    }
}
