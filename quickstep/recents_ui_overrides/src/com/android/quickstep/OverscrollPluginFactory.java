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
package com.android.quickstep;

import static com.android.launcher3.util.MainThreadInitializedObject.forOverride;

import com.android.launcher3.R;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.ResourceBasedOverride;
import com.android.systemui.plugins.OverscrollPlugin;

/**
 * Resource overrideable factory for forcing a local overscroll plugin.
 * Override {@link R.string#overscroll_plugin_factory_class} to set a different class.
 */
public class OverscrollPluginFactory implements ResourceBasedOverride {
    public static final MainThreadInitializedObject<OverscrollPluginFactory> INSTANCE = forOverride(
            OverscrollPluginFactory.class,
            R.string.overscroll_plugin_factory_class);

    /**
     * Get the plugin that is defined locally in launcher, as opposed to a dynamic side loaded one.
     */
    public OverscrollPlugin getLocalOverscrollPlugin() {
        return null;
    }
}
