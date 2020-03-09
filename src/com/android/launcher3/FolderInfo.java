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

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Process;

import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.widget.FrameLayout;
import ch.deletescape.lawnchair.LawnchairLauncher;
import ch.deletescape.lawnchair.folder.FirstItemProvider;
import ch.deletescape.lawnchair.iconpack.IconPack;
import ch.deletescape.lawnchair.iconpack.IconPackManager;
import ch.deletescape.lawnchair.iconpack.IconPackManager.CustomIconEntry;
import ch.deletescape.lawnchair.override.CustomInfoProvider;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.graphics.BitmapRenderer;
import com.android.launcher3.graphics.DrawableFactory;
import com.android.launcher3.model.ModelWriter;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.ContentWriter;

import java.util.ArrayList;
import org.jetbrains.annotations.Nullable;

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

    public static final int FLAG_COVER_MODE = 0x00000008;

    public int options;

    /**
     * The apps and shortcuts
     */
    public ArrayList<ShortcutInfo> contents = new ArrayList<ShortcutInfo>();

    ArrayList<FolderListener> listeners = new ArrayList<FolderListener>();

    public String swipeUpAction;

    private FirstItemProvider firstItemProvider = new FirstItemProvider(this);

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

        public default void onIconChanged() {
            // do nothing
        }
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

    public void setSwipeUpAction(@NonNull Context context, @Nullable String action) {
        swipeUpAction = action;
        ModelWriter.modifyItemInDatabase(context, this, null, swipeUpAction, false, null, null, false, true);
    }

    public ComponentKey toComponentKey() {
        return new ComponentKey(new ComponentName("ch.deletescape.lawnchair.folder", String.valueOf(id)), Process.myUserHandle());
    }

    public Drawable getIcon(Context context) {
        Launcher launcher = LawnchairLauncher.getLauncher(context);
        Drawable icn = getIconInternal(launcher);
        if (icn != null)  {
            return icn;
        }
        if (isCoverMode()) {
            return DrawableFactory.get(context).newIcon(getCoverInfo());
        }
        return getFolderIcon(launcher);
    }

    public Drawable getDefaultIcon(Launcher launcher) {
        if (isCoverMode()) {
            return new FastBitmapDrawable(getCoverInfo().iconBitmap);
        } else {
            return getFolderIcon(launcher);
        }
    }

    public Drawable getFolderIcon(Launcher launcher) {
        int iconSize = launcher.mDeviceProfile.iconSizePx;
        FrameLayout dummy = new FrameLayout(launcher, null);
        FolderIcon icon = FolderIcon.fromXml(R.layout.folder_icon, launcher, dummy, this);
        icon.isCustomIcon = false;
        icon.getFolderBackground().setStartOpacity(1f);
        Bitmap b = BitmapRenderer.createHardwareBitmap(iconSize, iconSize, out -> {
            out.translate(iconSize / 2f, 0);
            // TODO: make folder icons more visible in front of the bottom sheet
            // out.drawColor(Color.RED);
            icon.draw(out);
        });
        icon.unbind();
        return new BitmapDrawable(launcher.getResources(), b);
    }

    public boolean useIconMode(Context context) {
        return isCoverMode() || hasCustomIcon(context);
    }

    public boolean usingCustomIcon(Context context) {
        if (isCoverMode()) return false;
        Launcher launcher = LawnchairLauncher.getLauncher(context);
        return getIconInternal(launcher) != null;
    }

    private boolean hasCustomIcon(Context context) {
        Launcher launcher = LawnchairLauncher.getLauncher(context);
        return getIconInternal(launcher) != null;
    }

    public void clearCustomIcon(Context context) {
        Launcher launcher = LawnchairLauncher.getLauncher(context);
        CustomInfoProvider<FolderInfo> infoProvider = CustomInfoProvider.Companion.forItem(launcher, this);
        if (infoProvider != null) {
            infoProvider.setIcon(this, null);
        }
    }

    public boolean isCoverMode() {
        return hasOption(FLAG_COVER_MODE);
    }

    public void setCoverMode(boolean enable, ModelWriter modelWriter) {
        setOption(FLAG_COVER_MODE, enable, modelWriter);
    }

    public ShortcutInfo getCoverInfo() {
        return firstItemProvider.getFirstItem();
    }

    public CharSequence getIconTitle() {
        if (!TextUtils.equals(Folder.getDefaultFolderName(), title)) {
            return title;
        } else if (isCoverMode()) {
            ShortcutInfo info = getCoverInfo();
            if (info.customTitle != null) {
                return info.customTitle;
            }
            return info.title;
        } else {
            return Folder.getDefaultFolderName();
        }
    }

    /**
     * DO NOT USE OUTSIDE CUSTOMINFOPROVIDER
     */
    public void onIconChanged() {
        for (FolderListener listener : listeners) {
            listener.onIconChanged();
        }
    }

    private Drawable cached;
    private String cachedIcon;

    private Drawable getIconInternal(Launcher launcher) {
        CustomInfoProvider<FolderInfo> infoProvider = CustomInfoProvider.Companion.forItem(launcher, this);
        CustomIconEntry entry = infoProvider == null ? null : infoProvider.getIcon(this);
        if (entry != null && entry.getIcon() != null) {
            if (!entry.getIcon().equals(cachedIcon)) {
                IconPack pack = IconPackManager.Companion.getInstance(launcher)
                        .getIconPack(entry.getPackPackageName(), false, true);
                if (pack != null) {
                    cached = pack.getIcon(entry, launcher.mDeviceProfile.inv.fillResIconDpi);
                    cachedIcon = entry.getIcon();
                }
            }
            if (cached != null) {
                return cached.mutate();
            }
        }
        return null;
    }
}
