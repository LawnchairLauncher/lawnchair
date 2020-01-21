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

import android.content.Context;
import android.os.Process;
import android.text.TextUtils;
import android.util.Log;

import com.android.launcher3.AppInfo;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.WorkspaceItemInfo;
import com.android.launcher3.config.FeatureFlags;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Locates provider for the folder name.
 */
public class FolderNameProvider {

    private static final String TAG = "FolderNameProvider";
    private static final boolean DEBUG = FeatureFlags.FOLDER_NAME_SUGGEST.get();

    /**
     * IME usually has up to 3 suggest slots. In total, there are 4 suggest slots as the folder
     * name edit box can also be used to provide suggestion.
     */
    public static final int SUGGEST_MAX = 4;

    /**
     * When inheriting class requires precaching, override this method.
     */
    public void load(Context context) {}

    public CharSequence getSuggestedFolderName(Context context,
            ArrayList<WorkspaceItemInfo> workspaceItemInfos, CharSequence[] candidates) {

        if (DEBUG) {
            Log.d(TAG, "getSuggestedFolderName:" + Arrays.toString(candidates));
        }
        // If all the icons are from work profile,
        // Then, suggest "Work" as the folder name
        List<WorkspaceItemInfo> distinctItemInfos = workspaceItemInfos.stream()
                .filter(distinctByKey(p-> p.user))
                .collect(Collectors.toList());

        if (distinctItemInfos.size() == 1
                && !distinctItemInfos.get(0).user.equals(Process.myUserHandle())) {
            // Place it as last viable suggestion
            setAsLastSuggestion(candidates,
                    context.getResources().getString(R.string.work_folder_name));
        }

        // If all the icons are from same package (e.g., main icon, shortcut, shortcut)
        // Then, suggest the package's title as the folder name
        distinctItemInfos = workspaceItemInfos.stream()
                .filter(distinctByKey(p-> p.getTargetComponent() != null
                        ? p.getTargetComponent().getPackageName() : ""))
                .collect(Collectors.toList());

        if (distinctItemInfos.size() == 1) {
            Optional<AppInfo> info = LauncherAppState.getInstance(context).getModel()
                    .getAppInfoByPackageName(distinctItemInfos.get(0).getTargetComponent()
                            .getPackageName());
            // Place it as first viable suggestion and shift everything else
            info.ifPresent(i -> setAsFirstSuggestion(candidates, i.title.toString()));
        }
        if (DEBUG) {
            Log.d(TAG, "getSuggestedFolderName:" + Arrays.toString(candidates));
        }
        return candidates[0];
    }

    private void setAsFirstSuggestion(CharSequence[] candidatesOut, CharSequence candidate) {
        if (contains(candidatesOut, candidate)) {
            return;
        }
        for (int i = candidatesOut.length - 1; i > 0; i--) {
            if (!TextUtils.isEmpty(candidatesOut[i - 1])) {
                candidatesOut[i] = candidatesOut[i - 1];
            }
        }
        candidatesOut[0] = candidate;
    }

    private void setAsLastSuggestion(CharSequence[] candidatesOut, CharSequence candidate) {
        if (contains(candidatesOut, candidate)) {
            return;
        }
        for (int i = 0; i < candidate.length(); i++) {
            if (TextUtils.isEmpty(candidatesOut[i])) {
                candidatesOut[i] = candidate;
            }
        }
    }

    private boolean contains(CharSequence[] list, CharSequence key) {
        return Arrays.asList(list).stream()
                .filter(s -> s != null)
                .anyMatch(s -> s.toString().equalsIgnoreCase(key.toString()));
    }

    // This method can be moved to some Utility class location.
    private static <T> Predicate<T> distinctByKey(Function<? super T, Object> keyExtractor) {
        Map<Object, Boolean> map = new ConcurrentHashMap<>();
        return t -> map.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
    }
}
