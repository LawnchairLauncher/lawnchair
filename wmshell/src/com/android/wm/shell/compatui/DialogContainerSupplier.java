/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell.compatui;

import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * A component which can provide a {@link View} to use as a container for a Dialog
 */
public interface DialogContainerSupplier {

    /**
     * @return The {@link View} to use as a container for a Dialog
     */
    View getDialogContainerView();

    /**
     * @return The {@link Drawable} to use as background of the dialog.
     */
    Drawable getBackgroundDimDrawable();
}
