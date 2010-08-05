/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.launcher2;

import com.android.launcher.R;

import android.content.Context;
import android.content.pm.ResolveInfo;

public class FolderListAdapter extends IntentListAdapter {

    public FolderListAdapter(Context context, String actionFilter) {
        super(context, actionFilter);

        // Manually create a separate entry for creating a folder in Launcher
        ResolveInfo folder = new ResolveInfo();
        folder.icon = R.drawable.ic_launcher_folder;
        folder.labelRes = R.string.group_folder;
        folder.resolvePackageName = context.getPackageName();
        mIntentList.add(0, folder);
    }
}
