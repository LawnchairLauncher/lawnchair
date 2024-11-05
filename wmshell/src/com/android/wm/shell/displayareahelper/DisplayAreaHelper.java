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

import java.util.function.Consumer;

/**
 * Interface that allows to perform various display area related actions
 */
public interface DisplayAreaHelper {

    /**
     * Updates SurfaceControl builder to reparent it to the root display area
     * @param displayId id of the display to which root display area it should be reparented to
     * @param builder surface control builder that should be updated
     * @param onUpdated callback that is invoked after updating the builder, called on
     *                  the shell main thread
     */
    default void attachToRootDisplayArea(int displayId, SurfaceControl.Builder builder,
            Consumer<SurfaceControl.Builder> onUpdated) {
    }

}
