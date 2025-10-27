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

package com.android.launcher3.util;

/**
 * Proxy class used for StateManager ProtoLog support.
 */
public class StateManagerProtoLogProxy {

    public static void logGoToState(Object fromState, Object toState, String trace) { }

    public static void logCreateAtomicAnimation(Object fromState, Object toState, String trace) { }

    public static void logOnStateTransitionStart(Object state) { }

    public static void logOnStateTransitionEnd(Object state) { }

    public static void logCancelAnimation(boolean animationOngoing, String trace) { }
}
