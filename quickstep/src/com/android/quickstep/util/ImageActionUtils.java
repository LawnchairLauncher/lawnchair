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

package com.android.quickstep.util;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;
import static android.view.WindowManager.ScreenshotSource.SCREENSHOT_OVERVIEW;
import static android.view.WindowManager.TAKE_SCREENSHOT_PROVIDED_IMAGE;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.THREAD_POOL_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.prediction.AppTarget;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Insets;
import android.graphics.Picture;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.util.Log;
import android.view.View;

import androidx.annotation.WorkerThread;
import androidx.core.content.FileProvider;

import com.android.internal.app.ChooserActivity;
import com.android.internal.util.ScreenshotRequest;
import com.android.launcher3.BuildConfig;
import com.android.quickstep.SystemUiProxy;
import com.android.systemui.shared.recents.model.Task;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Utility class containing methods to help manage image actions such as sharing, cropping, and
 * saving image.
 */
public class ImageActionUtils {

    private static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".overview.fileprovider";
    private static final long FILE_LIFE = 1000L /*ms*/ * 60L /*s*/ * 60L /*m*/ * 24L /*h*/;
    private static final String SUB_FOLDER = "Overview";
    private static final String BASE_NAME = "overview_image_";
    private static final String TAG = "ImageActionUtils";

    /**
     * Saves screenshot to location determine by SystemUiProxy
     */
    public static void saveScreenshot(SystemUiProxy systemUiProxy, Bitmap screenshot,
            Rect screenshotBounds, Insets visibleInsets, Task.TaskKey task) {
        ScreenshotRequest request =
                new ScreenshotRequest.Builder(TAKE_SCREENSHOT_PROVIDED_IMAGE, SCREENSHOT_OVERVIEW)
                .setTopComponent(task.sourceComponent)
                .setTaskId(task.id)
                .setUserId(task.userId)
                .setBitmap(screenshot)
                .setBoundsOnScreen(screenshotBounds)
                .setInsets(visibleInsets)
                .build();
        systemUiProxy.takeScreenshot(request);
    }

