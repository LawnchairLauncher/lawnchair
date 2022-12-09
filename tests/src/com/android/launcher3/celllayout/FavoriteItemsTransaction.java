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

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.ui.AbstractLauncherUiTest;
import com.android.launcher3.util.ContentWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class FavoriteItemsTransaction {
    private ArrayList<ItemInfo> mItemsToSubmit;
    private Context mContext;
    private ContentResolver mResolver;
    public AbstractLauncherUiTest mTest;

    public FavoriteItemsTransaction(Context context, AbstractLauncherUiTest test) {
        mItemsToSubmit = new ArrayList<>();
        mContext = context;
        mResolver = mContext.getContentResolver();
        mTest = test;
    }

    public FavoriteItemsTransaction addItem(ItemInfo itemInfo) {
        this.mItemsToSubmit.add(itemInfo);
        return this;
    }

    public FavoriteItemsTransaction removeLast() {
        this.mItemsToSubmit.remove(this.mItemsToSubmit.size() - 1);
        return this;
    }

    /**
     * Commits all the ItemInfo into the database of Favorites
     **/
    public void commit() throws ExecutionException, InterruptedException {
        List<ContentValues> values = new ArrayList<>();
        for (ItemInfo item : this.mItemsToSubmit) {
            ContentWriter writer = new ContentWriter(mContext);
            item.onAddToDatabase(writer);
            writer.put(LauncherSettings.Favorites._ID, item.id);
            values.add(writer.getValues(mContext));
        }
        // Submit the icons to the database in the model thread to prevent race conditions
        MODEL_EXECUTOR.submit(() -> mResolver.bulkInsert(LauncherSettings.Favorites.CONTENT_URI,
                values.toArray(new ContentValues[0]))).get();
        // Reload the state of the Launcher
        MAIN_EXECUTOR.submit(() -> LauncherAppState.getInstance(
                mContext).getModel().forceReload()).get();
    }
}
