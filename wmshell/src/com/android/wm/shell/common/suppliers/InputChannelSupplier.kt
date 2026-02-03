/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.wm.shell.common.suppliers

import android.view.InputChannel
import com.android.wm.shell.dagger.WMSingleton
import java.util.function.Supplier
import javax.inject.Inject

/**
 * An Injectable [Supplier<InputChannel>]. This can be used in place of kotlin default
 * parameters values [builder = ::InputChannel] which requires the [@JvmOverloads] annotation to
 * make this available in Java.
 * This can be used every time a component needs the dependency to the default [Supplier] for
 * [InputChannel]s.
 */
@WMSingleton
class InputChannelSupplier @Inject constructor() : Supplier<InputChannel> {
    override fun get(): InputChannel = InputChannel()
}
