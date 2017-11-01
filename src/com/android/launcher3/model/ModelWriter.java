/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3.model;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.android.launcher3.FolderInfo;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherProvider;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.LauncherSettings.Settings;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.util.ContentWriter;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.LooperExecuter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Executor;

/**
 * Class for handling model updates.
 */
public class ModelWriter {

    private static final String TAG = "ModelWriter";

    private final Context mContext;
    private final BgDataModel mBgDataModel;
    private final Executor mWorkerExecutor;
    private final boolean mHasVerticalHotseat;

    public ModelWriter(Context context, BgDataModel dataModel, boolean hasVerticalHotseat) {
        mContext = context;
        mBgDataModel = dataModel;
        mWorkerExecutor = new LooperExecuter(LauncherModel.getWorkerLooper());
        mHasVerticalHotseat = hasVerticalHotseat;
    }

    private void updateItemInfoProps(
            ItemInfo item, long container, long screenId, int cellX, int cellY) {
        item.container = container;
        item.cellX = cellX;
        item.cellY = cellY;
        // We store hotseat items in canonical form which is this orientation invariant position
        // in the hotseat
        if (container == Favorites.CONTAINER_HOTSEAT) {
            item.screenId = mHasVerticalHotseat
                    ? LauncherAppState.getIDP(mContext).numHotseatIcons - cellY - 1 : cellX;
        } else {
            item.screenId = screenId;
        }
    }

    /**
     * Adds an item to the DB if it was not created previously, or move it to a new
     * <container, screen, cellX, cellY>
     */
    public void addOrMoveItemInDatabase(ItemInfo item,
            long container, long screenId, int cellX, int cellY) {
        if (item.container == ItemInfo.NO_ID) {
            // From all apps
            addItemToDatabase(item, container, screenId, cellX, cellY);
        } else {
            // From somewhere else
            moveItemInDatabase(item, container, screenId, cellX, cellY);
        }
    }

    private void checkItemInfoLocked(long itemId, ItemInfo item, StackTraceElement[] stackTrace) {
        ItemInfo modelItem = mBgDataModel.itemsIdMap.get(itemId);
        if (modelItem != null && item != modelItem) {
            // check all the data is consistent
            if (modelItem instanceof ShortcutInfo && item instanceof ShortcutInfo) {
                ShortcutInfo modelShortcut = (ShortcutInfo) modelItem;
                ShortcutInfo shortcut = (ShortcutInfo) item;
                if (modelShortcut.title.toString().equals(shortcut.title.toString()) &&
                        modelShortcut.intent.filterEquals(shortcut.intent) &&
                        modelShortcut.id == shortcut.id &&
                        modelShortcut.itemType == shortcut.itemType &&
                        modelShortcut.container == shortcut.container &&
                        modelShortcut.screenId == shortcut.screenId &&
                        modelShortcut.cellX == shortcut.cellX &&
                        modelShortcut.cellY == shortcut.cellY &&
                        modelShortcut.spanX == shortcut.spanX &&
                        modelShortcut.spanY == shortcut.spanY) {
                    // For all intents and purposes, this is the same object
                    return;
                }
            }

            // the modelItem needs to match up perfectly with item if our model is
            // to be consistent with the database-- for now, just require
            // modelItem == item or the equality check above
            String msg = "item: " + ((item != null) ? item.toString() : "null") +
                    "modelItem: " +
                    ((modelItem != null) ? modelItem.toString() : "null") +
                    "Error: ItemInfo passed to checkItemInfo doesn't match original";
            RuntimeException e = new RuntimeException(msg);
            if (stackTrace != null) {
                e.setStackTrace(stackTrace);
            }
            throw e;
        }
    }

    /**
     * Move an item in the DB to a new <container, screen, cellX, cellY>
     */
    public void moveItemInDatabase(final ItemInfo item,
            long container, long screenId, int cellX, int cellY) {
        updateItemInfoProps(item, container, screenId, cellX, cellY);

        final ContentWriter writer = new ContentWriter(mContext)
                .put(Favorites.CONTAINER, item.container)
                .put(Favorites.CELLX, item.cellX)
                .put(Favorites.CELLY, item.cellY)
                .put(Favorites.RANK, item.rank)
                .put(Favorites.SCREEN, item.screenId);

        mWorkerExecutor.execute(new UpdateItemRunnable(item, writer));
    }

