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

import static com.android.launcher3.LauncherSettings.Favorites.TABLE_NAME;
import static com.android.launcher3.provider.LauncherDbUtils.itemIdMatch;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.content.ContentValues;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherModel.CallbackTask;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.Utilities;
import com.android.launcher3.celllayout.CellPosMapper;
import com.android.launcher3.celllayout.CellPosMapper.CellPos;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.model.BgDataModel.Callbacks;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.provider.LauncherDbUtils.SQLiteTransaction;
import com.android.launcher3.util.ContentWriter;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.LooperExecutor;
import com.android.launcher3.widget.LauncherWidgetHolder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Class for handling model updates.
 */
public class ModelWriter {

    private static final String TAG = "ModelWriter";

    private final Context mContext;
    private final LauncherModel mModel;
    private final BgDataModel mBgDataModel;
    private final LooperExecutor mUiExecutor;

    @Nullable
    private final Callbacks mOwner;

    private final boolean mVerifyChanges;

    // Keep track of delete operations that occur when an Undo option is present; we may not commit.
    private final List<ModelTask> mDeleteRunnables = new ArrayList<>();
    private boolean mPreparingToUndo;
    private final CellPosMapper mCellPosMapper;

    public ModelWriter(Context context, LauncherModel model, BgDataModel dataModel,
            boolean verifyChanges, CellPosMapper cellPosMapper, @Nullable Callbacks owner) {
        mContext = context;
        mModel = model;
        mBgDataModel = dataModel;
        mVerifyChanges = verifyChanges;
        mOwner = owner;
        mCellPosMapper = cellPosMapper;
        mUiExecutor = Executors.MAIN_EXECUTOR;
    }

    private void updateItemInfoProps(
            ItemInfo item, int container, int screenId, int cellX, int cellY) {
        CellPos modelPos = mCellPosMapper.mapPresenterToModel(cellX, cellY, screenId, container);

        item.container = container;
        item.cellX = modelPos.cellX;
        item.cellY = modelPos.cellY;
        item.screenId = modelPos.screenId;

    }

    /**
     * Adds an item to the DB if it was not created previously, or move it to a new
     * <container, screen, cellX, cellY>
     */
    public void addOrMoveItemInDatabase(ItemInfo item,
            int container, int screenId, int cellX, int cellY) {
        if (item.id == ItemInfo.NO_ID) {
            // From all apps
            addItemToDatabase(item, container, screenId, cellX, cellY);
        } else {
            // From somewhere else
            moveItemInDatabase(item, container, screenId, cellX, cellY);
        }
    }

