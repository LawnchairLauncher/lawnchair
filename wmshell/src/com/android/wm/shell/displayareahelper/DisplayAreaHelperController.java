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

package com.android.wm.shell.displayareahelper;

import android.view.SurfaceControl;

import com.android.wm.shell.RootDisplayAreaOrganizer;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class DisplayAreaHelperController implements DisplayAreaHelper {

    private final Executor mExecutor;
    private final RootDisplayAreaOrganizer mRootDisplayAreaOrganizer;

    public DisplayAreaHelperController(Executor executor,
            RootDisplayAreaOrganizer rootDisplayAreaOrganizer) {
        mExecutor = executor;
        mRootDisplayAreaOrganizer = rootDisplayAreaOrganizer;
    }

    @Override
    public void attachToRootDisplayArea(int displayId, SurfaceControl.Builder builder,
            Consumer<SurfaceControl.Builder> onUpdated) {
        mExecutor.execute(() -> {
            mRootDisplayAreaOrganizer.attachToDisplayArea(displayId, builder);
            onUpdated.accept(builder);
        });
    }
}
