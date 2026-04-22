/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.content.Intent.EXTRA_STREAM;
import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;

import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.quickstep.util.ImageActionUtils.persistBitmapAndStartActivity;

import android.app.prediction.AppTarget;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.graphics.Bitmap;
import android.graphics.Insets;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.launcher3.BuildConfig;
import com.android.quickstep.util.ImageActionUtils;
import com.android.systemui.shared.recents.model.Task;

import java.util.function.Supplier;

/**
 * Contains image selection functions necessary to complete overview action button functions.
 */
public class ImageActionsApi {

    private static final String TAG = BuildConfig.APPLICATION_ID + "ImageActionsApi";

    protected final Context mContext;
    protected final Supplier<Bitmap> mBitmapSupplier;
    protected final SystemUiProxy mSystemUiProxy;

    public ImageActionsApi(Context context, Supplier<Bitmap> bitmapSupplier) {
        mContext = context;
        mBitmapSupplier = bitmapSupplier;
        mSystemUiProxy = SystemUiProxy.INSTANCE.get(context);
    }

    /**
     * Share the image this api was constructed with using the provided intent. The implementation
     * should add an {@link Intent#EXTRA_STREAM} with the URI pointing to the image to the intent.
     */
    @UiThread
    public void shareWithExplicitIntent(@Nullable Rect crop, Intent intent) {
        addImageAndSendIntent(crop, intent, false, null /* exceptionCallback */);
    }

    /**
     * Share the image this api was constructed with using the provided intent. The implementation
     * should set the intent's data field to the URI pointing to the image.
     * @param exceptionCallback An optional callback to be called when the intent can't be resolved
     */
    @UiThread
    public void shareAsDataWithExplicitIntent(@Nullable Rect crop, Intent intent,
            @Nullable Runnable exceptionCallback) {
        addImageAndSendIntent(crop, intent, true, exceptionCallback);
    }

    private void addImageAndSendIntent(@Nullable Rect crop, Intent intent, boolean setData,
            @Nullable Runnable exceptionCallback) {

        UI_HELPER_EXECUTOR.execute(() -> {
            Bitmap bitmap = mBitmapSupplier.get();
            if (bitmap == null) {
                Log.e(TAG, "No snapshot available, not starting share.");
                return;
            }
            persistBitmapAndStartActivity(mContext,
                    bitmap, crop, intent, (uri, intentForUri) -> {
                    intentForUri.addFlags(FLAG_GRANT_READ_URI_PERMISSION);
                    if (setData) {
                        intentForUri.setData(uri);
                    } else {
                        intentForUri.putExtra(EXTRA_STREAM, uri);
                    }
                    return new Intent[]{intentForUri};
                }, TAG, exceptionCallback);
        });
    }

    /**
     * Share the image this api was constructed with.
     */
    @UiThread
    public void startShareActivity(Rect crop) {
        ImageActionUtils.startShareActivity(mContext, mBitmapSupplier, crop, null, TAG);
    }

    @UiThread
    public void startLensActivity() {
        ImageActionUtils.startLensActivity(mContext, mBitmapSupplier, null, null, TAG);
    }

    /**
     * @param screenshot       to be saved to the media store.
     * @param screenshotBounds the location of where the bitmap was laid out on the screen in
     *                         screen coordinates.
     * @param visibleInsets    that are used to draw the screenshot within the bounds.
     * @param task             of the task that the screenshot was taken of.
     */
    public void saveScreenshot(Bitmap screenshot, Rect screenshotBounds,
            Insets visibleInsets, Task.TaskKey task) {
        ImageActionUtils.saveScreenshot(mSystemUiProxy, screenshot, screenshotBounds, visibleInsets,
                task);
    }

    /**
     * Share the image when user taps on overview share targets.
     */
    @UiThread
    public void shareImage(RectF rectF, ShortcutInfo shortcutInfo, AppTarget appTarget) {
        ImageActionUtils.shareImage(mContext, mBitmapSupplier, rectF, shortcutInfo, appTarget, TAG);
    }
}
