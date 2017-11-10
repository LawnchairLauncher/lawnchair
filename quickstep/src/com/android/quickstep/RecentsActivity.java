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
package com.android.quickstep;

import android.app.ListActivity;
import android.os.Bundle;
import android.os.UserHandle;
import android.support.annotation.Nullable;
import android.widget.ArrayAdapter;

import com.android.systemui.shared.recents.model.RecentsTaskLoadPlan;
import com.android.systemui.shared.recents.model.RecentsTaskLoader;
import com.android.systemui.shared.recents.model.Task;

/**
 * A simple activity to show the recently launched tasks
 */
public class RecentsActivity extends ListActivity {

    private ArrayAdapter<Task> mAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        RecentsTaskLoadPlan plan = new RecentsTaskLoadPlan(this);
        plan.preloadPlan(new RecentsTaskLoader(this, 1, 1, 0), -1, UserHandle.myUserId());

        mAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        mAdapter.addAll(plan.getTaskStack().getTasks());
        setListAdapter(mAdapter);
    }
}