    /**
     * Move items in the DB to a new <container, screen, cellX, cellY>. We assume that the
     * cellX, cellY have already been updated on the ItemInfos.
     */
    public void moveItemsInDatabase(final ArrayList<ItemInfo> items, long container, int screen) {
        ArrayList<ContentValues> contentValues = new ArrayList<>();
        int count = items.size();

        for (int i = 0; i < count; i++) {
            ItemInfo item = items.get(i);
            updateItemInfoProps(item, container, screen, item.cellX, item.cellY);

            final ContentValues values = new ContentValues();
            values.put(Favorites.CONTAINER, item.container);
            values.put(Favorites.CELLX, item.cellX);
            values.put(Favorites.CELLY, item.cellY);
            values.put(Favorites.RANK, item.rank);
            values.put(Favorites.SCREEN, item.screenId);

            contentValues.add(values);
        }
        mWorkerExecutor.execute(new UpdateItemsRunnable(items, contentValues));
    }

    /**
     * Move and/or resize item in the DB to a new <container, screen, cellX, cellY, spanX, spanY>
     */
    public void modifyItemInDatabase(final ItemInfo item,
            long container, long screenId, int cellX, int cellY, int spanX, int spanY) {
        updateItemInfoProps(item, container, screenId, cellX, cellY);
        item.spanX = spanX;
        item.spanY = spanY;

        final ContentWriter writer = new ContentWriter(mContext)
                .put(Favorites.CONTAINER, item.container)
                .put(Favorites.CELLX, item.cellX)
                .put(Favorites.CELLY, item.cellY)
                .put(Favorites.RANK, item.rank)
                .put(Favorites.SPANX, item.spanX)
                .put(Favorites.SPANY, item.spanY)
                .put(Favorites.SCREEN, item.screenId);

        mWorkerExecutor.execute(new UpdateItemRunnable(item, writer));
    }

    /**
     * Update an item to the database in a specified container.
     */
    public void updateItemInDatabase(ItemInfo item) {
        ContentWriter writer = new ContentWriter(mContext);
        item.onAddToDatabase(writer);
        mWorkerExecutor.execute(new UpdateItemRunnable(item, writer));
    }

    /**
     * Add an item to the database in a specified container. Sets the container, screen, cellX and
     * cellY fields of the item. Also assigns an ID to the item.
     */
    public void addItemToDatabase(final ItemInfo item,
            long container, long screenId, int cellX, int cellY) {
        updateItemInfoProps(item, container, screenId, cellX, cellY);

        final ContentWriter writer = new ContentWriter(mContext);
        final ContentResolver cr = mContext.getContentResolver();
        item.onAddToDatabase(writer);

        item.id = Settings.call(cr, Settings.METHOD_NEW_ITEM_ID).getLong(Settings.EXTRA_VALUE);
        writer.put(Favorites._ID, item.id);

        final StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        mWorkerExecutor.execute(new Runnable() {
            public void run() {
                cr.insert(Favorites.CONTENT_URI, writer.getValues(mContext));

                synchronized (mBgDataModel) {
                    checkItemInfoLocked(item.id, item, stackTrace);
                    mBgDataModel.addItem(mContext, item, true);
                }
            }
        });
    }

    /**
     * Removes the specified item from the database
     */
    public void deleteItemFromDatabase(ItemInfo item) {
        deleteItemsFromDatabase(Arrays.asList(item));
    }

    /**
     * Removes all the items from the database matching {@param matcher}.
     */
    public void deleteItemsFromDatabase(ItemInfoMatcher matcher) {
        deleteItemsFromDatabase(matcher.filterItemInfos(mBgDataModel.itemsIdMap));
    }

    /**
     * Removes the specified items from the database
     */
    public void deleteItemsFromDatabase(final Iterable<? extends ItemInfo> items) {
        mWorkerExecutor.execute(new Runnable() {
            public void run() {
                for (ItemInfo item : items) {
                    final Uri uri = Favorites.getContentUri(item.id);
                    mContext.getContentResolver().delete(uri, null, null);

                    mBgDataModel.removeItem(mContext, item);
                }
            }
        });
    }

