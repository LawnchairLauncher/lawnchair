/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.widget.picker;

import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.makeMeasureSpec;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.android.launcher3.recyclerview.ViewHolderBinder;
import com.android.launcher3.widget.model.WidgetListSpaceEntry;

import java.util.List;
import java.util.function.IntSupplier;

/**
 * {@link ViewHolderBinder} for binding the top empty space
 */
public class WidgetsSpaceViewHolderBinder
        implements ViewHolderBinder<WidgetListSpaceEntry, ViewHolder> {

    private final IntSupplier mEmptySpaceHeightProvider;

    public WidgetsSpaceViewHolderBinder(IntSupplier emptySpaceHeightProvider) {
        mEmptySpaceHeightProvider = emptySpaceHeightProvider;
    }

    @Override
    public ViewHolder newViewHolder(ViewGroup parent) {
        return new ViewHolder(new EmptySpaceView(parent.getContext())) { };
    }

    @Override
    public void bindViewHolder(ViewHolder holder, WidgetListSpaceEntry data,
            @ListPosition int position, List<Object> payloads) {
        ((EmptySpaceView) holder.itemView).setFixedHeight(mEmptySpaceHeightProvider.getAsInt());
    }

    /**
     * Empty view which allows listening for 'Y' changes
     */
    public static class EmptySpaceView extends View {

        private Runnable mOnYChangeCallback;
        private int mHeight = 0;

        private EmptySpaceView(Context context) {
            super(context);
            animate().setUpdateListener(v -> notifyYChanged());
        }

        /**
         * Sets the height for the empty view
         * @return true if the height changed, false otherwise
         */
        public boolean setFixedHeight(int height) {
            if (mHeight != height) {
                mHeight = height;
                requestLayout();
                return true;
            }
            return false;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, makeMeasureSpec(mHeight, EXACTLY));
        }

        public void setOnYChangeCallback(Runnable callback) {
            mOnYChangeCallback = callback;
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            notifyYChanged();
        }

        @Override
        public void offsetTopAndBottom(int offset) {
            super.offsetTopAndBottom(offset);
            notifyYChanged();
        }

        @Override
        public void setTranslationY(float translationY) {
            super.setTranslationY(translationY);
            notifyYChanged();
        }

        private void notifyYChanged() {
            if (mOnYChangeCallback != null) {
                mOnYChangeCallback.run();
            }
        }
    }
}
