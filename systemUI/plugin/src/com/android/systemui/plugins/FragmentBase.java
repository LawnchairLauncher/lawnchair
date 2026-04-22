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

import android.content.Context;
import android.view.View;

/**
 * Interface to deal with lack of multiple inheritance
 *
 * This interface is designed to be used as a base class for plugin interfaces
 * that need fragment methods. Plugins should not extend Fragment directly, so
 * plugins that are fragments should be extending PluginFragment, but in SysUI
 * these same versions should extend Fragment directly.
 *
 * Only methods that are on Fragment should be included here.
 */
public interface FragmentBase {
    View getView();
    Context getContext();
}
