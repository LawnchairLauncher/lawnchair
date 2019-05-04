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

import static com.android.quickstep.TaskAdapter.ITEM_TYPE_CLEAR_ALL;

import android.graphics.Canvas;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import java.util.function.Consumer;

/**
 * Callback for swipe input on {@link TaskHolder} views in the recents view.
 */
public final class TaskSwipeCallback extends ItemTouchHelper.SimpleCallback {

    private final Consumer<TaskHolder> mOnTaskSwipeCallback;

    public TaskSwipeCallback(Consumer<TaskHolder> onTaskSwipeCallback) {
        super(0 /* dragDirs */, RIGHT);
        mOnTaskSwipeCallback = onTaskSwipeCallback;
    }

    @Override
    public boolean onMove(RecyclerView recyclerView, ViewHolder viewHolder,
            ViewHolder target) {
        return false;
    }

    @Override
    public void onSwiped(ViewHolder viewHolder, int direction) {
        if (direction == RIGHT) {
            mOnTaskSwipeCallback.accept((TaskHolder) viewHolder);
        }
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
            @NonNull ViewHolder viewHolder, float dX, float dY, int actionState,
            boolean isCurrentlyActive) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            float alpha = 1.0f - dX / (float) viewHolder.itemView.getWidth();
            viewHolder.itemView.setAlpha(alpha);
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY,
                    actionState, isCurrentlyActive);
    }

    @Override
    public int getSwipeDirs(@NonNull RecyclerView recyclerView,
            @NonNull ViewHolder viewHolder) {
        if (viewHolder.getItemViewType() == ITEM_TYPE_CLEAR_ALL) {
            // Clear all button should not be swipable.
            return 0;
        }
        return super.getSwipeDirs(recyclerView, viewHolder);
    }
}
