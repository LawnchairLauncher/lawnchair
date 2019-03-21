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
package com.android.quickstep;

import static androidx.recyclerview.widget.ItemTouchHelper.RIGHT;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

/**
 * Callback for swipe input on {@link TaskHolder} views in the recents view.
 */
public final class TaskSwipeCallback extends ItemTouchHelper.SimpleCallback {

    private final TaskActionController mTaskActionController;

    public TaskSwipeCallback(TaskActionController taskActionController) {
        super(0 /* dragDirs */, RIGHT);
        mTaskActionController = taskActionController;
    }

    @Override
    public boolean onMove(RecyclerView recyclerView, ViewHolder viewHolder,
            ViewHolder target) {
        return false;
    }

    @Override
    public void onSwiped(ViewHolder viewHolder, int direction) {
        if (direction == RIGHT) {
            mTaskActionController.removeTask((TaskHolder) viewHolder);
        }
    }
}
