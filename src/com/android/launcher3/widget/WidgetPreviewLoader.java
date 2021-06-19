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
package com.android.launcher3.widget;

import android.graphics.Bitmap;
import android.os.CancellationSignal;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.model.WidgetItem;

/** Asynchronous loader of preview bitmaps for {@link WidgetItem}s. */
public interface WidgetPreviewLoader {
    /**
     * Loads a widget preview and calls back to {@code callback} when complete.
     *
     * @return a {@link CancellationSignal} which can be used to cancel the request.
     */
    @NonNull
    @UiThread
    CancellationSignal loadPreview(
            @NonNull BaseActivity activity,
            @NonNull WidgetItem item,
            @NonNull Size previewSize,
            @NonNull WidgetPreviewLoadedCallback callback);

    /** Callback class for requests to {@link WidgetPreviewLoader}. */
    interface WidgetPreviewLoadedCallback {
        void onPreviewLoaded(@NonNull Bitmap preview);
    }
}
