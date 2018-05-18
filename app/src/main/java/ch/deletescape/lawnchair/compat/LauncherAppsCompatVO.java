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

package ch.deletescape.lawnchair.compat;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.LauncherApps.PinItemRequest;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import ch.deletescape.lawnchair.LauncherAppState;
import ch.deletescape.lawnchair.LauncherModel;
import ch.deletescape.lawnchair.ShortcutInfo;
import ch.deletescape.lawnchair.graphics.LauncherIcons;
import ch.deletescape.lawnchair.shortcuts.ShortcutInfoCompat;
import ch.deletescape.lawnchair.util.LooperExecutor;

@TargetApi(26)
public class LauncherAppsCompatVO extends LauncherAppsCompatVL {

    LauncherAppsCompatVO(Context context) {
        super(context);
    }

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
     * Here its the caller's responsibility to add the newly created ShortcutInfo immediately
     * to the model (which may involves a single post-to-worker-thread). That will guarantee
     * that (d) happens after model is updated.
     */
    @Nullable
    public static ShortcutInfo createShortcutInfoFromPinItemRequest(
            Context context, final PinItemRequest request, final long acceptDelay) {
        if (request != null &&
                request.getRequestType() == PinItemRequest.REQUEST_TYPE_SHORTCUT &&
                request.isValid()) {

            if (acceptDelay <= 0) {
                if (!request.accept()) {
                    return null;
                }
            } else {
                // Block the worker thread until the accept() is called.
                new LooperExecutor(LauncherModel.getWorkerLooper()).execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(acceptDelay);
                        } catch (InterruptedException e) {
                            // Ignore
                        }
                        if (request.isValid()) {
                            request.accept();
                        }
                    }
                });
            }

            ShortcutInfoCompat compat = new ShortcutInfoCompat(request.getShortcutInfo());
            ShortcutInfo info = new ShortcutInfo(compat, context);
            // Apply the unbadged icon and fetch the actual icon asynchronously.
            info.iconBitmap = LauncherIcons
                    .createShortcutIcon(compat, context, false /* badged */);
            LauncherAppState.getInstance().getModel()
                    .updateAndBindShortcutInfo(info, compat);
            return info;
        } else {
            return null;
        }
    }

    public static PinItemRequest getPinItemRequest(Intent intent) {
        Parcelable extra = intent.getParcelableExtra(LauncherApps.EXTRA_PIN_ITEM_REQUEST);
        return extra instanceof PinItemRequest ? (PinItemRequest) extra : null;
    }
}
