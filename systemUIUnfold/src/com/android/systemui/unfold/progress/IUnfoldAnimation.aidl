/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.unfold.progress;


import com.android.systemui.unfold.progress.IUnfoldTransitionListener;


/**
 * Interface exposed by System UI to allow remote process to register for unfold animation events.
 */
oneway interface IUnfoldAnimation {

    /**
     * Sets a listener for the animation.
     *
     * Only one listener is supported. If there are multiple, the earlier one will be overridden.
     */
    void setListener(in IUnfoldTransitionListener listener);
}
