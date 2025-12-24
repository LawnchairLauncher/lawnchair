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

package com.android.systemui.log

/** Dagger-friendly interface so we can inject a fake [android.util.Log] in tests */
public interface LogProxy {
    /** verbose log */
    public fun v(tag: String, message: String)

    /** debug log */
    public fun d(tag: String, message: String)

    /** info log */
    public fun i(tag: String, message: String)

    /** warning log */
    public fun w(tag: String, message: String)

    /** error log */
    public fun e(tag: String, message: String)

    /** wtf log */
    public fun wtf(tag: String, message: String)
}
