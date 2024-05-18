/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3.celllayout;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.launcher3.LauncherSettings.Favorites.TABLE_NAME;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;
import static com.android.launcher3.util.TestUtil.runOnExecutorSync;

import android.content.Context;

import androidx.test.uiautomator.UiDevice;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.model.ModelDbController;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.provider.LauncherDbUtils.SQLiteTransaction;
import com.android.launcher3.tapl.LauncherInstrumentation;
import com.android.launcher3.util.ContentWriter;
import com.android.launcher3.util.ModelTestExtensions;

import java.util.ArrayList;
import java.util.function.Supplier;

public class FavoriteItemsTransaction {
    private ArrayList<Supplier<ItemInfo>> mItemsToSubmit;
    private Context mContext;

    public FavoriteItemsTransaction(Context context) {
        mItemsToSubmit = new ArrayList<>();
        mContext = context;
    }

    public FavoriteItemsTransaction addItem(Supplier<ItemInfo> itemInfo) {
        this.mItemsToSubmit.add(itemInfo);
        return this;
    }

    /**
     * Commits all the ItemInfo into the database of Favorites
     **/
    public void commit() {
        LauncherModel model = LauncherAppState.getInstance(mContext).getModel();
        // Load the model once so that there is no pending migration:
        ModelTestExtensions.INSTANCE.loadModelSync(model);
        runOnExecutorSync(MODEL_EXECUTOR, () -> {
            ModelDbController controller = model.getModelDbController();
            // Migrate any previous data so that the DB state is correct
            controller.tryMigrateDB();

            // Create DB again to load fresh data
            controller.createEmptyDB();
            controller.clearEmptyDbFlag();

            // Add new data
            try (SQLiteTransaction transaction = controller.newTransaction()) {
                int count = mItemsToSubmit.size();
                ArrayList<ItemInfo> containerItems = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    ContentWriter writer = new ContentWriter(mContext);
                    ItemInfo item = mItemsToSubmit.get(i).get();

                    if (item.itemType == LauncherSettings.Favorites.ITEM_TYPE_FOLDER) {
                        FolderInfo folderInfo = (FolderInfo) item;
                        for (ItemInfo itemInfo : folderInfo.contents) {
                            itemInfo.container = i;
                            containerItems.add(itemInfo);
                        }
                    }

                    item.onAddToDatabase(writer);
                    writer.put(LauncherSettings.Favorites._ID, i);
                    controller.insert(TABLE_NAME, writer.getValues(mContext));
                }

                for (int i = 0; i < containerItems.size(); i++) {
                    ContentWriter writer = new ContentWriter(mContext);
                    ItemInfo item = containerItems.get(i);
                    item.onAddToDatabase(writer);
                    writer.put(LauncherSettings.Favorites._ID, count + i);
                    controller.insert(TABLE_NAME, writer.getValues(mContext));
                }
                transaction.commit();
            }
        });

        // Reload model
        runOnExecutorSync(MAIN_EXECUTOR, model::forceReload);
        ModelTestExtensions.INSTANCE.loadModelSync(model);
    }

    /**
     * Commits the transaction and waits for home load
     */
    public void commitAndLoadHome(LauncherInstrumentation inst) {
        commit();

        // Launch the home activity
        UiDevice.getInstance(getInstrumentation()).pressHome();
        inst.waitForLauncherInitialized();
    }
}
