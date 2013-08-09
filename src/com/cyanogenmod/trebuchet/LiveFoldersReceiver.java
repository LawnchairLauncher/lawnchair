package com.cyanogenmod.trebuchet;

import java.util.ArrayList;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.util.Log;

import org.cyanogenmod.support.ui.LiveFolder;
import static org.cyanogenmod.support.ui.LiveFolder.Constants.*;

public class LiveFoldersReceiver extends BroadcastReceiver {

    private static final String TAG = LiveFoldersReceiver.class.getName();

    static void alertFolderModified(Context ctx, LiveFolderInfo info, boolean deleted) {
        Intent i = new Intent(LIVE_FOLDER_UPDATES);
        i.putExtra(FOLDER_UPDATE_TYPE_EXTRA, deleted ? FOLDER_DELETED : NEW_FOLDER_CREATED);
        i.putExtra(FOLDER_ID_EXTRA, info.id);
        i.setComponent(info.receiver);
        ctx.sendBroadcastAsUser(i, UserHandle.CURRENT_OR_SELF);
    }

    static void alertItemOpened(Context ctx, LiveFolderInfo info, LiveFolderItemInfo item) {
        Intent i = new Intent(LIVE_FOLDER_UPDATES);
        i.putExtra(FOLDER_UPDATE_TYPE_EXTRA, FOLDER_ITEM_SELECTED);
        i.putExtra(FOLDER_ID_EXTRA, info.id);
        i.putExtra(FOLDER_ITEM_TITLE_EXTRA, item.title);
        i.putExtra(FOLDER_ITEM_ID_EXTRA, item.item_id);
        i.setComponent(info.receiver);
        ctx.sendBroadcastAsUser(i, UserHandle.CURRENT_OR_SELF);
    }

    static void alertItemRemoved(Context ctx, LiveFolderInfo info, LiveFolderItemInfo item) {
        Intent i = new Intent(LIVE_FOLDER_UPDATES);
        i.putExtra(FOLDER_UPDATE_TYPE_EXTRA, FOLDER_ITEM_REMOVED);
        i.putExtra(FOLDER_ID_EXTRA, info.id);
        i.putExtra(FOLDER_ITEM_TITLE_EXTRA, item.title);
        i.putExtra(FOLDER_ITEM_ID_EXTRA, item.item_id);
        i.setComponent(info.receiver);
        ctx.sendBroadcastAsUser(i, UserHandle.CURRENT_OR_SELF);
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        // Verify item list
        ArrayList<LiveFolder.Item> items = intent.getParcelableArrayListExtra(
                FOLDER_ENTRIES_EXTRA);
        if (items == null || items.isEmpty()) {
            Log.e(TAG, "Cannot populate with empty items");
            return;
        }

        if (intent.hasExtra(FOLDER_ID_EXTRA)) {

            long id = intent.getLongExtra(FOLDER_ID_EXTRA, 0);
            if (id != 0) {
                synchronized (LauncherModel.sBgLock) {
                    FolderInfo folder = LauncherModel.sBgFolders.get(id);

                    if (folder == null || !(folder instanceof LiveFolderInfo)) {
                        Log.e(TAG, "No live folder found with id " + id);
                        return;
                    }

                    LiveFolderInfo fInfo = (LiveFolderInfo) folder;

                    if (!fInfo.isOwner(context, getSendingPackage(intent))) {
                        Log.e(TAG, "Cannot modify a folder that belongs to another package");
                        return;
                    }

                    fInfo.populateWithItems(items);

                    // Update folder title provided
                    if (intent.hasExtra(LiveFolder.Constants.FOLDER_TITLE_EXTRA)) {
                        String title = intent.getStringExtra(
                                LiveFolder.Constants.FOLDER_TITLE_EXTRA);
                        fInfo.title = title;
                    }
                }
            }

        } else if (intent.getBooleanExtra(FOLDER_UPDATE_ALL, false)) {

            synchronized (LauncherModel.sBgLock) {
                for (FolderInfo info : LauncherModel.sBgFolders.values()) {
                    if (info instanceof LiveFolderInfo) {
                        LiveFolderInfo fInfo = (LiveFolderInfo) info;

                        if (fInfo.isOwner(context, getSendingPackage(intent))) {
                            fInfo.populateWithItems(items);
                        }
                    }
                }
            }

        } else {
            Log.d(TAG, "No folder id specified");
        }
    }
}