    private void checkItemInfoLocked(int itemId, ItemInfo item, StackTraceElement[] stackTrace) {
        ItemInfo modelItem = mBgDataModel.itemsIdMap.get(itemId);
        if (modelItem != null && item != modelItem) {
            // check all the data is consistent
            if (!Utilities.IS_DEBUG_DEVICE && !FeatureFlags.IS_STUDIO_BUILD
                    && modelItem instanceof WorkspaceItemInfo
                    && item instanceof WorkspaceItemInfo) {
                if (modelItem.title.toString().equals(item.title.toString()) &&
                        modelItem.getIntent().filterEquals(item.getIntent()) &&
                        modelItem.id == item.id &&
                        modelItem.itemType == item.itemType &&
                        modelItem.container == item.container &&
                        modelItem.screenId == item.screenId &&
                        modelItem.cellX == item.cellX &&
                        modelItem.cellY == item.cellY &&
                        modelItem.spanX == item.spanX &&
                        modelItem.spanY == item.spanY) {
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
            int container, int screenId, int cellX, int cellY) {
        updateItemInfoProps(item, container, screenId, cellX, cellY);
        notifyItemModified(item);

        enqueueDeleteRunnable(new UpdateItemRunnable(item, () ->
                new ContentWriter(mContext)
                        .put(Favorites.CONTAINER, item.container)
                        .put(Favorites.CELLX, item.cellX)
                        .put(Favorites.CELLY, item.cellY)
                        .put(Favorites.RANK, item.rank)
                        .put(Favorites.SCREEN, item.screenId)));
    }

    /**
     * Move items in the DB to a new <container, screen, cellX, cellY>. We assume that the
     * cellX, cellY have already been updated on the ItemInfos.
     */
    public void moveItemsInDatabase(final ArrayList<ItemInfo> items, int container, int screen) {
        ArrayList<ContentValues> contentValues = new ArrayList<>();
        int count = items.size();
        notifyOtherCallbacks(c -> c.bindItemsModified(items));

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
        enqueueDeleteRunnable(new UpdateItemsRunnable(items, contentValues));
    }

    /**
     * Move and/or resize item in the DB to a new <container, screen, cellX, cellY, spanX, spanY>
     */
    public void modifyItemInDatabase(final ItemInfo item,
            int container, int screenId, int cellX, int cellY, int spanX, int spanY) {
        updateItemInfoProps(item, container, screenId, cellX, cellY);
        item.spanX = spanX;
        item.spanY = spanY;
        notifyItemModified(item);
        new UpdateItemRunnable(item, () ->
                new ContentWriter(mContext)
                        .put(Favorites.CONTAINER, item.container)
                        .put(Favorites.CELLX, item.cellX)
                        .put(Favorites.CELLY, item.cellY)
                        .put(Favorites.RANK, item.rank)
                        .put(Favorites.SPANX, item.spanX)
                        .put(Favorites.SPANY, item.spanY)
                        .put(Favorites.SCREEN, item.screenId))
                .executeOnModelThread();
    }

    /**
     * Update an item to the database in a specified container.
     */
    public void updateItemInDatabase(ItemInfo item) {
        notifyItemModified(item);
        new UpdateItemRunnable(item, () -> {
            ContentWriter writer = new ContentWriter(mContext);
            item.onAddToDatabase(writer);
            return writer;
        }).executeOnModelThread();
    }

    private void notifyItemModified(ItemInfo item) {
        notifyOtherCallbacks(c -> c.bindItemsModified(Collections.singletonList(item)));
    }

    /**
     * Add an item to the database in a specified container. Sets the container, screen, cellX and
     * cellY fields of the item. Also assigns an ID to the item.
     */
    public void addItemToDatabase(final ItemInfo item,
            int container, int screenId, int cellX, int cellY) {
        updateItemInfoProps(item, container, screenId, cellX, cellY);

        item.id = mModel.getModelDbController().generateNewItemId();
        notifyOtherCallbacks(c -> c.bindItems(Collections.singletonList(item), false));

        ModelVerifier verifier = new ModelVerifier();
        final StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        newModelTask(() -> {
            // Write the item on background thread, as some properties might have been updated in
            // the background.
            final ContentWriter writer = new ContentWriter(mContext);
            item.onAddToDatabase(writer);
            writer.put(Favorites._ID, item.id);

            mModel.getModelDbController().insert(Favorites.TABLE_NAME, writer.getValues(mContext));
            synchronized (mBgDataModel) {
                checkItemInfoLocked(item.id, item, stackTrace);
                mBgDataModel.addItem(mContext, item, true);
                verifier.verifyModel();
            }
        }).executeOnModelThread();
    }

    /**
     * Removes the specified item from the database
     */
    public void deleteItemFromDatabase(ItemInfo item, @Nullable final String reason) {
        deleteItemsFromDatabase(Arrays.asList(item), reason);
    }

    /**
     * Removes all the items from the database matching {@param matcher}.
     */
    public void deleteItemsFromDatabase(@NonNull final Predicate<ItemInfo> matcher,
            @Nullable final String reason) {
        deleteItemsFromDatabase(StreamSupport.stream(mBgDataModel.itemsIdMap.spliterator(), false)
                        .filter(matcher).collect(Collectors.toList()), reason);
    }

    /**
     * Removes the specified items from the database
     */
    public void deleteItemsFromDatabase(final Collection<? extends ItemInfo> items,
            @Nullable final String reason) {
        ModelVerifier verifier = new ModelVerifier();
        FileLog.d(TAG, "removing items from db " + items.stream().map(
                (item) -> item.getTargetComponent() == null ? ""
                        : item.getTargetComponent().getPackageName()).collect(
                Collectors.joining(","))
                + ". Reason: [" + (TextUtils.isEmpty(reason) ? "unknown" : reason) + "]");
        notifyDelete(items);
        enqueueDeleteRunnable(newModelTask(() -> {
            for (ItemInfo item : items) {
                mModel.getModelDbController().delete(TABLE_NAME, itemIdMatch(item.id), null);
                mBgDataModel.removeItem(mContext, item);
                verifier.verifyModel();
            }
        }));
    }

    /**
     * Remove the specified folder and all its contents from the database.
     */
    public void deleteFolderAndContentsFromDatabase(final FolderInfo info) {
        ModelVerifier verifier = new ModelVerifier();
        notifyDelete(Collections.singleton(info));

        enqueueDeleteRunnable(newModelTask(() -> {
            mModel.getModelDbController().delete(Favorites.TABLE_NAME,
                    Favorites.CONTAINER + "=" + info.id, null);
            mBgDataModel.removeItem(mContext, info.contents);
            info.contents.clear();

            mModel.getModelDbController().delete(Favorites.TABLE_NAME,
                    Favorites._ID + "=" + info.id, null);
            mBgDataModel.removeItem(mContext, info);
            verifier.verifyModel();
        }));
    }

    /**
     * Deletes the widget info and the widget id.
     */
    public void deleteWidgetInfo(final LauncherAppWidgetInfo info, LauncherWidgetHolder holder,
            @Nullable final String reason) {
        notifyDelete(Collections.singleton(info));
        if (holder != null && !info.isCustomWidget() && info.isWidgetIdAllocated()) {
            // Deleting an app widget ID is a void call but writes to disk before returning
            // to the caller...
            enqueueDeleteRunnable(newModelTask(() -> holder.deleteAppWidgetId(info.appWidgetId)));
        }
        deleteItemFromDatabase(info, reason);
    }

    private void notifyDelete(Collection<? extends ItemInfo> items) {
        notifyOtherCallbacks(c -> c.bindWorkspaceComponentsRemoved(ItemInfoMatcher.ofItems(items)));
    }

    /**
     * Delete operations tracked using {@link #enqueueDeleteRunnable} will only be called
     * if {@link #commitDelete} is called. Note that one of {@link #commitDelete()} or
     * {@link #abortDelete} MUST be called after this method, or else all delete
     * operations will remain uncommitted indefinitely.
     */
    public void prepareToUndoDelete() {
        if (!mPreparingToUndo) {
            if (!mDeleteRunnables.isEmpty() && FeatureFlags.IS_STUDIO_BUILD) {
                throw new IllegalStateException("There are still uncommitted delete operations!");
            }
            mDeleteRunnables.clear();
            mPreparingToUndo = true;
        }
    }

    /**
     * If {@link #prepareToUndoDelete} has been called, we store the Runnable to be run when
     * {@link #commitDelete()} is called (or abandoned if {@link #abortDelete} is called).
     * Otherwise, we run the Runnable immediately.
     */
    private void enqueueDeleteRunnable(ModelTask r) {
        if (mPreparingToUndo) {
            mDeleteRunnables.add(r);
        } else {
            r.executeOnModelThread();
        }
    }

    public void commitDelete() {
        mPreparingToUndo = false;
        mDeleteRunnables.forEach(ModelTask::executeOnModelThread);
        mDeleteRunnables.clear();
    }

    /**
     * Aborts a previous delete operation pending commit
     */
    public void abortDelete() {
        mPreparingToUndo = false;
        mDeleteRunnables.clear();
        // We do a full reload here instead of just a rebind because Folders change their internal
        // state when dragging an item out, which clobbers the rebind unless we load from the DB.
        mModel.forceReload();
    }

    private void notifyOtherCallbacks(CallbackTask task) {
        if (mOwner == null) {
            // If the call is happening from a model, it will take care of updating the callbacks
            return;
        }
        mUiExecutor.execute(() -> {
            for (Callbacks c : mModel.getCallbacks()) {
                if (c != mOwner) {
                    task.execute(c);
                }
            }
        });
    }

    private class UpdateItemRunnable extends UpdateItemBaseRunnable {
        private final ItemInfo mItem;
        private final Supplier<ContentWriter> mWriter;
        private final int mItemId;

        UpdateItemRunnable(ItemInfo item, Supplier<ContentWriter> writer) {
            mItem = item;
            mWriter = writer;
            mItemId = item.id;
        }

        @Override
        public void runImpl() {
            mModel.getModelDbController().update(
                    TABLE_NAME, mWriter.get().getValues(mContext), itemIdMatch(mItemId), null);
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
        public void runImpl() {
            try (SQLiteTransaction t = mModel.getModelDbController().newTransaction()) {
                int count = mItems.size();
                for (int i = 0; i < count; i++) {
                    ItemInfo item = mItems.get(i);
                    final int itemId = item.id;
                    mModel.getModelDbController().update(
                            TABLE_NAME, mValues.get(i), itemIdMatch(itemId), null);
                    updateItemArrays(item, itemId);
                }
                t.commit();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private abstract class UpdateItemBaseRunnable extends ModelTask {
        private final StackTraceElement[] mStackTrace;
        private final ModelVerifier mVerifier = new ModelVerifier();

        UpdateItemBaseRunnable() {
            mStackTrace = new Throwable().getStackTrace();
        }

        protected void updateItemArrays(ItemInfo item, int itemId) {
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
                        case Favorites.ITEM_TYPE_DEEP_SHORTCUT:
                        case Favorites.ITEM_TYPE_FOLDER:
                        case Favorites.ITEM_TYPE_APP_PAIR:
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
                mVerifier.verifyModel();
            }
        }
    }

    private abstract class ModelTask implements Runnable {

        private final int mLoadId = mBgDataModel.lastLoadId;

        @Override
        public final void run() {
            if (mLoadId != mModel.getLastLoadId()) {
                Log.d(TAG, "Model changed before the task could execute");
                return;
            }
            runImpl();
        }

        public final void executeOnModelThread() {
            MODEL_EXECUTOR.execute(this);
        }

        public abstract void runImpl();
    }

    private ModelTask newModelTask(Runnable r) {
        return new ModelTask() {
            @Override
            public void runImpl() {
                r.run();
            }
        };
    }

    /**
     * Utility class to verify model updates are propagated properly to the callback.
     */
    public class ModelVerifier {

        final int startId;

        ModelVerifier() {
            startId = mBgDataModel.lastBindId;
        }

        void verifyModel() {
            if (!mVerifyChanges || !mModel.hasCallbacks()) {
                return;
            }

            int executeId = mBgDataModel.lastBindId;

            mUiExecutor.post(() -> {
                int currentId = mBgDataModel.lastBindId;
                if (currentId > executeId) {
                    // Model was already bound after job was executed.
                    return;
                }
                if (executeId == startId) {
                    // Bound model has not changed during the job
                    return;
                }

                // Bound model was changed between submitting the job and executing the job
                mModel.rebindCallbacks();
            });
        }
    }
}
