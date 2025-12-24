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

package com.android.systemui.util.time.impl

import com.android.systemui.util.time.SystemClock
import dagger.Binds
import dagger.Module
import javax.inject.Singleton

@Module
public abstract class TimeModule {
    @Singleton @Binds public abstract fun bindSystemClock(impl: SystemClockImpl): SystemClock
}
