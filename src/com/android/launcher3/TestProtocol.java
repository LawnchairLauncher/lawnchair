/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.launcher3;

/**
 * Protocol for custom accessibility events for communication with UI Automation tests.
 */
public final class TestProtocol {
    public static final String GET_SCROLL_MESSAGE = "TAPL_GET_SCROLL";
    public static final String SCROLL_Y_FIELD = "scrollY";
    public static final String SWITCHED_TO_STATE_MESSAGE = "TAPL_SWITCHED_TO_STATE";
    public static final String RESPONSE_MESSAGE_POSTFIX = "_RESPONSE";
}