    /**
     * Remove the specified folder and all its contents from the database.
     */
    public void deleteFolderAndContentsFromDatabase(final FolderInfo info) {
        mWorkerExecutor.execute(new Runnable() {
            public void run() {
                ContentResolver cr = mContext.getContentResolver();
                cr.delete(LauncherSettings.Favorites.CONTENT_URI,
                        LauncherSettings.Favorites.CONTAINER + "=" + info.id, null);
                mBgDataModel.removeItem(mContext, info.contents);
                info.contents.clear();

                cr.delete(LauncherSettings.Favorites.getContentUri(info.id), null, null);
                mBgDataModel.removeItem(mContext, info);
            }
        });
    }

    private class UpdateItemRunnable extends UpdateItemBaseRunnable {
        private final ItemInfo mItem;
        private final ContentWriter mWriter;
        private final long mItemId;

        UpdateItemRunnable(ItemInfo item, ContentWriter writer) {
            mItem = item;
            mWriter = writer;
            mItemId = item.id;
        }

        @Override
        public void run() {
            Uri uri = Favorites.getContentUri(mItemId);
            mContext.getContentResolver().update(uri, mWriter.getValues(mContext), null, null);
            updateItemArrays(mItem, mItemId);
        }
    }

    private class UpdateItemsRunnable extends UpdateItemBaseRunnable {
        private final ArrayList<ContentValues> mValues;
        private final ArrayList<ItemInfo> mItems;

        UpdateItemsRunnable(ArrayList<ItemInfo> items, ArrayList<ContentValues> values) {
            mValues = values;
            mItems = items;
        }

        @Override
        public void run() {
            ArrayList<ContentProviderOperation> ops = new ArrayList<>();
            int count = mItems.size();
            for (int i = 0; i < count; i++) {
                ItemInfo item = mItems.get(i);
                final long itemId = item.id;
                final Uri uri = Favorites.getContentUri(itemId);
                ContentValues values = mValues.get(i);

                ops.add(ContentProviderOperation.newUpdate(uri).withValues(values).build());
                updateItemArrays(item, itemId);
            }
            try {
                mContext.getContentResolver().applyBatch(LauncherProvider.AUTHORITY, ops);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private abstract class UpdateItemBaseRunnable implements Runnable {
        private final StackTraceElement[] mStackTrace;

        UpdateItemBaseRunnable() {
            mStackTrace = new Throwable().getStackTrace();
        }

        protected void updateItemArrays(ItemInfo item, long itemId) {
            // Lock on mBgLock *after* the db operation
            synchronized (mBgDataModel) {
                checkItemInfoLocked(itemId, item, mStackTrace);

                if (item.container != Favorites.CONTAINER_DESKTOP &&
                        item.container != Favorites.CONTAINER_HOTSEAT) {
                    // Item is in a folder, make sure this folder exists
                    if (!mBgDataModel.folders.containsKey(item.container)) {
                        // An items container is being set to a that of an item which is not in
                        // the list of Folders.
                        String msg = "item: " + item + " container being set to: " +
                                item.container + ", not in the list of folders";
                        Log.e(TAG, msg);
                    }
                }

                // Items are added/removed from the corresponding FolderInfo elsewhere, such
                // as in Workspace.onDrop. Here, we just add/remove them from the list of items
                // that are on the desktop, as appropriate
                ItemInfo modelItem = mBgDataModel.itemsIdMap.get(itemId);
                if (modelItem != null &&
                        (modelItem.container == Favorites.CONTAINER_DESKTOP ||
                                modelItem.container == Favorites.CONTAINER_HOTSEAT)) {
                    switch (modelItem.itemType) {
                        case Favorites.ITEM_TYPE_APPLICATION:
                        case Favorites.ITEM_TYPE_SHORTCUT:
                        case Favorites.ITEM_TYPE_DEEP_SHORTCUT:
                        case Favorites.ITEM_TYPE_FOLDER:
                            if (!mBgDataModel.workspaceItems.contains(modelItem)) {
                                mBgDataModel.workspaceItems.add(modelItem);
                            }
                            break;
                        default:
                            break;
                    }
                } else {
                    mBgDataModel.workspaceItems.remove(modelItem);
                }
            }
        }
    }
}
