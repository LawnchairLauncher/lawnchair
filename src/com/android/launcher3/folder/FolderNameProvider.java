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

import android.content.ComponentName;
import android.content.Context;
import android.os.Process;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;

import com.android.launcher3.AppInfo;
import com.android.launcher3.FolderInfo;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.WorkspaceItemInfo;
import com.android.launcher3.model.AllAppsList;
import com.android.launcher3.model.BaseModelUpdateTask;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.util.IntSparseArrayMap;
import com.android.launcher3.util.ResourceBasedOverride;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Locates provider for the folder name.
 */
public class FolderNameProvider implements ResourceBasedOverride {

    private static final String TAG = "FolderNameProvider";
    private static final boolean DEBUG = true;

    /**
     * IME usually has up to 3 suggest slots. In total, there are 4 suggest slots as the folder
     * name edit box can also be used to provide suggestion.
     */
    public static final int SUGGEST_MAX = 4;
    protected IntSparseArrayMap<FolderInfo> mFolderInfos;
    protected List<AppInfo> mAppInfos;

    /**
     * Retrieve instance of this object that can be overridden in runtime based on the build
     * variant of the application.
     */
    public static FolderNameProvider newInstance(Context context) {
        FolderNameProvider fnp = Overrides.getObject(FolderNameProvider.class,
                context.getApplicationContext(), R.string.folder_name_provider_class);
        fnp.load(context);

        return fnp;
    }

    public static FolderNameProvider newInstance(Context context, List<AppInfo> appInfos,
            IntSparseArrayMap<FolderInfo> folderInfos) {
        FolderNameProvider fnp = Overrides.getObject(FolderNameProvider.class,
                context.getApplicationContext(), R.string.folder_name_provider_class);
        fnp.load(appInfos, folderInfos);

        return fnp;
    }

    private void load(Context context) {
        LauncherAppState.getInstance(context).getModel().enqueueModelUpdateTask(
                new FolderNameWorker());
    }

    private void load(List<AppInfo> appInfos, IntSparseArrayMap<FolderInfo> folderInfos) {
        mAppInfos = appInfos;
        mFolderInfos = folderInfos;
    }

    /**
     * Generate and rank the suggested Folder names.
     */
    public void getSuggestedFolderName(Context context,
            ArrayList<WorkspaceItemInfo> workspaceItemInfos,
            FolderNameInfo[] nameInfos) {

        if (DEBUG) {
            Log.d(TAG, "getSuggestedFolderName:" + Arrays.toString(nameInfos));
        }
        // If all the icons are from work profile,
        // Then, suggest "Work" as the folder name
        Set<UserHandle> users = workspaceItemInfos.stream().map(w -> w.user)
                .collect(Collectors.toSet());
        if (users.size() == 1 && !users.contains(Process.myUserHandle())) {
            setAsLastSuggestion(nameInfos,
                    context.getResources().getString(R.string.work_folder_name));
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
            info.ifPresent(i -> setAsFirstSuggestion(nameInfos, i.title.toString()));
        }
        if (DEBUG) {
            Log.d(TAG, "getSuggestedFolderName:" + Arrays.toString(nameInfos));
        }
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

    private void setAsFirstSuggestion(FolderNameInfo[] nameInfos, CharSequence label) {
        if (nameInfos.length == 0 || contains(nameInfos, label)) {
            return;
        }
        for (int i = nameInfos.length - 1; i > 0; i--) {
            if (nameInfos[i - 1] != null && !TextUtils.isEmpty(nameInfos[i - 1].getLabel())) {
                nameInfos[i] = nameInfos[i - 1];
            }
        }
        nameInfos[0] = new FolderNameInfo(label, 1.0);
    }

    private void setAsLastSuggestion(FolderNameInfo[] nameInfos, CharSequence label) {
        if (nameInfos.length == 0 || contains(nameInfos, label)) {
            return;
        }

        for (int i = 0; i < nameInfos.length; i++) {
            if (nameInfos[i] == null || TextUtils.isEmpty(nameInfos[i].getLabel())) {
                nameInfos[i] = new FolderNameInfo(label, 1.0);
                return;
            }
        }
        // Overwrite the last suggestion.
        int lastIndex = nameInfos.length - 1;
        nameInfos[lastIndex] = new FolderNameInfo(label, 1.0);
    }

    private boolean contains(FolderNameInfo[] nameInfos, CharSequence label) {
        return Arrays.stream(nameInfos)
                .filter(Objects::nonNull)
                .anyMatch(nameInfo -> nameInfo.getLabel().toString().equalsIgnoreCase(
                        label.toString()));
    }

    private class FolderNameWorker extends BaseModelUpdateTask {
        @Override
        public void execute(LauncherAppState app, BgDataModel dataModel, AllAppsList apps) {
            mFolderInfos = dataModel.folders.clone();
            mAppInfos = Arrays.asList(apps.copyData());
        }
    }

}
