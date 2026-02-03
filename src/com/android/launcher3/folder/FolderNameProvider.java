/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.launcher3.folder;

import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER;

import android.annotation.SuppressLint;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Process;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.WorkerThread;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.StringCache;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.CollectionInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.IntSparseArrayMap;
import com.android.launcher3.util.Preconditions;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

/**
 * Locates provider for the folder name.
 */
public class FolderNameProvider {

    private static final String TAG = "FolderNameProvider";
    private static final boolean DEBUG = false;

    /**
     * IME usually has up to 3 suggest slots. In total, there are 4 suggest slots as the folder
     * name edit box can also be used to provide suggestion.
     */
    public static final int SUGGEST_MAX = 4;
    protected IntSparseArrayMap<CollectionInfo> mCollectionInfos;
    protected List<AppInfo> mAppInfos;

    /**
     * FolderNameProvider should be constructed on a background thread always.
     * If you want to inject FolderNameProvider, use {@link FolderNameSuggestionLoader} or use
     * {@link javax.inject.Provider} to create the FolderNameProvider in background thread
     */
    @Inject
    public FolderNameProvider() {
        Preconditions.assertWorkerThread();
    }

    public void load(List<AppInfo> appInfos, IntSparseArrayMap<CollectionInfo> folderInfos) {
        Preconditions.assertWorkerThread();
        mAppInfos = appInfos;
        mCollectionInfos = folderInfos;
    }

    /**
     * Generate and rank the suggested Folder names.
     */
    @WorkerThread
    public void getSuggestedFolderName(Context context,
            ArrayList<WorkspaceItemInfo> workspaceItemInfos,
            FolderNameInfos nameInfos) {
        Preconditions.assertWorkerThread();
        if (DEBUG) {
            Log.d(TAG, "getSuggestedFolderName:" + nameInfos.toString());
        }

        // If all the icons are from work profile,
        // Then, suggest "Work" as the folder name
        Set<UserHandle> users = workspaceItemInfos.stream().map(w -> w.user)
                .collect(Collectors.toSet());
        if (users.size() == 1 && !users.contains(Process.myUserHandle())) {
            setAsLastSuggestion(nameInfos, getWorkFolderName(context));
        }

        // If all the icons are from same package (e.g., main icon, shortcut, shortcut)
        // Then, suggest the package's title as the folder name
        Set<String> packageNames = workspaceItemInfos.stream()
                .map(WorkspaceItemInfo::getTargetComponent)
                .filter(Objects::nonNull)
                .map(ComponentName::getPackageName)
                .collect(Collectors.toSet());

        if (packageNames.size() == 1) {
            Optional<AppInfo> info = getAppInfoByPackageName(packageNames.iterator().next());
            // Place it as first viable suggestion and shift everything else
            info.ifPresent(i -> setAsFirstSuggestion(
                    nameInfos, i.title == null ? "" : i.title.toString()));
        }
        if (DEBUG) {
            Log.d(TAG, "getSuggestedFolderName:" + nameInfos.toString());
        }
    }

    @WorkerThread
    @SuppressLint("NewApi")
    private String getWorkFolderName(Context context) {
        if (!Utilities.ATLEAST_T) {
            return context.getString(R.string.work_folder_name);
        }
        return context.getSystemService(DevicePolicyManager.class).getResources()
                .getString(StringCache.WORK_FOLDER_NAME, () ->
                        context.getString(R.string.work_folder_name));
    }

    private Optional<AppInfo> getAppInfoByPackageName(String packageName) {
        if (mAppInfos == null || mAppInfos.isEmpty()) {
            return Optional.empty();
        }
        return mAppInfos.stream()
                .filter(info -> info.componentName != null)
                .filter(info -> info.componentName.getPackageName().equals(packageName))
                .findAny();
    }

    private void setAsFirstSuggestion(FolderNameInfos nameInfos, CharSequence label) {
        if (nameInfos == null || nameInfos.contains(label)) {
            return;
        }
        nameInfos.setStatus(FolderNameInfos.HAS_PRIMARY);
        nameInfos.setStatus(FolderNameInfos.HAS_SUGGESTIONS);
        CharSequence[] labels = nameInfos.getLabels();
        Float[] scores = nameInfos.getScores();
        for (int i = labels.length - 1; i > 0; i--) {
            if (labels[i - 1] != null && !TextUtils.isEmpty(labels[i - 1])) {
                nameInfos.setLabel(i, labels[i - 1], scores[i - 1]);
            }
        }
        nameInfos.setLabel(0, label, 1.0f);
    }

    private void setAsLastSuggestion(FolderNameInfos nameInfos, CharSequence label) {
        if (nameInfos == null || nameInfos.contains(label)) {
            return;
        }
        nameInfos.setStatus(FolderNameInfos.HAS_PRIMARY);
        nameInfos.setStatus(FolderNameInfos.HAS_SUGGESTIONS);
        CharSequence[] labels = nameInfos.getLabels();
        for (int i = 0; i < labels.length; i++) {
            if (labels[i] == null || TextUtils.isEmpty(labels[i])) {
                nameInfos.setLabel(i, label, 1.0f);
                return;
            }
        }
        // Overwrite the last suggestion.
        nameInfos.setLabel(labels.length - 1, label, 1.0f);
    }

    public static IntSparseArrayMap<CollectionInfo> getCollectionForSuggestions(
            BgDataModel dataModel) {
        IntSparseArrayMap<CollectionInfo> result = new IntSparseArrayMap<>();
        dataModel.itemsIdMap.stream()
                .filter(item -> item.itemType == ITEM_TYPE_FOLDER)
                .forEach(item -> result.put(item.id, (FolderInfo) item));
        return result;
    }

}
