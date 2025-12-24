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
package com.android.wm.shell.compatui.api

import java.util.function.Consumer

/**
 * Abstraction for the objects responsible to handle all the CompatUI components and the
 * communication with the server.
 */
interface CompatUIHandler {
    /** Invoked when a new model is coming from the server. */
    fun onCompatInfoChanged(compatUIInfo: CompatUIInfo)

    /** Invoked when another component in Shell requests a CompatUI state change. */
    fun sendCompatUIRequest(compatUIRequest: CompatUIRequest)

    /** Optional reference to the object responsible to send {@link CompatUIEvent} */
    fun setCallback(compatUIEventSender: Consumer<CompatUIEvent>?)
}