    /**
     * Launch the activity to share image for overview sharing. This is to share cropped bitmap
     * with specific share targets (with shortcutInfo and appTarget) rendered in overview.
     */
    public static void shareImage(Context context, Supplier<Bitmap> bitmapSupplier, RectF rectF,
            ShortcutInfo shortcutInfo, AppTarget appTarget, String tag) {
        UI_HELPER_EXECUTOR.execute(() -> {
            Bitmap bitmap = bitmapSupplier.get();
            if (bitmap == null) {
                return;
            }
            Rect crop = new Rect();
            rectF.round(crop);
            Intent intent = new Intent();
            Uri uri = getImageUri(bitmap, crop, context, tag);
            ClipData clipdata = new ClipData(new ClipDescription("content",
                    new String[]{"image/png"}),
                    new ClipData.Item(uri));
            intent.setAction(Intent.ACTION_SEND)
                    .setComponent(
                            new ComponentName(appTarget.getPackageName(), appTarget.getClassName()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .addFlags(FLAG_GRANT_READ_URI_PERMISSION)
                    .setType("image/png")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .putExtra(Intent.EXTRA_SHORTCUT_ID, shortcutInfo.getId())
                    .setClipData(clipdata);

            if (context.getUserId() != appTarget.getUser().getIdentifier()) {
                intent.prepareToLeaveUser(context.getUserId());
                intent.fixUris(context.getUserId());
                context.startActivityAsUser(intent, appTarget.getUser());
            } else {
                context.startActivity(intent);
            }
        });
    }

    /**
     * Launch the activity to share image.
     */
    public static void startShareActivity(Context context, Supplier<Bitmap> bitmapSupplier,
            Rect crop, Intent intent, String tag) {
        UI_HELPER_EXECUTOR.execute(() -> {
            Bitmap bitmap = bitmapSupplier.get();
            if (bitmap == null) {
                Log.e(tag, "No snapshot available, not starting share.");
                return;
            }
            persistBitmapAndStartActivity(context, bitmap, crop, intent,
                    ImageActionUtils::getShareIntentForImageUri, tag);
        });
    }

    /**
     * Launch the activity to share image with shared element transition.
     */
    public static void startShareActivity(Context context, Supplier<Bitmap> bitmapSupplier,
            Rect crop, Intent intent, String tag, View sharedElement) {
        UI_HELPER_EXECUTOR.execute(() -> {
            Bitmap bitmap = bitmapSupplier.get();
            if (bitmap == null) {
                Log.e(tag, "No snapshot available, not starting share.");
                return;
            }
            persistBitmapAndStartActivity(context, bitmap,
                    crop, intent, ImageActionUtils::getShareIntentForImageUri, tag, sharedElement);
        });
    }

    /**
     * Starts activity based on given intent created from image uri.
     */
    @WorkerThread
    public static void persistBitmapAndStartActivity(Context context, Bitmap bitmap, Rect crop,
            Intent intent, BiFunction<Uri, Intent, Intent[]> uriToIntentMap, String tag) {
        persistBitmapAndStartActivity(context, bitmap, crop, intent, uriToIntentMap, tag,
                (Runnable) null);
    }

    /**
     * Starts activity based on given intent created from image uri.
     * @param exceptionCallback An optional callback to be called when the intent can't be resolved
     */
    @WorkerThread
    public static void persistBitmapAndStartActivity(Context context, Bitmap bitmap, Rect crop,
            Intent intent, BiFunction<Uri, Intent, Intent[]> uriToIntentMap, String tag,
            Runnable exceptionCallback) {
        Intent[] intents = uriToIntentMap.apply(getImageUri(bitmap, crop, context, tag), intent);

        try {
            // Work around b/159412574
            if (intents.length == 1) {
                context.startActivity(intents[0]);
            } else {
                context.startActivities(intents);
            }
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "No activity found to receive image intent");
            if (exceptionCallback != null) {
                exceptionCallback.run();
            }
        }
    }

    /**
     * Starts activity based on given intent created from image uri with shared element transition.
     */
    @WorkerThread
    public static void persistBitmapAndStartActivity(Context context, Bitmap bitmap, Rect crop,
            Intent intent, BiFunction<Uri, Intent, Intent[]> uriToIntentMap, String tag,
            View scaledImage) {
        Intent[] intents = uriToIntentMap.apply(getImageUri(bitmap, crop, context, tag), intent);

        // Work around b/159412574
        if (intents.length == 1) {
            MAIN_EXECUTOR.execute(() -> context.startActivity(intents[0],
                    ActivityOptions.makeSceneTransitionAnimation((Activity) context, scaledImage,
                            ChooserActivity.FIRST_IMAGE_PREVIEW_TRANSITION_NAME).toBundle()));

        } else {
            MAIN_EXECUTOR.execute(() -> context.startActivities(intents,
                    ActivityOptions.makeSceneTransitionAnimation((Activity) context, scaledImage,
                            ChooserActivity.FIRST_IMAGE_PREVIEW_TRANSITION_NAME).toBundle()));
        }
    }



    /**
     * Converts image bitmap to Uri by temporarily saving bitmap to cache, and creating Uri pointing
     * to that location. Used to be able to share an image with another app.
     *
     * @param bitmap  The whole bitmap to be shared.
     * @param crop    The section of the bitmap to be shared.
     * @param context The application context, used to interact with file system.
     * @param tag     Tag used to log errors.
     * @return Uri that points to the cropped version of desired bitmap to share.
     */
    @WorkerThread
    public static Uri getImageUri(Bitmap bitmap, Rect crop, Context context, String tag) {
        clearOldCacheFiles(context);
        Bitmap croppedBitmap = cropBitmap(bitmap, crop);
        int cropHash = crop == null ? 0 : crop.hashCode();
        String baseName = BASE_NAME + bitmap.hashCode() + "_" + cropHash + ".png";
        File parent = new File(context.getCacheDir(), SUB_FOLDER);
        parent.mkdir();
        File file = new File(parent, baseName);

        try (FileOutputStream fos = new FileOutputStream(file)) {
            croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
        } catch (IOException e) {
            Log.e(tag, "Error saving image", e);
        }

        return FileProvider.getUriForFile(context, AUTHORITY, file);
    }

    /**
     * Crops the bitmap to the provided size and returns a software backed bitmap whenever possible.
     *
     * @param bitmap The bitmap to be cropped.
     * @param crop   The section of the bitmap in the crop.
     * @return The cropped bitmap.
     */
    @WorkerThread
    public static Bitmap cropBitmap(Bitmap bitmap, Rect crop) {
        Rect src = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        if (crop == null) {
            crop = new Rect(src);
        }
        if (crop.equals(src)) {
            return bitmap;
        } else {
            if (bitmap.getConfig() != Bitmap.Config.HARDWARE) {
                return Bitmap.createBitmap(bitmap, crop.left, crop.top, crop.width(),
                        crop.height());
            }

            // For hardware bitmaps, use the Picture API to directly create a software bitmap
            Picture picture = new Picture();
            Canvas canvas = picture.beginRecording(crop.width(), crop.height());
            canvas.drawBitmap(bitmap, -crop.left, -crop.top, null);
            picture.endRecording();
            return Bitmap.createBitmap(picture, crop.width(), crop.height(),
                    Bitmap.Config.ARGB_8888);
        }
    }

    /**
     * Gets the intent used to share image.
     */
    @WorkerThread
    private static Intent[] getShareIntentForImageUri(Uri uri, Intent intent) {
        if (intent == null) {
            intent = new Intent();
        }
        ClipData clipdata = new ClipData(new ClipDescription("content",
                new String[]{"image/png"}),
                new ClipData.Item(uri));
        intent.setAction(Intent.ACTION_SEND)
                .setComponent(null)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(FLAG_GRANT_READ_URI_PERMISSION)
                .setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .setClipData(clipdata);
        return new Intent[]{Intent.createChooser(intent, null).addFlags(FLAG_ACTIVITY_NEW_TASK)};
    }

    private static void clearOldCacheFiles(Context context) {
        THREAD_POOL_EXECUTOR.execute(() -> {
            File parent = new File(context.getCacheDir(), SUB_FOLDER);
            File[] files = parent.listFiles((File f, String s) -> s.startsWith(BASE_NAME));
            if (files != null) {
                for (File file: files) {
                    if (file.lastModified() + FILE_LIFE < System.currentTimeMillis()) {
                        file.delete();
                    }
                }
            }
        });

    }
}
