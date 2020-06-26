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

package com.android.launcher3.graphics;

import static com.android.launcher3.config.FeatureFlags.MULTI_DB_GRID_MIRATION_ALGO;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.view.Display;
import android.view.SurfaceControlViewHost;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.model.GridSizeMigrationTask;
import com.android.launcher3.model.GridSizeMigrationTaskV2;

import java.util.concurrent.TimeUnit;

/** Render preview using surface view. */
public class PreviewSurfaceRenderer implements IBinder.DeathRecipient {

    private static final int FADE_IN_ANIMATION_DURATION = 200;

    private static final String KEY_HOST_TOKEN = "host_token";
    private static final String KEY_VIEW_WIDTH = "width";
    private static final String KEY_VIEW_HEIGHT = "height";
    private static final String KEY_DISPLAY_ID = "display_id";
    private static final String KEY_SURFACE_PACKAGE = "surface_package";
    private static final String KEY_CALLBACK = "callback";

    private final Context mContext;
    private final InvariantDeviceProfile mIdp;
    private final IBinder mHostToken;
    private final int mWidth;
    private final int mHeight;
    private final Display mDisplay;

    private SurfaceControlViewHost mSurfaceControlViewHost;

    PreviewSurfaceRenderer(Context context, Bundle bundle) {
        mContext = context;

        String gridName = bundle.getString("name");
        bundle.remove("name");
        if (gridName == null) {
            gridName = InvariantDeviceProfile.getCurrentGridName(context);
        }
        mIdp = new InvariantDeviceProfile(context, gridName);

        mHostToken = bundle.getBinder(KEY_HOST_TOKEN);
        mWidth = bundle.getInt(KEY_VIEW_WIDTH);
        mHeight = bundle.getInt(KEY_VIEW_HEIGHT);

        final DisplayManager displayManager = (DisplayManager) context.getSystemService(
                Context.DISPLAY_SERVICE);
        mDisplay = displayManager.getDisplay(bundle.getInt(KEY_DISPLAY_ID));
    }

    /** Handle a received surface view request. */
    Bundle render() {
        if (mSurfaceControlViewHost != null) {
            binderDied();
        }

        SurfaceControlViewHost.SurfacePackage surfacePackage;
        try {
            mSurfaceControlViewHost = MAIN_EXECUTOR
                    .submit(() -> new SurfaceControlViewHost(mContext, mDisplay, mHostToken))
                    .get(5, TimeUnit.SECONDS);
            surfacePackage = mSurfaceControlViewHost.getSurfacePackage();
            mHostToken.linkToDeath(this, 0);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        MODEL_EXECUTOR.post(() -> {
            final boolean success = doGridMigrationIfNecessary();

            MAIN_EXECUTOR.post(() -> {
                // If mSurfaceControlViewHost is null due to any reason (e.g. binder died,
                // happening when user leaves the preview screen before preview rendering finishes),
                // we should return here.
                SurfaceControlViewHost host = mSurfaceControlViewHost;
                if (host == null) {
                    return;
                }

                View view = new LauncherPreviewRenderer(mContext, mIdp, success).getRenderedView();
                // This aspect scales the view to fit in the surface and centers it
                final float scale = Math.min(mWidth / (float) view.getMeasuredWidth(),
                        mHeight / (float) view.getMeasuredHeight());
                view.setScaleX(scale);
                view.setScaleY(scale);
                view.setPivotX(0);
                view.setPivotY(0);
                view.setTranslationX((mWidth - scale * view.getWidth()) / 2);
                view.setTranslationY((mHeight - scale * view.getHeight()) / 2);
                view.setAlpha(0);
                view.animate().alpha(1)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .setDuration(FADE_IN_ANIMATION_DURATION)
                        .start();
                host.setView(view, view.getMeasuredWidth(), view.getMeasuredHeight());
            });
        });

        Bundle result = new Bundle();
        result.putParcelable(KEY_SURFACE_PACKAGE, surfacePackage);

        Handler handler = new Handler(Looper.getMainLooper(), message -> {
            binderDied();
            return true;
        });
        Messenger messenger = new Messenger(handler);
        Message msg = Message.obtain();
        msg.replyTo = messenger;
        result.putParcelable(KEY_CALLBACK, msg);
        return result;
    }

    @Override
    public void binderDied() {
        if (mSurfaceControlViewHost != null) {
            MAIN_EXECUTOR.execute(() -> {
                mSurfaceControlViewHost.release();
                mSurfaceControlViewHost = null;
            });
        }
        mHostToken.unlinkToDeath(this, 0);
    }

    private boolean doGridMigrationIfNecessary() {
        boolean needsToMigrate =
                MULTI_DB_GRID_MIRATION_ALGO.get()
                        ? GridSizeMigrationTaskV2.needsToMigrate(mContext, mIdp)
                        : GridSizeMigrationTask.needsToMigrate(mContext, mIdp);
        if (!needsToMigrate) {
            return false;
        }
        return MULTI_DB_GRID_MIRATION_ALGO.get()
                ? GridSizeMigrationTaskV2.migrateGridIfNeeded(mContext, mIdp)
                : GridSizeMigrationTask.migrateGridIfNeeded(mContext, mIdp);
    }
}
