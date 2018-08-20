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
package com.android.launcher3.uioverrides;

import android.content.Context;
import android.os.Handler;

import android.view.OrientationEventListener;
import com.android.launcher3.Utilities;
import com.android.systemui.shared.system.RotationWatcher;

/**
 * Utility class for listening for rotation changes
 */
public interface DisplayRotationListener {

    static DisplayRotationListener create(Context context, Runnable callback) {
        if (Utilities.ATLEAST_OREO && Utilities.HIDDEN_APIS_ALLOWED) {
            return new DisplayRotationListenerRW(context, callback);
        } else {
            return new DisplayRotationListenerOEL(context, callback);
        }
    }

    void enable();
    void disable();

    class DisplayRotationListenerRW extends RotationWatcher implements DisplayRotationListener {

        private final Runnable mCallback;
        private Handler mHandler;

        private DisplayRotationListenerRW(Context context, Runnable callback) {
            super(context);
            mCallback = callback;
        }

        @Override
        public void enable() {
            if (mHandler == null) {
                mHandler = new Handler();
            }
            super.enable();
        }

        @Override
        protected void onRotationChanged(int i) {
            mHandler.post(mCallback);
        }
    }

    class DisplayRotationListenerOEL extends OrientationEventListener implements DisplayRotationListener {

        private final Runnable mCallback;

        private DisplayRotationListenerOEL(Context context, Runnable callback) {
            super(context);
            mCallback = callback;
        }

        @Override
        public void onOrientationChanged(int i) {
            mCallback.run();
        }
    }

}
