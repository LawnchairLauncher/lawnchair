/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3.debug;

/**
 * TestEventsEmitter shouldn't do anything since it runs on the launcher code and not on
 * tests. This is just a placeholder and test should mock the static sendEvent method.
 * See "EventsRule.kt" in tests folder where sendEvent is statically mocked to change the
 * behavior in tests.
 */
public class TestEventEmitter {
    public static void sendEvent(TestEvent event) {
    }

    /** Events fired by the launcher. */
    public enum TestEvent {

        LAUNCHER_ON_CREATE("LAUNCHER_ON_CREATE"),
        WORKSPACE_ON_DROP("WORKSPACE_ON_DROP"),
        RESIZE_FRAME_SHOWING("RESIZE_FRAME_SHOWING"),
        WORKSPACE_FINISH_LOADING("WORKSPACE_FINISH_LOADING"),
        SPRING_LOADED_STATE_STARTED("SPRING_LOADED_STATE_STARTED"),
        SPRING_LOADED_STATE_COMPLETED("SPRING_LOADED_STATE_COMPLETED"),

        LAUNCHER_STATE_COMPLETED("LAUNCHER_STATE_COMPLETED");

        TestEvent(String event) {
        }

    }
}


