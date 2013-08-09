package com.cyanogenmod.trebuchet;

import java.util.ArrayList;
import java.util.Iterator;

import org.cyanogenmod.support.ui.LiveFolder;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.text.TextUtils;

/**
 * Extension of regular folders with different characteristics
 * User cannot drag items to/from the folder.
 * Allows deletion of items when workspace is locked
 * Long press to delete items
 * Contents of this folder are non-persistent, and populated
 * by an accompanying receiver in the 3rd party application
 * Folder continues to exist even when its empty
 */
class LiveFolderInfo extends FolderInfo {

    ComponentName receiver;
    long lastUpdate;
    Intent.ShortcutIconResource iconResource;

    LiveFolderInfo() {
        this("LiveFolder");
    }

    LiveFolderInfo(String lblMsg) {
        itemType = LauncherSettings.Favorites.ITEM_TYPE_LIVE_FOLDER;
        title = TextUtils.isEmpty(lblMsg) ? "LiveFolder" : lblMsg;
    }

    @Override
    void onAddToDatabase(ContentValues values) {
        super.onAddToDatabase(values);
        values.put(LauncherSettings.Favorites.TITLE, title.toString());
        values.put(LauncherSettings.Favorites.RECEIVER_COMPONENT, receiver.flattenToString());
    }

    public void removeAll() {
        Iterator<ShortcutInfo> iter = contents.iterator();
        while (iter.hasNext()) {
            ShortcutInfo info = iter.next();
            iter.remove();
            for (FolderListener listener : listeners) {
                listener.onRemove(info);
            }
        }
        itemsChanged();
    }

    public boolean isOwner(Context ctx, String packageName) {
        if (packageName.equals(receiver.getPackageName())) {
            return true;
        }
        PackageManager packageManager = ctx.getPackageManager();
        try {
            int callingUid = packageManager.getApplicationInfo(packageName, 0).uid;
            int ownerUid = packageManager.getApplicationInfo(receiver.getPackageName(), 0).uid;
            return callingUid == ownerUid;
        } catch (NameNotFoundException e) {
            return false;
        }
    }

    public void populateWithItems(ArrayList<LiveFolder.Item> items) {
        lastUpdate = System.currentTimeMillis();
        removeAll();
        for (LiveFolder.Item item : items) {
            LiveFolderItemInfo cInfo = new LiveFolderItemInfo();
            cInfo.title = item.getLabel();
            cInfo.intent = item.getIntent();
            cInfo.setIcon(item.getIcon());
            cInfo.item_id = item.getId();
            cInfo.container = id;
            add(cInfo);
            if (contents.size() == LiveFolder.Constants.MAX_ITEMS) {
                break;
            }
        }
    }
}
