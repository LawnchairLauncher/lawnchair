/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.plugins;

import android.content.Intent;
import android.graphics.drawable.Drawable;

import com.android.systemui.plugins.annotations.ProvidesInterface;

/**
 * An Intent Button represents a triggerable element in SysUI that consists of an
 * Icon and an intent to trigger when it is activated (clicked, swiped, etc.).
 */
@ProvidesInterface(version = IntentButtonProvider.VERSION)
public interface IntentButtonProvider extends Plugin {

    public static final int VERSION = 1;

    public IntentButton getIntentButton();

    public interface IntentButton {
        public static class IconState {
            public boolean isVisible = true;
            public CharSequence contentDescription = null;
            public Drawable drawable;
            public boolean tint = true;
        }

        public IconState getIcon();

        public Intent getIntent();
    }
}
