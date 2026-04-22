/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.wm.shell.windowdecor

/**
 * Listener that allows notifies when an animation that is repositioning a task is starting
 * and finishing the animation.
 */
interface OnTaskRepositionAnimationListener {
    /**
     * Notifies that an animation is about to be started.
     */
    fun onAnimationStart(taskId: Int)

    /**
     * Notifies that an animation is about to be finished.
     */
    fun onAnimationEnd(taskId: Int)
}
