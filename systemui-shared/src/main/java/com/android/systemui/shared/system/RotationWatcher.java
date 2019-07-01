/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.systemui.shared.system;

import android.content.Context;
import android.os.RemoteException;
import android.util.Log;
import android.view.IRotationWatcher;
import android.view.WindowManagerGlobal;

public abstract class RotationWatcher {

    private static final String TAG = "RotationWatcher";

    private final Context mContext;

    private final IRotationWatcher mWatcher = new IRotationWatcher.Stub() {

        @Override
        public void onRotationChanged(int rotation) {
            RotationWatcher.this.onRotationChanged(rotation);

        }
    };

    private boolean mIsWatching = false;

    public RotationWatcher(Context context) {
        mContext = context;
    }

    protected abstract void onRotationChanged(int rotation);

    public void enable() {
        if (!mIsWatching) {
            try {
                WindowManagerGlobal.getWindowManagerService().watchRotation(mWatcher,
                        mContext.getDisplay().getDisplayId());
                mIsWatching = true;
            } catch (RemoteException e) {
                Log.w(TAG, "Failed to set rotation watcher", e);
            }
        }
    }

    public void disable() {
        if (mIsWatching) {
            try {
                WindowManagerGlobal.getWindowManagerService().removeRotationWatcher(mWatcher);
                mIsWatching = false;
            }  catch (RemoteException e) {
                Log.w(TAG, "Failed to remove rotation watcher", e);
            }
        }
    }
}
