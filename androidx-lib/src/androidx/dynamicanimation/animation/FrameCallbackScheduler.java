/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.dynamicanimation.animation;


import androidx.annotation.NonNull;

/**
 * A scheduler that runs the given Runnable on the next frame.
 */
public interface FrameCallbackScheduler {
    /**
     * Callbacks on new frame arrived.
     *
     * @param frameCallback The runnable of new frame should be posted
     */
    void postFrameCallback(@NonNull Runnable frameCallback);

    /**
     * Returns whether the current thread is the same as the thread that the scheduler is
     * running on.
     *
     * @return true if the scheduler is running on the same thread as the current thread.
     */
    boolean isCurrentThread();
}
