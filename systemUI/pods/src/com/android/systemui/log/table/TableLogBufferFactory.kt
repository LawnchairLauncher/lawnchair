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

package com.android.systemui.log.table

/** Factory for creating [TableLogBuffer] instances. */
public interface TableLogBufferFactory {
    /**
     * Creates a new [TableLogBuffer]. This method should only be called from static contexts, where
     * it is guaranteed only to be created one time. See [getOrCreate] for a cache-aware method of
     * obtaining a buffer.
     *
     * @param name a unique table name
     * @param maxSize the buffer max size. See [com.android.systemui.log.adjustMaxSize]
     * @return a new [TableLogBuffer] registered with [com.android.systemui.dump.DumpManager]
     *
     * TODO(b/307607958): Remove this method and always use [getOrCreate] instead.
     */
    public fun create(name: String, maxSize: Int): TableLogBuffer

    /**
     * Log buffers are retained indefinitely by [com.android.systemui.dump.DumpManager], so that
     * they can be represented in bugreports. Because of this, many of them are created statically
     * in the Dagger graph.
     *
     * In the case where you have to create a logbuffer with a name only known at runtime, this
     * method can be used to lazily create a table log buffer which is then cached for reuse.
     *
     * @return a [TableLogBuffer] suitable for reuse
     */
    public fun getOrCreate(name: String, maxSize: Int): TableLogBuffer
}
