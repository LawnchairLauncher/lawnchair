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

package com.android.wm.shell.pip;

/**
 * Listener interface that Launcher attaches to SystemUI to get Pip animation callbacks.
 */
oneway interface IPipAnimationListener {
    /**
     * Notifies the listener that the Pip animation is started.
     */
    void onPipAnimationStarted();

    /**
     * Notifies the listener about PiP resource dimensions changed.
     * Listener can expect an immediate callback the first time they attach.
     *
     * @param cornerRadius the pixel value of the corner radius, zero means it's disabled.
     * @param shadowRadius the pixel value of the shadow radius, zero means it's disabled.
     */
    void onPipResourceDimensionsChanged(int cornerRadius, int shadowRadius);

    /**
     * Notifies the listener that user leaves PiP by tapping on the expand button.
     */
    void onExpandPip();
}
