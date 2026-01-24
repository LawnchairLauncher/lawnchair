/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.launcher3.pm;

import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.LauncherApps.PinItemRequest;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.os.Build;
import android.os.Parcelable;
import android.os.SystemClock;

import androidx.annotation.Nullable;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.icons.CacheableShortcutCachingLogic;
import com.android.launcher3.icons.CacheableShortcutInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;

public class PinRequestHelper {

    /**
     * request.accept() will initiate the following flow:
     *      -> go-to-system-process for actual processing (a)
     *      -> callback-to-launcher on UI thread (b)
     *      -> post callback on the worker thread (c)
     *      -> Update model and unpin (in system) any shortcut not in out model. (d)
     *
     * Note that (b) will take at-least one frame as it involves posting callback from binder
     * thread to UI thread.
     * If (d) happens before we add this shortcut to our model, we will end up unpinning
     * the shortcut in the system.
     * Here its the caller's responsibility to add the newly created WorkspaceItemInfo immediately
     * to the model (which may involves a single post-to-worker-thread). That will guarantee
     * that (d) happens after model is updated.
     */
    @Nullable
    @TargetApi(Build.VERSION_CODES.O)
    public static WorkspaceItemInfo createWorkspaceItemFromPinItemRequest(
            Context context, final PinItemRequest request, final long acceptDelay) {
        if (request != null && request.getRequestType() == PinItemRequest.REQUEST_TYPE_SHORTCUT
                && request.isValid()) {

            if (acceptDelay <= 0) {
                if (!request.accept()) {
                    return null;
                }
            } else {
                // Block the worker thread until the accept() is called.
                MODEL_EXECUTOR.execute(() -> {
                    SystemClock.sleep(acceptDelay);
                    if (request.isValid()) {
                        request.accept();
                    }
                });
            }

            ShortcutInfo si = request.getShortcutInfo();
            WorkspaceItemInfo info = new WorkspaceItemInfo(si, context);
            // Apply the unbadged icon synchronously using the caching logic directly and
            // fetch the actual icon asynchronously.
            LauncherAppState app = LauncherAppState.getInstance(context);
            info.bitmap = CacheableShortcutCachingLogic.INSTANCE.loadIcon(
                    context, app.getIconCache(), new CacheableShortcutInfo(si, context));
            app.getModel().updateAndBindWorkspaceItem(info, si);
            return info;
        } else {
            return null;
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    public static PinItemRequest getPinItemRequest(Intent intent) {
        Parcelable extra = intent.getParcelableExtra(LauncherApps.EXTRA_PIN_ITEM_REQUEST);
        return extra instanceof PinItemRequest ? (PinItemRequest) extra : null;
    }

    /**
     * Returns a PinItemRequest corresponding to the provided ShortcutInfo
     */
    public static PinItemRequest createRequestForShortcut(Context context, ShortcutInfo info) {
        return context.getSystemService(LauncherApps.class)
                .getPinItemRequest(context.getSystemService(ShortcutManager.class)
                        .createShortcutResultIntent(info));
    }
}
